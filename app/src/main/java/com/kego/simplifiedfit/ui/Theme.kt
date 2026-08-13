package com.kego.simplifiedfit.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object FitColors {
    val Black = Color(0xFF000000)
    val Surface = Color(0xFF090A0B)
    val Rule = Color(0xFF292B2D)
    val Track = Color(0xFF242628)
    val White = Color(0xFFF5F5F2)
    val Muted = Color(0xFF969A9D)
    val Green = Color(0xFFB7F34A)
    val Violet = Color(0xFFA88BFF)
    val StageAwake = Color(0xFF6448D6)
    val StageDeep = Color(0xFF3458C8)
    val StageRem = Color(0xFF8ED8D8)
    val Cyan = Color(0xFF42D9F5)
    val Coral = Color(0xFFFF766F)
}

object FitType {
    val Eyebrow = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        letterSpacing = 1.8.sp,
    )
    val Display = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 52.sp,
        letterSpacing = (-2.5).sp,
    )
    val Metric = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 27.sp,
        letterSpacing = (-1).sp,
    )
    val Body = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    )
}

@Composable
fun SimplifiedFitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = FitColors.Black,
            surface = FitColors.Surface,
            primary = FitColors.Green,
            onBackground = FitColors.White,
            onSurface = FitColors.White,
        ),
        typography = MaterialTheme.typography.copy(
            bodyMedium = FitType.Body,
            titleLarge = FitType.Metric,
        ),
        content = content,
    )
}
