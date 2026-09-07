package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Pure OLED Black Base & Layered Dark Surfaces
val OledBlack = Color(0xFF000000)
val OledSurface = Color(0xFF07090D)
val OledSurfaceVariant = Color(0xFF0E131A)
val OledCard = Color(0xFF141922)
val OledCardElevated = Color(0xFF1B222E)
val OledCardGlass = Color(0xFF121720)
val OledBorder = Color(0xFF1F2734)
val OledBorderSubtle = Color(0xFF161C26)
val OledBorderGlow = Color(0x332AABEE)

// Purposeful Brand Accent: Telegram Cyan-Blue (Used with restraint)
val TelegramBlue = Color(0xFF2AABEE)
val TelegramBlueDark = Color(0xFF178ECB)
val TelegramBlueContainer = Color(0xFF0A202E)
val OnTelegramBlueContainer = Color(0xFFBCE6FF)

// Vibrant FAB & Accent Gradient
val FabGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF38B8F6), Color(0xFF1592D6))
)
val StorageGlowGradient = Brush.verticalGradient(
    colors = listOf(Color(0x182AABEE), Color(0x00000000))
)

// Typography & Hierarchy Grays (OLED contrast calibrated)
val TextPrimary = Color(0xFFF6F8FA)
val TextSecondary = Color(0xFF8B98A7)
val TextTertiary = Color(0xFF4E5B6A)

// Distinct File-Type Accents (Breaks visual monotony)
val FileColorImage = Color(0xFF00B0FF)      // Electric Sky
val FileColorVideo = Color(0xFFAB47BC)      // Deep Orchid
val FileColorAudio = Color(0xFFFF9100)      // Amber Orange
val FileColorDoc = Color(0xFF00E676)        // Emerald Green
val FileColorArchive = Color(0xFFFF5252)    // Coral Red
val FileColorFolder = Color(0xFFE2B714)     // Warm Gold
val FileColorGeneric = Color(0xFF78909C)    // Slate Gray

// Status Indicators
val StatusSuccess = Color(0xFF00E676)
val StatusSuccessContainer = Color(0xFF00381B)
val StatusError = Color(0xFFFF5252)
val StatusErrorContainer = Color(0xFF3E1212)
val StatusWarning = Color(0xFFFFD54F)
val StatusWarningContainer = Color(0xFF382E00)

