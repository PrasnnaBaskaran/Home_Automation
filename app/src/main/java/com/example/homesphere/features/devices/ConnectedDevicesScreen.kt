package com.example.homesphere.features.devices

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.homesphere.core.design.EmptyStateCard
import com.example.homesphere.core.design.HomeSphereCard
import com.example.homesphere.core.design.HomeSphereDetailTopBar
import com.example.homesphere.core.design.HomeSphereTextStyles
import com.example.homesphere.core.design.HomeSphereTheme
import com.example.homesphere.core.design.StatusBadge
import com.example.homesphere.core.design.StatusTone
import com.example.homesphere.core.utils.FormatUtils
import com.example.homesphere.domain.model.ConnectedDevice
import com.example.homesphere.domain.model.DiscoveredDeviceCategory

@Composable
fun ConnectedDevicesScreen(
    viewModel: ConnectedDevicesViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.feedbackMessage) {
        state.feedbackMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFeedback()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            HomeSphereDetailTopBar(
                title = "Connected Devices",
                subtitle = "${state.devices.size} device(s) listed",
                onNavigateBack = onNavigateBack,
                actions = {
                    IconButton(
                        onClick = viewModel::startMdnsDiscovery,
                        enabled = !state.isScanningMdns,
                        modifier = Modifier.testTag("devices_mdns_scan_button")
                    ) {
                        if (state.isScanningMdns) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Radar, contentDescription = "Discover LAN devices via mDNS")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_lan_device_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Register manual LAN device")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = state.selectedCategory == null,
                            onClick = { viewModel.selectCategory(null) },
                            label = { Text("All") }
                        )
                    }
                    items(DiscoveredDeviceCategory.entries) { cat ->
                        FilterChip(
                            selected = state.selectedCategory == cat,
                            onClick = { viewModel.selectCategory(cat) },
                            label = { Text(cat.label) }
                        )
                    }
                }
            }

            if (state.devices.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.DevicesOther,
                        title = "No Devices in Category",
                        subtitle = "Tap + to manually register a router, ESP8266 node, or IoT device, or tap Scan for mDNS."
                    )
                }
            } else {
                items(state.devices, key = { it.id }) { device ->
                    ConnectedDeviceCard(
                        device = device,
                        onDelete = { viewModel.deleteDevice(device.id) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddManualDeviceDialog(
            onDismiss = { showAddDialog = false },
            onSave = { name, ip, mac, cat, details ->
                viewModel.registerManualDevice(name, ip, mac, cat, details)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun ConnectedDeviceCard(
    device: ConnectedDevice,
    onDelete: () -> Unit
) {
    val ext = HomeSphereTheme.extendedColors
    val icon = when (device.category) {
        DiscoveredDeviceCategory.MOBILE_PHONE -> Icons.Default.PhoneAndroid
        DiscoveredDeviceCategory.LAPTOP -> Icons.Default.Laptop
        DiscoveredDeviceCategory.ESP8266 -> Icons.Default.Memory
        DiscoveredDeviceCategory.ROUTER -> Icons.Default.Router
        DiscoveredDeviceCategory.OTHER_IOT -> Icons.Default.DevicesOther
    }

    HomeSphereCard(
        borderColor = ext.cardBorder
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = device.category.label,
                    tint = if (device.isOnline) MaterialTheme.colorScheme.primary else ext.offline
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(device.name, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "IP: ${device.ipAddress}  •  MAC: ${device.macAddress ?: "Unavailable"}",
                    style = HomeSphereTextStyles.MonoMetricSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = device.connectionDetails,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_device_${device.id}")
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete ${device.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusBadge(
                    label = if (device.isOnline) "ONLINE" else "OFFLINE",
                    tone = if (device.isOnline) StatusTone.ONLINE else StatusTone.DISCONNECTED
                )
                if (device.isManual) {
                    StatusBadge(label = "MANUAL", tone = StatusTone.INFO)
                }
            }
            Text(
                text = "Seen ${FormatUtils.formatRelativeTime(device.lastSeen)}",
                style = HomeSphereTextStyles.MonoMetricSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AddManualDeviceDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String?, DiscoveredDeviceCategory, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var ip by remember { mutableStateOf("192.168.1.") }
    var mac by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(DiscoveredDeviceCategory.ESP8266) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Register Network Device") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Device Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("Local IP Address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = mac,
                    onValueChange = { mac = it },
                    label = { Text("MAC Address (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it },
                    label = { Text("Connection Notes") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(DiscoveredDeviceCategory.entries) { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && ip.isNotBlank()) {
                        onSave(name, ip, mac.takeIf { it.isNotBlank() }, category, details)
                    }
                },
                enabled = name.isNotBlank() && ip.isNotBlank()
            ) { Text("Register") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
