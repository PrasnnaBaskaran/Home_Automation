package com.example.homesphere.features.lighting

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.example.homesphere.domain.model.LightMode
import kotlin.math.roundToInt

@Composable
fun RoomLightScreen(
    viewModel: LightingViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val light = state.lightState
    val nowMillis = rememberLiveTimeMillis()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.feedbackMessage) {
        state.feedbackMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFeedback()
        }
    }

    val controlsInteractive = light.connectionStatus == ConnectionStatus.CONNECTED

    val targetLampColor = when (light.mode) {
        LightMode.WARM -> Color(0xFFFFB74D)
        LightMode.COOL -> Color(0xFF7DD3FC)
        LightMode.READING -> Color(0xFFFDE68A)
        LightMode.NIGHT -> Color(0xFFF97316)
    }
    val lampColor by animateColorAsState(
        targetValue = if (light.isOn) targetLampColor else Color(0xFF64748B),
        animationSpec = tween(450),
        label = "lamp_color"
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            HomeSphereDetailTopBar(
                title = "Room Light",
                subtitle = "Last updated ${FormatUtils.formatRelativeTime(light.lastUpdated, nowMillis)} • Checks every 30s",
                statusLabel = light.connectionStatus.label,
                statusTone = light.connectionStatus.toTone(),
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
                    connectionStatus = light.connectionStatus,
                    endpointLabel = "ESP8266 LIGHT • ${state.esp8266Ip}",
                    explanation = if (controlsInteractive) {
                        "Connected live to http://${state.esp8266Ip}/api/light for real-time dimming & presets."
                    } else {
                        "Awaiting live ESP8266 dimmer relay at http://${state.esp8266Ip}/api/light. Tap Sync to poll."
                    },
                    isSyncing = state.isSyncing,
                    onSyncClick = viewModel::syncHardware
                )
            }

            // Glowing Lamp Hero
            item {
                HomeSphereCard(accentGlow = if (light.isOn) lampColor else null, contentPadding = 20.dp) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier.size(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val intensity = if (light.isOn) (light.brightnessPercent / 100f).coerceIn(0.2f, 1f) else 0f
                                if (intensity > 0f) {
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            colors = listOf(
                                                lampColor.copy(alpha = 0.55f * intensity),
                                                lampColor.copy(alpha = 0.16f * intensity),
                                                Color.Transparent
                                            ),
                                            center = center,
                                            radius = size.minDimension / 2f
                                        ),
                                        radius = size.minDimension / 2f,
                                        center = center
                                    )
                                }
                                drawCircle(
                                    color = lampColor.copy(alpha = if (light.isOn) 0.25f else 0.12f),
                                    radius = 48.dp.toPx(),
                                    center = center
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = "Room light bulb",
                                tint = lampColor,
                                modifier = Modifier.size(52.dp)
                            )
                        }

                        Text(
                            text = if (light.isOn) "${light.brightnessPercent}% • ${light.mode.label}" else "OFF",
                            style = HomeSphereTextStyles.MonoMetricLarge,
                            color = if (light.isOn) lampColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${light.mode.kelvin} Color Preset",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = viewModel::togglePower,
                            enabled = controlsInteractive,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (light.isOn) lampColor else MaterialTheme.colorScheme.primary,
                                contentColor = Color(0xFF0A0E17)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("light_power_button")
                        ) {
                            Icon(Icons.Default.PowerSettingsNew, contentDescription = "Toggle room light")
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = when {
                                    !controlsInteractive -> "Light Disconnected"
                                    light.isOn -> "Turn Light OFF"
                                    else -> "Turn Light ON"
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }

            // Brightness Slider
            item {
                HomeSphereCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Brightness", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${light.brightnessPercent}%",
                            style = HomeSphereTextStyles.MonoMetricMedium,
                            color = lampColor
                        )
                    }
                    Slider(
                        value = light.brightnessPercent.toFloat(),
                        onValueChange = { viewModel.setBrightness(it.roundToInt()) },
                        valueRange = 0f..100f,
                        enabled = controlsInteractive,
                        modifier = Modifier.testTag("light_brightness_slider"),
                        colors = SliderDefaults.colors(
                            thumbColor = lampColor,
                            activeTrackColor = lampColor
                        )
                    )
                }
            }

            // Lighting Modes Grid
            item {
                SectionHeader(title = "Lighting Modes")
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LightMode.entries.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            pair.forEach { mode ->
                                val selected = light.mode == mode
                                val icon = when (mode) {
                                    LightMode.WARM -> Icons.Default.WbTwilight
                                    LightMode.COOL -> Icons.Default.WbSunny
                                    LightMode.READING -> Icons.Default.MenuBook
                                    LightMode.NIGHT -> Icons.Default.NightsStay
                                }
                                HomeSphereCard(
                                    onClick = if (controlsInteractive) {
                                        { viewModel.setMode(mode) }
                                    } else null,
                                    borderColor = if (selected) lampColor else HomeSphereTheme.extendedColors.cardBorder,
                                    accentGlow = if (selected && light.isOn) lampColor else null,
                                    contentPadding = 14.dp,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("light_mode_${mode.name.lowercase()}")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = mode.label,
                                            tint = if (selected) lampColor else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Column {
                                            Text(
                                                text = mode.label,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = mode.kelvin,
                                                style = HomeSphereTextStyles.MonoMetricSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Live Energy Usage Telemetry Card
            item {
                val watts = light.measuredWatts
                HomeSphereCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = "Energy usage",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Column {
                                Text("Energy Usage Telemetry", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = if (watts != null) "Live load from INA219 / smart meter"
                                    else "Awaiting smart meter / INA219 sensor reading",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = watts?.let { String.format("%.1f W", it) } ?: "-- W",
                                style = HomeSphereTextStyles.MonoMetricMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            StatusBadge(
                                label = if (watts != null) "LIVE" else "AWAITING METER",
                                tone = if (watts != null) StatusTone.ONLINE else StatusTone.DISCONNECTED
                            )
                        }
                    }
                }
            }

            // Activity History
            item {
                SectionHeader(title = "Light Activity History")
            }

            if (state.activityHistory.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.Lightbulb,
                        title = "No Light History",
                        subtitle = "Connect your ESP8266 lighting relay to record live switching and dimming activity."
                    )
                }
            } else {
                item {
                    HomeSphereCard {
                        state.activityHistory.take(6).forEachIndexed { idx, event ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(event.title, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        event.details,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    FormatUtils.formatRelativeTime(event.timestamp),
                                    style = HomeSphereTextStyles.MonoMetricSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (idx < state.activityHistory.take(6).lastIndex) {
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
