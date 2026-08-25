package com.campusai.caesar.bandbridge

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Device-local vault reserved for a future verified Band 9 pairing token.
 *
 * The token is never exposed through the provider, diagnostics page, logs, or CampusAI process.
 * Gadgetbridge's token remains Gadgetbridge-owned; this vault does not attempt to extract it.
 */
internal class PairingTokenVault(context: Context) {
    private val applicationContext = context.applicationContext
    private val tokenFile = File(applicationContext.noBackupFilesDir, TOKEN_FILE)

    @Synchronized
    fun save(token: ByteArray): Result<Unit> = runCatching {
        require(token.isNotEmpty() && token.size <= MAX_TOKEN_BYTES) { "Invalid pairing token length" }
        val copy = token.copyOf()
        try {
            val encoded = TokenCipher.encrypt(loadOrCreateKey(), copy, applicationContext.packageName.toByteArray())
            val temporary = File.createTempFile("pairing-token-", ".tmp", applicationContext.noBackupFilesDir)
            try {
                temporary.outputStream().use { it.write(encoded) }
                Files.move(
                    temporary.toPath(),
                    tokenFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } finally {
                temporary.delete()
            }
        } finally {
            copy.fill(0)
        }
    }

    @Synchronized
    fun load(): Result<ByteArray?> = runCatching {
        if (!tokenFile.isFile) return@runCatching null
        TokenCipher.decrypt(
            loadOrCreateKey(),
            tokenFile.readBytes(),
            applicationContext.packageName.toByteArray(),
        )
    }

    fun hasToken(): Boolean = tokenFile.isFile && tokenFile.length() > 0

    @Synchronized
    fun clear(): Result<Unit> = runCatching {
        if (tokenFile.exists() && !tokenFile.delete()) error("Unable to clear pairing token")
    }

    private fun loadOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    companion object {
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "caesar.band9.pairing.v1"
        private const val TOKEN_FILE = "band9-pairing-token.v1"
        private const val MAX_TOKEN_BYTES = 4096
    }
}

internal object TokenCipher {
    private const val VERSION: Byte = 1
    private const val TAG_BITS = 128

    fun encrypt(key: SecretKey, plaintext: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(aad)
        val encrypted = cipher.doFinal(plaintext)
        val iv = cipher.iv
        require(iv.size <= 255)
        return ByteBuffer.allocate(2 + iv.size + encrypted.size)
            .put(VERSION)
            .put(iv.size.toByte())
            .put(iv)
            .put(encrypted)
            .array()
    }

    fun decrypt(key: SecretKey, encoded: ByteArray, aad: ByteArray): ByteArray {
        require(encoded.size >= 3 && encoded[0] == VERSION) { "Unsupported token envelope" }
        val ivSize = encoded[1].toInt() and 0xff
        require(ivSize in 12..32 && encoded.size > 2 + ivSize) { "Corrupt token envelope" }
        val iv = encoded.copyOfRange(2, 2 + ivSize)
        val ciphertext = encoded.copyOfRange(2 + ivSize, encoded.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext)
    }
}
