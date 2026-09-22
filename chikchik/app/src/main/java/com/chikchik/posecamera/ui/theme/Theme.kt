package com.chikchik.posecamera.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** The app's single accent color. Used sparingly for active/highlighted states. */
val ChikChikAccent = Color(0xFFFFC94A)

private val DarkColorScheme = darkColorScheme(
    primary = ChikChikAccent,
    onPrimary = Color.Black,
    secondary = ChikChikAccent,
    onSecondary = Color.Black,
    background = Color(0xFF0A0A0C),
    onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF141416),
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF1E1E21),
    onSurfaceVariant = Color(0xFFCFCFCF),
)

/**
 * ChikChik is a dark, minimal camera app by design: a single fixed dark
 * palette is used everywhere (no light theme, no per-device dynamic
 * colors) so the interface stays calm and consistent across devices.
 */
@Composable
fun ChikChikTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
