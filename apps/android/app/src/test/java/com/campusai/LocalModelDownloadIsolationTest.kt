package com.campusai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.campusai.core.localai.LocalModelDownloadWorker
import com.campusai.core.localai.LocalModelManifest
import com.campusai.core.localai.LocalModelStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class LocalModelDownloadIsolationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val qualityId = LocalModelManifest.DEFAULT_MODEL_ID
    private val fastId = "qwen3.5-2b-mnn"

    @Test fun `quality keeps legacy transfer names while fast is isolated`() {
        assertEquals(LocalModelDownloadWorker.UNIQUE_WORK, LocalModelDownloadWorker.uniqueWorkName(qualityId))
        assertEquals(LocalModelDownloadWorker.PREFS, LocalModelDownloadWorker.transferPreferencesName(qualityId))
        assertNotEquals(
            LocalModelDownloadWorker.uniqueWorkName(qualityId),
            LocalModelDownloadWorker.uniqueWorkName(fastId),
        )
        assertNotEquals(
            LocalModelDownloadWorker.transferPreferencesName(qualityId),
            LocalModelDownloadWorker.transferPreferencesName(fastId),
        )
    }

    @Test fun `pause preference is isolated by model id`() {
        val quality = context.getSharedPreferences(
            LocalModelDownloadWorker.transferPreferencesName(qualityId),
            Context.MODE_PRIVATE,
        )
        val fast = context.getSharedPreferences(
            LocalModelDownloadWorker.transferPreferencesName(fastId),
            Context.MODE_PRIVATE,
        )
        quality.edit().clear().commit()
        fast.edit().clear().commit()
        try {
            quality.edit().putBoolean(LocalModelDownloadWorker.KEY_PAUSED, true).commit()
            assertTrue(quality.getBoolean(LocalModelDownloadWorker.KEY_PAUSED, false))
            assertFalse(fast.getBoolean(LocalModelDownloadWorker.KEY_PAUSED, false))
        } finally {
            quality.edit().clear().commit()
            fast.edit().clear().commit()
        }
    }

    @Test fun `storage accounting and deletion only touch the selected model`() {
        val quality = LocalModelStorage(context, LocalModelManifest.load(context, qualityId))
        val fast = LocalModelStorage(context, LocalModelManifest.load(context, fastId))
        quality.deleteSelected()
        fast.deleteSelected()
        try {
            File(quality.stagingDirectory, "quality.part").apply {
                parentFile?.mkdirs()
                writeBytes(ByteArray(7))
            }
            File(fast.stagingDirectory, "fast.part").apply {
                parentFile?.mkdirs()
                writeBytes(ByteArray(11))
            }
            File(fast.activeDirectory, "incomplete.bin").apply {
                parentFile?.mkdirs()
                writeBytes(ByteArray(13))
            }

            assertEquals(7L, quality.occupiedBytes())
            assertEquals(11L, fast.downloadedBytes())
            assertEquals(24L, fast.occupiedBytes())
            assertTrue(quality.deleteSelected())
            assertFalse(quality.stagingDirectory.exists())
            assertTrue(fast.stagingDirectory.exists())
            assertTrue(fast.activeDirectory.exists())
            assertEquals(24L, fast.occupiedBytes())
        } finally {
            quality.deleteSelected()
            fast.deleteSelected()
        }
    }

    @Test fun `unknown model id fails before resolving transfer state`() {
        assertThrows(IllegalArgumentException::class.java) {
            LocalModelDownloadWorker.uniqueWorkName("unknown-model")
        }
        assertThrows(IllegalArgumentException::class.java) {
            LocalModelDownloadWorker.transferPreferencesName("unknown-model")
        }
    }
}
