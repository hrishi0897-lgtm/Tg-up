package com.example.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ==========================================
// TeleVault Theme Color System (Light & OLED Dark)
// ==========================================

data class TeleVaultColors(
    val bg: Color,
    val surface: Color,
    val surfaceHi: Color,
    val line: Color,
    val text: Color,
    val textDim: Color,
    val textFaint: Color,
    val heroGlow: Color,
    val navBg: Color,
    val btnEdge: Color,
    val searchBg: Color,
    val bodyBg: Color,
    val violet: Color = Color(0xFF8C7CFF),
    val violetDim: Color,
    val teal: Color = Color(0xFF35E0C2),
    val amber: Color = Color(0xFFF2B84B),
    val mint: Color = Color(0xFF4ADE9E),
    val danger: Color = Color(0xFFFF5252),
    val isDark: Boolean
)

val DarkTeleVaultColors = TeleVaultColors(
    bg = Color(0xFF0B0D14),
    surface = Color(0xFF141826),
    surfaceHi = Color(0xFF1B2033),
    line = Color(0xFF262C42),
    text = Color(0xFFF2F3F8),
    textDim = Color(0xFF8890A8),
    textFaint = Color(0xFF545C77),
    heroGlow = Color(0xFF191F35),
    navBg = Color(0xEB0B0D14),
    btnEdge = Color(0xFF0C0F1A),
    searchBg = Color(0xFF0E1120),
    bodyBg = Color(0xFF05060A),
    violetDim = Color(0xFF4A4180),
    isDark = true
)

val LightTeleVaultColors = TeleVaultColors(
    bg = Color(0xFFF3F4FA),
    surface = Color(0xFFFFFFFF),
    surfaceHi = Color(0xFFECEEF6),
    line = Color(0xFFE1E4EE),
    text = Color(0xFF14161F),
    textDim = Color(0xFF5B6178),
    textFaint = Color(0xFF98A0B8),
    heroGlow = Color(0xFFFFFFFF),
    navBg = Color(0xD9FFFFFF),
    btnEdge = Color(0xFFCBCFDE),
    searchBg = Color(0xFFECEEF6),
    bodyBg = Color(0xFFE7E9F2),
    violetDim = Color(0xFF7364DE),
    isDark = false
)

val LocalTeleVaultColors = staticCompositionLocalOf { LightTeleVaultColors }

// Category Storage Donut Colors
val CategoryViolet = Color(0xFF8C7CFF)
val CategoryTeal = Color(0xFF35E0C2)
val CategoryAmber = Color(0xFFF2B84B)

// Surfaces & Backgrounds (Default dark constants for backward compatibility)
val AppBackgroundOuter = Color(0xFF05060A)
val AppSurface = Color(0xFF0B0D14)
val SurfaceCard = Color(0xFF141826)
val SurfaceCardElevated = Color(0xFF1B2033)
val BorderDivider = Color(0xFF262C42)
val BorderSubtle = Color(0x66262C42)

// Accents
val AccentViolet = Color(0xFF8C7CFF)
val AccentVioletDim = Color(0xFF4A4180)
val AccentVioletDark = Color(0xFF4A3FCB)
val AccentVioletDeep = Color(0xFF5B4FE0)
val AccentTeal = Color(0xFF35E0C2)
val AccentTealDark = Color(0xFF21A88E)

// Text Hierarchy
val TextPrimary = Color(0xFFF2F3F8)
val TextDimmed = Color(0xFF8890A8)
val TextFaint = Color(0xFF545C77)

// Status & Indicators
val StatusMint = Color(0xFF4ADE9E)
val StatusMintBg = Color(0x1A4ADE9E)
val StatusMintBorder = Color(0x404ADE9E)
val StatusError = Color(0xFFFF5252)
val StatusErrorBg = Color(0x22FF5252)
val StatusErrorBorder = Color(0x55FF5252)
val StatusWarning = Color(0xFFFFD54F)

// Signature Gradients
val BrandMarkGradient = Brush.linearGradient(
    colors = listOf(AccentViolet, AccentVioletDark)
)
val StorageRingGradient = Brush.linearGradient(
    colors = listOf(AccentViolet, AccentTeal)
)
val TealFabGradient = Brush.linearGradient(
    colors = listOf(AccentTeal, AccentTealDark)
)
val VioletButtonGradient = Brush.linearGradient(
    colors = listOf(AccentViolet, AccentVioletDeep)
)
val HeroCardRadialGradient = Brush.radialGradient(
    colors = listOf(Color(0xFF191F35), SurfaceCard),
    radius = 1200f
)

// Legacy compatibility aliases mapped to new tokens
val OledBlack = AppSurface
val OledSurface = SurfaceCard
val OledSurfaceVariant = SurfaceCardElevated
val OledCard = SurfaceCard
val OledCardElevated = SurfaceCardElevated
val OledCardGlass = SurfaceCard
val OledBorder = BorderDivider
val OledBorderSubtle = BorderDivider
val OledBorderGlow = Color(0x338C7CFF)

val TelegramBlue = AccentViolet
val TelegramBlueDark = AccentVioletDim
val TelegramBlueContainer = Color(0xFF1C1B33)
val OnTelegramBlueContainer = TextPrimary

val TextSecondary = TextDimmed
val TextTertiary = TextFaint

val FabGradient = TealFabGradient
val StorageGlowGradient = Brush.verticalGradient(
    colors = listOf(Color(0x208C7CFF), Color(0x00000000))
)

val StatusSuccess = StatusMint
val StatusSuccessContainer = StatusMintBg
val StatusErrorContainer = StatusErrorBg
val StatusWarningContainer = Color(0x20FFD54F)

// File Type Colors (Harmonized with new dark palette)
val FileColorImage = Color(0xFF00B0FF)
val FileColorVideo = Color(0xFFAB47BC)
val FileColorAudio = Color(0xFFFF9100)
val FileColorDoc = Color(0xFF35E0C2)
val FileColorArchive = Color(0xFFFF5252)
val FileColorFolder = Color(0xFFE2B714)
val FileColorGeneric = Color(0xFF78909C)


