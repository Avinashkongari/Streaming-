package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ColorTokenDivider = androidx.compose.ui.graphics.Color(0xFF22222E)
private val ColorTokenErrorContainer = androidx.compose.ui.graphics.Color(0xFF3E1118)

private val AxionDarkColorScheme = darkColorScheme(
    primary = AxionCrimson,
    onPrimary = TextPrimary,
    primaryContainer = AxionCrimsonContainer,
    onPrimaryContainer = AxionCrimsonBright,
    secondary = ChampagneGold,
    onSecondary = ObsidianBlack,
    secondaryContainer = SurfaceElevated,
    onSecondaryContainer = ChampagneGold,
    tertiary = EmeraldActive,
    onTertiary = ObsidianBlack,
    background = ObsidianBlack,
    onBackground = TextPrimary,
    surface = ObsidianDeep,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceCarbon,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianBlack,
    surfaceContainerLow = ObsidianDeep,
    surfaceContainer = SurfaceCarbon,
    surfaceContainerHigh = SurfaceSlate,
    surfaceContainerHighest = SurfaceElevated,
    outline = BorderSubtle,
    outlineVariant = ColorTokenDivider,
    error = DangerRose,
    onError = ObsidianBlack,
    errorContainer = ColorTokenErrorContainer,
    onErrorContainer = TextPrimary
)

val AxionShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun AxionStreamTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AxionDarkColorScheme,
        typography = AxionTypography,
        shapes = AxionShapes,
        content = content
    )
}
