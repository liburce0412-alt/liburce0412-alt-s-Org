package com.campusai.core.health.mifitness

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MiFitnessProtocolTest {
    @Test
    fun `rc4 drop 1024 matches verified vector`() {
        val key = ByteArray(32) { it.toByte() }
        val plaintext = "xiaomi-wire-protocol".toByteArray()

        val actual = MiFitnessProtocol.rc4Crypt(key, plaintext).toHex()

        assertEquals("ce0131835d9ad18c36d39e531a260c06eeaac74f", actual)
    }

    @Test
    fun `continuous sorted-field stream matches verified form`() {
        val request = MiFitnessProtocol.buildTimeSeriesRequest(
            metric = "steps",
            startEpochSeconds = 1_743_436_800L,
            endEpochSecondsExclusive = 1_743_523_199L,
        )
        val nonce = MiFitnessProtocol.decodeBase64("AAECAwQFBgcAAAAq", "nonce")

        val actual = MiFitnessProtocol.buildEncryptedForm(
            request = request,
            ssecurityBase64 = "c3NlY3VyaXR5LXZlY3Rvcg==",
            nonce = nonce,
        )

        assertEquals(
            mapOf(
                "data" to "g3v3b7BuvSDK+4BW0DuyYj3nPPsDj0D4X62ofPJbCgVY1D1/Z4v7XctOR+PQYmIl6iSxRSHeToCQsFp/AKeoZ8Vg2yLKf0HDsHZm8BevxwesX/yLB/BfPKVn",
                "rc4_hash__" to "j/CctLjR04ANsIEshjqaznSKKq0BKTz1NMQ9SQ==",
                "signature" to "e+HHLv3ixrLzwAg8Vpz25kCEvj4=",
                "_nonce" to "AAECAwQFBgcAAAAq",
            ),
            actual.parameters,
        )
    }

    @Test
    fun `official daily aggregate request matches APK snake case contract`() {
        val request = MiFitnessProtocol.buildDailyAggregateRequest(
            metric = "steps",
            startEpochSeconds = 1_777_305_600L,
            endEpochSecondsExclusive = 1_777_392_000L,
            nextKey = "synthetic-next",
        )
        val payload = JSONObject(request.payloadJson)

        assertEquals("GET", request.method)
        assertEquals("/app/v1/data/get_aggregated_fitness_data_by_time", request.path)
        assertEquals("daily_report", payload.getString("tag"))
        assertEquals("steps", payload.getString("key"))
        assertTrue(payload.getBoolean("reverse"))
        assertEquals(100, payload.getInt("limit"))
        assertEquals("synthetic-next", payload.getString("next_key"))
        assertEquals(1_777_305_600L, payload.getLong("start_time"))
        assertEquals(1_777_392_000L, payload.getLong("end_time"))
        assertFalse(payload.has("startTime"))
        assertFalse(payload.has("endTime"))
        assertFalse(payload.has("nextKey"))
    }

    @Test
    fun `sport record request uses verified read only endpoint`() {
        val request = MiFitnessProtocol.buildSportRecordsRequest(100L, 200L)
        val payload = JSONObject(request.payloadJson)

        assertEquals("/app/v1/data/get_sport_records_by_time", request.path)
        assertEquals("", payload.getString("category"))
        assertEquals(100L, payload.getLong("start_time"))
        assertEquals(200L, payload.getLong("end_time"))
        assertEquals(50, payload.getInt("limit"))
        assertTrue(payload.getBoolean("reverse"))
    }

    @Test
    fun `response decrypt matches verified object`() {
        val decrypted = MiFitnessProtocol.decryptResponse(
            ciphertextBase64 = "g3v/Za0ppTiJo8dUxmrrLDqxZ/JVtFXlU5fmL7AYHAw3nCkzPtayXZQRFIiXPzx28C6wXjeGHsuBu0E1WOCqcZEpiyfOMQ7S4Dkhp2nj0Qu9cOSyXOhWLLYq0OD/qeOQ/a4wtNBA/TPYqjHeMrpFMhvsIfJkEfkq98m0NwcPPd08ESM9mpIH+i3un5zdISVMegOWRetLotrXPyig99p/oT7K",
            ssecurityBase64 = "c3NlY3VyaXR5LXZlY3Rvcg==",
            nonceBase64 = "AAECAwQFBgcAAAAq",
        )
        val response = JSONObject(decrypted)
        val result = response.getJSONObject("result")

        assertEquals(0, response.getInt("code"))
        assertEquals(3_210, JSONObject(result.getJSONArray("data_list").getJSONObject(0).getString("value")).getInt("steps"))
        assertFalse(result.getBoolean("has_more"))
        assertEquals("", result.getString("next_key"))
    }

    @Test
    fun `region metric and Xiaomi redirect hosts are allowlisted`() {
        assertEquals("https://hlth.io.mi.com/", MiFitnessProtocol.regionBaseUrl("cn").toString())
        MiFitnessProtocol.requireXiaomiHttps("https://sts-hlth.io.mi.com/healthapp/sts".toHttpUrl())
        MiFitnessProtocol.requireXiaomiHttps("https://account.xiaomi.com/pass/serviceLogin".toHttpUrl())

        listOf("de", "https://hlth.io.mi.com", "").forEach { region ->
            assertThrows(IllegalArgumentException::class.java) {
                MiFitnessProtocol.regionBaseUrl(region)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            MiFitnessProtocol.buildDailyAggregateRequest("unknown", 100L, 200L)
        }
        listOf(
            "http://account.xiaomi.com/pass/serviceLogin",
            "https://account.xiaomi.com:8443/pass/serviceLogin",
            "https://user@account.xiaomi.com/pass/serviceLogin",
            "https://mi.com.evil.example/",
            "https://example.com/",
        ).forEach { url ->
            assertThrows(MiFitnessAuthenticationException::class.java) {
                MiFitnessProtocol.requireXiaomiHttps(url.toHttpUrl())
            }
        }
    }

    @Test
    fun `cookie values reject every RFC6265 separator and non ASCII byte`() {
        listOf("space value", "quote\"value", "comma,value", "semi;value", "slash\\value", "line\nvalue", "中文")
            .forEach { value ->
                assertThrows(MiFitnessAuthenticationException::class.java) {
                    MiFitnessProtocol.cookieHeader(mapOf("serviceToken" to value))
                }
            }
        assertEquals(
            "serviceToken=abc-_.~+/=:",
            MiFitnessProtocol.cookieHeader(mapOf("serviceToken" to "abc-_.~+/=:")),
        )
    }

    @Test
    fun `login prefix and client sign match verified vector`() {
        val login = MiFitnessProtocol.parseServiceLogin(
            body = "&&&START&&&{\"userId\":12345,\"cUserId\":\"fake-c-user\",\"passToken\":\"fake-refreshed-token\",\"ssecurity\":\"c3NlY3VyaXR5LXZlY3Rvcg==\",\"nonce\":\"fake-login-nonce\",\"location\":\"https://sts-hlth.io.mi.com/healthapp/sts?sid=miothealth\"}",
            fallbackUserId = "12345",
            originalPassToken = "synthetic-original-pass-token",
            responseCookies = emptyMap(),
            extensionHeader = null,
        )

        assertEquals("12345", login.userId)
        assertEquals("rX1ZA6l/U9I8qZEGRgu7+swZ6Wc=", login.stsUrl.queryParameter("clientSign"))
        assertEquals("true", login.stsUrl.queryParameter("_userIdNeedEncrypt"))
        assertTrue(login.tokenRefreshed)
        assertEquals("fake-refreshed-token", login.refreshedPassToken)
        assertFalse(login.toString().contains("fake-refreshed-token"))
    }

    @Test
    fun `login rejects a response bound to a different account`() {
        assertThrows(MiFitnessAuthenticationException::class.java) {
            MiFitnessProtocol.parseServiceLogin(
                body = "&&&START&&&{\"userId\":54321,\"cUserId\":\"fake-c-user\",\"ssecurity\":\"c3NlY3VyaXR5LXZlY3Rvcg==\",\"nonce\":\"fake-login-nonce\",\"location\":\"https://sts-hlth.io.mi.com/healthapp/sts?sid=miothealth\"}",
                fallbackUserId = "12345",
                originalPassToken = "synthetic-original-pass-token",
                responseCookies = emptyMap(),
                extensionHeader = null,
            )
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }
}
