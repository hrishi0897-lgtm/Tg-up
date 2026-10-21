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
    bg = Color(0xFF000000),
    surface = Color(0xFF0A0C12),
    surfaceHi = Color(0xFF121622),
    line = Color(0xFF1E2436),
    text = Color(0xFFF8FAFC),
    textDim = Color(0xFF94A3B8),
    textFaint = Color(0xFF566078),
    heroGlow = Color(0xFF0F1322),
    navBg = Color(0xF2000000),
    btnEdge = Color(0xFF090B12),
    searchBg = Color(0xFF080A10),
    bodyBg = Color(0xFF000000),
    violet = Color(0xFF7C6BFF),
    violetDim = Color(0xFF4A4180),
    teal = Color(0xFF20D6B5),
    amber = Color(0xFFF5A623),
    mint = Color(0xFF10B981),
    danger = Color(0xFFEF4444),
    isDark = true
)

val LightTeleVaultColors = TeleVaultColors(
    bg = Color(0xFFF7F8FC),
    surface = Color(0xFFFFFFFF),
    surfaceHi = Color(0xFFEEF1F8),
    line = Color(0xFFE2E6F0),
    text = Color(0xFF0B0D14),
    textDim = Color(0xFF52586E),
    textFaint = Color(0xFF8C93A8),
    heroGlow = Color(0xFFFFFFFF),
    navBg = Color(0xF2FFFFFF),
    btnEdge = Color(0xFFD5D9E8),
    searchBg = Color(0xFFEEF1F8),
    bodyBg = Color(0xFFEBEEF7),
    violet = Color(0xFF6B59F5),
    violetDim = Color(0xFF5646D9),
    teal = Color(0xFF0FB89A),
    amber = Color(0xFFE09112),
    mint = Color(0xFF0D9F6E),
    danger = Color(0xFFE53935),
    isDark = false
)

val LocalTeleVaultColors = staticCompositionLocalOf { DarkTeleVaultColors }

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


