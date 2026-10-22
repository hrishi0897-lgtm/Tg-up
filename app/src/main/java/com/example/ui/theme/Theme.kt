package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

@Composable
fun TeleVaultTheme(
    isDark: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = if (isDark) DarkTeleVaultColors else LightTeleVaultColors

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = colors.violet,
            onPrimary = Color.White,
            primaryContainer = colors.surfaceHi,
            onPrimaryContainer = colors.text,
            secondary = colors.violet,
            onSecondary = Color.White,
            secondaryContainer = colors.surfaceHi,
            onSecondaryContainer = colors.text,
            tertiary = colors.violet,
            onTertiary = Color.White,
            background = colors.bg,
            onBackground = colors.text,
            surface = colors.surface,
            onSurface = colors.text,
            surfaceVariant = colors.surfaceHi,
            onSurfaceVariant = colors.textDim,
            outline = colors.line,
            outlineVariant = colors.btnEdge,
            error = StatusError,
            onError = Color.White,
            errorContainer = StatusErrorBg,
            onErrorContainer = colors.text
        )
    } else {
        lightColorScheme(
            primary = colors.violet,
            onPrimary = Color.White,
            primaryContainer = colors.surfaceHi,
            onPrimaryContainer = colors.text,
            secondary = colors.violet,
            onSecondary = Color.White,
            secondaryContainer = colors.surfaceHi,
            onSecondaryContainer = colors.text,
            tertiary = colors.violet,
            onTertiary = Color.White,
            background = colors.bg,
            onBackground = colors.text,
            surface = colors.surface,
            onSurface = colors.text,
            surfaceVariant = colors.surfaceHi,
            onSurfaceVariant = colors.textDim,
            outline = colors.line,
            outlineVariant = colors.btnEdge,
            error = StatusError,
            onError = Color.White,
            errorContainer = StatusErrorBg,
            onErrorContainer = colors.text
        )
    }

    val reduceMotion = rememberReduceMotion()
    val baseDensity = androidx.compose.ui.platform.LocalDensity.current
    val boostedDensity = androidx.compose.runtime.remember(baseDensity) {
        androidx.compose.ui.unit.Density(
            density = baseDensity.density,
            fontScale = baseDensity.fontScale * 1.18f
        )
    }
    CompositionLocalProvider(
        LocalTeleVaultColors provides colors,
        LocalReduceMotion provides reduceMotion,
        androidx.compose.ui.platform.LocalDensity provides boostedDensity
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}



