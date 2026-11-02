package com.example.securescan.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = BluePrimary,
    onPrimary = AppSurface,
    primaryContainer = AppSurfaceSoft,
    onPrimaryContainer = TextPrimary,

    secondary = BlueSecondary,
    onSecondary = AppSurface,
    secondaryContainer = AppSurfaceSoft,
    onSecondaryContainer = TextPrimary,

    tertiary = BlueAccent,
    onTertiary = AppSurface,

    background = AppBackground,
    onBackground = TextPrimary,

    surface = AppSurface,
    onSurface = TextPrimary,
    surfaceVariant = AppSurfaceSoft,
    onSurfaceVariant = TextSecondary,

    error = ErrorRed,
    onError = AppSurface,
    errorContainer = ColorRedContainer(),
    onErrorContainer = ErrorRed,

    outline = TextMuted
)

private val DarkColors = darkColorScheme(
    primary = DarkBluePrimary,
    onPrimary = DarkBackground,
    primaryContainer = DarkSurfaceSoft,
    onPrimaryContainer = DarkTextPrimary,

    secondary = DarkBlueSecondary,
    onSecondary = DarkBackground,
    secondaryContainer = DarkSurfaceSoft,
    onSecondaryContainer = DarkTextPrimary,

    tertiary = DarkBluePrimary,
    onTertiary = DarkBackground,

    background = DarkBackground,
    onBackground = DarkTextPrimary,

    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceSoft,
    onSurfaceVariant = DarkTextSecondary,

    error = ErrorRed,
    onError = DarkTextPrimary,
    errorContainer = DarkSurfaceSoft,
    onErrorContainer = ErrorRed,

    outline = DarkTextMuted
)

@Composable
fun SecureScanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}

private fun ColorRedContainer() = androidx.compose.ui.graphics.Color(0xFFFDECEC)
