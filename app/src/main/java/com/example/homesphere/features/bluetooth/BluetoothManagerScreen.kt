package com.example.homesphere.features.bluetooth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.homesphere.core.design.EmptyStateCard
import com.example.homesphere.core.design.HomeSphereCard
import com.example.homesphere.core.design.HomeSphereDetailTopBar
import com.example.homesphere.core.design.HomeSphereTextStyles
import com.example.homesphere.core.design.HomeSphereTheme
import com.example.homesphere.core.design.SectionHeader
import com.example.homesphere.core.design.StatusBadge
import com.example.homesphere.core.design.StatusTone
import com.example.homesphere.core.design.rememberLiveTimeMillis
import com.example.homesphere.core.utils.FormatUtils
import com.example.homesphere.domain.model.BluetoothDeviceCategory
import com.example.homesphere.domain.model.BluetoothDeviceInfo
import com.example.homesphere.hardware.bluetooth.BluetoothAdapterStatus

@Composable
fun BluetoothManagerScreen(
    viewModel: BluetoothViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snap = state.snapshot
    val nowMillis = rememberLiveTimeMillis()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        viewModel.onPermissionsResult(allGranted)
    }

    DisposableEffect(Unit) {
        viewModel.onEnterScreen()
        onDispose { viewModel.onLeaveScreen() }
    }

    LaunchedEffect(state.feedbackMessage) {
        state.feedbackMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFeedback()
        }
    }

    val statusTone = when (snap.adapterStatus) {
        BluetoothAdapterStatus.ON -> StatusTone.ONLINE
        BluetoothAdapterStatus.PERMISSION_REQUIRED -> StatusTone.WARNING
        else -> StatusTone.DISCONNECTED
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            HomeSphereDetailTopBar(
                title = "Bluetooth Manager",
                subtitle = "Last updated ${FormatUtils.formatRelativeTime(snap.lastUpdated, nowMillis)} • Checks every 30s",
                statusLabel = snap.adapterStatus.label,
                statusTone = statusTone,
                onNavigateBack = onNavigateBack
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Adapter Control Card
            item {
                HomeSphereCard(
                    accentGlow = if (snap.adapterStatus == BluetoothAdapterStatus.ON) MaterialTheme.colorScheme.primary else null
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when {
                                    snap.isScanning -> Icons.Default.BluetoothSearching
                                    snap.adapterStatus == BluetoothAdapterStatus.ON -> Icons.Default.Bluetooth
                                    else -> Icons.Default.BluetoothDisabled
                                },
                                contentDescription = "Bluetooth adapter",
                                tint = if (snap.adapterStatus == BluetoothAdapterStatus.ON) {
                                    MaterialTheme.colorScheme.primary
                                } else HomeSphereTheme.extendedColors.offline
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Adapter: ${snap.adapterStatus.label}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = snap.statusMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    when (snap.adapterStatus) {
                        BluetoothAdapterStatus.PERMISSION_REQUIRED -> {
                            Button(
                                onClick = { permissionLauncher.launch(viewModel.requiredPermissions()) },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("bt_request_permissions_button")
                            ) {
                                Text("Grant Bluetooth Permissions")
                            }
                        }
                        BluetoothAdapterStatus.OFF -> {
                            Button(
                                onClick = {
                                    runCatching { context.startActivity(viewModel.openSettingsIntent()) }
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("bt_open_settings_button")
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.size(8.dp))
                                Text("Open Android Bluetooth Settings")
                            }
                        }
                        BluetoothAdapterStatus.ON -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (snap.isScanning) viewModel.stopScan() else viewModel.startScan()
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("bt_scan_button")
                                ) {
                                    if (snap.isScanning) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Spacer(modifier = Modifier.size(8.dp))
                                        Text("Stop Scan")
                                    } else {
                                        Icon(Icons.Default.BluetoothSearching, contentDescription = null)
                                        Spacer(modifier = Modifier.size(8.dp))
                                        Text("Scan Nearby")
                                    }
                                }
                                OutlinedButton(
                                    onClick = {
                                        runCatching { context.startActivity(viewModel.openSettingsIntent()) }
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Icon(Icons.Default.Settings, contentDescription = "Bluetooth settings")
                                }
                            }
                        }
                        BluetoothAdapterStatus.UNAVAILABLE -> {
                            Text(
                                text = "This device or emulator does not expose a Bluetooth radio.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Nearby Discovered Devices
            item {
                SectionHeader(title = "Nearby Discovered (${snap.discoveredDevices.size})")
            }

            if (snap.discoveredDevices.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.BluetoothSearching,
                        title = if (snap.isScanning) "Scanning Nearby Airwaves…" else "No Discovered Devices",
                        subtitle = "Tap Scan Nearby with Bluetooth enabled. HomeSphere never pairs or connects automatically."
                    )
                }
            } else {
                items(snap.discoveredDevices, key = { "disc_${it.address}" }) { device ->
                    BluetoothDeviceRowCard(
                        device = device,
                        onClick = { viewModel.selectDevice(device) }
                    )
                }
            }

            // Paired Devices
            item {
                SectionHeader(title = "Paired Devices (${snap.pairedDevices.size})")
            }

            if (snap.pairedDevices.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.Bluetooth,
                        title = "No Paired Devices Listed",
                        subtitle = "Previously paired Android Bluetooth devices appear here once permissions and Bluetooth are active."
                    )
                }
            } else {
                items(snap.pairedDevices, key = { "paired_${it.address}" }) { device ->
                    BluetoothDeviceRowCard(
                        device = device,
                        onClick = { viewModel.selectDevice(device) }
                    )
                }
            }
        }
    }

    state.selectedDevice?.let { selected ->
        AlertDialog(
            onDismissRequest = { viewModel.selectDevice(null) },
            title = { Text(selected.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Type: ${selected.category.label}", style = MaterialTheme.typography.bodyMedium)
                    Text("Address: ${selected.address}", style = HomeSphereTextStyles.MonoMetricSmall)
                    Text(
                        "Signal Strength: ${selected.rssi?.let { "$it dBm" } ?: "Not reported"}",
                        style = HomeSphereTextStyles.MonoMetricSmall
                    )
                    Text(
                        "Bond State: ${if (selected.isPaired) "Paired" else "Not Paired"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "For security, HomeSphere does not auto-pair or auto-connect without explicit user action in Android Bluetooth Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    runCatching { context.startActivity(viewModel.openSettingsIntent()) }
                    viewModel.selectDevice(null)
                }) { Text("Pair in Settings") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.selectDevice(null) }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun BluetoothDeviceRowCard(
    device: BluetoothDeviceInfo,
    onClick: () -> Unit
) {
    val icon = when (device.category) {
        BluetoothDeviceCategory.AUDIO -> Icons.Default.Headphones
        BluetoothDeviceCategory.WEARABLE -> Icons.Default.Watch
        BluetoothDeviceCategory.COMPUTER -> Icons.Default.Computer
        BluetoothDeviceCategory.PHONE -> Icons.Default.PhoneAndroid
        else -> Icons.Default.DevicesOther
    }

    HomeSphereCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = device.category.label, tint = MaterialTheme.colorScheme.primary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(device.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${device.category.label} • ${device.address}",
                    style = HomeSphereTextStyles.MonoMetricSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                StatusBadge(
                    label = if (device.isPaired) "PAIRED" else "FOUND",
                    tone = if (device.isPaired) StatusTone.ONLINE else StatusTone.INFO
                )
                device.rssi?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$it dBm",
                        style = HomeSphereTextStyles.MonoMetricSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
