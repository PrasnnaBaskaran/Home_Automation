package com.example.homesphere.features.fan

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.homesphere.core.design.HardwareEndpointBanner
import com.example.homesphere.core.design.HomeSphereCard
import com.example.homesphere.core.design.HomeSphereDetailTopBar
import com.example.homesphere.core.design.HomeSphereTextStyles
import com.example.homesphere.core.design.HomeSphereTheme
import com.example.homesphere.core.design.SectionHeader
import com.example.homesphere.core.design.rememberLiveTimeMillis
import com.example.homesphere.core.design.toTone
import com.example.homesphere.core.utils.FormatUtils
import com.example.homesphere.domain.model.ConnectionStatus
import com.example.homesphere.domain.model.FanMode
import com.example.homesphere.domain.model.FanSpeedPreset
import kotlin.math.roundToInt

@Composable
fun FanControlScreen(
    viewModel: FanViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val fan = state.fanState
    val nowMillis = rememberLiveTimeMillis()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.feedbackMessage) {
        state.feedbackMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFeedback()
        }
    }

    val isConnected = fan.connectionStatus == ConnectionStatus.CONNECTED

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            HomeSphereDetailTopBar(
                title = "Smart Fan",
                subtitle = "Last updated ${FormatUtils.formatRelativeTime(fan.lastUpdated, nowMillis)} • Checks every 30s",
                statusLabel = fan.connectionStatus.label,
                statusTone = fan.connectionStatus.toTone(),
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
                    connectionStatus = fan.connectionStatus,
                    endpointLabel = "HARDWARE DISCONNECTED",
                    explanation = if (isConnected) {
                        "Live ESP8266 fan relay active at http://${state.esp8266Ip}/api/fan"
                    } else {
                        "Awaiting ESP8266 relay at http://${state.esp8266Ip}/api/fan. Tap Sync when connected to LAN."
                    },
                    isSyncing = state.isSyncing,
                    onSyncClick = viewModel::syncHardware
                )
            }

            item {
                val primary = MaterialTheme.colorScheme.primary

                HomeSphereCard(
                    accentGlow = if (fan.isOn && isConnected) primary else null,
                    contentPadding = 20.dp
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AnimatedSmartFanCanvas(
                            isOn = fan.isOn && isConnected,
                            speedPercent = fan.speedPercent,
                            accentColor = primary,
                            modifier = Modifier.size(200.dp)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = if (fan.isOn && isConnected) "${fan.speedPercent}%" else "OFF",
                            style = HomeSphereTextStyles.MonoMetricLarge,
                            color = if (fan.isOn && isConnected) primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = fan.operatingStatus,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = viewModel::togglePower,
                            enabled = isConnected,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (fan.isOn) MaterialTheme.colorScheme.error else primary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("fan_power_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PowerSettingsNew,
                                contentDescription = "Toggle fan power"
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = when {
                                    !isConnected -> "Fan Disconnected"
                                    fan.isOn -> "Turn Fan OFF"
                                    else -> "Turn Fan ON"
                                },
                                style = MaterialTheme.typography.titleMedium
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
                        Text(
                            text = "Speed Control",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${fan.speedPercent}%",
                            style = HomeSphereTextStyles.MonoMetricMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = fan.speedPercent.toFloat(),
                        onValueChange = { viewModel.setSpeed(it.roundToInt()) },
                        valueRange = 0f..100f,
                        enabled = isConnected,
                        modifier = Modifier.testTag("fan_speed_slider"),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FanSpeedPreset.entries.forEach { preset ->
                            val selected = isConnected && fan.isOn && kotlin.math.abs(fan.speedPercent - preset.speedPercent) <= 10
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else HomeSphereTheme.extendedColors.cardBorder
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable(enabled = isConnected) {
                                        viewModel.setSpeed(preset.speedPercent)
                                    }
                                    .testTag("fan_preset_${preset.name.lowercase()}")
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = preset.label,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${preset.speedPercent}%",
                                        style = HomeSphereTextStyles.MonoMetricSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                SectionHeader(title = "Operating Mode")
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FanMode.entries.forEach { mode ->
                        val isSelected = fan.mode == mode && isConnected
                        val icon = when (mode) {
                            FanMode.NORMAL -> Icons.Default.Air
                            FanMode.SLEEP -> Icons.Default.Bedtime
                            FanMode.TURBO -> Icons.Default.Bolt
                        }
                        HomeSphereCard(
                            onClick = if (isConnected) {
                                { viewModel.setMode(mode) }
                            } else null,
                            borderColor = if (isSelected) MaterialTheme.colorScheme.primary
                            else HomeSphereTheme.extendedColors.cardBorder,
                            accentGlow = if (isSelected) MaterialTheme.colorScheme.primary else null,
                            contentPadding = 12.dp,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("fan_mode_${mode.name.lowercase()}")
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = mode.label,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = mode.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )
                        }
                    }
                }
            }

            if (state.recentEvents.isNotEmpty()) {
                item {
                    SectionHeader(title = "Recent Fan Activity")
                    Spacer(modifier = Modifier.height(8.dp))
                    HomeSphereCard {
                        state.recentEvents.take(5).forEachIndexed { idx, event ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
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
                            if (idx < state.recentEvents.take(5).lastIndex) {
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

@Composable
private fun AnimatedSmartFanCanvas(
    isOn: Boolean,
    speedPercent: Int,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    var rotationDegrees by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isOn, speedPercent) {
        if (!isOn || speedPercent <= 0) return@LaunchedEffect
        var lastFrameNanos = 0L
        while (true) {
            withFrameNanos { frameTimeNanos ->
                if (lastFrameNanos != 0L) {
                    val deltaSec = (frameTimeNanos - lastFrameNanos) / 1_000_000_000f
                    val degPerSec = 110f + (speedPercent * 6.5f)
                    rotationDegrees = (rotationDegrees + degPerSec * deltaSec) % 360f
                }
                lastFrameNanos = frameTimeNanos
            }
        }
    }

    val ringColor = MaterialTheme.colorScheme.outline
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f - 10.dp.toPx()

        if (isOn && speedPercent > 0) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.25f * (speedPercent / 100f).coerceAtLeast(0.25f)),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius * 1.15f
                ),
                radius = radius * 1.15f,
                center = center
            )
        }

        drawCircle(
            color = if (isOn) accentColor.copy(alpha = 0.65f) else ringColor,
            radius = radius,
            center = center,
            style = Stroke(width = 3.dp.toPx())
        )
        drawCircle(
            color = surfaceColor.copy(alpha = 0.45f),
            radius = radius - 6.dp.toPx(),
            center = center
        )

        val bladeLength = radius * 0.78f
        val bladeWidth = radius * 0.28f
        rotate(degrees = rotationDegrees, pivot = center) {
            for (i in 0 until 3) {
                rotate(degrees = i * 120f, pivot = center) {
                    val bladePath = Path().apply {
                        moveTo(center.x, center.y)
                        cubicTo(
                            center.x - bladeWidth,
                            center.y - bladeLength * 0.35f,
                            center.x - bladeWidth * 0.7f,
                            center.y - bladeLength,
                            center.x + bladeWidth * 0.15f,
                            center.y - bladeLength * 0.92f
                        )
                        cubicTo(
                            center.x + bladeWidth * 0.55f,
                            center.y - bladeLength * 0.72f,
                            center.x + bladeWidth * 0.3f,
                            center.y - bladeLength * 0.25f,
                            center.x,
                            center.y
                        )
                        close()
                    }
                    drawPath(
                        path = bladePath,
                        color = if (isOn) accentColor else Color(0xFF64748B)
                    )
                }
            }
        }

        drawCircle(
            color = Color(0xFF0A0E17),
            radius = radius * 0.18f,
            center = center
        )
        drawCircle(
            color = if (isOn) accentColor else ringColor,
            radius = radius * 0.18f,
            center = center,
            style = Stroke(width = 2.5.dp.toPx())
        )
        drawCircle(
            color = if (isOn) accentColor else Color(0xFF64748B),
            radius = radius * 0.07f,
            center = center
        )
    }
}
