package com.campusai.features.community

import com.campusai.core.model.UiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class CampusListUiStateTest {
    @Test
    fun successAndEmptyAlwaysLeaveLoading() = runTest {
        val data = loadListUiState("fallback") { Result.success(listOf("item")) }
        val empty = loadListUiState<String>("fallback") { Result.success(emptyList()) }

        assertEquals(UiState.Data(listOf("item")), data)
        assertEquals(UiState.Empty, empty)
    }

    @Test
    fun resultFailureAndDirectThrowBecomeError() = runTest {
        val failure = loadListUiState<String>("fallback") { Result.failure(IllegalStateException("network")) }
        val thrown = loadListUiState<String>("fallback") { error("malformed listing") }

        assertEquals(UiState.Error("network"), failure)
        assertEquals(UiState.Error("malformed listing"), thrown)
    }

    @Test
    fun cancellationIsNeverConvertedIntoUiError() = runTest {
        val cancelled = CancellationException("newer refresh")
        try {
            loadListUiState<String>("fallback") { throw cancelled }
            fail("CancellationException must escape")
        } catch (actual: CancellationException) {
            assertSame(cancelled, actual)
        }
    }

    @Test
    fun refreshKeepsAnEmptyOrPopulatedStateVisible() {
        assertEquals(UiState.Empty, keepVisibleListDuringRefresh<String>(UiState.Loading))
        assertEquals(UiState.Empty, keepVisibleListDuringRefresh<String>(UiState.Error("offline")))
        assertEquals(
            UiState.Data(listOf("listing")),
            keepVisibleListDuringRefresh(UiState.Data(listOf("listing"))),
        )
    }

    @Test
    fun failedRefreshNeverReturnsToLoadingOrInventsRemoteData() {
        val noLocalListings = settleVisibleListAfterRefresh<String>(
            current = UiState.Empty,
            loaded = UiState.Error("timeout"),
        )
        val staleListings = settleVisibleListAfterRefresh(
            current = UiState.Data(listOf("listing")),
            loaded = UiState.Error("timeout"),
        )

        assertEquals(UiState.Empty, noLocalListings)
        assertEquals(UiState.Offline(listOf("listing")), staleListings)
    }
}
