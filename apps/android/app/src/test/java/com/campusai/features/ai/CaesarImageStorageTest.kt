package com.campusai.features.ai

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.campusai.core.model.AiConversationMessage
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CaesarImageStorageTest {
    @Test
    fun `legacy cache image migrates to stable no-backup reference and reports deletion`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val legacy = File(context.cacheDir, "legacy-image.jpg")
        FileOutputStream(legacy).use { output ->
            Bitmap.createBitmap(8, 6, Bitmap.Config.ARGB_8888).also { bitmap ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output))
                bitmap.recycle()
            }
        }
        val processor = CaesarImageProcessor(context)

        val migrated = processor.migrateLegacy(
            conversationId = "conversation-unit",
            messages = listOf(AiConversationMessage("user", "图片", attachmentPaths = listOf(legacy.absolutePath))),
        ).single()

        assertEquals(1, migrated.attachmentRefs.size)
        assertTrue(migrated.attachmentPaths.single().startsWith(context.noBackupFilesDir.canonicalPath))
        assertFalse(AiConversationCodec.encode(listOf(migrated)).contains(legacy.absolutePath))

        File(migrated.attachmentPaths.single()).delete()
        val missing = processor.hydrate(listOf(migrated)).single()
        assertTrue(missing.attachmentPaths.isEmpty())
        assertEquals(1, missing.missingAttachmentCount)
        processor.deleteConversation("conversation-unit")
    }
}
