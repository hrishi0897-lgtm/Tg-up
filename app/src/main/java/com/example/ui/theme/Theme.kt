package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val OledColorScheme = darkColorScheme(
  primary = TelegramBlue,
  onPrimary = OledBlack,
  primaryContainer = TelegramBlueContainer,
  onPrimaryContainer = OnTelegramBlueContainer,
  secondary = TelegramBlue,
  onSecondary = OledBlack,
  secondaryContainer = OledSurfaceVariant,
  onSecondaryContainer = TextPrimary,
  tertiary = StatusSuccess,
  onTertiary = OledBlack,
  background = OledBlack,
  onBackground = TextPrimary,
  surface = OledBlack,
  onSurface = TextPrimary,
  surfaceVariant = OledSurfaceVariant,
  onSurfaceVariant = TextSecondary,
  outline = OledBorder,
  outlineVariant = OledSurfaceVariant,
  error = StatusError,
  onError = OledBlack,
  errorContainer = StatusErrorContainer,
  onErrorContainer = TextPrimary
)

@Composable
fun TeleVaultTheme(
  content: @Composable () -> Unit
) {
  MaterialTheme(
    colorScheme = OledColorScheme,
    typography = Typography,
    content = content
  )
}

