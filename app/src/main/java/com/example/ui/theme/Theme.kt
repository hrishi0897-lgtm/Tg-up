package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val TeleVaultColorScheme = darkColorScheme(
    primary = AccentViolet,
    onPrimary = AppBackgroundOuter,
    primaryContainer = TelegramBlueContainer,
    onPrimaryContainer = TextPrimary,
    secondary = AccentTeal,
    onSecondary = AppBackgroundOuter,
    secondaryContainer = SurfaceCardElevated,
    onSecondaryContainer = TextPrimary,
    tertiary = StatusMint,
    onTertiary = AppBackgroundOuter,
    background = AppSurface,
    onBackground = TextPrimary,
    surface = SurfaceCard,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceCardElevated,
    onSurfaceVariant = TextDimmed,
    outline = BorderDivider,
    outlineVariant = BorderSubtle,
    error = StatusError,
    onError = AppBackgroundOuter,
    errorContainer = StatusErrorBg,
    onErrorContainer = TextPrimary
)

@Composable
fun TeleVaultTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TeleVaultColorScheme,
        typography = Typography,
        content = content
    )
}


