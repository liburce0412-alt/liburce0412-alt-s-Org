package com.campusai.core.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.campusai.core.model.MotionMode
import com.campusai.core.model.RenderQuality
import com.campusai.core.model.SpectraEnvironment
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun SpectraBackdrop(
    environment: SpectraEnvironment,
    quality: RenderQuality,
    motion: MotionMode,
    modifier: Modifier = Modifier,
) {
    if (motion == MotionMode.OFF) {
        Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    var surface by remember { mutableStateOf<SpectraSurfaceView?>(null) }
    AndroidView(
        factory = { context -> SpectraSurfaceView(context).also { surface = it } },
        update = { it.configure(environment, quality) },
        modifier = modifier.fillMaxSize(),
    )
    DisposableEffect(lifecycleOwner, surface) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> surface?.onResume()
                Lifecycle.Event.ON_PAUSE -> surface?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); surface?.onPause() }
    }
    LaunchedEffect(surface, quality) {
        val frameDelay = if (quality == RenderQuality.LOW) 50L else 33L
        while (true) { surface?.requestRender(); delay(frameDelay) }
    }
}

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    radius: Int = 16,
    emphasized: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius.dp)
    val dark = MaterialTheme.colorScheme.background == SpectraColors.Night
    val fill = if (dark) Color.White.copy(alpha = if (emphasized) .16f else .11f)
    else Color.White.copy(alpha = if (emphasized) .64f else .48f)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .99f else 1f, tween(120), label = "glass-press")
    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(fill)
            .border(
                BorderStroke(
                    1.dp,
                    Brush.linearGradient(listOf(Color.White.copy(.9f), SpectraColors.Silver.copy(.62f), SpectraColors.Violet.copy(.28f))),
                ),
                shape,
            )
            .then(if (onClick != null) Modifier.clickable(source, null) { onClick() } else Modifier),
        content = content,
    )
}

@Composable
fun SpectraPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .98f else 1f, tween(110), label = "primary-press")
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = source,
        modifier = modifier.scale(scale).defaultMinSize(minHeight = 52.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = SpectraColors.Ink, contentColor = Color.White),
        border = BorderStroke(1.dp, Brush.horizontalGradient(listOf(SpectraColors.Cyan.copy(.55f), SpectraColors.Violet.copy(.65f), SpectraColors.Warm.copy(.45f)))),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) Icon(icon, null)
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun TelemetryChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.defaultMinSize(minHeight = 40.dp).clickable(onClick = onClick),
        shape = CircleShape,
        color = if (selected) SpectraColors.Ink else Color.White.copy(.5f),
        contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, if (selected) SpectraColors.Violet.copy(.75f) else SpectraColors.Silver.copy(.8f)),
    ) { Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.Center) { Text(text, style = MaterialTheme.typography.labelMedium) } }
}

@Composable
fun SlideConfirm(
    text: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val density = LocalDensity.current
    val horizontalInset = with(density) { 8.dp.toPx() }
    val thumbWidth = with(density) { 48.dp.toPx() }
    var trackWidth by remember { mutableStateOf(0f) }
    var dragOffset by remember { mutableStateOf(0f) }
    val maxOffset = (trackWidth - thumbWidth - horizontalInset).coerceAtLeast(0f)
    val progress = if (maxOffset == 0f) 0f else (dragOffset / maxOffset).coerceIn(0f, 1f)
    val shape = CircleShape
    val dragState = rememberDraggableState { delta ->
        if (enabled) dragOffset = (dragOffset + delta).coerceIn(0f, maxOffset)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .onSizeChanged { trackWidth = it.width.toFloat(); dragOffset = dragOffset.coerceAtMost(maxOffset) }
            .clip(shape)
            .background(if (enabled) SpectraColors.Ink else SpectraColors.Ink.copy(.45f))
            .border(
                1.dp,
                Brush.horizontalGradient(listOf(SpectraColors.Cyan.copy(.55f), SpectraColors.Violet.copy(.7f), SpectraColors.Warm.copy(.45f))),
                shape,
            )
            .semantics {
                role = Role.Button
                onClick(label = text) {
                    if (enabled) onConfirm()
                    enabled
                }
            }
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                enabled = enabled,
                onDragStopped = {
                    if (progress >= .82f) onConfirm()
                    dragOffset = 0f
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White.copy(alpha = .72f + progress * .28f), style = MaterialTheme.typography.labelLarge)
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset((4.dp.toPx() + dragOffset).roundToInt(), 0) }
                .size(48.dp)
                .background(Color.White.copy(if (enabled) .94f else .56f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(14.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(SpectraColors.Cyan, SpectraColors.Violet, SpectraColors.Warm),
                            start = Offset.Zero,
                            end = Offset.Infinite,
                        ),
                        CircleShape,
                    ),
            )
        }
    }
}

@Composable
fun BrandMark(modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurface) {
    androidx.compose.foundation.Canvas(modifier) {
        val stroke = size.minDimension * .085f
        val radius = size.minDimension * .29f
        val center = center
        drawArc(tint, 48f, 264f, false, topLeft = androidx.compose.ui.geometry.Offset(center.x-radius, center.y-radius), size = androidx.compose.ui.geometry.Size(radius*2,radius*2), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        val points = listOf(
            androidx.compose.ui.geometry.Offset(.69f,.22f), androidx.compose.ui.geometry.Offset(.32f,.2f),
            androidx.compose.ui.geometry.Offset(.2f,.52f), androidx.compose.ui.geometry.Offset(.39f,.79f),
            androidx.compose.ui.geometry.Offset(.72f,.7f),
        )
        points.forEachIndexed { index, p ->
            drawCircle(listOf(SpectraColors.Cyan,SpectraColors.Violet,SpectraColors.Rose,SpectraColors.Warm,SpectraColors.Focus)[index], radius = stroke*.68f, center = androidx.compose.ui.geometry.Offset(size.width*p.x,size.height*p.y))
        }
    }
}
