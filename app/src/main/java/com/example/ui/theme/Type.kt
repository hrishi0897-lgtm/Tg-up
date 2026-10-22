package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ==========================================
// Three-Font System
// ==========================================

// 1. Serif display font (Instrument Serif or closest Android serif alternative)
val DisplaySerifFont = FontFamily.Serif

// 2. Sans-serif font (Inter or system sans-serif for body text & UI labels)
val BodySansFont = FontFamily.SansSerif

// 3. Monospace font (JetBrains Mono / Roboto Mono for numeric counters & stats)
val NumericMonoFont = FontFamily.Monospace

// Specific Signature Typography Styles
val WordmarkTextStyle = TextStyle(
    fontFamily = DisplaySerifFont,
    fontStyle = FontStyle.Italic,
    fontWeight = FontWeight.Normal,
    fontSize = 33.sp,
    lineHeight = 35.sp,
    color = Color.Unspecified
)

val EmptyHeadlineStyle = TextStyle(
    fontFamily = DisplaySerifFont,
    fontStyle = FontStyle.Normal,
    fontWeight = FontWeight.Normal,
    fontSize = 27.sp,
    lineHeight = 33.sp,
    color = Color.Unspecified
)

val MonoStatValueLarge = TextStyle(
    fontFamily = NumericMonoFont,
    fontWeight = FontWeight.SemiBold,
    fontSize = 24.sp,
    lineHeight = 27.sp,
    color = Color.Unspecified
)

val MonoStatValueMedium = TextStyle(
    fontFamily = NumericMonoFont,
    fontWeight = FontWeight.Medium,
    fontSize = 19.sp,
    lineHeight = 23.sp,
    color = Color.Unspecified
)

val MonoStatValueSmall = TextStyle(
    fontFamily = NumericMonoFont,
    fontWeight = FontWeight.Normal,
    fontSize = 17.5.sp,
    lineHeight = 21.sp,
    color = Color.Unspecified
)

// Standard M3 Typography paired to the three-font system
val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = DisplaySerifFont,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.Normal,
        fontSize = 41.sp,
        lineHeight = 45.sp,
        color = Color.Unspecified
    ),
    displayMedium = TextStyle(
        fontFamily = DisplaySerifFont,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.Normal,
        fontSize = 33.sp,
        lineHeight = 37.sp,
        color = Color.Unspecified
    ),
    headlineLarge = TextStyle(
        fontFamily = DisplaySerifFont,
        fontWeight = FontWeight.Normal,
        fontSize = 27.sp,
        lineHeight = 33.sp,
        color = Color.Unspecified
    ),
    headlineMedium = TextStyle(
        fontFamily = BodySansFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 23.sp,
        lineHeight = 29.sp,
        color = Color.Unspecified
    ),
    titleLarge = TextStyle(
        fontFamily = BodySansFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 27.sp,
        color = Color.Unspecified
    ),
    titleMedium = TextStyle(
        fontFamily = BodySansFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 23.sp,
        color = Color.Unspecified
    ),
    bodyLarge = TextStyle(
        fontFamily = BodySansFont,
        fontWeight = FontWeight.Normal,
        fontSize = 19.sp,
        lineHeight = 25.sp,
        color = Color.Unspecified
    ),
    bodyMedium = TextStyle(
        fontFamily = BodySansFont,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 23.sp,
        color = Color.Unspecified
    ),
    bodySmall = TextStyle(
        fontFamily = BodySansFont,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        color = Color.Unspecified
    ),
    labelLarge = TextStyle(
        fontFamily = BodySansFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 21.sp,
        color = Color.Unspecified
    ),
    labelSmall = TextStyle(
        fontFamily = BodySansFont,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 19.sp,
        color = Color.Unspecified
    )
)

