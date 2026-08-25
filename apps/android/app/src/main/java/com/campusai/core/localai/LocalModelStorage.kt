package com.campusai.core.localai

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

class LocalModelStorage(
    private val context: Context,
    val manifest: LocalModelManifest = LocalModelManifest.load(context),
) {
    val root: File get() = File(context.noBackupFilesDir, "models")
    val activeDirectory: File get() = File(root, "${manifest.id}-${manifest.version}")
    val stagingDirectory: File get() = File(root, ".${manifest.id}-${manifest.version}.staging")
    val readyMarker: File get() = File(activeDirectory, READY_MARKER)

    fun compatibilityIssue(): String? {
        if (Build.VERSION.SDK_INT < manifest.minimumApi) return "需要 Android ${manifest.minimumApi} 或更高版本。"
        if (Build.SUPPORTED_ABIS.none(manifest.supportedAbis::contains)) return "首版仅支持 arm64-v8a 设备。"
        val memory = ActivityManager.MemoryInfo().also {
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it)
        }
        if (memory.totalMem < manifest.minimumRamBytes) {
            val requiredGb = manifest.minimumRamBytes / (1024L * 1024L * 1024L)
            return "设备内存不足 $requiredGb GB，无法保证 ${manifest.displayName} 稳定运行。"
        }
        return null
    }

    fun isReady(): Boolean = readyMarker.isFile && manifest.files.all { expected ->
        File(activeDirectory, expected.path).let { it.isFile && it.length() == expected.size }
    }

    fun downloadedBytes(): Long = when {
        stagingDirectory.exists() -> directoryBytes(stagingDirectory).coerceAtMost(manifest.totalBytes)
        isReady() -> manifest.totalBytes
        else -> 0L
    }

    fun occupiedBytes(): Long = sequenceOf(activeDirectory, stagingDirectory)
        .filter(File::exists)
        .flatMap { it.walkTopDown().filter(File::isFile) }
        .sumOf(File::length)

    fun hasEnoughSpace(): Boolean {
        root.mkdirs()
        val remaining = (manifest.totalBytes - downloadedBytes()).coerceAtLeast(0)
        return StatFs(root.absolutePath).availableBytes >= remaining + manifest.safetyMarginBytes
    }

    fun deleteSelected(): Boolean {
        val activeDeleted = !activeDirectory.exists() || activeDirectory.deleteRecursively()
        val stagingDeleted = !stagingDirectory.exists() || stagingDirectory.deleteRecursively()
        return activeDeleted && stagingDeleted
    }

    private fun directoryBytes(directory: File): Long =
        directory.walkTopDown().filter(File::isFile).sumOf(File::length)

    companion object { const val READY_MARKER = ".ready.json" }
}

object Sha256Verifier {
    fun digest(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun verify(file: File, expectedSize: Long, expectedSha256: String): Boolean =
        file.isFile && file.length() == expectedSize && digest(file) == expectedSha256
}
