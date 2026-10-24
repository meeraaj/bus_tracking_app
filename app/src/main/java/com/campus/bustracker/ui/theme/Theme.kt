package com.campus.bustracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = AmberPrimary,
    onPrimary = Navy950,
    primaryContainer = AmberDark,
    onPrimaryContainer = TextPrimary,
    secondary = TealRoute,
    onSecondary = Navy950,
    secondaryContainer = TealDark,
    onSecondaryContainer = TextPrimary,
    background = Navy950,
    onBackground = TextPrimary,
    surface = CardBackground,
    onSurface = TextPrimary,
    surfaceVariant = Navy800,
    onSurfaceVariant = TextSecondary,
    outline = CardBorder,
    error = ErrorRed,
    onError = TextPrimary
)

@Composable
fun BusTrackerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
