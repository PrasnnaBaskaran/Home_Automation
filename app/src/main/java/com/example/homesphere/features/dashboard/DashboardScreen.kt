package com.example.homesphere.features.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.DoorFront
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.homesphere.core.design.HomeSphereCard
import com.example.homesphere.core.design.HomeSphereTextStyles
import com.example.homesphere.core.design.HomeSphereTheme
import com.example.homesphere.core.design.StatusBadge
import com.example.homesphere.core.design.StatusTone
import com.example.homesphere.core.design.toTone
import com.example.homesphere.core.utils.FormatUtils
import com.example.homesphere.domain.model.ConnectionStatus
import com.example.homesphere.domain.model.Device
import com.example.homesphere.domain.model.DeviceType

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToDevice: (DeviceType) -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToConnectedDevices: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showEditIpDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                DashboardHeroHeader(
                    state = state,
                    onNotificationsClick = onNavigateToNotifications,
                    onSettingsClick = onNavigateToSettings,
                    onEditEspIpClick = { showEditIpDialog = true },
                    onSyncLiveHardware = viewModel::syncLiveHardware
                )
            }

            item {
                SummaryMetricsGrid(
                    total = state.totalDevices,
                    online = state.onlineDevices,
                    offline = state.offlineDevices,
                    automations = state.activeAutomations,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Connected Systems",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Auto-checks every 30s • Updated ${FormatUtils.formatRelativeTime(state.lastConnectionCheckAt, state.nowMillis)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onNavigateToConnectedDevices)
                            .testTag("dashboard_lan_devices_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DevicesOther,
                                contentDescription = "LAN Devices",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "LAN",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    state.devices.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            pair.forEach { device ->
                                DeviceDashboardCard(
                                    device = device,
                                    nowMillis = state.nowMillis,
                                    onOpen = { onNavigateToDevice(device.deviceType) },
                                    onQuickToggle = { viewModel.onQuickToggle(device) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (pair.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
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

    if (showEditIpDialog) {
        var ipInput by remember { mutableStateOf(state.esp8266Ip) }
        AlertDialog(
            onDismissRequest = { showEditIpDialog = false },
            title = { Text("ESP8266 Local IP Address") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter your running ESP8266 IP (or IP:port). HomeSphere checks for HTTP 200 OK immediately and re-verifies every 30s.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = ipInput,
                        onValueChange = { ipInput = it },
                        label = { Text("ESP8266 IP Address") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dashboard_esp_ip_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateEsp8266Ip(ipInput)
                        showEditIpDialog = false
                    },
                    enabled = ipInput.isNotBlank()
                ) { Text("Save & Check 200") }
            },
            dismissButton = {
                TextButton(onClick = { showEditIpDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun DashboardHeroHeader(
    state: DashboardUiState,
    onNotificationsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onEditEspIpClick: () -> Unit,
    onSyncLiveHardware: () -> Unit
) {
    val ext = HomeSphereTheme.extendedColors
    val espLive = state.espHealth.isLive

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_home_hero),
            contentDescription = "HomeSphere smart home architectural backdrop",
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xD90A0E17),
                            Color(0xB80D1526),
                            Color(0xEE0A0E17)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 18.dp, vertical = 16.dp)
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
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0D1526))
                            .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.45f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.homesphere_launcher_fg),
                            contentDescription = "HomeSphere Logo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Column {
                        Text(
                            text = "HomeSphere",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                        Text(
                            text = "Your Home, Connected.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF7DD3FC)
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onNotificationsClick,
                        modifier = Modifier.testTag("dashboard_notifications_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (state.recentAlertCount > 0) {
                                    Badge(containerColor = MaterialTheme.colorScheme.error) {
                                        Text("${state.recentAlertCount}")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Notifications & Activity",
                                tint = Color.White
                            )
                        }
                    }
                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier.testTag("dashboard_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Profile and Settings",
                            tint = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "${state.greeting}, ${state.userName.substringBefore(" ")}",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Hub,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = state.homeName,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFFE2E8F0)
                )
                Text(
                    text = "•",
                    color = Color(0xFF64748B)
                )
                Text(
                    text = state.formattedDateTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Real-Time Hardware & Editable ESP8266 Status Bar (Auto-checks every 30s)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF121826).copy(alpha = 0.88f),
                border = BorderStroke(
                    1.dp,
                    if (espLive) ext.online.copy(alpha = 0.55f) else Color(0xFF38BDF8).copy(alpha = 0.35f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onEditEspIpClick)
                            .testTag("dashboard_edit_esp_ip")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "ESP8266: ${state.esp8266Ip}",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit ESP8266 IP",
                                tint = Color(0xFF7DD3FC),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            StatusBadge(
                                label = if (espLive) "LIVE • 200" else "OFFLINE",
                                tone = if (espLive) StatusTone.ONLINE else StatusTone.DISCONNECTED,
                                pulsing = espLive
                            )
                            Text(
                                text = FormatUtils.formatRelativeTime(state.lastConnectionCheckAt, state.nowMillis),
                                style = HomeSphereTextStyles.MonoMetricSmall,
                                color = Color(0xFF94A3B8),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = onSyncLiveHardware,
                        enabled = !state.isSyncingHardware,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("dashboard_sync_button")
                    ) {
                        if (state.isSyncingHardware) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(15.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Sync real-time hardware",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sync", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryMetricsGrid(
    total: Int,
    online: Int,
    offline: Int,
    automations: Int,
    modifier: Modifier = Modifier
) {
    val ext = HomeSphereTheme.extendedColors
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SummaryKpiCard(
                label = "Total Devices",
                value = "$total",
                icon = Icons.Default.DevicesOther,
                accent = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            SummaryKpiCard(
                label = "Online",
                value = "$online",
                icon = Icons.Default.CheckCircle,
                accent = ext.online,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SummaryKpiCard(
                label = "Offline",
                value = "$offline",
                icon = Icons.Default.CloudOff,
                accent = ext.offline,
                modifier = Modifier.weight(1f)
            )
            SummaryKpiCard(
                label = "Active Rules",
                value = "$automations",
                icon = Icons.Default.AutoAwesome,
                accent = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SummaryKpiCard(
    label: String,
    value: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier
) {
    HomeSphereCard(
        modifier = modifier,
        cornerRadius = 16.dp,
        contentPadding = 12.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = value,
                    style = HomeSphereTextStyles.MonoMetricLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun DeviceDashboardCard(
    device: Device,
    nowMillis: Long,
    onOpen: () -> Unit,
    onQuickToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = HomeSphereTheme.extendedColors
    val icon = when (device.deviceType) {
        DeviceType.FAN -> Icons.Default.Air
        DeviceType.DOOR -> Icons.Default.DoorFront
        DeviceType.WATER_MONITOR -> Icons.Default.WaterDrop
        DeviceType.ROOM_LIGHT -> Icons.Default.Lightbulb
        DeviceType.WIFI_ROUTER -> Icons.Default.Router
        DeviceType.BLUETOOTH -> Icons.Default.Bluetooth
    }

    val accentColor = when (device.deviceType) {
        DeviceType.FAN -> MaterialTheme.colorScheme.primary
        DeviceType.DOOR -> ext.online
        DeviceType.WATER_MONITOR -> ext.waterAccent
        DeviceType.ROOM_LIGHT -> ext.lightWarm
        DeviceType.WIFI_ROUTER -> MaterialTheme.colorScheme.primary
        DeviceType.BLUETOOTH -> Color(0xFF60A5FA)
    }

    val isOnline = device.connectionStatus == ConnectionStatus.CONNECTED
    val isHighlighted = device.isPoweredOn || isOnline

    HomeSphereCard(
        modifier = modifier.testTag("device_card_${device.id}"),
        onClick = onOpen,
        accentGlow = if (isHighlighted) accentColor else null,
        borderColor = if (isHighlighted) accentColor.copy(alpha = 0.5f) else ext.cardBorder,
        contentPadding = 14.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(
                        if (isHighlighted) accentColor.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = device.name,
                    tint = if (isHighlighted) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }

            if (device.deviceType == DeviceType.FAN || device.deviceType == DeviceType.ROOM_LIGHT) {
                Surface(
                    shape = CircleShape,
                    color = if (device.isPoweredOn) accentColor else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onQuickToggle)
                        .testTag("quick_toggle_${device.id}")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Quick power toggle for ${device.name}",
                            tint = if (device.isPoweredOn) Color(0xFF0A0E17) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Open ${device.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = device.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = device.operatingStatus,
            style = HomeSphereTextStyles.MonoMetricSmall,
            color = if (isHighlighted) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(10.dp))

        StatusBadge(
            label = device.connectionStatus.label,
            tone = device.connectionStatus.toTone(),
            pulsing = isOnline
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Updated ${FormatUtils.formatRelativeTime(device.lastUpdated, nowMillis)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
