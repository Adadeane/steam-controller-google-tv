@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.steamcontroller.googletv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme

val SteamTvTypography = Typography(
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        color = TextPrimary
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        color = TextPrimary
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        color = TextPrimary
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        color = TextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        color = TextSecondary
    )
)

private val SteamTvColorScheme = darkColorScheme(
    primary = SteamAccentBlue,
    onPrimary = TextPrimary,
    surface = SteamSurface,
    onSurface = TextPrimary,
    surfaceVariant = SteamSurfaceVariant,
    onSurfaceVariant = TextPrimary,
    background = SteamDarkBackground,
    onBackground = TextPrimary,
    error = SteamError
)

@Composable
fun SteamControllerTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SteamTvColorScheme,
        typography = SteamTvTypography,
        content = content
    )
}
