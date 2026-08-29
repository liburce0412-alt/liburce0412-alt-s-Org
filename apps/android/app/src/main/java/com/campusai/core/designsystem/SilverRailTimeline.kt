package com.campusai.core.designsystem

import com.campusai.core.model.RenderQuality

/** Pure contract mirrored by the SPECTRA shader so motion timing can be regression-tested. */
internal object SilverRailTimeline {
    const val CYCLE_SECONDS = 26f

    enum class Stage { STATIC, STRETCH, SPLIT, SCATTER, GATHER, MERGE }

    /** Motion-free counterpart of the shader rail, expressed in viewport fractions. */
    data class StaticRailProfile(
        val centerYFraction: Float,
        val lengthFraction: Float,
        val bodyThicknessFraction: Float,
        val glowThicknessFraction: Float,
    )

    fun normalizedPhase(elapsedSeconds: Float): Float {
        if (!elapsedSeconds.isFinite()) return 0f
        val wrapped = elapsedSeconds % CYCLE_SECONDS
        return (if (wrapped < 0f) wrapped + CYCLE_SECONDS else wrapped) / CYCLE_SECONDS
    }

    fun stageAt(elapsedSeconds: Float, motionEnabled: Boolean = true): Stage {
        if (!motionEnabled) return Stage.STATIC
        return when (normalizedPhase(elapsedSeconds)) {
            in 0f..<.20f -> Stage.STRETCH
            in .20f..<.38f -> Stage.SPLIT
            in .38f..<.62f -> Stage.SCATTER
            in .62f..<.82f -> Stage.GATHER
            else -> Stage.MERGE
        }
    }

    fun dropletCount(quality: RenderQuality, motionEnabled: Boolean = true): Int = when {
        !motionEnabled -> 0
        quality == RenderQuality.LOW -> 4
        else -> 7
    }

    fun staticRailProfile(motionEnabled: Boolean): StaticRailProfile? =
        if (stageAt(0f, motionEnabled) == Stage.STATIC) STATIC_RAIL_PROFILE else null

    private val STATIC_RAIL_PROFILE = StaticRailProfile(
        centerYFraction = .51f,
        lengthFraction = .92f,
        bodyThicknessFraction = .018f,
        glowThicknessFraction = .052f,
    )
}
