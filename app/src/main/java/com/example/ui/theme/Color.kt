package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ==========================================
// TeleVault Redesign Color System
// ==========================================

// Surfaces & Backgrounds
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


