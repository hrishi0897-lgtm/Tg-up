package com.example.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor

// ==========================================
// TeleVault Single-Color Theme System (Signature Violet)
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
    val violet: Color = Color(0xFF7C6BFF),
    val violetDim: Color,
    val teal: Color = Color(0xFF7C6BFF),
    val amber: Color = Color(0xFF7C6BFF),
    val mint: Color = Color(0xFF7C6BFF),
    val danger: Color = Color(0xFF7C6BFF),
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
    teal = Color(0xFF7C6BFF),
    amber = Color(0xFF7C6BFF),
    mint = Color(0xFF7C6BFF),
    danger = Color(0xFF7C6BFF),
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
    violetDim = Color(0xFF4A3DC7),
    teal = Color(0xFF6B59F5),
    amber = Color(0xFF6B59F5),
    mint = Color(0xFF6B59F5),
    danger = Color(0xFF6B59F5),
    isDark = false
)

val LocalTeleVaultColors = staticCompositionLocalOf { DarkTeleVaultColors }

// Category Storage Tonal Violet Shades (100%, 72%, 48% opacity of Signature Violet)
val CategoryViolet = Color(0xFF7C6BFF)
val CategoryTeal = Color(0xB87C6BFF)
val CategoryAmber = Color(0x7A7C6BFF)

// Surfaces & Backgrounds
val AppBackgroundOuter = Color(0xFF05060A)
val AppSurface = Color(0xFF0B0D14)
val SurfaceCard = Color(0xFF141826)
val SurfaceCardElevated = Color(0xFF1B2033)
val BorderDivider = Color(0xFF262C42)
val BorderSubtle = Color(0x66262C42)

// Accents (Unified Signature Violet)
val AccentViolet = Color(0xFF7C6BFF)
val AccentVioletDim = Color(0xFF4A4180)
val AccentVioletDark = Color(0xFF4A3FCB)
val AccentVioletDeep = Color(0xFF5B4FE0)
val AccentTeal = AccentViolet
val AccentTealDark = AccentVioletDark

// Text Hierarchy
val TextPrimary = Color(0xFFF2F3F8)
val TextDimmed = Color(0xFF8890A8)
val TextFaint = Color(0xFF545C77)

// Status & Indicators (Unified Signature Violet shades)
val StatusMint = AccentViolet
val StatusMintBg = Color(0x1F7C6BFF)
val StatusMintBorder = Color(0x4D7C6BFF)
val StatusError = AccentViolet
val StatusErrorBg = Color(0x227C6BFF)
val StatusErrorBorder = Color(0x557C6BFF)
val StatusWarning = AccentViolet

// Solid Flat Brushes (No multi-color gradients)
val BrandMarkGradient: Brush = SolidColor(AccentViolet)
val StorageRingGradient: Brush = SolidColor(AccentViolet)
val TealFabGradient: Brush = SolidColor(AccentViolet)
val VioletButtonGradient: Brush = SolidColor(AccentViolet)
val HeroCardRadialGradient: Brush = SolidColor(SurfaceCard)

// Legacy compatibility aliases mapped to unified tokens
val OledBlack = AppSurface
val OledSurface = SurfaceCard
val OledSurfaceVariant = SurfaceCardElevated
val OledCard = SurfaceCard
val OledCardElevated = SurfaceCardElevated
val OledCardGlass = SurfaceCard
val OledBorder = BorderDivider
val OledBorderSubtle = BorderDivider
val OledBorderGlow = Color(0x337C6BFF)

val TelegramBlue = AccentViolet
val TelegramBlueDark = AccentVioletDim
val TelegramBlueContainer = Color(0xFF1C1B33)
val OnTelegramBlueContainer = TextPrimary

val TextSecondary = TextDimmed
val TextTertiary = TextFaint

val FabGradient: Brush = SolidColor(AccentViolet)
val StorageGlowGradient: Brush = SolidColor(Color(0x147C6BFF))

val StatusSuccess = StatusMint
val StatusSuccessContainer = StatusMintBg
val StatusErrorContainer = StatusErrorBg
val StatusWarningContainer = Color(0x207C6BFF)

// File Type Colors (Unified to Signature Violet)
val FileColorImage = AccentViolet
val FileColorVideo = AccentViolet
val FileColorAudio = AccentViolet
val FileColorDoc = AccentViolet
val FileColorArchive = AccentViolet
val FileColorFolder = AccentViolet
val FileColorGeneric = AccentViolet
