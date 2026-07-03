package com.eggplant.detector.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = EggplantPurple,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = LeafGreen,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    background = AppBackground,
    onBackground = Ink,
    surface = androidx.compose.ui.graphics.Color.White,
    onSurface = Ink,
    surfaceVariant = EggplantLavender,
    onSurfaceVariant = MutedInk,
    outline = CardBorder,
)

private val DarkColors = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFFB89BE2),
    onPrimary = EggplantPurpleDark,
    secondary = androidx.compose.ui.graphics.Color(0xFF91D49D),
    onSecondary = LeafGreenDark,
    background = DarkBackground,
    onBackground = androidx.compose.ui.graphics.Color(0xFFF5F1FA),
    surface = DarkSurface,
    onSurface = androidx.compose.ui.graphics.Color(0xFFF5F1FA),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF302B3B),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFD4CDDD),
)

@Composable
fun EggplantDetectorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
