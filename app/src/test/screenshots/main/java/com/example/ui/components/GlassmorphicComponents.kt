package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*

/**
 * 极光背景 (Aurora Background) - 模仿 Gemini 的动态未来感有机光晕背景
 */
@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = isSystemInDarkTheme()
    
    // Smooth infinite transition for rotating/shifting gradients
    val infiniteTransition = rememberInfiniteTransition(label = "aurora_shift")
    val shiftAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shift"
    )

    // Palette: Campus Green (#22C55E), Blue (#4285F4), Purple (#8B5CF6), Orange (#FBBC05)
    val colorPrimary = GeminiPrimary.copy(alpha = if (isDark) 0.15f else 0.12f)
    val colorSecondary = GeminiSecondary.copy(alpha = if (isDark) 0.12f else 0.08f)
    val colorTertiary = GeminiTertiary.copy(alpha = if (isDark) 0.15f else 0.10f)
    val colorVibrant = GeminiOrange.copy(alpha = if (isDark) 0.08f else 0.06f)

    val bgColor = if (isDark) GeminiDarkBg else GeminiLightBg

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        // Overlay organic aurora gradients
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(colorPrimary, Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(100f, 200f),
                        radius = 800f
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(colorTertiary, Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(900f, 600f),
                        radius = 1000f
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(colorSecondary, Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(300f, 1200f),
                        radius = 900f
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(colorVibrant, Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(1000f, 1500f),
                        radius = 800f
                    )
                )
        )

        // Backdrop noise / content container
        Box(
            modifier = Modifier.fillMaxSize(),
            content = content
        )
    }
}

/**
 * 玻璃拟态卡片 (Glassmorphism Card)
 * 采用高对比度半透明背景 + 柔和彩色双边框 + 微弱阴影，提供极强的悬浮轻盈质感
 */
@Composable
fun GlassmorphicCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    borderWidth: Dp = 1.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = isSystemInDarkTheme()
    
    val bgTransparentColor = if (isDark) {
        // OpenAI Premium slate space transparent tint
        Color(0x7F161F30)
    } else {
        // Warm white translucent tint
        Color(0x99FFFFFF)
    }

    val borderColor = if (isDark) {
        Color(0x22FFFFFF)
    } else {
        Color(0x110F172A)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(bgTransparentColor)
            .border(
                width = borderWidth,
                brush = Brush.linearGradient(
                    colors = listOf(
                        borderColor,
                        borderColor.copy(alpha = 0.02f)
                    )
                ),
                shape = RoundedCornerShape(cornerRadius)
            )
            .padding(1.dp) // Offset slightly for border glow effect
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            content = content
        )
    }
}

/**
 * Typewriter 打字机动态流式文本 (模拟 ChatGPT / OpenAI 交互感)
 */
@Composable
fun StreamingTypeText(
    text: String,
    modifier: Modifier = Modifier,
    delayMillis: Long = 15L,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    onComplete: (() -> Unit)? = null
) {
    var currentIndex by remember(text) { mutableStateOf(0) }
    
    LaunchedEffect(text) {
        currentIndex = 0
        if (text.isNotEmpty()) {
            while (currentIndex < text.length) {
                kotlinx.coroutines.delay(delayMillis)
                currentIndex++
            }
            onComplete?.invoke()
        }
    }

    val visibleText = remember(text, currentIndex) {
        if (text.isEmpty()) "" else text.substring(0, currentIndex.coerceAtMost(text.length))
    }

    Text(
        text = visibleText,
        modifier = modifier,
        style = style,
        color = color
    )
}
