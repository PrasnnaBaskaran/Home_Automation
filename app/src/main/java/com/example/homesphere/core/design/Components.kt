package com.example.homesphere.core.design

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.homesphere.domain.model.ConnectionStatus
import kotlinx.coroutines.delay

@Composable
fun rememberLiveTimeMillis(intervalMs: Long = 1_000L): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(intervalMs) {
        while (true) {
            delay(intervalMs)
            now = System.currentTimeMillis()
        }
    }
    return now
}

@Composable
fun HomeSphereCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    borderColor: Color = HomeSphereTheme.extendedColors.cardBorder,
    accentGlow: Color? = null,
    cornerRadius: Dp = 20.dp,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val ext = HomeSphereTheme.extendedColors
    val shape = RoundedCornerShape(cornerRadius)
    val gradient = if (accentGlow != null) {
        Brush.linearGradient(
            colors = listOf(
                accentGlow.copy(alpha = if (ext.isDark) 0.16f else 0.10f),
                ext.cardGradientStart,
                ext.cardGradientEnd
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(ext.cardGradientStart, ext.cardGradientEnd)
        )
    }

    val clickableModifier = if (onClick != null) {
        Modifier
            .clip(shape)
            .clickable(onClick = onClick)
    } else {
        Modifier.clip(shape)
    }

    Box(
        modifier = modifier
            .then(clickableModifier)
            .background(gradient, shape)
            .border(BorderStroke(1.dp, borderColor), shape)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content
        )
    }
}

enum class StatusTone { ONLINE, WARNING, DISCONNECTED, ALERT, INFO }

@Composable
fun StatusBadge(
    label: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    pulsing: Boolean = false
) {
    val ext = HomeSphereTheme.extendedColors
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) = when (tone) {
        StatusTone.ONLINE -> ext.onlineContainer to ext.online
        StatusTone.WARNING -> ext.demoContainer to ext.warning
        StatusTone.DISCONNECTED -> ext.offlineContainer to ext.offline
        StatusTone.ALERT -> scheme.errorContainer to scheme.error
        StatusTone.INFO -> scheme.primaryContainer to scheme.primary
    }

    val pulseAlpha = if (pulsing) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val anim by infiniteTransition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_alpha"
        )
        anim
    } else 1f

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = bg,
        border = BorderStroke(1.dp, fg.copy(alpha = 0.32f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(fg.copy(alpha = pulseAlpha))
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = fg,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

fun ConnectionStatus.toTone(): StatusTone = when (this) {
    ConnectionStatus.CONNECTED -> StatusTone.ONLINE
    ConnectionStatus.CONNECTING -> StatusTone.INFO
    ConnectionStatus.DISCONNECTED -> StatusTone.DISCONNECTED
}

@Composable
fun HardwareEndpointBanner(
    connectionStatus: ConnectionStatus,
    endpointLabel: String,
    explanation: String,
    isSyncing: Boolean,
    onSyncClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = HomeSphereTheme.extendedColors
    val isConnected = connectionStatus == ConnectionStatus.CONNECTED
    val borderColor = if (isConnected) ext.online.copy(alpha = 0.55f) else ext.cardBorder
    val containerColor = if (isConnected) ext.onlineContainer.copy(alpha = 0.55f) else ext.offlineContainer.copy(alpha = 0.7f)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isConnected) ext.online.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = "ESP8266 hardware link",
                    tint = if (isConnected) ext.online else ext.offline,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isConnected) "LIVE HARDWARE CONNECTED" else endpointLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isConnected) ext.online else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalButton(
                onClick = onSyncClick,
                enabled = !isSyncing,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("sync_hardware_button")
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Sync, contentDescription = "Sync hardware", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.size(4.dp))
                    Text("Sync", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeSphereDetailTopBar(
    title: String,
    subtitle: String? = null,
    statusLabel: String? = null,
    statusTone: StatusTone = StatusTone.DISCONNECTED,
    onNavigateBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (statusLabel != null) {
                        StatusBadge(
                            label = statusLabel,
                            tone = statusTone,
                            pulsing = statusTone == StatusTone.ONLINE
                        )
                    }
                }
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.testTag("back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Navigate back"
                )
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground
        )
    )
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailingText: String? = null,
    onTrailingClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (trailingText != null) {
            Text(
                text = trailingText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = if (onTrailingClick != null) {
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onTrailingClick)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                } else Modifier
            )
        }
    }
}

@Composable
fun TelemetryLineChart(
    values: List<Float>,
    lineColor: Color,
    unit: String,
    modifier: Modifier = Modifier,
    minValOverride: Float? = null,
    maxValOverride: Float? = null,
    emptyMessage: String = "Awaiting real-time telemetry samples"
) {
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    if (values.size < 2) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(140.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = emptyMessage,
                style = MaterialTheme.typography.bodySmall,
                color = labelColor
            )
        }
        return
    }

    val minVal = minValOverride ?: (values.minOrNull() ?: 0f)
    val maxVal = maxValOverride ?: (values.maxOrNull() ?: 100f)
    val range = (maxVal - minVal).coerceAtLeast(1f)
    val latest = values.last()

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Min ${formatChartNumber(minVal)} $unit  •  Max ${formatChartNumber(maxVal)} $unit",
                style = HomeSphereTextStyles.MonoMetricSmall,
                color = labelColor
            )
            Text(
                text = "Live ${formatChartNumber(latest)} $unit",
                style = HomeSphereTextStyles.MonoMetricSmall,
                color = lineColor,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
        ) {
            val width = size.width
            val height = size.height
            val paddingY = 10.dp.toPx()
            val usableHeight = (height - paddingY * 2).coerceAtLeast(1f)

            for (i in 0..3) {
                val y = paddingY + usableHeight * (i / 3f)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            val stepX = width / (values.size - 1).coerceAtLeast(1)
            val points = values.mapIndexed { idx, v ->
                val norm = ((v - minVal) / range).coerceIn(0f, 1f)
                Offset(
                    x = idx * stepX,
                    y = paddingY + usableHeight * (1f - norm)
                )
            }

            val linePath = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 0 until points.lastIndex) {
                    val p0 = points[i]
                    val p1 = points[i + 1]
                    val cx = (p0.x + p1.x) / 2f
                    cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                }
            }

            val fillPath = Path().apply {
                addPath(linePath)
                lineTo(points.last().x, height)
                lineTo(points.first().x, height)
                close()
            }

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        lineColor.copy(alpha = 0.34f),
                        lineColor.copy(alpha = 0.02f)
                    )
                )
            )

            drawPath(
                path = linePath,
                color = lineColor,
                style = Stroke(
                    width = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            val last = points.last()
            drawCircle(
                color = lineColor.copy(alpha = 0.28f),
                radius = 7.dp.toPx(),
                center = last
            )
            drawCircle(
                color = lineColor,
                radius = 3.5.dp.toPx(),
                center = last
            )
        }
    }
}

private fun formatChartNumber(value: Float): String {
    return if (value % 1f == 0f) value.toInt().toString() else String.format("%.1f", value)
}

@Composable
fun EmptyStateCard(
    icon: ImageVector = Icons.Outlined.Info,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    HomeSphereCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
