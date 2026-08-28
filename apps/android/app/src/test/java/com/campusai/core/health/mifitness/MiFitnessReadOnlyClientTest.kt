package com.campusai.core.health.mifitness

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.ArrayDeque
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class MiFitnessReadOnlyClientTest {
    @Test
    fun `default client follows the Android system proxy selector`() {
        assertNull(MiFitnessReadOnlyClient.defaultHttpClient().proxy)
    }

    @Test
    fun `cancelling authentication cancels the active OkHttp call`() = runTest {
        val started = CountDownLatch(1)
        val cancellationObserved = CountDownLatch(1)
        val blocking = Interceptor { chain ->
            started.countDown()
            while (!chain.call().isCanceled()) Thread.sleep(5L)
            cancellationObserved.countDown()
            throw IOException("cancelled")
        }
        val client = client(blocking)
        val job = launch(Dispatchers.Default) {
            client.exchangePassToken("12345", "synthetic-pass-token", "synthetic-device-id")
        }

        assertTrue(started.await(5, TimeUnit.SECONDS))
        job.cancelAndJoin()

        assertTrue(cancellationObserved.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun `serviceLogin follows only dynamic Xiaomi STS without forwarding pass token`() = runTest {
        val transport = SyntheticInterceptor(
            { syntheticResponse(body = LOGIN_RESPONSE) },
            {
                syntheticResponse(
                    code = 302,
                    headers = Headers.headersOf(
                        "Location",
                        "https://hlth.io.mi.com/healthapp?serviceToken=synthetic-service-token",
                    ),
                )
            },
        )
        val client = client(transport)

        val session = client.exchangePassToken(
            userId = "12345",
            passToken = "synthetic-original-pass-token",
            deviceId = "synthetic-device-id",
        )

        assertEquals(2, transport.requests.size)
        val login = transport.requests[0]
        assertEquals("account.xiaomi.com", login.url.host)
        assertEquals("miothealth", login.url.queryParameter("sid"))
        assertEquals("true", login.url.queryParameter("_json"))
        assertEquals("com.mi.health", login.url.queryParameter("appName"))
        assertEquals("zh_CN", login.url.queryParameter("_locale"))
        assertTrue(login.header("Cookie").orEmpty().contains("passToken=synthetic-original-pass-token"))

        val sts = transport.requests[1]
        assertEquals("sts-hlth.io.mi.com", sts.url.host)
        assertEquals("rX1ZA6l/U9I8qZEGRgu7+swZ6Wc=", sts.url.queryParameter("clientSign"))
        assertEquals("true", sts.url.queryParameter("_userIdNeedEncrypt"))
        assertFalse(sts.header("Cookie").orEmpty().contains("passToken"))
        assertTrue(sts.header("Cookie").orEmpty().contains("cUserId=fake-c-user"))
        assertEquals("synthetic-service-token", session.serviceToken)
        assertTrue(session.tokenRefreshed)
        assertEquals("fake-refreshed-token", session.refreshedPassToken)
        assertFalse(session.toString().contains(session.serviceToken))
        assertFalse(session.toString().contains("fake-refreshed-token"))
    }

    @Test
    fun `non Xiaomi dynamic STS is rejected before a second request`() = runTest {
        val transport = SyntheticInterceptor(
            {
                syntheticResponse(
                    body = LOGIN_RESPONSE.replace(
                        "https://sts-hlth.io.mi.com/healthapp/sts?sid=miothealth",
                        "https://mi.com.evil.example/healthapp/sts?sid=miothealth",
                    ),
                )
            },
        )
        val client = client(transport)

        assertThrows(MiFitnessAuthenticationException::class.java) {
            kotlinx.coroutines.runBlocking {
                client.exchangePassToken("12345", "synthetic-pass-token", "synthetic-device-id")
            }
        }
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun `steps fetch uses only verified CN daily aggregate endpoint and decrypts response`() = runTest {
        val plaintext = "{\"code\":0,\"result\":{\"data_list\":[],\"has_more\":false,\"next_key\":\"\"}}"
        val session = MiFitnessSession(
            userId = "12345",
            cUserId = "synthetic-c-user",
            serviceToken = "synthetic-service-token",
            ssecurityBase64 = "c3NlY3VyaXR5LXZlY3Rvcg==",
            deviceId = "synthetic-device",
            tokenRefreshed = false,
        )
        val transport = SyntheticInterceptor({ request ->
            val nonce = MiFitnessProtocol.decodeBase64(checkNotNull(request.url.queryParameter("_nonce")), "nonce")
            val security = MiFitnessProtocol.decodeBase64(session.ssecurityBase64, "ssecurity")
            val ciphertext = MiFitnessProtocol.rc4Crypt(
                MiFitnessProtocol.signedNonce(security, nonce),
                plaintext.toByteArray(),
            )
            syntheticResponse(body = MiFitnessProtocol.encodeBase64(ciphertext))
        })
        val client = client(transport)

        val actual = client.fetchSteps(session, 1_743_436_800L, 1_743_523_199L)

        assertEquals(plaintext, actual)
        val request = transport.requests.single()
        assertEquals("GET", request.method)
        assertEquals("hlth.io.mi.com", request.url.host)
        assertEquals("/app/v1/data/get_aggregated_fitness_data_by_time", request.url.encodedPath)
        assertEquals("cn", request.header("region_tag"))
        assertEquals("true", request.header("handleparams"))
        assertTrue(request.header("Cookie").orEmpty().contains("serviceToken=synthetic-service-token"))
        assertTrue(request.url.queryParameterNames.containsAll(setOf("data", "rc4_hash__", "signature", "_nonce")))
    }

    @Test
    fun `read retries bounded rate limit and server failures before succeeding`() = runTest {
        val plaintext = "{\"code\":0,\"result\":{\"data_list\":[],\"has_more\":false,\"next_key\":\"\"}}"
        val session = syntheticSession()
        val transport = SyntheticInterceptor(
            { syntheticResponse(code = 429, headers = Headers.headersOf("Retry-After", "0")) },
            { syntheticResponse(code = 503) },
            { request -> encryptedResponse(request, session, plaintext) },
        )

        val actual = client(transport).fetchSteps(session, 100L, 200L)

        assertEquals(plaintext, actual)
        assertEquals(3, transport.requests.size)
    }

    @Test
    fun `chunked response is rejected before exceeding the body limit`() = runTest {
        val session = MiFitnessSession(
            userId = "12345",
            cUserId = "synthetic-c-user",
            serviceToken = "synthetic-service-token",
            ssecurityBase64 = "c3NlY3VyaXR5LXZlY3Rvcg==",
            deviceId = "synthetic-device",
            tokenRefreshed = false,
        )
        val oversized = "A".repeat(2 * 1024 * 1024 + 1)
        val transport = SyntheticInterceptor({ syntheticResponse(body = oversized) })
        val client = client(transport)

        val exception = assertThrows(MiFitnessProtocolException::class.java) {
            kotlinx.coroutines.runBlocking { client.fetchSteps(session, 100L, 200L) }
        }

        assertEquals("Xiaomi response exceeds the size limit", exception.message)
    }

    private fun client(interceptor: Interceptor): MiFitnessReadOnlyClient =
        MiFitnessReadOnlyClient(OkHttpClient.Builder().addInterceptor(interceptor).build())

    private fun syntheticSession() = MiFitnessSession(
        userId = "12345",
        cUserId = "synthetic-c-user",
        serviceToken = "synthetic-service-token",
        ssecurityBase64 = "c3NlY3VyaXR5LXZlY3Rvcg==",
        deviceId = "synthetic-device",
        tokenRefreshed = false,
    )

    private fun encryptedResponse(
        request: Request,
        session: MiFitnessSession,
        plaintext: String,
    ): SyntheticResponse {
        val nonce = MiFitnessProtocol.decodeBase64(checkNotNull(request.url.queryParameter("_nonce")), "nonce")
        val security = MiFitnessProtocol.decodeBase64(session.ssecurityBase64, "ssecurity")
        val ciphertext = MiFitnessProtocol.rc4Crypt(
            MiFitnessProtocol.signedNonce(security, nonce),
            plaintext.toByteArray(),
        )
        return syntheticResponse(body = MiFitnessProtocol.encodeBase64(ciphertext))
    }

    private class SyntheticInterceptor(
        vararg handlers: (Request) -> SyntheticResponse,
    ) : Interceptor {
        private val handlers = ArrayDeque(handlers.toList())
        val requests = mutableListOf<Request>()

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            requests += request
            val synthetic = checkNotNull(handlers.pollFirst()) { "Unexpected request" }(request)
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(synthetic.code)
                .message("synthetic")
                .headers(synthetic.headers)
                .body(synthetic.body.toResponseBody("text/plain".toMediaType()))
                .build()
        }
    }

    private data class SyntheticResponse(
        val code: Int,
        val body: String,
        val headers: Headers,
    )

    private companion object {
        const val LOGIN_RESPONSE =
            "&&&START&&&{\"userId\":12345,\"cUserId\":\"fake-c-user\",\"passToken\":\"fake-refreshed-token\",\"ssecurity\":\"c3NlY3VyaXR5LXZlY3Rvcg==\",\"nonce\":\"fake-login-nonce\",\"location\":\"https://sts-hlth.io.mi.com/healthapp/sts?sid=miothealth\"}"

        fun syntheticResponse(
            code: Int = 200,
            body: String = "",
            headers: Headers = Headers.headersOf(),
        ) = SyntheticResponse(code, body, headers)
    }
}
