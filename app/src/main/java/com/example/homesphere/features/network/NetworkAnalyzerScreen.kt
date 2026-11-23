package com.example.homesphere.features.network

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.homesphere.core.design.HomeSphereCard
import com.example.homesphere.core.design.HomeSphereTextStyles
import com.example.homesphere.core.design.HomeSphereTheme
import com.example.homesphere.core.design.SectionHeader
import com.example.homesphere.core.design.StatusBadge
import com.example.homesphere.core.design.StatusTone
import com.example.homesphere.core.design.TelemetryLineChart
import com.example.homesphere.core.design.rememberLiveTimeMillis
import com.example.homesphere.core.utils.FormatUtils
import com.example.homesphere.domain.model.ConnectionStatus
import com.example.homesphere.hardware.esp8266.WebSocketChannelState

@Composable
fun NetworkAnalyzerScreen(
    viewModel: NetworkViewModel,
    onOpenConnectedDevices: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val net = state.status
    val ext = HomeSphereTheme.extendedColors
    val nowMillis = rememberLiveTimeMillis()
    val snackbarHostState = remember { SnackbarHostState() }
    var showEspIpDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.feedbackMessage) {
        state.feedbackMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFeedback()
        }
    }

    val isOffline = net.connectionStatus == ConnectionStatus.DISCONNECTED

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding() + 12.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Title + Live Network Status Badge
            item {
                Column(modifier = Modifier.statusBarsPadding()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Network Analyzer",
                                style = MaterialTheme.typography.headlineMedium
                            )
                            Text(
                                text = "${net.ssid} • Updated ${FormatUtils.formatRelativeTime(net.lastUpdated, nowMillis)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusBadge(
                            label = if (isOffline) "OFFLINE" else "LIVE",
                            tone = if (isOffline) StatusTone.ALERT else StatusTone.ONLINE,
                            pulsing = !isOffline
                        )
                    }
                }
            }

            // Primary Speed & Signal Telemetry Hero
            item {
                HomeSphereCard(
                    accentGlow = if (isOffline) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        (if (isOffline) MaterialTheme.colorScheme.error else ext.online).copy(alpha = 0.16f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isOffline) Icons.Default.CloudOff else Icons.Default.Router,
                                    contentDescription = "Router status",
                                    tint = if (isOffline) MaterialTheme.colorScheme.error else ext.online
                                )
                            }
                            Column {
                                Text(
                                    text = if (isOffline) "Gateway Unreachable" else "Live Network Link",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isOffline) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Session ${FormatUtils.formatUptime(net.uptimeSeconds)}",
                                    style = HomeSphereTextStyles.MonoMetricSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        StatusBadge(
                            label = if (net.internetAvailable) "WAN OK" else "NO WAN",
                            tone = if (net.internetAvailable) StatusTone.ONLINE else StatusTone.ALERT
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        NetworkMetricMiniCard(
                            icon = Icons.Default.SignalWifi4Bar,
                            label = net.rssi?.let { "RSSI (${FormatUtils.rssiQualityLabel(it)})" } ?: "Wi-Fi Signal",
                            value = net.rssi?.let { "$it dBm" } ?: "N/A",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        NetworkMetricMiniCard(
                            icon = Icons.Default.Speed,
                            label = "Ping Latency",
                            value = net.latencyMs?.let { "$it ms" } ?: "--",
                            tint = ext.online,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        NetworkMetricMiniCard(
                            icon = Icons.Default.Download,
                            label = "Download",
                            value = String.format("%.2f Mbps", net.downloadSpeedMbps),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        NetworkMetricMiniCard(
                            icon = Icons.Default.Upload,
                            label = "Upload",
                            value = String.format("%.2f Mbps", net.uploadSpeedMbps),
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Network Overview Details Table
            item {
                HomeSphereCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Network Configuration", style = MaterialTheme.typography.titleMedium)
                        StatusBadge(
                            label = if (isOffline) "OFFLINE" else "REAL-TIME",
                            tone = if (isOffline) StatusTone.DISCONNECTED else StatusTone.ONLINE
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    NetworkSpecRow("Wi-Fi SSID", net.ssid)
                    NetworkSpecRow("Device Local IP", net.localIp)
                    NetworkSpecRow("Router Name", net.routerName)
                    NetworkSpecRow("Router IP", net.routerIp)
                    NetworkSpecRow("Default Gateway", net.gatewayIp)
                    NetworkSpecRow("Local Subnet", net.subnetMask)
                    NetworkSpecRow("ESP8266 Analyzer IP", net.espIp)
                    NetworkSpecRow(
                        "Approx. Router Distance",
                        net.estimatedDistanceMeters?.takeIf { it > 0f }
                            ?.let { String.format("~%.1f meters", it) } ?: "N/A"
                    )
                    NetworkSpecRow(
                        "Internet Reachability",
                        if (net.internetAvailable) "Verified" else "Offline"
                    )
                }
            }

            // Interactive Live Network Graphs
            item {
                HomeSphereCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Live Telemetry Graphs", style = MaterialTheme.typography.titleMedium)
                        StatusBadge(
                            label = if (isOffline) "STANDBY" else "LIVE STREAM",
                            tone = if (isOffline) StatusTone.DISCONNECTED else StatusTone.ONLINE,
                            pulsing = !isOffline
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(NetworkGraphTab.entries) { tab ->
                            FilterChip(
                                selected = state.selectedGraphTab == tab,
                                onClick = { viewModel.selectGraphTab(tab) },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("network_graph_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    val (chartValues, chartColor, minOverride, maxOverride) = when (state.selectedGraphTab) {
                        NetworkGraphTab.RSSI -> Quad(
                            state.metricHistory.mapNotNull { it.rssi?.toFloat() },
                            MaterialTheme.colorScheme.primary,
                            -95f,
                            -30f
                        )
                        NetworkGraphTab.LATENCY -> Quad(
                            state.metricHistory.mapNotNull { it.latencyMs?.toFloat() },
                            ext.online,
                            0f,
                            null
                        )
                        NetworkGraphTab.DOWNLOAD -> Quad(
                            state.metricHistory.map { it.downloadSpeedMbps },
                            MaterialTheme.colorScheme.primary,
                            0f,
                            null
                        )
                        NetworkGraphTab.UPLOAD -> Quad(
                            state.metricHistory.map { it.uploadSpeedMbps },
                            MaterialTheme.colorScheme.secondary,
                            0f,
                            null
                        )
                    }

                    TelemetryLineChart(
                        values = chartValues,
                        lineColor = chartColor,
                        unit = state.selectedGraphTab.unit,
                        minValOverride = minOverride,
                        maxValOverride = maxOverride,
                        emptyMessage = if (state.selectedGraphTab == NetworkGraphTab.RSSI && net.rssi == null) {
                            "Connect to Wi-Fi to stream live RSSI signal telemetry"
                        } else {
                            "Collecting live network telemetry samples…"
                        }
                    )
                }
            }

            // ESP8266 Integration Adapter Card
            item {
                HomeSphereCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = "ESP8266 Integration",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text("ESP8266 Network Analyzer", style = MaterialTheme.typography.titleMedium)
                        }
                        IconButton(
                            onClick = { showEspIpDialog = true },
                            modifier = Modifier.testTag("network_edit_esp_ip_button")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit ESP8266 IP")
                        }
                    }

                    Text(
                        text = state.espPollingStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "WebSocket (ws://${net.espIp}:81/ws)",
                            style = HomeSphereTextStyles.MonoMetricSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        StatusBadge(
                            label = state.webSocketState.label,
                            tone = if (state.webSocketState == WebSocketChannelState.CONNECTED) {
                                StatusTone.ONLINE
                            } else StatusTone.DISCONNECTED
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = viewModel::pollEsp8266Now,
                            enabled = !state.isPollingEsp,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("network_poll_esp_button")
                        ) {
                            if (state.isPollingEsp) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Run Diagnostics")
                        }
                        OutlinedButton(
                            onClick = viewModel::toggleWebSocketProbe,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("network_ws_probe_button")
                        ) {
                            Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("WebSocket")
                        }
                    }
                }
            }

            // Connected Devices Preview + Navigation to Full Manager
            item {
                SectionHeader(
                    title = "Connected LAN Devices (${state.connectedDevices.size})",
                    trailingText = "Manage All",
                    onTrailingClick = onOpenConnectedDevices
                )
                Spacer(modifier = Modifier.height(8.dp))
                HomeSphereCard(
                    onClick = onOpenConnectedDevices,
                    modifier = Modifier.testTag("network_open_devices_card")
                ) {
                    if (state.connectedDevices.isEmpty()) {
                        Text(
                            text = "No LAN devices recorded yet. Tap to scan via mDNS or register an endpoint.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        state.connectedDevices.take(4).forEachIndexed { idx, device ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (device.isOnline) ext.online else ext.offline)
                                    )
                                    Column {
                                        Text(device.name, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            "${device.ipAddress} • ${device.category.label}",
                                            style = HomeSphereTextStyles.MonoMetricSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                StatusBadge(
                                    label = if (device.isOnline) "ONLINE" else "OFFLINE",
                                    tone = if (device.isOnline) StatusTone.ONLINE else StatusTone.DISCONNECTED
                                )
                            }
                            if (idx < state.connectedDevices.take(4).lastIndex) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.DevicesOther,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Open Connected Device Manager & mDNS Discovery",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = contentPadding.calculateBottomPadding() + 8.dp)
        )
    }

    if (showEspIpDialog) {
        var ipInput by remember { mutableStateOf(net.espIp) }
        AlertDialog(
            onDismissRequest = { showEspIpDialog = false },
            title = { Text("ESP8266 Local IP Address") },
            text = {
                OutlinedTextField(
                    value = ipInput,
                    onValueChange = { ipInput = it },
                    label = { Text("Local IPv4 Address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateEspIp(ipInput)
                    showEspIpDialog = false
                }) { Text("Save & Check 200") }
            },
            dismissButton = {
                TextButton(onClick = { showEspIpDialog = false }) { Text("Cancel") }
            }
        )
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
private fun NetworkMetricMiniCard(
    icon: ImageVector,
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = value,
            style = HomeSphereTextStyles.MonoMetricMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun NetworkSpecRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = HomeSphereTextStyles.MonoMetricSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
}
