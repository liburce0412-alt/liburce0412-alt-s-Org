package com.campusai.core.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selectableGroup
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.campusai.core.model.MotionMode
import com.campusai.core.model.RenderQuality
import com.campusai.core.model.SpectraEnvironment
import com.campusai.R
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SpectraBackdrop(
    environment: SpectraEnvironment,
    quality: RenderQuality,
    motion: MotionMode,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    phase: SpectraPhase = SpectraPhase.AMBIENT,
) {
    if (motion == MotionMode.OFF) {
        Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val darkMode = MaterialTheme.colorScheme.background.luminance() < .35f
    var surface by remember { mutableStateOf<SpectraSurfaceView?>(null) }
    var lifecycleActive by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    AndroidView(
        factory = { context -> SpectraSurfaceView(context).also { surface = it } },
        update = { it.configure(environment, quality, darkMode, phase) },
        modifier = modifier.fillMaxSize().pointerInput(surface) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.firstOrNull()?.position?.let { position -> surface?.setPointer(position.x, position.y) }
                }
            }
        },
    )
    DisposableEffect(lifecycleOwner, surface) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> lifecycleActive = true
                Lifecycle.Event.ON_PAUSE -> lifecycleActive = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleActive = false; lifecycleOwner.lifecycle.removeObserver(observer); surface?.onPause() }
    }
    LaunchedEffect(surface, lifecycleActive) {
        if (lifecycleActive) surface?.onResume() else surface?.onPause()
    }
    LaunchedEffect(surface, active, lifecycleActive) {
        if (lifecycleActive) surface?.setSceneActive(active)
    }
    LaunchedEffect(surface, quality, lifecycleActive, active) {
        if (!lifecycleActive || !active) return@LaunchedEffect
        val minFrameIntervalNanos = when (quality) {
            RenderQuality.LOW -> 48_000_000L
            RenderQuality.AUTO,
            RenderQuality.HIGH -> 15_000_000L
        }
        var lastRequestedAt = Long.MIN_VALUE
        while (lifecycleActive && active) {
            withFrameNanos { frameTimeNanos ->
                if (
                    lastRequestedAt == Long.MIN_VALUE ||
                    frameTimeNanos - lastRequestedAt >= minFrameIntervalNanos
                ) {
                    surface?.requestRender()
                    lastRequestedAt = frameTimeNanos
                }
            }
        }
    }
}

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    radius: Int = 16,
    emphasized: Boolean = false,
    shadowed: Boolean = true,
    onClick: (() -> Unit)? = null,
    optical: Boolean = emphasized,
    opticalPriority: Int = if (emphasized) 1 else 0,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius.dp)
    val dark = MaterialTheme.colorScheme.background.luminance() < .35f
    val fill = if (dark) Color.White.copy(alpha = if (emphasized) .085f else .055f)
    else Color.White.copy(alpha = if (emphasized) .09f else .06f)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .99f else 1f, tween(120), label = "glass-press")
    val depression = with(LocalDensity.current) { if (pressed) 1.dp.toPx() else 0f }
    Box(
        modifier = modifier
            .opticalGlassRegion(
                enabled = optical,
                radius = radius.dp,
                priority = opticalPriority,
                refraction = if (emphasized) 5.4.dp else 3.8.dp,
                dispersion = if (emphasized) 1.45.dp else .92.dp,
                flow = if (emphasized) 2.1.dp else 1.45.dp,
                bodyOpacity = if (emphasized) .12f else .085f,
            )
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = depression
            }
            .then(
                if (shadowed) Modifier
                    .shadow(
                        14.dp,
                        shape,
                        ambientColor = SpectraColors.Ink.copy(if (dark) .16f else .055f),
                        spotColor = SpectraColors.Ink.copy(if (dark) .20f else .08f),
                    )
                else Modifier
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        fill.copy(alpha = (fill.alpha + .035f).coerceAtMost(1f)),
                        fill,
                        fill.copy(alpha = (fill.alpha - .025f).coerceAtLeast(.025f)),
                    ),
                ),
            )
            .drawWithCache {
                val one = 1.dp.toPx()
                val corner = CornerRadius(radius.dp.toPx(), radius.dp.toPx())
                val edge = Brush.linearGradient(
                    listOf(
                        Color.White.copy(if (dark) .44f else .78f),
                        Color.White.copy(if (dark) .16f else .30f),
                        SpectraColors.Ink.copy(if (dark) .18f else .10f),
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                )
                val crown = Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        Color.White.copy(if (dark) .38f else .76f),
                        Color.Transparent,
                    ),
                    startX = size.width * .08f,
                    endX = size.width * .72f,
                )
                onDrawWithContent {
                    drawContent()
                    drawRoundRect(edge, cornerRadius = corner, style = Stroke(one))
                    drawLine(
                        brush = crown,
                        start = Offset(size.width * .08f, one * 1.1f),
                        end = Offset(size.width * .72f, one * 1.1f),
                        strokeWidth = one,
                    )
                }
            }
            .then(if (onClick != null) Modifier.clickable(source, null) { onClick() } else Modifier),
        content = content,
    )
}

/**
 * Shared direct-manipulation selector used anywhere Caesar∞ presents one choice from a small set.
 * The rail owns the live refraction; the moving pearl lens stays semi-solid so labels never enter
 * the RGB dispersion pass. Tap and drag share the same state transition and boundary haptics.
 */
@Composable
fun CaesarSlidingSelector(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    motionEnabled: Boolean = true,
    enabled: Boolean = true,
) {
    if (options.isEmpty()) return
    val safeIndex = selectedIndex.coerceIn(options.indices)
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val animatedX = remember { Animatable(0f) }
    var railWidth by remember { mutableFloatStateOf(0f) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var visualIndex by remember { mutableIntStateOf(safeIndex) }
    var positionInitialized by remember(options) { mutableStateOf(false) }

    fun xFor(index: Int): Float = railWidth / options.size.coerceAtLeast(1) * index

    LaunchedEffect(safeIndex, railWidth, dragging, motionEnabled) {
        if (railWidth <= 0f || dragging) return@LaunchedEffect
        visualIndex = safeIndex
        val target = xFor(safeIndex)
        if (!motionEnabled || !positionInitialized) {
            animatedX.snapTo(target)
            positionInitialized = true
        } else {
            animatedX.animateTo(
                target,
                spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
    }

    val dark = MaterialTheme.colorScheme.background.luminance() < .35f
    GlassPanel(
        modifier = modifier.height(56.dp),
        radius = 28,
        emphasized = true,
        shadowed = false,
        opticalPriority = 3,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(5.dp)
                .onSizeChanged {
                    railWidth = it.width.toFloat()
                    if (!positionInitialized) dragX = xFor(safeIndex)
                }
                .pointerInput(enabled, railWidth, options) {
                    if (!enabled || railWidth <= 0f || options.size < 2) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dragging = true
                            dragX = animatedX.value.takeIf { it >= 0f } ?: xFor(safeIndex)
                            visualIndex = safeIndex
                        },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            val slot = railWidth / options.size
                            dragX = (dragX + amount).coerceIn(0f, railWidth - slot)
                            val next = (dragX / slot).roundToInt().coerceIn(options.indices)
                            if (next != visualIndex) {
                                visualIndex = next
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                        onDragEnd = {
                            val next = visualIndex.coerceIn(options.indices)
                            scope.launch {
                                animatedX.snapTo(dragX)
                                dragging = false
                                onSelected(next)
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                dragging = false
                                visualIndex = safeIndex
                                animatedX.snapTo(xFor(safeIndex))
                            }
                        },
                    )
                },
        ) {
            val slotFraction = 1f / options.size
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(slotFraction)
                    .graphicsLayer { translationX = if (dragging) dragX else animatedX.value }
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            if (dark) listOf(
                                Color(0xFF171A21).copy(.94f),
                                Color(0xFF343943).copy(.82f),
                                Color(0xFF171A21).copy(.94f),
                            ) else listOf(
                                Color.White.copy(.82f),
                                Color(0xFFE7E8EA).copy(.72f),
                                Color.White.copy(.78f),
                            ),
                        ),
                    )
                    .drawWithCache {
                        val edge = Brush.horizontalGradient(
                            listOf(
                                Color.White.copy(if (dark) .28f else .58f),
                                Color.White.copy(if (dark) .62f else .92f),
                                SpectraColors.Ink.copy(if (dark) .20f else .10f),
                            ),
                        )
                        onDrawWithContent {
                            drawContent()
                            drawRoundRect(edge, cornerRadius = CornerRadius(size.height / 2f), style = Stroke(1.dp.toPx()))
                            drawLine(
                                Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(.76f), Color.Transparent)),
                                Offset(size.width * .20f, 3.dp.toPx()),
                                Offset(size.width * .80f, 3.dp.toPx()),
                                1.dp.toPx(),
                            )
                        }
                    },
            )
            Row(
                Modifier.fillMaxSize().semantics { selectableGroup() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                options.forEachIndexed { index, label ->
                    val selectedNow = index == if (dragging) visualIndex else safeIndex
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .selectable(
                                selected = selectedNow,
                                enabled = enabled,
                                interactionSource = remember(label) { MutableInteractionSource() },
                                indication = null,
                                role = Role.Tab,
                                onClick = {
                                    if (index != safeIndex) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onSelected(index)
                                    }
                                },
                            )
                            .semantics(mergeDescendants = true) {
                                contentDescription = label
                                selected = selectedNow
                                stateDescription = if (selectedNow) "已选中" else "未选中"
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            color = MaterialTheme.colorScheme.onSurface.copy(
                                when {
                                    !enabled -> .34f
                                    selectedNow -> .96f
                                    else -> .58f
                                },
                            ),
                            fontSize = 12.sp,
                            fontWeight = if (selectedNow) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
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
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        border = BorderStroke(1.dp, Color.White.copy(.34f)),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) Icon(icon, null)
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun TelemetryChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    SpectraAction(
        text = text,
        onClick = onClick,
        modifier = modifier,
        selected = selected,
        enabled = enabled,
        mood = PageMood.CAESAR,
    )
}

@Composable
fun SlideConfirm(
    text: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
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
                Color.White.copy(.28f),
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
                    if (progress >= .82f) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onConfirm()
                    }
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
                    .background(SpectraColors.Ink.copy(.72f), CircleShape),
            )
        }
    }
}

@Composable
fun BrandMark(
    modifier: Modifier = Modifier,
    tint: Color? = null,
    decorative: Boolean = false,
    contentDescription: String = "Caesar∞ 标识",
) {
    Image(
        painter = painterResource(R.drawable.campusai_infinity_icon),
        contentDescription = if (decorative) null else contentDescription,
        modifier = modifier,
        colorFilter = tint?.let { ColorFilter.tint(it) },
    )
}
