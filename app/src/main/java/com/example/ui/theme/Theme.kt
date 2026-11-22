package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val StudioDarkColorScheme = darkColorScheme(
    primary = CinemaAmber,
    onPrimary = Color(0xFF1A1100),
    primaryContainer = CinemaAmberDark,
    onPrimaryContainer = Color(0xFFFFE0A6),
    secondary = AnamorphicCyan,
    onSecondary = Color(0xFF002026),
    secondaryContainer = AnamorphicCyanDark,
    onSecondaryContainer = Color(0xFFBAF4FF),
    tertiary = StudioRose,
    onTertiary = Color(0xFF2B000B),
    tertiaryContainer = StudioRoseDark,
    onTertiaryContainer = Color(0xFFFFD9DF),
    background = ObsidianAbyss,
    onBackground = TextPrimary,
    surface = StudioSlate,
    onSurface = TextPrimary,
    surfaceVariant = ElevatedPanel,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLow = StudioSlate,
    surfaceContainer = ElevatedPanel,
    surfaceContainerHigh = TrackSurface,
    outline = SubtleBorder,
    outlineVariant = Color(0xFF1D2639),
    error = StudioRose,
    onError = Color(0xFF2B000B),
    errorContainer = StudioRoseDark,
    onErrorContainer = Color(0xFFFFD9DF)
)

private val StudioShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun AshishStudioTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StudioDarkColorScheme,
        typography = StudioTypography,
        shapes = StudioShapes,
        content = content
    )
}
