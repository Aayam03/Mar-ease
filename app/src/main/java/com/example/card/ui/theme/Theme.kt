package com.example.card.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = MarEasePrimary,
    secondary = MarEaseAccent,
    tertiary = MarEaseDark,
    background = MarEaseDark,
    surface = MarEaseDark,
    onPrimary = MarEaseWhite,
    onSecondary = MarEaseWhite,
    onBackground = MarEaseWhite,
    onSurface = MarEaseWhite
)

private val LightColorScheme = lightColorScheme(
    primary = MarEasePrimary,
    secondary = MarEaseAccent,
    tertiary = MarEaseDark,
    background = MarEaseDark, // Keeping it blue as requested
    surface = MarEasePrimary,
    onPrimary = MarEaseWhite,
    onSecondary = MarEaseWhite,
    onBackground = MarEaseWhite,
    onSurface = MarEaseWhite
)

@Composable
fun CardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
