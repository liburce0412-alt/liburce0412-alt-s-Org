package com.campusai

import com.campusai.core.localai.LocalModelDownloadWorker
import com.campusai.core.localai.LocalModelSnapshot
import com.campusai.core.localai.TransferWorkState
import com.campusai.core.localai.reduceLocalModelState
import com.campusai.core.model.LocalModelState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModelStateReducerTest {
    private val total = 100L

    @Test fun `covers fresh download pause resume verification ready and incompatibility`() {
        assertEquals(LocalModelState.NotDownloaded, reduceLocalModelState(LocalModelSnapshot(totalBytes = total)))
        assertEquals(LocalModelState.Downloading(.2f, 20, total), reduceLocalModelState(LocalModelSnapshot(workState = TransferWorkState.RUNNING, downloadedBytes = 20, totalBytes = total)))
        assertEquals(LocalModelState.Paused(20, total), reduceLocalModelState(LocalModelSnapshot(paused = true, downloadedBytes = 20, totalBytes = total)))
        assertEquals(LocalModelState.Verifying, reduceLocalModelState(LocalModelSnapshot(workState = TransferWorkState.RUNNING, stage = LocalModelDownloadWorker.STAGE_VERIFYING, downloadedBytes = total, totalBytes = total)))
        assertEquals(LocalModelState.Ready, reduceLocalModelState(LocalModelSnapshot(ready = true, totalBytes = total)))
        assertTrue(reduceLocalModelState(LocalModelSnapshot(compatible = false, incompatibilityReason = "arm64 only", totalBytes = total)) is LocalModelState.Incompatible)
    }

    @Test fun `failed checksum never becomes ready`() {
        val state = reduceLocalModelState(LocalModelSnapshot(workState = TransferWorkState.FAILED, errorCode = "sha256_mismatch", errorMessage = "bad", totalBytes = total))
        assertEquals(LocalModelState.Error("sha256_mismatch", true, "bad"), state)
    }
}
