package com.campusai.core.health.mifitness

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

class MiFitnessReadOnlyClient(
    httpClient: OkHttpClient = defaultHttpClient(),
) {
    private val client = httpClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .cookieJar(CookieJar.NO_COOKIES)
        .build()

    suspend fun exchangePassToken(
        userId: String,
        passToken: String,
        deviceId: String? = null,
    ): MiFitnessSession = withContext(Dispatchers.IO) {
        val device = deviceId ?: generateDeviceId()
        val loginUrl = MiFitnessProtocol.LOGIN_URL.toHttpUrl().newBuilder()
            .addQueryParameter("sid", MiFitnessProtocol.SERVICE_SID)
            .addQueryParameter("_json", "true")
            .addQueryParameter("appName", "com.mi.health")
            .addQueryParameter("_locale", "zh_CN")
            .build()
        val loginRequest = Request.Builder()
            .url(loginUrl)
            .header("User-Agent", MiFitnessProtocol.LOGIN_USER_AGENT)
            .header(
                "Cookie",
                MiFitnessProtocol.cookieHeader(
                    linkedMapOf("userId" to userId, "passToken" to passToken, "deviceId" to device),
                ),
            )
            .get()
            .build()
        val loginResponse = executeOnce(loginRequest, "login").also(::requireSuccess)
        val loginCookies = cookies(loginResponse)
        val extensionHeader = listOf(
            "Extension-Pragma",
            "extension-pragma",
            "Extension_Pragama",
            "extension_pragma",
        ).firstNotNullOfOrNull { name -> loginResponse.headers[name]?.takeIf(String::isNotBlank) }
        val login = MiFitnessProtocol.parseServiceLogin(
            body = loginResponse.body,
            fallbackUserId = userId,
            originalPassToken = passToken,
            responseCookies = loginCookies,
            extensionHeader = extensionHeader,
        )

        val serviceToken = exchangeSts(login.stsUrl, login.cUserId, device)
        MiFitnessSession(
            userId = login.userId,
            cUserId = login.cUserId,
            serviceToken = serviceToken,
            ssecurityBase64 = login.ssecurityBase64,
            deviceId = device,
            tokenRefreshed = login.tokenRefreshed,
            refreshedPassToken = login.refreshedPassToken,
        )
    }

    suspend fun fetchSteps(
        session: MiFitnessSession,
        startEpochSeconds: Long,
        endEpochSecondsExclusive: Long,
        nextKey: String = "",
    ): String = fetchDailyAggregate(
        session = session,
        metric = "steps",
        startEpochSeconds = startEpochSeconds,
        endEpochSecondsExclusive = endEpochSecondsExclusive,
        nextKey = nextKey,
    )

    suspend fun fetchDailyAggregate(
        session: MiFitnessSession,
        metric: String,
        startEpochSeconds: Long,
        endEpochSecondsExclusive: Long,
        nextKey: String = "",
    ): String = withContext(Dispatchers.IO) {
        executeRead(
            session,
            MiFitnessProtocol.buildDailyAggregateRequest(
                metric = metric,
                startEpochSeconds = startEpochSeconds,
                endEpochSecondsExclusive = endEpochSecondsExclusive,
                nextKey = nextKey,
            ),
            stage = "daily_aggregate",
        )
    }

    suspend fun fetchStepSeries(
        session: MiFitnessSession,
        startEpochSeconds: Long,
        endEpochSecondsExclusive: Long,
        nextKey: String = "",
    ): String = withContext(Dispatchers.IO) {
        executeRead(
            session,
            MiFitnessProtocol.buildTimeSeriesRequest(
                metric = "steps",
                startEpochSeconds = startEpochSeconds,
                endEpochSecondsExclusive = endEpochSecondsExclusive,
                nextKey = nextKey,
            ),
            stage = "step_series",
        )
    }

    suspend fun fetchSportRecords(
        session: MiFitnessSession,
        startEpochSeconds: Long,
        endEpochSecondsExclusive: Long,
        nextKey: String = "",
    ): String = withContext(Dispatchers.IO) {
        executeRead(
            session,
            MiFitnessProtocol.buildSportRecordsRequest(
                startEpochSeconds = startEpochSeconds,
                endEpochSecondsExclusive = endEpochSecondsExclusive,
                nextKey = nextKey,
            ),
            stage = "sport_records",
        )
    }

    private suspend fun executeRead(
        session: MiFitnessSession,
        readRequest: MiFitnessReadRequest,
        stage: String,
    ): String {
        val form = MiFitnessProtocol.buildEncryptedForm(readRequest, session.ssecurityBase64)
        val url = MiFitnessProtocol.regionBaseUrl("cn").newBuilder()
            .addPathSegments(readRequest.path.removePrefix("/"))
            .apply { form.parameters.forEach { (name, value) -> addQueryParameter(name, value) } }
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", MiFitnessProtocol.API_USER_AGENT)
            .header("region_tag", "cn")
            .header("handleparams", "true")
            .header(
                "Cookie",
                MiFitnessProtocol.cookieHeader(
                    linkedMapOf(
                        "cUserId" to session.cUserId,
                        "serviceToken" to session.serviceToken,
                        "locale" to "zh_cn",
                    ),
                ),
            )
            .get()
            .build()
        val response = executeReadWithRetry(request, stage)
        return MiFitnessProtocol.decryptResponse(
            ciphertextBase64 = response.body,
            ssecurityBase64 = session.ssecurityBase64,
            nonceBase64 = form.nonceBase64,
        )
    }

    private suspend fun executeReadWithRetry(request: Request, stage: String): HttpResponse {
        var retry = 0
        while (true) {
            val response = try {
                executeOnce(request, stage)
            } catch (network: MiFitnessNetworkException) {
                if (retry >= MAX_TRANSIENT_READ_RETRIES) throw network
                delay(transientBackoffMillis(retry++))
                continue
            }
            try {
                requireSuccess(response)
                return response
            } catch (rateLimit: MiFitnessRateLimitException) {
                if (retry >= MAX_TRANSIENT_READ_RETRIES) throw rateLimit
                val delayMillis = rateLimit.retryAfterMillis
                    ?.coerceIn(MIN_RETRY_DELAY_MILLIS, MAX_RETRY_DELAY_MILLIS)
                    ?: transientBackoffMillis(retry)
                retry += 1
                delay(delayMillis)
            } catch (server: MiFitnessServerException) {
                if (retry >= MAX_TRANSIENT_READ_RETRIES) throw server
                delay(transientBackoffMillis(retry++))
            }
        }
    }

    private suspend fun exchangeSts(initialUrl: HttpUrl, cUserId: String, deviceId: String): String {
        var url = initialUrl
        var sendIdentityCookie = true
        repeat(MAX_STS_REQUESTS) {
            MiFitnessProtocol.requireXiaomiHttps(url)
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", MiFitnessProtocol.LOGIN_USER_AGENT)
                .apply {
                    if (sendIdentityCookie) {
                        header(
                            "Cookie",
                            MiFitnessProtocol.cookieHeader(
                                linkedMapOf("cUserId" to cUserId, "deviceId" to deviceId),
                            ),
                        )
                    }
                }
                .get()
                .build()
            val response = executeOnce(request, "sts")
            serviceToken(response.url, response.headers)?.let { return it }
            cookies(response)["miothealth_serviceToken"]?.let { return it }
            cookies(response)["serviceToken"]?.let { return it }

            if (response.code in 300..399) {
                val target = response.headers["Location"]
                    ?.let(response.url::resolve)
                    ?: throw MiFitnessAuthenticationException("Xiaomi STS redirect is missing a valid location")
                MiFitnessProtocol.requireXiaomiHttps(target)
                serviceToken(target, Headers.headersOf())?.let { return it }
                url = target
                sendIdentityCookie = false
            } else {
                requireSuccess(response)
                throw MiFitnessAuthenticationException("Xiaomi login did not return a service token")
            }
        }
        throw MiFitnessAuthenticationException("Xiaomi STS exceeded the redirect limit")
    }

    private suspend fun executeOnce(
        request: Request,
        stage: String,
    ): HttpResponse = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.i(LOG_TAG, "stage=$stage transport=${e.javaClass.simpleName}")
                if (continuation.isActive) {
                    continuation.resumeWith(
                        Result.failure(MiFitnessNetworkException("Xiaomi network request failed")),
                    )
                }
            }

            override fun onResponse(call: Call, response: okhttp3.Response) {
                Log.i(LOG_TAG, "stage=$stage http=${response.code}")
                val result = try {
                    response.use {
                        Result.success(
                            HttpResponse(
                                code = it.code,
                                body = readBoundedBody(it.body),
                                url = it.request.url,
                                headers = it.headers,
                            ),
                        )
                    }
                } catch (_: IOException) {
                    Log.i(LOG_TAG, "stage=$stage body=read_failed")
                    Result.failure(MiFitnessNetworkException("Xiaomi network request failed"))
                } catch (error: Exception) {
                    Result.failure(error)
                }
                if (continuation.isActive) continuation.resumeWith(result)
            }
        })
    }

    private fun requireSuccess(response: HttpResponse) {
        if (response.code in 200..299) return
        if (response.code == 401 || response.code == 403) {
            throw MiFitnessAuthenticationException("Xiaomi authentication failed (HTTP ${response.code})")
        }
        if (response.code == 429) {
            val retryAfterMillis = response.headers["Retry-After"]
                ?.trim()
                ?.toLongOrNull()
                ?.takeIf { it >= 0L }
                ?.let { seconds ->
                    seconds.coerceAtMost(MAX_RETRY_DELAY_MILLIS / 1_000L) * 1_000L
                }
            throw MiFitnessRateLimitException(retryAfterMillis)
        }
        if (response.code in 500..599) throw MiFitnessServerException()
        throw MiFitnessNetworkException("Xiaomi request failed (HTTP ${response.code})")
    }

    private fun transientBackoffMillis(retry: Int): Long =
        (BASE_RETRY_DELAY_MILLIS * (1L shl retry.coerceIn(0, 4))).coerceAtMost(MAX_RETRY_DELAY_MILLIS)

    private fun cookies(response: HttpResponse): Map<String, String> =
        Cookie.parseAll(response.url, response.headers).associate { cookie -> cookie.name to cookie.value }

    private fun serviceToken(url: HttpUrl, headers: Headers): String? {
        val fromUrl = url.queryParameter("miothealth_serviceToken")
            ?: url.queryParameter("serviceToken")
        if (!fromUrl.isNullOrEmpty()) return fromUrl
        return Cookie.parseAll(url, headers)
            .firstOrNull { it.name == "miothealth_serviceToken" || it.name == "serviceToken" }
            ?.value
            ?.takeIf(String::isNotEmpty)
    }

    private fun readBoundedBody(body: okhttp3.ResponseBody?): String {
        if (body == null) return ""
        if (body.contentLength() > MAX_HTTP_RESPONSE_BYTES) {
            throw MiFitnessProtocolException("Xiaomi response exceeds the size limit")
        }
        val source = body.source()
        if (source.request(MAX_HTTP_RESPONSE_BYTES + 1L)) {
            throw MiFitnessProtocolException("Xiaomi response exceeds the size limit")
        }
        return source.readUtf8()
    }

    private data class HttpResponse(
        val code: Int,
        val body: String,
        val url: HttpUrl,
        val headers: Headers,
    )

    companion object {
        private const val MAX_STS_REQUESTS = 5
        private const val MAX_TRANSIENT_READ_RETRIES = 2
        private const val BASE_RETRY_DELAY_MILLIS = 250L
        private const val MIN_RETRY_DELAY_MILLIS = 100L
        private const val MAX_RETRY_DELAY_MILLIS = 5_000L
        private const val LOG_TAG = "MiFitnessHttp"
        private const val MAX_HTTP_RESPONSE_BYTES = 2 * 1024 * 1024L
        private val random = SecureRandom()

        internal fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .cookieJar(CookieJar.NO_COOKIES)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(35, TimeUnit.SECONDS)
            .build()

        private fun generateDeviceId(): String {
            val bytes = ByteArray(16).also(random::nextBytes)
            return "an_" + bytes.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        }
    }
}
