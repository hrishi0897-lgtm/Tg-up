package com.example.ui.theme

import androidx.compose.material3.Typography
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
    fontSize = 28.sp,
    lineHeight = 30.sp,
    color = TextPrimary
)

val EmptyHeadlineStyle = TextStyle(
    fontFamily = DisplaySerifFont,
    fontStyle = FontStyle.Normal,
    fontWeight = FontWeight.Normal,
    fontSize = 22.sp,
    lineHeight = 28.sp,
    color = TextPrimary
)

val MonoStatValueLarge = TextStyle(
    fontFamily = NumericMonoFont,
    fontWeight = FontWeight.SemiBold,
    fontSize = 19.sp,
    lineHeight = 22.sp,
    color = TextPrimary
)

val MonoStatValueMedium = TextStyle(
    fontFamily = NumericMonoFont,
    fontWeight = FontWeight.Medium,
    fontSize = 14.sp,
    lineHeight = 18.sp,
    color = TextPrimary
)

val MonoStatValueSmall = TextStyle(
    fontFamily = NumericMonoFont,
    fontWeight = FontWeight.Normal,
    fontSize = 12.5.sp,
    lineHeight = 16.sp,
    color = TextFaint
)

// Standard M3 Typography paired to the three-font system
val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = DisplaySerifFont,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 40.sp,
        color = TextPrimary
    ),
    displayMedium = TextStyle(
        fontFamily = DisplaySerifFont,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        color = TextPrimary
    ),
    headlineLarge = TextStyle(
        fontFamily = DisplaySerifFont,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        color = TextPrimary
    ),
    headlineMedium = TextStyle(
        fontFamily = BodySansFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        color = TextPrimary
    ),
    titleLarge = TextStyle(
        fontFamily = BodySansFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        color = TextPrimary
    ),
    titleMedium = TextStyle(
        fontFamily = BodySansFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        color = TextPrimary
    ),
    bodyLarge = TextStyle(
        fontFamily = BodySansFont,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = TextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = BodySansFont,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = TextDimmed
    ),
    bodySmall = TextStyle(
        fontFamily = BodySansFont,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        color = TextFaint
    ),
    labelLarge = TextStyle(
        fontFamily = BodySansFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        color = TextPrimary
    ),
    labelSmall = TextStyle(
        fontFamily = BodySansFont,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        color = TextFaint
    )
)

