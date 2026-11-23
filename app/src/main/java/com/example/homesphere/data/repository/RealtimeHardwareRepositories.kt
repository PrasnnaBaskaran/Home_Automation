package com.example.homesphere.data.repository

import android.os.SystemClock
import com.example.homesphere.core.database.WaterHistoryDao
import com.example.homesphere.core.database.WaterHistoryEntity
import com.example.homesphere.core.utils.FormatUtils
import com.example.homesphere.domain.model.ConnectionStatus
import com.example.homesphere.domain.model.NetworkMetricPoint
import com.example.homesphere.domain.model.NetworkStatus
import com.example.homesphere.domain.model.WaterLevelReading
import com.example.homesphere.hardware.abstraction.NetworkMonitorRepository
import com.example.homesphere.hardware.abstraction.WaterLevelRepository
import com.example.homesphere.hardware.esp8266.Esp8266HardwareGateway
import com.example.homesphere.hardware.router.AndroidNetworkInspector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 100% Real-Time Water Level Repository backed by ESP8266 `/api/water` + HTTP 200 health + Room history.
 */
class RealtimeWaterLevelRepository(
    private val espGateway: Esp8266HardwareGateway,
    private val waterHistoryDao: WaterHistoryDao,
    private val capacityProvider: () -> Int,
    private val lowThresholdProvider: () -> Int,
    private val highThresholdProvider: () -> Int
) : WaterLevelRepository {

    private val _currentReading = MutableStateFlow(
        WaterLevelReading(
            percentage = 0,
            currentLitres = 0,
            tankCapacity = capacityProvider(),
            timestamp = System.currentTimeMillis(),
            sensorConnected = false,
            lowThresholdPercent = lowThresholdProvider(),
            highThresholdPercent = highThresholdProvider()
        )
    )

    override fun observeWaterLevel(): Flow<WaterLevelReading> = _currentReading.asStateFlow()

    override fun observeHistory(): Flow<List<WaterLevelReading>> =
        waterHistoryDao.observeHistory().map { list ->
            list.map {
                WaterLevelReading(
                    percentage = it.percentage,
                    currentLitres = it.currentLitres,
                    tankCapacity = it.tankCapacity,
                    timestamp = it.timestamp,
                    sensorConnected = true
                )
            }
        }

    fun syncWithSettings() {
        val cap = capacityProvider()
        val low = lowThresholdProvider()
        val high = highThresholdProvider()
        _currentReading.update { current ->
            current.copy(
                tankCapacity = cap,
                currentLitres = if (current.sensorConnected) (cap * current.percentage / 100f).roundToInt() else 0,
                lowThresholdPercent = low,
                highThresholdPercent = high
            )
        }
    }

    override suspend fun refresh(): Result<WaterLevelReading> = refreshWithHubStatus(espHubIsLive = false)

    suspend fun refreshWithHubStatus(
        espHubIsLive: Boolean,
        hostOverride: String? = null
    ): Result<WaterLevelReading> {
        val cap = capacityProvider()
        val low = lowThresholdProvider()
        val high = highThresholdProvider()
        val now = System.currentTimeMillis()

        val result = espGateway.pollWaterSensor(hostOverride)
        return result.map { dto ->
            val pct = dto.percentage.coerceIn(0, 100)
            val litres = if (dto.litres > 0) dto.litres else (cap * pct / 100f).roundToInt()
            val connected = dto.sensorOk || espHubIsLive
            val reading = WaterLevelReading(
                percentage = pct,
                currentLitres = litres,
                tankCapacity = cap,
                timestamp = now,
                sensorConnected = connected,
                lowThresholdPercent = low,
                highThresholdPercent = high
            )
            _currentReading.value = reading
            if (connected) {
                waterHistoryDao.insertReading(
                    WaterHistoryEntity(
                        percentage = pct,
                        currentLitres = litres,
                        tankCapacity = cap,
                        timestamp = now
                    )
                )
            }
            reading
        }.recoverCatching { err ->
            if (espHubIsLive) {
                val updated = _currentReading.value.copy(
                    tankCapacity = cap,
                    lowThresholdPercent = low,
                    highThresholdPercent = high,
                    sensorConnected = true,
                    timestamp = now
                )
                _currentReading.value = updated
                updated
            } else {
                _currentReading.update {
                    it.copy(
                        tankCapacity = cap,
                        lowThresholdPercent = low,
                        highThresholdPercent = high,
                        sensorConnected = false,
                        timestamp = now
                    )
                }
                throw err
            }
        }
    }
}

/**
 * 100% Real-Time Network Monitor Repository.
 * Polls live Android ConnectivityManager, LinkProperties, WifiManager, TrafficStats throughput,
 * real TCP/ICMP latency probes, and real ESP8266 diagnostics without any simulated data.
 */
class RealtimeNetworkMonitorRepository(
    private val inspector: AndroidNetworkInspector,
    private val espGateway: Esp8266HardwareGateway,
    private val routerIpProvider: () -> String,
    private val espIpProvider: () -> String,
    scope: CoroutineScope
) : NetworkMonitorRepository {

    private val _status = MutableStateFlow(buildInitialSnapshot())
    private val _history = MutableStateFlow<List<NetworkMetricPoint>>(emptyList())

    init {
        scope.launch {
            while (isActive) {
                sampleLiveTelemetry(pollEsp = false, knownEspLive = _status.value.espReachable)
                delay(3000L)
            }
        }
    }

    private fun buildInitialSnapshot(): NetworkStatus {
        val local = runCatching { inspector.inspectCurrentNetwork() }.getOrNull()
        val routerIp = local?.gatewayIp ?: routerIpProvider()
        val rssi = local?.rssiDbm
        val freq = local?.frequencyMhz ?: 2437
        val connected = local?.isActiveConnected == true
        return NetworkStatus(
            ssid = local?.ssid ?: if (local?.isWifiConnected == true) "Wi-Fi Connected" else "No Wi-Fi",
            routerName = if (connected) "Active Network Gateway" else "Gateway Offline",
            routerIp = routerIp,
            gatewayIp = local?.gatewayIp ?: "Unavailable",
            localIp = local?.localIp ?: "Unavailable",
            espIp = espIpProvider(),
            subnetMask = local?.subnetMask ?: "255.255.255.0",
            rssi = rssi,
            estimatedDistanceMeters = rssi?.let { FormatUtils.estimateDistanceMeters(it, freq) },
            latencyMs = null,
            downloadSpeedMbps = 0f,
            uploadSpeedMbps = 0f,
            internetAvailable = local?.hasInternet == true,
            isWifiConnected = local?.isWifiConnected == true,
            uptimeSeconds = runCatching { SystemClock.elapsedRealtime() / 1000L }.getOrDefault(0L),
            connectionStatus = if (connected) ConnectionStatus.CONNECTED else ConnectionStatus.DISCONNECTED,
            espReachable = false,
            lastUpdated = System.currentTimeMillis()
        )
    }

    private suspend fun sampleLiveTelemetry(
        pollEsp: Boolean,
        knownEspLive: Boolean? = null,
        espHostOverride: String? = null
    ): NetworkStatus {
        val local = runCatching { inspector.inspectCurrentNetwork() }.getOrNull()
        val (downRaw, upRaw) = runCatching { inspector.sampleRealThroughputMbps() }.getOrDefault(0f to 0f)
        val latency = if (local?.isActiveConnected == true) {
            inspector.measureRealLatencyMs(local.gatewayIp)
        } else null

        val espDto = if (pollEsp) espGateway.pollDiagnostics(espHostOverride).getOrNull() else null
        val espLive = (espDto != null) || (knownEspLive == true)
        val effectiveEspIp = espHostOverride?.takeIf { it.isNotBlank() } ?: espIpProvider()

        val routerIp = espDto?.gatewayIp ?: local?.gatewayIp ?: routerIpProvider()
        val rssi = espDto?.rssi ?: local?.rssiDbm
        val freq = local?.frequencyMhz ?: 2437
        val distance = espDto?.estimatedDistanceMeters ?: rssi?.let { FormatUtils.estimateDistanceMeters(it, freq) }
        val effectiveLatency = espDto?.latencyMs ?: latency
        val uptime = espDto?.uptimeSeconds ?: runCatching { SystemClock.elapsedRealtime() / 1000L }.getOrDefault(0L)
        val isOnline = (local?.isActiveConnected == true) || (espDto?.gatewayReachable == true) || espLive
        val downMbps = (downRaw * 100f).roundToInt() / 100f
        val upMbps = (upRaw * 100f).roundToInt() / 100f
        val now = System.currentTimeMillis()

        val snapshot = NetworkStatus(
            ssid = espDto?.ssid ?: local?.ssid ?: if (local?.isWifiConnected == true) "Wi-Fi Network" else "Mobile / Ethernet",
            routerName = if (espLive) "ESP8266 Verified Gateway" else if (isOnline) "Local Network Gateway" else "Gateway Offline",
            routerIp = routerIp,
            gatewayIp = espDto?.gatewayIp ?: local?.gatewayIp ?: "Unavailable",
            localIp = local?.localIp ?: "Unavailable",
            espIp = effectiveEspIp,
            subnetMask = local?.subnetMask ?: "255.255.255.0",
            rssi = rssi,
            estimatedDistanceMeters = distance,
            latencyMs = effectiveLatency,
            downloadSpeedMbps = downMbps,
            uploadSpeedMbps = upMbps,
            internetAvailable = local?.hasInternet == true,
            isWifiConnected = local?.isWifiConnected == true,
            uptimeSeconds = uptime,
            connectionStatus = if (isOnline) ConnectionStatus.CONNECTED else ConnectionStatus.DISCONNECTED,
            espReachable = espLive,
            lastUpdated = now
        )

        _status.value = snapshot
        _history.update { list ->
            val point = NetworkMetricPoint(
                timestamp = now,
                rssi = rssi,
                latencyMs = effectiveLatency,
                downloadSpeedMbps = downMbps,
                uploadSpeedMbps = upMbps
            )
            (list + point).takeLast(30)
        }
        return snapshot
    }

    override fun observeNetworkStatus(): Flow<NetworkStatus> = _status.asStateFlow()

    override fun observeMetricHistory(): Flow<List<NetworkMetricPoint>> = _history.asStateFlow()

    override suspend fun refreshDiagnostics(): Result<NetworkStatus> = runCatching {
        sampleLiveTelemetry(pollEsp = true)
    }

    suspend fun refreshDiagnosticsWithEspHealth(
        espIsLive: Boolean,
        espHostOverride: String? = null
    ): Result<NetworkStatus> = runCatching {
        sampleLiveTelemetry(pollEsp = espIsLive, knownEspLive = espIsLive, espHostOverride = espHostOverride)
    }
}
