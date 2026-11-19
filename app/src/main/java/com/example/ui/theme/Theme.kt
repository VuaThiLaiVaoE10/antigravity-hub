package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class TelemetryColors(
    val warning: Color = SignalAmber,
    val warningContainer: Color = SignalAmberContainer,
    val online: Color = OrbitalEmerald,
    val onlineContainer: Color = OrbitalEmeraldContainer,
    val thinking: Color = DeepThinkingViolet,
    val thinkingContainer: Color = DeepThinkingVioletContainer,
    val hairlineBorder: Color = HairlineBorder,
    val hairlineBorderStrong: Color = HairlineBorderStrong,
    val inkMuted: Color = InkMuted
)

val LocalTelemetryColors = staticCompositionLocalOf { TelemetryColors() }

private val MissionControlDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = ElectricCyanDark,
    primaryContainer = ElectricCyanContainer,
    onPrimaryContainer = ElectricCyanLight,
    secondary = OrbitalEmerald,
    onSecondary = OrbitalEmeraldDark,
    secondaryContainer = OrbitalEmeraldContainer,
    onSecondaryContainer = OrbitalEmeraldLight,
    tertiary = DeepThinkingViolet,
    onTertiary = DeepThinkingVioletDark,
    tertiaryContainer = DeepThinkingVioletContainer,
    onTertiaryContainer = DeepThinkingVioletLight,
    error = SignalCoral,
    onError = SignalCoralDark,
    errorContainer = SignalCoralContainer,
    onErrorContainer = SignalCoralLight,
    background = ObsidianDeep,
    onBackground = InkPrimary,
    surface = SlateCommandDeck,
    onSurface = InkPrimary,
    surfaceVariant = TelemetryCardElevated,
    onSurfaceVariant = InkSecondary,
    surfaceContainerLowest = ObsidianDeep,
    surfaceContainerLow = SlateCommandDeck,
    surfaceContainer = TelemetrySurface,
    surfaceContainerHigh = TelemetryCardElevated,
    surfaceContainerHighest = TelemetrySurfaceHighest,
    outline = HairlineBorderStrong,
    outlineVariant = HairlineBorder
)

val MissionControlShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun AntigravityHubTheme(
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalTelemetryColors provides TelemetryColors()) {
        MaterialTheme(
            colorScheme = MissionControlDarkColorScheme,
            typography = AppTypography,
            shapes = MissionControlShapes,
            content = content
        )
    }
}
