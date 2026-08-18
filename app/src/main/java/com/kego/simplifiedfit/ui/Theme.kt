package com.kego.simplifiedfit.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private data class FitColorPalette(
    val background: Color,
    val surface: Color,
    val rule: Color,
    val track: Color,
    val content: Color,
    val muted: Color,
    val green: Color,
    val violet: Color,
    val stageAwake: Color,
    val stageDeep: Color,
    val stageRem: Color,
    val cyan: Color,
    val coral: Color,
)

private val DarkFitColors = FitColorPalette(
    background = Color(0xFF080D0F),
    surface = Color(0xFF2A3032),
    rule = Color(0xFF424A4D),
    track = Color(0xFF343C3F),
    content = Color(0xFFF7F8F7),
    muted = Color(0xFF969FA2),
    green = Color(0xFF00E6A7),
    violet = Color(0xFF87A9FF),
    stageAwake = Color(0xFF6448D6),
    stageDeep = Color(0xFF3458C8),
    stageRem = Color(0xFF8ED8D8),
    cyan = Color(0xFF21B9EC),
    coral = Color(0xFFFF5B63),
)

private val LightFitColors = DarkFitColors.copy(
    background = Color(0xFFF7F7F2),
    surface = Color(0xFFEDEDE7),
    rule = Color(0xFFD6D7D1),
    track = Color(0xFFE0E1DB),
    content = Color(0xFF151718),
    muted = Color(0xFF666B6D),
)

private val LocalFitColors = staticCompositionLocalOf { DarkFitColors }

object FitColors {
    val Black: Color @Composable get() = LocalFitColors.current.background
    val Surface: Color @Composable get() = LocalFitColors.current.surface
    val Rule: Color @Composable get() = LocalFitColors.current.rule
    val Track: Color @Composable get() = LocalFitColors.current.track
    val White: Color @Composable get() = LocalFitColors.current.content
    val Muted: Color @Composable get() = LocalFitColors.current.muted
    val Green: Color @Composable get() = LocalFitColors.current.green
    val Violet: Color @Composable get() = LocalFitColors.current.violet
    val StageAwake: Color @Composable get() = LocalFitColors.current.stageAwake
    val StageDeep: Color @Composable get() = LocalFitColors.current.stageDeep
    val StageRem: Color @Composable get() = LocalFitColors.current.stageRem
    val Cyan: Color @Composable get() = LocalFitColors.current.cyan
    val Coral: Color @Composable get() = LocalFitColors.current.coral
}

object FitType {
    val Eyebrow = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 1.5.sp,
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
fun SimplifiedFitTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkFitColors else LightFitColors
    CompositionLocalProvider(LocalFitColors provides colors) {
        MaterialTheme(
            colorScheme = if (darkTheme) {
                darkColorScheme(
                    background = colors.background,
                    surface = colors.surface,
                    primary = colors.green,
                    onBackground = colors.content,
                    onSurface = colors.content,
                )
            } else {
                lightColorScheme(
                    background = colors.background,
                    surface = colors.surface,
                    primary = colors.green,
                    onPrimary = colors.content,
                    onBackground = colors.content,
                    onSurface = colors.content,
                )
            },
            typography = MaterialTheme.typography.copy(
                bodyMedium = FitType.Body,
                titleLarge = FitType.Metric,
            ),
            content = content,
        )
    }
}
