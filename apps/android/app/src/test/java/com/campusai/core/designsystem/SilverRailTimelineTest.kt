package com.campusai.core.designsystem

import com.campusai.core.model.RenderQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SilverRailTimelineTest {
    @Test
    fun twentySixSecondCycleUsesTheFiveLiquidStages() {
        assertEquals(SilverRailTimeline.Stage.STRETCH, SilverRailTimeline.stageAt(0f))
        assertEquals(SilverRailTimeline.Stage.SPLIT, SilverRailTimeline.stageAt(6f))
        assertEquals(SilverRailTimeline.Stage.SCATTER, SilverRailTimeline.stageAt(11f))
        assertEquals(SilverRailTimeline.Stage.GATHER, SilverRailTimeline.stageAt(17f))
        assertEquals(SilverRailTimeline.Stage.MERGE, SilverRailTimeline.stageAt(22f))
        assertEquals(SilverRailTimeline.Stage.STRETCH, SilverRailTimeline.stageAt(26f))
    }

    @Test
    fun qualityControlsOnlyDropletCountAndMotionOffIsStatic() {
        assertEquals(4, SilverRailTimeline.dropletCount(RenderQuality.LOW))
        assertEquals(7, SilverRailTimeline.dropletCount(RenderQuality.AUTO))
        assertEquals(7, SilverRailTimeline.dropletCount(RenderQuality.HIGH))
        assertEquals(0, SilverRailTimeline.dropletCount(RenderQuality.HIGH, motionEnabled = false))
        assertEquals(SilverRailTimeline.Stage.STATIC, SilverRailTimeline.stageAt(10f, motionEnabled = false))
    }

    @Test
    fun reducedMotionProvidesOneCompleteStaticRailProfile() {
        assertNull(SilverRailTimeline.staticRailProfile(motionEnabled = true))
        val profile = SilverRailTimeline.staticRailProfile(motionEnabled = false)
        assertNotNull(profile)
        checkNotNull(profile)
        assertTrue(profile.centerYFraction in 0f..1f)
        assertTrue(profile.lengthFraction in .8f..1f)
        assertTrue(profile.bodyThicknessFraction > 0f)
        assertTrue(profile.glowThicknessFraction > profile.bodyThicknessFraction)
    }
}
