package com.example.homesphere.features.water

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.homesphere.core.design.HardwareEndpointBanner
import com.example.homesphere.core.design.HomeSphereCard
import com.example.homesphere.core.design.HomeSphereDetailTopBar
import com.example.homesphere.core.design.HomeSphereTextStyles
import com.example.homesphere.core.design.HomeSphereTheme
import com.example.homesphere.core.design.SectionHeader
import com.example.homesphere.core.design.StatusBadge
import com.example.homesphere.core.design.StatusTone
import com.example.homesphere.core.design.TelemetryLineChart
import com.example.homesphere.core.design.rememberLiveTimeMillis
import com.example.homesphere.core.design.toTone
import com.example.homesphere.core.utils.FormatUtils
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun WaterMonitorScreen(
    viewModel: WaterViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val reading = state.reading
    val ext = HomeSphereTheme.extendedColors
    val nowMillis = rememberLiveTimeMillis()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCapacityDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.feedbackMessage) {
        state.feedbackMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFeedback()
        }
    }

    val isConnected = reading.sensorConnected
    val isLowAlert = isConnected && reading.percentage <= reading.lowThresholdPercent

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            HomeSphereDetailTopBar(
                title = "Water Level Monitor",
                subtitle = "Last updated ${FormatUtils.formatRelativeTime(reading.timestamp, nowMillis)} • Checks every 30s",
                statusLabel = if (!isConnected) "SENSOR DISCONNECTED" else reading.connectionStatus.label,
                statusTone = if (!isConnected) StatusTone.DISCONNECTED else reading.connectionStatus.toTone(),
                onNavigateBack = onNavigateBack,
                actions = {
                    IconButton(
                        onClick = viewModel::refreshReading,
                        modifier = Modifier.testTag("water_refresh_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh water sensor")
                    }
                }
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
                    connectionStatus = reading.connectionStatus,
                    endpointLabel = "SENSOR DISCONNECTED",
                    explanation = if (isConnected) {
                        "Receiving live ultrasonic readings from http://${state.settings.esp8266Ip}/api/water"
                    } else {
                        "Awaiting ESP8266 water sensor at http://${state.settings.esp8266Ip}/api/water."
                    },
                    isSyncing = state.isSyncing,
                    onSyncClick = viewModel::refreshReading
                )
            }

            item {
                val gaugeColor = when {
                    !isConnected -> ext.offline
                    isLowAlert -> MaterialTheme.colorScheme.error
                    reading.percentage >= reading.highThresholdPercent -> ext.online
                    else -> ext.waterAccent
                }

                HomeSphereCard(
                    accentGlow = if (isConnected) gaugeColor else null,
                    contentPadding = 22.dp
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularWaterGauge(
                            percentage = if (isConnected) reading.percentage else 0,
                            active = isConnected,
                            waterColor = gaugeColor,
                            modifier = Modifier.size(210.dp)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        if (!isConnected) {
                            StatusBadge(label = "SENSOR DISCONNECTED", tone = StatusTone.DISCONNECTED)
                        } else if (isLowAlert) {
                            StatusBadge(label = "LOW WATER THRESHOLD BREACHED", tone = StatusTone.ALERT, pulsing = true)
                        } else {
                            StatusBadge(label = "LIVE SENSOR READING", tone = StatusTone.ONLINE, pulsing = true)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            WaterStatBox(
                                label = "Current Volume",
                                value = if (isConnected) "${reading.currentLitres} L" else "-- L",
                                accent = gaugeColor,
                                modifier = Modifier.weight(1f)
                            )
                            WaterStatBox(
                                label = "Remaining Space",
                                value = if (isConnected) "${reading.remainingCapacityLitres} L" else "-- L",
                                accent = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            WaterStatBox(
                                label = "Tank Capacity",
                                value = "${reading.tankCapacity} L",
                                accent = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            item {
                HomeSphereCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionHeader(title = "Water Level History", modifier = Modifier.weight(1f))
                        StatusBadge(label = "LIVE LOG", tone = StatusTone.INFO)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    TelemetryLineChart(
                        values = state.history.map { it.percentage.toFloat() },
                        lineColor = ext.waterAccent,
                        unit = "%",
                        minValOverride = 0f,
                        maxValOverride = 100f,
                        emptyMessage = "No sensor readings recorded yet. Connect your ESP8266 ultrasonic sensor to plot live history."
                    )
                }
            }

            item {
                HomeSphereCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tank & Alert Settings",
                            style = MaterialTheme.typography.titleMedium
                        )
                        TextButton(
                            onClick = { showCapacityDialog = true },
                            modifier = Modifier.testTag("water_edit_capacity_button")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Configure tank", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(4.dp))
                            Text("Configure Tank")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Low-Water Alert Threshold: ${state.settings.lowWaterThresholdPercent}%",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = state.settings.lowWaterThresholdPercent.toFloat(),
                        onValueChange = {
                            viewModel.updateTankConfiguration(
                                state.settings.tankCapacityLitres,
                                it.roundToInt(),
                                state.settings.highWaterThresholdPercent
                            )
                        },
                        valueRange = 5f..45f,
                        modifier = Modifier.testTag("water_low_threshold_slider")
                    )

                    Text(
                        text = "High-Water Alert Threshold: ${state.settings.highWaterThresholdPercent}%",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = state.settings.highWaterThresholdPercent.toFloat(),
                        onValueChange = {
                            viewModel.updateTankConfiguration(
                                state.settings.tankCapacityLitres,
                                state.settings.lowWaterThresholdPercent,
                                it.roundToInt()
                            )
                        },
                        valueRange = 65f..100f,
                        modifier = Modifier.testTag("water_high_threshold_slider")
                    )

                    Spacer(modifier = Modifier.height(8.dp))
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
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text("Low-Water Push Alerts", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Notify when level drops below ${state.settings.lowWaterThresholdPercent}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = state.settings.lowWaterAlertEnabled,
                            onCheckedChange = viewModel::toggleLowWaterAlert,
                            modifier = Modifier.testTag("water_alert_switch")
                        )
                    }
                }
            }
        }
    }

    if (showCapacityDialog) {
        TankCapacityDialog(
            currentCapacity = state.settings.tankCapacityLitres,
            onDismiss = { showCapacityDialog = false },
            onSave = { newCapacity ->
                viewModel.updateTankConfiguration(
                    newCapacity,
                    state.settings.lowWaterThresholdPercent,
                    state.settings.highWaterThresholdPercent
                )
                showCapacityDialog = false
            }
        )
    }
}

@Composable
private fun WaterStatBox(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = HomeSphereTextStyles.MonoMetricMedium,
            color = accent,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CircularWaterGauge(
    percentage: Int,
    active: Boolean,
    waterColor: Color,
    modifier: Modifier = Modifier
) {
    val animatedFill by animateFloatAsState(
        targetValue = if (active) (percentage / 100f).coerceIn(0f, 1f) else 0f,
        animationSpec = tween(700),
        label = "water_fill"
    )
    val infinite = rememberInfiniteTransition(label = "water_wave")
    val wavePhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val borderColor = MaterialTheme.colorScheme.outline

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = 10.dp.toPx()
            val radius = (size.minDimension / 2f) - strokePx
            val center = Offset(size.width / 2f, size.height / 2f)

            drawCircle(
                color = trackColor,
                radius = radius,
                center = center
            )

            val innerRadius = radius - 6.dp.toPx()
            val circlePath = Path().apply {
                addOval(
                    androidx.compose.ui.geometry.Rect(
                        center = center,
                        radius = innerRadius
                    )
                )
            }

            if (active && animatedFill > 0.01f) {
                clipPath(circlePath) {
                    val topY = center.y + innerRadius - (innerRadius * 2f * animatedFill)
                    val amplitude = 7.dp.toPx()
                    val wavePath = Path().apply {
                        moveTo(center.x - innerRadius, topY)
                        var x = center.x - innerRadius
                        while (x <= center.x + innerRadius) {
                            val relX = (x - (center.x - innerRadius)) / (innerRadius * 2f)
                            val y = topY + sin(relX * 2f * PI.toFloat() + wavePhase) * amplitude
                            lineTo(x, y)
                            x += 4f
                        }
                        lineTo(center.x + innerRadius, center.y + innerRadius)
                        lineTo(center.x - innerRadius, center.y + innerRadius)
                        close()
                    }
                    drawPath(
                        path = wavePath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                waterColor.copy(alpha = 0.78f),
                                waterColor.copy(alpha = 0.32f)
                            ),
                            startY = topY,
                            endY = center.y + innerRadius
                        )
                    )
                }
            }

            drawCircle(
                color = borderColor.copy(alpha = 0.5f),
                radius = radius,
                center = center,
                style = Stroke(width = strokePx)
            )
            if (active) {
                drawArc(
                    color = waterColor,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedFill,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (active) "$percentage%" else "--%",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (active) "TANK LEVEL" else "OFFLINE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TankCapacityDialog(
    currentCapacity: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    var input by remember { mutableStateOf(currentCapacity.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure Tank Capacity") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Enter your overhead or underground water tank capacity in litres.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = { value -> input = value.filter { it.isDigit() } },
                    label = { Text("Capacity (Litres)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tank_capacity_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = input.toIntOrNull()?.coerceIn(100, 20000) ?: currentCapacity
                    onSave(parsed)
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
