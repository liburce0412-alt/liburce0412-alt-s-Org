package com.campusai.caesar.bandbridge

import javax.crypto.AEADBadTagException
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TokenCipherTest {
    private val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")

    @Test
    fun encryptedTokenRoundTripsWithBoundPackageName() {
        val plaintext = byteArrayOf(7, 8, 9, 10)
        val aad = "com.campusai.caesar.bandbridge".toByteArray()

        val encrypted = TokenCipher.encrypt(key, plaintext, aad)

        assertArrayEquals(plaintext, TokenCipher.decrypt(key, encrypted, aad))
    }

    @Test
    fun wrongPackageBindingCannotDecryptToken() {
        val encrypted = TokenCipher.encrypt(key, byteArrayOf(1, 2, 3), "bridge".toByteArray())

        assertThrows(AEADBadTagException::class.java) {
            TokenCipher.decrypt(key, encrypted, "other-app".toByteArray())
        }
    }
}
