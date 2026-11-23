package com.example.homesphere.features.door

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoorFront
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.homesphere.core.design.EmptyStateCard
import com.example.homesphere.core.design.HardwareEndpointBanner
import com.example.homesphere.core.design.HomeSphereCard
import com.example.homesphere.core.design.HomeSphereDetailTopBar
import com.example.homesphere.core.design.HomeSphereTextStyles
import com.example.homesphere.core.design.HomeSphereTheme
import com.example.homesphere.core.design.SectionHeader
import com.example.homesphere.core.design.StatusBadge
import com.example.homesphere.core.design.StatusTone
import com.example.homesphere.core.design.rememberLiveTimeMillis
import com.example.homesphere.core.design.toTone
import com.example.homesphere.core.utils.FormatUtils
import com.example.homesphere.domain.model.ConnectionStatus
import com.example.homesphere.domain.model.DoorLockState
import com.example.homesphere.domain.model.DoorSensorState
import com.example.homesphere.domain.model.EventSeverity

@Composable
fun SmartDoorScreen(
    viewModel: DoorViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val door = state.doorState
    val ext = HomeSphereTheme.extendedColors
    val nowMillis = rememberLiveTimeMillis()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.feedbackMessage) {
        state.feedbackMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFeedback()
        }
    }

    val isConnected = door.connectionStatus == ConnectionStatus.CONNECTED

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            HomeSphereDetailTopBar(
                title = "Smart Door",
                subtitle = "Last updated ${FormatUtils.formatRelativeTime(door.lastUpdated, nowMillis)} • Checks every 30s",
                statusLabel = door.connectionStatus.label,
                statusTone = door.connectionStatus.toTone(),
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
            item {
                HardwareEndpointBanner(
                    connectionStatus = door.connectionStatus,
                    endpointLabel = "DISCONNECTED / OFFLINE",
                    explanation = if (isConnected) {
                        "Live reed sensor & deadbolt relay connected at http://${state.esp8266Ip}/api/door"
                    } else {
                        "Door relay offline at http://${state.esp8266Ip}/api/door. Lock state is never fabricated without hardware confirmation."
                    },
                    isSyncing = state.isSyncing,
                    onSyncClick = viewModel::syncHardware
                )
            }

            item {
                val accent = when {
                    !isConnected -> ext.offline
                    door.sensorState == DoorSensorState.OPEN -> MaterialTheme.colorScheme.error
                    door.lockState == DoorLockState.LOCKED -> ext.online
                    else -> ext.warning
                }

                HomeSphereCard(accentGlow = if (isConnected) accent else null, contentPadding = 20.dp) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when {
                                    !isConnected -> Icons.Default.DoorFront
                                    door.lockState == DoorLockState.LOCKED -> Icons.Default.Lock
                                    else -> Icons.Default.LockOpen
                                },
                                contentDescription = "Door lock status",
                                tint = accent,
                                modifier = Modifier.size(48.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = if (!isConnected) "OFFLINE • UNCONFIRMED"
                            else "${door.lockState.label} (${door.sensorState.label})",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        StatusBadge(
                            label = if (isConnected) "HARDWARE CONFIRMED" else "HARDWARE NOT CONNECTED",
                            tone = if (isConnected) StatusTone.ONLINE else StatusTone.DISCONNECTED,
                            pulsing = isConnected
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = viewModel::lockDoor,
                                enabled = isConnected,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ext.online),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("door_lock_button")
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = "Lock door")
                                Spacer(modifier = Modifier.size(6.dp))
                                Text("Lock")
                            }
                            OutlinedButton(
                                onClick = viewModel::unlockDoor,
                                enabled = isConnected,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("door_unlock_button")
                            ) {
                                Icon(Icons.Default.LockOpen, contentDescription = "Unlock door")
                                Spacer(modifier = Modifier.size(6.dp))
                                Text("Unlock")
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HomeSphereCard(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Door Sensor",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = door.sensorState.label,
                            style = HomeSphereTextStyles.MonoMetricMedium,
                            color = if (door.sensorState == DoorSensorState.OPEN) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    HomeSphereCard(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Actuator Lock",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = door.lockState.label,
                            style = HomeSphereTextStyles.MonoMetricMedium,
                            color = if (door.lockState == DoorLockState.LOCKED && isConnected) ext.online
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                SectionHeader(title = "Security Events & Access History")
            }

            if (state.accessHistory.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.Shield,
                        title = "No Door Events Yet",
                        subtitle = "Real hardware lock, unlock, and reed sensor events will appear here."
                    )
                }
            } else {
                item {
                    HomeSphereCard {
                        state.accessHistory.forEachIndexed { idx, event ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val iconColor = when (event.severity) {
                                    EventSeverity.ALERT -> MaterialTheme.colorScheme.error
                                    EventSeverity.WARNING -> ext.warning
                                    EventSeverity.INFO -> ext.online
                                }
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(iconColor.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (event.severity == EventSeverity.ALERT) Icons.Default.Warning
                                        else Icons.Default.Shield,
                                        contentDescription = null,
                                        tint = iconColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = event.title, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = event.details,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = FormatUtils.formatRelativeTime(event.timestamp),
                                    style = HomeSphereTextStyles.MonoMetricSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (idx < state.accessHistory.lastIndex) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
