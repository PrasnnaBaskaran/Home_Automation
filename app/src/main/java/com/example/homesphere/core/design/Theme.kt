package com.example.homesphere.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.homesphere.domain.model.AppThemeMode

// Obsidian Precision IoT Palette (Dark Default)
val ObsidianBg = Color(0xFF0A0E17)
val MidnightSurface = Color(0xFF121826)
val RaisedSurface = Color(0xFF1A2235)
val SurfaceStrokeDark = Color(0xFF28344D)

val ElectricCyan = Color(0xFF38BDF8)
val ElectricCyanDark = Color(0xFF0284C7)
val CyanContainerDark = Color(0xFF0E344A)

val WarmAmber = Color(0xFFF59E0B)
val WarmAmberSoft = Color(0xFFFBBF24)
val AmberContainerDark = Color(0xFF3B2908)

val EmeraldPulse = Color(0xFF10B981)
val EmeraldContainerDark = Color(0xFF0A3327)

val CoralAlert = Color(0xFFF43F5E)
val CoralContainerDark = Color(0xFF42121D)

val SlateOffline = Color(0xFF64748B)
val SlateOfflineLight = Color(0xFF475569)

val AlabasterText = Color(0xFFF1F5F9)
val CoolMutedText = Color(0xFF94A3B8)

// Porcelain Daylight Palette (Light Theme)
val MistBg = Color(0xFFF3F6FB)
val PureWhiteSurface = Color(0xFFFFFFFF)
val SoftElevatedLight = Color(0xFFE9EFF8)
val SurfaceStrokeLight = Color(0xFFD5DFEE)
val InkText = Color(0xFF0F172A)
val SlateMutedLight = Color(0xFF475569)

@Immutable
data class HomeSphereExtendedColors(
    val online: Color,
    val onOnline: Color,
    val onlineContainer: Color,
    val demo: Color,
    val onDemo: Color,
    val demoContainer: Color,
    val offline: Color,
    val offlineContainer: Color,
    val warning: Color,
    val waterAccent: Color,
    val lightWarm: Color,
    val lightCool: Color,
    val cardBorder: Color,
    val cardGradientStart: Color,
    val cardGradientEnd: Color,
    val heroScrim: Color,
    val isDark: Boolean
)

val DarkExtendedColors = HomeSphereExtendedColors(
    online = EmeraldPulse,
    onOnline = Color(0xFF042218),
    onlineContainer = EmeraldContainerDark,
    demo = WarmAmberSoft,
    onDemo = Color(0xFF2B1C00),
    demoContainer = AmberContainerDark,
    offline = SlateOffline,
    offlineContainer = Color(0xFF1E2636),
    warning = WarmAmber,
    waterAccent = ElectricCyan,
    lightWarm = Color(0xFFFFB74D),
    lightCool = Color(0xFF7DD3FC),
    cardBorder = SurfaceStrokeDark,
    cardGradientStart = Color(0xFF172033),
    cardGradientEnd = Color(0xFF101624),
    heroScrim = Color(0xCC0A0E17),
    isDark = true
)

val LightExtendedColors = HomeSphereExtendedColors(
    online = Color(0xFF059669),
    onOnline = Color.White,
    onlineContainer = Color(0xFFD1FAE5),
    demo = Color(0xFFB45309),
    onDemo = Color.White,
    demoContainer = Color(0xFFFEF3C7),
    offline = SlateOfflineLight,
    offlineContainer = Color(0xFFE2E8F0),
    warning = Color(0xFFD97706),
    waterAccent = Color(0xFF0284C7),
    lightWarm = Color(0xFFEA580C),
    lightCool = Color(0xFF0284C7),
    cardBorder = SurfaceStrokeLight,
    cardGradientStart = Color(0xFFFFFFFF),
    cardGradientEnd = Color(0xFFF6F9FD),
    heroScrim = Color(0xB30F172A),
    isDark = false
)

val LocalHomeSphereColors = staticCompositionLocalOf { DarkExtendedColors }

private val HomeSphereDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = Color(0xFF032030),
    primaryContainer = CyanContainerDark,
    onPrimaryContainer = Color(0xFFBAE6FD),
    secondary = WarmAmber,
    onSecondary = Color(0xFF291800),
    secondaryContainer = AmberContainerDark,
    onSecondaryContainer = Color(0xFFFDE68A),
    tertiary = EmeraldPulse,
    onTertiary = Color(0xFF022C22),
    tertiaryContainer = EmeraldContainerDark,
    onTertiaryContainer = Color(0xFFA7F3D0),
    error = CoralAlert,
    onError = Color.White,
    errorContainer = CoralContainerDark,
    onErrorContainer = Color(0xFFFECDD3),
    background = ObsidianBg,
    onBackground = AlabasterText,
    surface = MidnightSurface,
    onSurface = AlabasterText,
    surfaceVariant = RaisedSurface,
    onSurfaceVariant = CoolMutedText,
    outline = SurfaceStrokeDark,
    outlineVariant = Color(0xFF1E293B)
)

private val HomeSphereLightColorScheme = lightColorScheme(
    primary = ElectricCyanDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF075985),
    secondary = Color(0xFFD97706),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFEF3C7),
    onSecondaryContainer = Color(0xFF92400E),
    tertiary = Color(0xFF059669),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD1FAE5),
    onTertiaryContainer = Color(0xFF065F46),
    error = Color(0xFFE11D48),
    onError = Color.White,
    errorContainer = Color(0xFFFFE4E6),
    onErrorContainer = Color(0xFF9F1239),
    background = MistBg,
    onBackground = InkText,
    surface = PureWhiteSurface,
    onSurface = InkText,
    surfaceVariant = SoftElevatedLight,
    onSurfaceVariant = SlateMutedLight,
    outline = SurfaceStrokeLight,
    outlineVariant = Color(0xFFE2E8F0)
)

val HomeSphereShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

object HomeSphereTheme {
    val extendedColors: HomeSphereExtendedColors
        @Composable
        get() = LocalHomeSphereColors.current
}

@Composable
fun HomeSphereAppTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (useDark) HomeSphereDarkColorScheme else HomeSphereLightColorScheme
    val extended = if (useDark) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalHomeSphereColors provides extended) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = HomeSphereTypography,
            shapes = HomeSphereShapes,
            content = content
        )
    }
}
