package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = GeminiPrimary,
    secondary = GeminiSecondary,
    tertiary = GeminiTertiary,
    background = GeminiDarkBg,
    surface = GeminiDarkSurface,
    onPrimary = GeminiLightSurface,
    onSecondary = GeminiLightSurface,
    onBackground = GeminiDarkTextPrimary,
    onSurface = GeminiDarkTextPrimary,
    primaryContainer = GeminiSecondary.copy(alpha = 0.2f),
    onPrimaryContainer = GeminiDarkTextPrimary
)

private val LightColorScheme = lightColorScheme(
    primary = GeminiPrimary,
    secondary = GeminiSecondary,
    tertiary = GeminiTertiary,
    background = GeminiLightBg,
    surface = GeminiLightSurface,
    onPrimary = GeminiLightSurface,
    onSecondary = GeminiLightSurface,
    onBackground = GeminiLightTextPrimary,
    onSurface = GeminiLightTextPrimary,
    primaryContainer = GeminiPrimary.copy(alpha = 0.15f),
    onPrimaryContainer = GeminiLightTextPrimary
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Set to false to force our custom green/blue branding
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
