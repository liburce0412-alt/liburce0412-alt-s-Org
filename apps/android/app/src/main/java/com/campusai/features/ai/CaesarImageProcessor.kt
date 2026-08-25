package com.campusai.features.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import java.io.File
import java.io.FileOutputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

data class CaesarImageAttachment(
    val localPath: String,
    val mimeType: String,
    val ocrText: String,
)

class CaesarImageProcessor(context: Context) {
    private val appContext = context.applicationContext
    private val cacheDirectory = File(appContext.cacheDir, "caesar-images")

    suspend fun import(uri: Uri): CaesarImageAttachment = withContext(Dispatchers.IO) {
        val resolver = appContext.contentResolver
        val source = resolver.openInputStream(uri)?.use { input ->
            val bytes = input.readAtMost(MAX_SOURCE_BYTES + 1)
            require(bytes.size <= MAX_SOURCE_BYTES) { "图片不能超过 15 MB。" }
            bytes
        } ?: error("无法读取这张图片。")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "图片格式无法解析。" }
        var sample = 1
        while (bounds.outWidth / sample > MAX_EDGE * 2 || bounds.outHeight / sample > MAX_EDGE * 2) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(source, 0, source.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("图片解码失败。")
        val orientation = runCatching { ExifInterface(source.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
            .getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val oriented = decoded.orient(orientation)
        val scale = minOf(1f, MAX_EDGE.toFloat() / maxOf(oriented.width, oriented.height))
        val normalized = if (scale < 1f) Bitmap.createScaledBitmap(oriented, (oriented.width * scale).toInt().coerceAtLeast(1), (oriented.height * scale).toInt().coerceAtLeast(1), true) else oriented
        cacheDirectory.mkdirs()
        val output = File(cacheDirectory, "${UUID.randomUUID()}.jpg")
        FileOutputStream(output).use { stream -> check(normalized.compress(Bitmap.CompressFormat.JPEG, 90, stream)); stream.fd.sync() }
        if (normalized !== oriented) normalized.recycle()
        if (oriented !== decoded) oriented.recycle()
        if (!decoded.isRecycled) decoded.recycle()
        CaesarImageAttachment(output.absolutePath, "image/jpeg", "")
    }.let { attachment ->
        val text = recognize(attachment.localPath)
        attachment.copy(ocrText = text.take(MAX_OCR_CHARS))
    }

    fun delete(attachment: CaesarImageAttachment) {
        val candidate = File(attachment.localPath)
        if (candidate.parentFile?.canonicalFile == cacheDirectory.canonicalFile) candidate.delete()
    }

    private suspend fun recognize(path: String): String = runCatching {
        suspendCancellableCoroutine { continuation ->
            val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            val image = InputImage.fromFilePath(appContext, Uri.fromFile(File(path)))
            recognizer.process(image)
                .addOnSuccessListener { if (continuation.isActive) continuation.resume(it.text.trim()) }
                .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
                .addOnCompleteListener { recognizer.close() }
        }
    }.getOrDefault("")

    private fun Bitmap.orient(orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postScale(-1f, 1f); matrix.postRotate(270f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postScale(-1f, 1f); matrix.postRotate(90f) }
            else -> return this
        }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    private fun InputStream.readAtMost(limit: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(limit, 64 * 1024))
        val buffer = ByteArray(8 * 1024)
        while (output.size() < limit) {
            val count = read(buffer, 0, minOf(buffer.size, limit - output.size()))
            if (count < 0) break
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private companion object {
        const val MAX_SOURCE_BYTES = 15 * 1024 * 1024
        const val MAX_EDGE = 1600
        const val MAX_OCR_CHARS = 8_000
    }
}
