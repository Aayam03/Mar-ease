package com.example.card.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.card.Difficulty

private fun getDarkColorScheme(difficulty: Difficulty): ColorScheme {
    val bgColor = when (difficulty) {
        Difficulty.EASY -> MarEaseEasy
        Difficulty.MEDIUM -> MarEaseDark
        Difficulty.HARD -> MarEaseHard
    }
    return darkColorScheme(
        primary = MarEasePrimary,
        secondary = MarEaseAccent,
        tertiary = bgColor,
        background = bgColor,
        surface = bgColor,
        onPrimary = MarEaseWhite,
        onSecondary = MarEaseWhite,
        onBackground = MarEaseWhite,
        onSurface = MarEaseWhite
    )
}

private fun getLightColorScheme(difficulty: Difficulty): ColorScheme {
    val bgColor = when (difficulty) {
        Difficulty.EASY -> MarEaseEasy
        Difficulty.MEDIUM -> MarEaseDark
        Difficulty.HARD -> MarEaseHard
    }
    return lightColorScheme(
        primary = MarEasePrimary,
        secondary = MarEaseAccent,
        tertiary = bgColor,
        background = bgColor,
        surface = MarEasePrimary,
        onPrimary = MarEaseWhite,
        onSecondary = MarEaseWhite,
        onBackground = MarEaseWhite,
        onSurface = MarEaseWhite
    )
}

@Composable
fun CardTheme(
    difficulty: Difficulty = Difficulty.MEDIUM,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) getDarkColorScheme(difficulty) else getLightColorScheme(difficulty)
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
