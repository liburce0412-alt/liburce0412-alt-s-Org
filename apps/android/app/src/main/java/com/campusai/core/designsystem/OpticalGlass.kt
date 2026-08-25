package com.campusai.core.designsystem

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * A screen-space request for renderer-owned refraction.
 *
 * The shared SPECTRA renderer can refract only its own scene texture. Compose content is drawn
 * afterwards, so labels and icons remain sharp and are never fed through the dispersion shader.
 */
internal data class OpticalGlassRegion(
    val id: Long,
    val boundsInWindow: Rect,
    val cornerRadiusPx: Float,
    val refractionPx: Float,
    val dispersionPx: Float,
    val flowPx: Float,
    val bodyOpacity: Float,
    val priority: Int,
) {
    val area: Float get() = boundsInWindow.width * boundsInWindow.height
}

/** Process-local bridge between Compose layout and the single GLSurfaceView renderer. */
internal object OpticalGlassRegistry {
    private const val MAX_HIGH_QUALITY_REGIONS = 3
    private val nextId = AtomicLong(1L)
    private val regions = ConcurrentHashMap<Long, OpticalGlassRegion>()

    fun nextId(): Long = nextId.getAndIncrement()

    fun update(region: OpticalGlassRegion) {
        if (region.boundsInWindow.width < 2f || region.boundsInWindow.height < 2f) {
            regions.remove(region.id)
        } else {
            regions[region.id] = region
        }
    }

    fun remove(id: Long) {
        regions.remove(id)
    }

    fun snapshot(viewLeft: Float, viewTop: Float, viewWidth: Float, viewHeight: Float): List<OpticalGlassRegion> {
        val viewRight = viewLeft + viewWidth
        val viewBottom = viewTop + viewHeight
        return regions.values
            .asSequence()
            .filter { region ->
                region.boundsInWindow.right > viewLeft &&
                    region.boundsInWindow.left < viewRight &&
                    region.boundsInWindow.bottom > viewTop &&
                    region.boundsInWindow.top < viewBottom
            }
            .sortedWith(
                compareByDescending<OpticalGlassRegion> { it.priority }
                    .thenByDescending { it.area }
                    .thenBy { it.id },
            )
            .take(MAX_HIGH_QUALITY_REGIONS)
            .toList()
    }
}

/**
 * Registers this Compose surface with the shared SPECTRA refraction pass.
 *
 * Keep this on a small number of meaningful glass surfaces. The renderer deliberately caps the
 * expensive path at three visible regions and falls back to the normal Compose material on low
 * render quality, motion-off, or framebuffer failure.
 */
fun Modifier.opticalGlassRegion(
    enabled: Boolean = true,
    radius: Dp = 16.dp,
    priority: Int = 0,
    refraction: Dp = 4.2.dp,
    dispersion: Dp = 1.1.dp,
    flow: Dp = 1.8.dp,
    bodyOpacity: Float = .12f,
): Modifier = composed {
    val id = remember { OpticalGlassRegistry.nextId() }
    val density = LocalDensity.current
    val radiusPx = with(density) { radius.toPx() }
    val refractionPx = with(density) { refraction.toPx() }
    val dispersionPx = with(density) { dispersion.toPx() }
    val flowPx = with(density) { flow.toPx() }

    DisposableEffect(id, enabled) {
        if (!enabled) OpticalGlassRegistry.remove(id)
        onDispose { OpticalGlassRegistry.remove(id) }
    }

    if (!enabled) {
        this
    } else {
        onGloballyPositioned { coordinates ->
            if (!coordinates.isAttached) {
                OpticalGlassRegistry.remove(id)
                return@onGloballyPositioned
            }
            OpticalGlassRegistry.update(
                OpticalGlassRegion(
                    id = id,
                    boundsInWindow = coordinates.boundsInWindow(),
                    cornerRadiusPx = radiusPx,
                    refractionPx = refractionPx,
                    dispersionPx = dispersionPx,
                    flowPx = flowPx,
                    bodyOpacity = bodyOpacity.coerceIn(.06f, .30f),
                    priority = priority,
                ),
            )
        }
    }
}
