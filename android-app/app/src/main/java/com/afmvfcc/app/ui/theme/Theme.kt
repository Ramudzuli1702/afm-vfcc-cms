package com.afmvfcc.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// AFM VFCC brand colours
val Navy       = Color(0xFF1B3A6B)
val NavyLight  = Color(0xFF2A5298)
val Gold       = Color(0xFFC8961E)
val GoldLight  = Color(0xFFF0C040)
val LightBlue  = Color(0xFFEBF2FF)
val White      = Color(0xFFFFFFFF)
val Background = Color(0xFFF5F6FA)
val Surface    = Color(0xFFFFFFFF)
val OnNavy     = Color(0xFFFFFFFF)
val TextDark   = Color(0xFF1A1A2E)
val TextGrey   = Color(0xFF5A6275)
val TextMuted  = Color(0xFF9099AA)
val DangerRed  = Color(0xFFD94040)
val SuccessGreen = Color(0xFF2D9E4E)

private val AfmColorScheme = lightColorScheme(
    primary        = Navy,
    onPrimary      = White,
    primaryContainer   = LightBlue,
    onPrimaryContainer = Navy,
    secondary      = Gold,
    onSecondary    = White,
    background     = Background,
    onBackground   = TextDark,
    surface        = Surface,
    onSurface      = TextDark,
    surfaceVariant = LightBlue,
    outline        = Color(0xFFDDE1EA)
)

@Composable
fun AfmTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AfmColorScheme,
        typography  = Typography(),
        content     = content
    )
}
