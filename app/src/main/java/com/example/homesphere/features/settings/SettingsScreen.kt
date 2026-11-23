package com.example.homesphere.features.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.homesphere.core.design.HomeSphereCard
import com.example.homesphere.core.design.HomeSphereTextStyles
import com.example.homesphere.core.design.HomeSphereTheme
import com.example.homesphere.core.design.SectionHeader
import com.example.homesphere.core.design.StatusBadge
import com.example.homesphere.core.design.StatusTone
import com.example.homesphere.core.design.rememberLiveTimeMillis
import com.example.homesphere.core.utils.FormatUtils
import com.example.homesphere.domain.model.AppThemeMode
import com.example.homesphere.domain.model.TemperatureUnit

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val cfg = state.settings
    val health = state.espHealth
    val ext = HomeSphereTheme.extendedColors
    val nowMillis = rememberLiveTimeMillis()
    val snackbarHostState = remember { SnackbarHostState() }

    var activeDialog by remember { mutableStateOf<SettingsEditDialog?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshNotificationPermission()
    }

    LaunchedEffect(state.feedbackMessage) {
        state.feedbackMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFeedback()
        }
    }

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
            item {
                Column(modifier = Modifier.statusBarsPadding()) {
                    Text("Settings", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Preferences persist locally via DataStore • Auto-checks connections every 30s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Profile & Home Card
            item {
                SectionHeader(title = "Home & Profile")
                Spacer(modifier = Modifier.height(8.dp))
                HomeSphereCard {
                    SettingsClickableRow(
                        icon = Icons.Default.Person,
                        title = cfg.userName,
                        subtitle = cfg.userEmail,
                        onClick = { activeDialog = SettingsEditDialog.PROFILE },
                        testTag = "settings_edit_profile"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    SettingsClickableRow(
                        icon = Icons.Default.Home,
                        title = "Home Name",
                        subtitle = cfg.homeName,
                        onClick = { activeDialog = SettingsEditDialog.HOME_NAME },
                        testTag = "settings_edit_home_name"
                    )
                }
            }

            // Appearance & Units
            item {
                SectionHeader(title = "Appearance & Units")
                Spacer(modifier = Modifier.height(8.dp))
                HomeSphereCard {
                    Text("Theme Selection", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = cfg.themeMode == mode,
                                onClick = { viewModel.updateTheme(mode) },
                                label = { Text(mode.label.substringBefore(" ")) },
                                modifier = Modifier.testTag("settings_theme_${mode.name.lowercase()}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Temperature Unit", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TemperatureUnit.entries.forEach { unit ->
                            FilterChip(
                                selected = cfg.temperatureUnit == unit,
                                onClick = { viewModel.updateTemperatureUnit(unit) },
                                label = { Text(unit.label) }
                            )
                        }
                    }
                }
            }

            // Hardware & Network Endpoints (with Live HTTP 200 Verification)
            item {
                SectionHeader(title = "Hardware, Water & Network")
                Spacer(modifier = Modifier.height(8.dp))
                HomeSphereCard(
                    accentGlow = if (health.isLive) ext.online else null,
                    borderColor = if (health.isLive) ext.online.copy(alpha = 0.55f) else ext.cardBorder
                ) {
                    // ESP8266 Editable Endpoint & Live HTTP 200 Status Panel
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (health.isLive) ext.onlineContainer.copy(alpha = 0.45f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        border = BorderStroke(
                            1.dp,
                            if (health.isLive) ext.online.copy(alpha = 0.45f) else ext.cardBorder
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
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
                                        contentDescription = "ESP8266 Hub",
                                        tint = if (health.isLive) ext.online else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text("ESP8266 Local IP", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = "http://${cfg.esp8266Ip}",
                                            style = HomeSphereTextStyles.MonoMetricSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                StatusBadge(
                                    label = when {
                                        state.isCheckingEsp -> "CHECKING"
                                        health.isLive -> "LIVE • 200"
                                        health.httpStatusCode != null -> "HTTP ${health.httpStatusCode}"
                                        else -> "OFFLINE"
                                    },
                                    tone = when {
                                        state.isCheckingEsp -> StatusTone.INFO
                                        health.isLive -> StatusTone.ONLINE
                                        else -> StatusTone.DISCONNECTED
                                    },
                                    pulsing = health.isLive
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = health.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (health.isLive) ext.online else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Last checked ${FormatUtils.formatRelativeTime(state.lastConnectionCheckAt, nowMillis)} • Auto-checks every 30s",
                                style = HomeSphereTextStyles.MonoMetricSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { activeDialog = SettingsEditDialog.ESP_IP },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("settings_esp_ip")
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.size(6.dp))
                                    Text("Edit IP")
                                }
                                FilledTonalButton(
                                    onClick = viewModel::checkEsp8266Now,
                                    enabled = !state.isCheckingEsp,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("settings_check_esp_button")
                                ) {
                                    if (state.isCheckingEsp) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.size(6.dp))
                                        Text("Check 200")
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    SettingsKeyValueRow(
                        label = "Router IP Address",
                        value = cfg.routerIp,
                        onEdit = { activeDialog = SettingsEditDialog.ROUTER_IP },
                        testTag = "settings_router_ip"
                    )
                    SettingsKeyValueRow(
                        label = "Water Tank Capacity",
                        value = "${cfg.tankCapacityLitres} L (Low < ${cfg.lowWaterThresholdPercent}%)",
                        onEdit = { activeDialog = SettingsEditDialog.WATER_TANK },
                        testTag = "settings_water_tank"
                    )
                }
            }

            // Notification Preferences
            item {
                SectionHeader(title = "Notification Preferences")
                Spacer(modifier = Modifier.height(8.dp))
                HomeSphereCard {
                    if (!state.notificationPermissionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Button(
                            onClick = {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_grant_notif_button")
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Grant Notification Permission")
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    SettingsSwitchRow(
                        title = "Enable Local Alerts",
                        subtitle = "Master switch for all smart home alerts",
                        checked = cfg.notificationsEnabled,
                        onCheckedChange = { viewModel.updateNotifications(master = it) }
                    )
                    SettingsSwitchRow(
                        title = "Low Water Tank Alert",
                        subtitle = "Warn below ${cfg.lowWaterThresholdPercent}% capacity",
                        checked = cfg.lowWaterAlertEnabled,
                        onCheckedChange = { viewModel.updateNotifications(lowWater = it) }
                    )
                    SettingsSwitchRow(
                        title = "Door Security Alerts",
                        subtitle = "Notify on door unlock & reed open events",
                        checked = cfg.doorAlertEnabled,
                        onCheckedChange = { viewModel.updateNotifications(door = it) }
                    )
                    SettingsSwitchRow(
                        title = "Router Disconnection Alert",
                        subtitle = "Alert when local gateway goes offline",
                        checked = cfg.routerDisconnectAlertEnabled,
                        onCheckedChange = { viewModel.updateNotifications(router = it) }
                    )
                    SettingsSwitchRow(
                        title = "Automation Events",
                        subtitle = "Notify when scheduled or real-time rules run",
                        checked = cfg.automationAlertEnabled,
                        onCheckedChange = { viewModel.updateNotifications(automation = it) }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = viewModel::sendTestNotification,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_test_notification_button")
                    ) {
                        Text("Send Test Notification")
                    }
                }
            }

            // About & Version Info
            item {
                SectionHeader(title = "About HomeSphere")
                Spacer(modifier = Modifier.height(8.dp))
                HomeSphereCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = "About HomeSphere",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column {
                            Text("HomeSphere – Smart Home Automation", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Your Home, Connected.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Version ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE}) • Real-Time 30s Sync",
                                style = HomeSphereTextStyles.MonoMetricSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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

    when (activeDialog) {
        SettingsEditDialog.HOME_NAME -> SingleTextEditDialog(
            title = "Edit Home Name",
            label = "Home Name",
            helperText = null,
            confirmText = "Save",
            initial = cfg.homeName,
            onDismiss = { activeDialog = null },
            onSave = {
                viewModel.updateHomeName(it)
                activeDialog = null
            }
        )
        SettingsEditDialog.ROUTER_IP -> SingleTextEditDialog(
            title = "Router IP Address",
            label = "IPv4 Gateway Address",
            helperText = null,
            confirmText = "Save",
            initial = cfg.routerIp,
            onDismiss = { activeDialog = null },
            onSave = {
                viewModel.updateRouterIp(it)
                activeDialog = null
            }
        )
        SettingsEditDialog.ESP_IP -> SingleTextEditDialog(
            title = "ESP8266 Local IP Address",
            label = "IP or Host:Port (e.g. 192.168.1.50)",
            helperText = "HomeSphere will immediately check HTTP 200 OK on save and re-verify all connections every 30 seconds.",
            confirmText = "Save & Check 200",
            initial = cfg.esp8266Ip,
            onDismiss = { activeDialog = null },
            onSave = {
                viewModel.updateEspIp(it)
                activeDialog = null
            }
        )
        SettingsEditDialog.PROFILE -> {
            var name by remember { mutableStateOf(cfg.userName) }
            var role by remember { mutableStateOf(cfg.userEmail) }
            AlertDialog(
                onDismissRequest = { activeDialog = null },
                title = { Text("Edit User Profile") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Display Name") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = role,
                            onValueChange = { role = it },
                            label = { Text("Role or Email") },
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        viewModel.updateProfile(name, role)
                        activeDialog = null
                    }) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { activeDialog = null }) { Text("Cancel") }
                }
            )
        }
        SettingsEditDialog.WATER_TANK -> {
            var cap by remember { mutableStateOf(cfg.tankCapacityLitres.toString()) }
            var low by remember { mutableStateOf(cfg.lowWaterThresholdPercent.toString()) }
            AlertDialog(
                onDismissRequest = { activeDialog = null },
                title = { Text("Water Tank Defaults") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = cap,
                            onValueChange = { cap = it.filter(Char::isDigit) },
                            label = { Text("Tank Capacity (Litres)") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = low,
                            onValueChange = { low = it.filter(Char::isDigit) },
                            label = { Text("Low-Water Alert Threshold (%)") },
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        viewModel.updateWaterConfig(
                            cap.toIntOrNull() ?: cfg.tankCapacityLitres,
                            low.toIntOrNull() ?: cfg.lowWaterThresholdPercent,
                            cfg.highWaterThresholdPercent
                        )
                        activeDialog = null
                    }) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { activeDialog = null }) { Text("Cancel") }
                }
            )
        }
        null -> Unit
    }
}

private enum class SettingsEditDialog {
    PROFILE, HOME_NAME, ROUTER_IP, ESP_IP, WATER_TANK
}

@Composable
private fun SettingsClickableRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.Edit, contentDescription = "Edit $title", modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SettingsKeyValueRow(
    label: String,
    value: String,
    onEdit: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onEdit)
            .padding(vertical = 8.dp)
            .testTag(testTag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = value,
                style = HomeSphereTextStyles.MonoMetricSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Icon(Icons.Default.Edit, contentDescription = "Edit $label", modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier
        )
    }
}

@Composable
private fun SingleTextEditDialog(
    title: String,
    label: String,
    helperText: String?,
    confirmText: String,
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!helperText.isNullOrBlank()) {
                    Text(
                        text = helperText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(label) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_dialog_text_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(value) },
                enabled = value.isNotBlank()
            ) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
