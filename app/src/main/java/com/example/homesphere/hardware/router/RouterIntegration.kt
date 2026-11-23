package com.example.homesphere.hardware.router

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import com.example.homesphere.domain.model.ConnectedDevice
import com.example.homesphere.domain.model.DiscoveredDeviceCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Future Router API integration contract (e.g., OpenWrt ubus, ASUSWRT, UniFi, or TR-069 gateway).
 */
interface RouterApiClient {
    suspend fun fetchRouterClients(routerIp: String): Result<List<ConnectedDevice>>
    suspend fun fetchRouterUptimeSeconds(routerIp: String): Result<Long>
}

class StubRouterApiClient : RouterApiClient {
    override suspend fun fetchRouterClients(routerIp: String): Result<List<ConnectedDevice>> =
        Result.failure(UnsupportedOperationException("Router API credentials not configured for $routerIp"))

    override suspend fun fetchRouterUptimeSeconds(routerIp: String): Result<Long> =
        Result.failure(UnsupportedOperationException("Router API credentials not configured for $routerIp"))
}

data class LocalAndroidNetworkSnapshot(
    val hasInternet: Boolean,
    val isWifiConnected: Boolean,
    val isActiveConnected: Boolean,
    val ssid: String?,
    val localIp: String?,
    val gatewayIp: String?,
    val subnetMask: String?,
    val rssiDbm: Int?,
    val frequencyMhz: Int?,
    val linkDownstreamKbps: Int?,
    val linkUpstreamKbps: Int?
)

/**
 * Reads 100% real-time Android network telemetry via ConnectivityManager, LinkProperties,
 * TrafficStats, real TCP/ICMP latency probes, and NsdManager (mDNS) discovery.
 */
class AndroidNetworkInspector(private val context: Context) {

    private var lastRxBytes: Long = TrafficStats.getTotalRxBytes().takeIf { it != TrafficStats.UNSUPPORTED.toLong() } ?: 0L
    private var lastTxBytes: Long = TrafficStats.getTotalTxBytes().takeIf { it != TrafficStats.UNSUPPORTED.toLong() } ?: 0L
    private var lastTrafficTimeMs: Long = System.currentTimeMillis()

    fun inspectCurrentNetwork(): LocalAndroidNetworkSnapshot = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = cm?.activeNetwork
        val caps = activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val linkProps = activeNetwork?.let { cm.getLinkProperties(it) }

        val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) || activeNetwork != null)
        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val isConnected = activeNetwork != null && caps != null

        val ipv4LinkAddress = linkProps?.linkAddresses
            ?.firstOrNull { it.address is Inet4Address && !it.address.isLoopbackAddress }

        val localIp = ipv4LinkAddress?.address?.hostAddress
        val subnetMask = ipv4LinkAddress?.prefixLength?.let { prefixToSubnetMask(it) }

        val gatewayIp = linkProps?.routes
            ?.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
            ?.gateway
            ?.hostAddress

        var ssid: String? = null
        var rssi: Int? = null
        var freqMhz: Int? = null

        if (isWifi) {
            runCatching {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                @Suppress("DEPRECATION")
                val info = wifiManager?.connectionInfo
                if (info != null) {
                    val rawSsid = info.ssid?.removePrefix("\"")?.removeSuffix("\"")
                    if (!rawSsid.isNullOrBlank() && rawSsid != "<unknown ssid>") {
                        ssid = rawSsid
                    }
                    if (info.rssi in -110..-10) {
                        rssi = info.rssi
                    }
                    if (info.frequency > 2000) {
                        freqMhz = info.frequency
                    }
                }
            }
        }

        LocalAndroidNetworkSnapshot(
            hasInternet = hasInternet,
            isWifiConnected = isWifi,
            isActiveConnected = isConnected,
            ssid = ssid,
            localIp = localIp,
            gatewayIp = gatewayIp,
            subnetMask = subnetMask,
            rssiDbm = rssi,
            frequencyMhz = freqMhz,
            linkDownstreamKbps = caps?.linkDownstreamBandwidthKbps,
            linkUpstreamKbps = caps?.linkUpstreamBandwidthKbps
        )
    }.getOrElse {
        LocalAndroidNetworkSnapshot(
            hasInternet = false,
            isWifiConnected = false,
            isActiveConnected = false,
            ssid = null,
            localIp = null,
            gatewayIp = null,
            subnetMask = null,
            rssiDbm = null,
            frequencyMhz = null,
            linkDownstreamKbps = null,
            linkUpstreamKbps = null
        )
    }

    /**
     * Measures real-time device network throughput (download Mbps, upload Mbps) via Android TrafficStats.
     */
    fun sampleRealThroughputMbps(): Pair<Float, Float> {
        val now = System.currentTimeMillis()
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        if (rx == TrafficStats.UNSUPPORTED.toLong() || tx == TrafficStats.UNSUPPORTED.toLong()) {
            return 0f to 0f
        }
        val elapsedSec = ((now - lastTrafficTimeMs) / 1000f).coerceAtLeast(0.25f)
        val deltaRx = (rx - lastRxBytes).coerceAtLeast(0L)
        val deltaTx = (tx - lastTxBytes).coerceAtLeast(0L)

        lastRxBytes = rx
        lastTxBytes = tx
        lastTrafficTimeMs = now

        val downMbps = (deltaRx * 8f) / (elapsedSec * 1_000_000f)
        val upMbps = (deltaTx * 8f) / (elapsedSec * 1_000_000f)
        return downMbps to upMbps
    }

    /**
     * Measures real network round-trip latency (ms) by probing the gateway or DNS server (1.1.1.1 / 8.8.8.8).
     */
    suspend fun measureRealLatencyMs(gatewayIp: String?): Int? = withContext(Dispatchers.IO) {
        // 1. Try real TCP RTT to 1.1.1.1:53 or 8.8.8.8:53 (works reliably without root ICMP)
        val targets = listOfNotNull(
            gatewayIp?.let { it to 80 },
            "1.1.1.1" to 53,
            "8.8.8.8" to 53
        )
        for ((host, port) in targets) {
            val measured = runCatching {
                val start = System.nanoTime()
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), 1200)
                }
                ((System.nanoTime() - start) / 1_000_000L).toInt().coerceAtLeast(1)
            }.getOrNull()
            if (measured != null) return@withContext measured
        }
        // 2. Fallback to InetAddress.isReachable on gateway
        if (!gatewayIp.isNullOrBlank()) {
            val rtt = runCatching {
                val start = System.nanoTime()
                val ok = InetAddress.getByName(gatewayIp).isReachable(1200)
                if (ok) ((System.nanoTime() - start) / 1_000_000L).toInt().coerceAtLeast(1) else null
            }.getOrNull()
            if (rtt != null) return@withContext rtt
        }
        null
    }

    suspend fun isHostReachable(ip: String, timeoutMs: Int = 800): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            InetAddress.getByName(ip).isReachable(timeoutMs)
        }.getOrDefault(false)
    }

    private fun prefixToSubnetMask(prefixLength: Int): String {
        val clamped = prefixLength.coerceIn(0, 32)
        val mask = if (clamped == 0) 0 else (-1 shl (32 - clamped))
        return "${(mask ushr 24) and 0xFF}.${(mask ushr 16) and 0xFF}.${(mask ushr 8) and 0xFF}.${mask and 0xFF}"
    }

    /**
     * Supported Android local network service discovery using NsdManager (mDNS / Bonjour).
     */
    fun discoverLocalServices(): Flow<ConnectedDevice> = callbackFlow {
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
        if (nsdManager == null) {
            close()
            return@callbackFlow
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                runCatching { nsdManager.stopServiceDiscovery(this) }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                runCatching { nsdManager.stopServiceDiscovery(this) }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                val name = serviceInfo.serviceName ?: "mDNS IoT Device"
                val category = when {
                    name.contains("esp", ignoreCase = true) -> DiscoveredDeviceCategory.ESP8266
                    name.contains("router", ignoreCase = true) -> DiscoveredDeviceCategory.ROUTER
                    else -> DiscoveredDeviceCategory.OTHER_IOT
                }
                trySend(
                    ConnectedDevice(
                        name = name,
                        ipAddress = "mDNS (${serviceInfo.serviceType})",
                        macAddress = null,
                        category = category,
                        isOnline = true,
                        lastSeen = System.currentTimeMillis(),
                        connectionDetails = "Discovered in real time via Android NsdManager (mDNS)",
                        isManual = false
                    )
                )
            }
        }

        runCatching {
            nsdManager.discoverServices("_http._tcp.", NsdManager.PROTOCOL_DNS_SD, listener)
        }

        awaitClose {
            runCatching { nsdManager.stopServiceDiscovery(listener) }
        }
    }
}
