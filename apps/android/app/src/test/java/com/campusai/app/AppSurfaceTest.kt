package com.campusai.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppSurfaceTest {
    @Test
    fun everySurfaceRoundTripsThroughSavedStateEncoding() {
        val surfaces = listOf(
            AppSurface.Main(MainDestination.HOME),
            AppSurface.Main(MainDestination.PROFILE),
            AppSurface.Ai(MainDestination.TIME),
            AppSurface.Messages(MainDestination.MARKET),
            AppSurface.Focus(25, MainDestination.TIME),
            AppSurface.Login(MainDestination.CAMPUS),
        )

        surfaces.forEach { surface ->
            assertEquals(surface, decodeAppSurface(encodeAppSurface(surface)))
        }
    }

    @Test
    fun malformedSavedStateIsRejected() {
        assertNull(decodeAppSurface("focus|0|TIME"))
        assertNull(decodeAppSurface("main|NOT_A_ROUTE"))
        assertNull(decodeAppSurface("unknown|HOME"))
    }

    @Test
    fun fullScreenRoutesDoNotShareMainOpticalScope() {
        val main = AppSurface.Main(MainDestination.HOME)
        assertEquals("main:HOME", main.opticalRouteKey)
        assertEquals("fullscreen:ai", AppSurface.Ai(MainDestination.HOME).opticalRouteKey)
        assertEquals("fullscreen:messages", AppSurface.Messages(MainDestination.HOME).opticalRouteKey)
        assertEquals("fullscreen:focus", AppSurface.Focus(5).opticalRouteKey)
        assertEquals("fullscreen:login", AppSurface.Login(MainDestination.HOME).opticalRouteKey)
    }

    @Test
    fun supersededShareCompletionIsAcknowledgedWithoutConsumingTheNewIntent() {
        assertEquals(
            ExternalImageCompletionDisposition.ACK_STALE,
            externalImageCompletionDisposition(currentSharedUri = null, completedUri = "content://image/a"),
        )
        assertEquals(
            ExternalImageCompletionDisposition.ACK_STALE,
            externalImageCompletionDisposition("content://image/b", "content://image/a"),
        )
        assertEquals(
            ExternalImageCompletionDisposition.CONSUME_AND_ACK,
            externalImageCompletionDisposition("content://image/a", "content://image/a"),
        )
    }
}
