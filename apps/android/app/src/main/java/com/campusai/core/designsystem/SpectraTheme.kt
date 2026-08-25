package com.campusai.core.designsystem

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.campusai.R
import com.campusai.core.model.ThemeMode

object SpectraColors {
    val Paper = Color(0xFFF8F9FD)
    val Ink = Color(0xFF162033)
    val Night = Color(0xFF0D1422)
    val Cyan = Color(0xFF16C5DC)
    val Violet = Color(0xFF7562F5)
    val Warm = Color(0xFFFF8B43)
    val Rose = Color(0xFFFF79B9)
    val Silver = Color(0xFFDBE3ED)
    val Success = Color(0xFF159763)
    val Warning = Color(0xFFFFB020)
    val Error = Color(0xFFD33F65)
    val Focus = Color(0xFF5A7DFF)
}

val Tomorrow = FontFamily(
    Font(R.font.tomorrow_regular, FontWeight.Normal),
    Font(R.font.tomorrow_semibold, FontWeight.SemiBold),
)

val Plex = FontFamily(
    Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_medium, FontWeight.Medium),
)

private val LightColors = lightColorScheme(
    primary = SpectraColors.Focus,
    onPrimary = Color.White,
    secondary = SpectraColors.Violet,
    tertiary = SpectraColors.Cyan,
    background = SpectraColors.Paper,
    onBackground = SpectraColors.Ink,
    surface = Color.White,
    onSurface = SpectraColors.Ink,
    outline = SpectraColors.Silver,
    error = SpectraColors.Error,
)

private val DarkColors = darkColorScheme(
    primary = Color.White,
    onPrimary = SpectraColors.Night,
    secondary = Color(0xFFAFA4FF),
    tertiary = Color(0xFF72E4EE),
    background = SpectraColors.Night,
    onBackground = Color(0xFFF3F6FC),
    surface = Color(0xFF151E2E),
    onSurface = Color(0xFFF3F6FC),
    outline = Color(0xFF42506A),
    error = Color(0xFFFF7D9D),
)

private val SpectraTypography = androidx.compose.material3.Typography(
    displayLarge = TextStyle(fontFamily = Plex, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontFamily = Plex, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
    headlineMedium = TextStyle(fontFamily = Plex, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontFamily = Plex, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)

@Composable
fun CampusTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors: ColorScheme = if (dark) DarkColors else LightColors
    val view = LocalView.current
    DisposableEffect(dark) {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        }
        onDispose { }
    }
    CompositionLocalProvider(LocalSpectraTokens provides DefaultSpectraTokens) {
        MaterialTheme(colorScheme = colors, typography = SpectraTypography, content = content)
    }
}
