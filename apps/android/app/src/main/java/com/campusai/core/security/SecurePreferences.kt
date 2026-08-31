package com.campusai.core.security

import android.annotation.SuppressLint
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SecurePreferences {
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "CampusAISecureKeyAlias"
    private const val PREFS_NAME = "campus_ai_secure_prefs"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    init {
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    KEYSTORE_PROVIDER
                )
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
                keyGenerator.init(spec)
                keyGenerator.generateKey()
            }
        } catch (e: Exception) {
            Log.e("SecurePrefs", "Failed to initialize Android KeyStore", e)
        }
    }

    private fun getSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
        } catch (e: Exception) {
            Log.e("SecurePrefs", "Failed to retrieve secret key", e)
            null
        }
    }

    // Secret writes must report durable success before the UI claims the Key was saved.
    @SuppressLint("ApplySharedPref")
    fun encrypt(context: Context, key: String, plainText: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (plainText.isEmpty()) {
            return prefs.edit().remove(key).remove("${key}_iv").commit()
        }
        return try {
            val secretKey = getSecretKey()
            if (secretKey != null) {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                val iv = cipher.iv
                val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
                
                val ivString = Base64.encodeToString(iv, Base64.NO_WRAP)
                val encryptedString = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
                
                prefs.edit()
                    .putString("${key}_iv", ivString)
                    .putString(key, encryptedString)
                    .commit()
            } else {
                Log.e("SecurePrefs", "Android KeyStore unavailable; refusing to persist secret")
                false
            }
        } catch (e: Exception) {
            Log.e("SecurePrefs", "Encryption failed for $key; secret was not persisted", e)
            false
        }
    }

    fun decrypt(context: Context, key: String): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encryptedString = prefs.getString(key, null)
        val ivString = prefs.getString("${key}_iv", null)
        
        if (encryptedString == null) return ""
        
        try {
            val secretKey = getSecretKey()
            if (secretKey != null && ivString != null) {
                val iv = Base64.decode(ivString, Base64.NO_WRAP)
                val encryptedBytes = Base64.decode(encryptedString, Base64.NO_WRAP)
                
                val cipher = Cipher.getInstance(TRANSFORMATION)
                val spec = GCMParameterSpec(128, iv)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
                
                val decryptedBytes = cipher.doFinal(encryptedBytes)
                return String(decryptedBytes, Charsets.UTF_8)
            }
            return ""
        } catch (e: Exception) {
            Log.e("SecurePrefs", "Decryption failed for $key", e)
            return ""
        }
    }
}
