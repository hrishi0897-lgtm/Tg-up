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
            onPrimary = colors.bg,
            primaryContainer = colors.surfaceHi,
            onPrimaryContainer = colors.text,
            secondary = colors.teal,
            onSecondary = colors.bg,
            secondaryContainer = colors.surfaceHi,
            onSecondaryContainer = colors.text,
            tertiary = colors.amber,
            onTertiary = colors.bg,
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
            secondary = colors.teal,
            onSecondary = Color.Black,
            secondaryContainer = colors.surfaceHi,
            onSecondaryContainer = colors.text,
            tertiary = colors.amber,
            onTertiary = Color.Black,
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
    CompositionLocalProvider(
        LocalTeleVaultColors provides colors,
        LocalReduceMotion provides reduceMotion
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}



