package com.campusai.core.health.mifitness

import android.util.Base64
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONException
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale

open class MiFitnessException(message: String) : IllegalStateException(message)

class MiFitnessAuthenticationException(message: String) : MiFitnessException(message)

class MiFitnessProtocolException(message: String) : MiFitnessException(message)

open class MiFitnessNetworkException(message: String) : MiFitnessException(message)

class MiFitnessRateLimitException(
    val retryAfterMillis: Long?,
) : MiFitnessNetworkException("Xiaomi request was rate limited")

class MiFitnessServerException : MiFitnessNetworkException("Xiaomi service is temporarily unavailable")

class MiFitnessSession(
    val userId: String,
    val cUserId: String,
    val serviceToken: String,
    val ssecurityBase64: String,
    val deviceId: String,
    val tokenRefreshed: Boolean,
    internal val refreshedPassToken: String? = null,
) {
    override fun toString(): String = "MiFitnessSession(<redacted>)"
}

class MiFitnessReadRequest internal constructor(
    val method: String,
    val path: String,
    val payloadJson: String,
)

class MiFitnessEncryptedForm internal constructor(
    val parameters: Map<String, String>,
    val nonceBase64: String,
) {
    override fun toString(): String = "MiFitnessEncryptedForm(<redacted>)"
}

internal class MiFitnessServiceLogin(
    val userId: String,
    val cUserId: String,
    val ssecurityBase64: String,
    val stsUrl: HttpUrl,
    val tokenRefreshed: Boolean,
    val refreshedPassToken: String?,
) {
    override fun toString(): String = "MiFitnessServiceLogin(<redacted>)"
}

object MiFitnessProtocol {
    internal const val LOGIN_PREFIX = "&&&START&&&"
    internal const val LOGIN_URL = "https://account.xiaomi.com/pass/serviceLogin"
    internal const val SERVICE_SID = "miothealth"
    internal const val FITNESS_BY_TIME_PATH = "/app/v1/data/get_fitness_data_by_time"
    internal const val AGGREGATE_FITNESS_PATH = "/app/v1/data/get_aggregated_fitness_data_by_time"
    internal const val SPORT_RECORDS_PATH = "/app/v1/data/get_sport_records_by_time"
    internal const val LOGIN_USER_AGENT =
        "Dalvik/2.1.0 (Linux; U; Android 16) APP/mi.health APPV/358000 CPN/com.mi.health PassportSDK/"
    internal const val API_USER_AGENT = "Android-16-3.58.0-CampusAI-ReadOnly-PoC"

    private const val CN_REGION = "cn"
    private const val STEPS_METRIC = "steps"
    private const val RC4_DROP_BYTES = 1024
    private const val MAX_SMALL_DECODED_BYTES = 4 * 1024
    private const val MAX_DECRYPTED_RESPONSE_BYTES = 2 * 1024 * 1024
    private const val MAX_PASS_TOKEN_CHARS = 8_192
    private val xiaomiHostSuffixes = setOf("xiaomi.com", "mi.com")
    private val secureRandom = SecureRandom()

    fun regionBaseUrl(region: String): HttpUrl {
        val normalized = region.trim().lowercase(Locale.ROOT)
        if (normalized != CN_REGION) {
            throw IllegalArgumentException("Unsupported Mi Fitness region")
        }
        return checkNotNull("https://hlth.io.mi.com".toHttpUrlOrNull())
    }

    fun buildTimeSeriesRequest(
        metric: String,
        startEpochSeconds: Long,
        endEpochSecondsExclusive: Long,
        nextKey: String = "",
    ): MiFitnessReadRequest {
        require(metric == STEPS_METRIC) { "Unsupported read-only Mi Fitness metric" }
        require(startEpochSeconds < endEpochSecondsExclusive) { "Mi Fitness time range must be non-empty" }
        require(nextKey.length <= MAX_NEXT_KEY_CHARS) { "Mi Fitness pagination key is too long" }
        val payload = buildString {
            append("{\"key\":\"steps\",\"start_time\":")
            append(startEpochSeconds)
            append(",\"end_time\":")
            append(endEpochSecondsExclusive)
            append(",\"reverse\":true,\"next_key\":")
            append(quoteJson(nextKey))
            append('}')
        }
        return MiFitnessReadRequest("GET", FITNESS_BY_TIME_PATH, payload)
    }

    fun buildDailyAggregateRequest(
        metric: String,
        startEpochSeconds: Long,
        endEpochSecondsExclusive: Long,
        nextKey: String = "",
    ): MiFitnessReadRequest {
        MiFitnessMetricRegistry.definition(metric)
        require(startEpochSeconds < endEpochSecondsExclusive) { "Mi Fitness day range must be non-empty" }
        require(nextKey.length <= MAX_NEXT_KEY_CHARS) { "Mi Fitness pagination key is too long" }
        val payload = buildString {
            append("{\"tag\":\"daily_report\",\"key\":")
            append(quoteJson(metric))
            append(",\"reverse\":true,\"limit\":100,\"next_key\":")
            append(quoteJson(nextKey))
            append(",\"start_time\":")
            append(startEpochSeconds)
            append(",\"end_time\":")
            append(endEpochSecondsExclusive)
            append('}')
        }
        return MiFitnessReadRequest("GET", AGGREGATE_FITNESS_PATH, payload)
    }

    fun buildSportRecordsRequest(
        startEpochSeconds: Long,
        endEpochSecondsExclusive: Long,
        nextKey: String = "",
    ): MiFitnessReadRequest {
        require(startEpochSeconds < endEpochSecondsExclusive) { "Mi Fitness day range must be non-empty" }
        require(nextKey.length <= MAX_NEXT_KEY_CHARS) { "Mi Fitness pagination key is too long" }
        val payload = buildString {
            append("{\"category\":\"\",\"start_time\":")
            append(startEpochSeconds)
            append(",\"end_time\":")
            append(endEpochSecondsExclusive)
            append(",\"reverse\":true,\"next_key\":")
            append(quoteJson(nextKey))
            append(",\"limit\":50}")
        }
        return MiFitnessReadRequest("GET", SPORT_RECORDS_PATH, payload)
    }

    fun buildEncryptedForm(
        request: MiFitnessReadRequest,
        ssecurityBase64: String,
        nonce: ByteArray = generateNonce(),
    ): MiFitnessEncryptedForm {
        require(request.method == "GET" && request.path in READ_ONLY_PATHS) {
            "Only the allowlisted read-only GET endpoint is permitted"
        }
        val security = decodeBase64(ssecurityBase64, "ssecurity")
        val key = signedNonce(security, nonce)
        val plaintext = sortedMapOf("data" to request.payloadJson)
        plaintext["rc4_hash__"] = signature(request.method, request.path, plaintext, key)

        val encodedValues = plaintext.mapValues { (_, value) -> value.toByteArray(Charsets.UTF_8) }
        val joined = encodedValues.values.fold(ByteArray(0)) { accumulated, bytes -> accumulated + bytes }
        val encryptedStream = rc4Crypt(key, joined)
        var offset = 0
        val encrypted = linkedMapOf<String, String>()
        encodedValues.forEach { (name, bytes) ->
            encrypted[name] = encodeBase64(encryptedStream.copyOfRange(offset, offset + bytes.size))
            offset += bytes.size
        }
        encrypted["signature"] = signature(request.method, request.path, encrypted, key)
        val nonceBase64 = encodeBase64(nonce)
        encrypted["_nonce"] = nonceBase64
        return MiFitnessEncryptedForm(encrypted.toMap(), nonceBase64)
    }

    fun decryptResponse(
        ciphertextBase64: String,
        ssecurityBase64: String,
        nonceBase64: String,
    ): String {
        val security = decodeBase64(ssecurityBase64, "ssecurity")
        val nonce = decodeBase64(nonceBase64, "nonce")
        if (ciphertextBase64.length > maxBase64Length(MAX_DECRYPTED_RESPONSE_BYTES)) {
            throw MiFitnessProtocolException("Encrypted response exceeds the size limit")
        }
        val ciphertext = decodeBase64(
            ciphertextBase64.trim(),
            "response",
            MAX_DECRYPTED_RESPONSE_BYTES,
        )
        val plaintextBytes = rc4Crypt(signedNonce(security, nonce), ciphertext)
        if (plaintextBytes.size > MAX_DECRYPTED_RESPONSE_BYTES) {
            throw MiFitnessProtocolException("Decrypted response exceeds the size limit")
        }
        val plaintext = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(plaintextBytes))
                .toString()
        } catch (_: Exception) {
            throw MiFitnessProtocolException("Encrypted response could not be decoded")
        }
        try {
            JSONObject(plaintext)
        } catch (_: JSONException) {
            throw MiFitnessProtocolException("Encrypted response must contain a JSON object")
        }
        return plaintext
    }

    internal fun parseServiceLogin(
        body: String,
        fallbackUserId: String,
        originalPassToken: String,
        responseCookies: Map<String, String>,
        extensionHeader: String?,
    ): MiFitnessServiceLogin {
        if (!body.startsWith(LOGIN_PREFIX)) {
            throw MiFitnessProtocolException("Unexpected Xiaomi login response prefix")
        }
        val payload = parseJsonObject(body.removePrefix(LOGIN_PREFIX), "Malformed Xiaomi login response")
        val extension = extensionHeader
            ?.takeIf(String::isNotBlank)
            ?.let { parseJsonObject(it, "Malformed Xiaomi login security header") }

        val returnedUserId = payload.stringValue("userId")
        if (returnedUserId != null && returnedUserId != fallbackUserId) {
            throw MiFitnessAuthenticationException("Xiaomi login returned an unexpected account")
        }
        val userId = fallbackUserId
        val cUserId = payload.stringValue("cUserId") ?: responseCookies["cUserId"]
            ?: throw MiFitnessAuthenticationException("Xiaomi login did not return an encrypted user ID")
        val ssecurity = payload.stringValue("ssecurity") ?: extension?.stringValue("ssecurity")
            ?: throw MiFitnessAuthenticationException("Xiaomi login response is missing session security")
        decodeBase64(ssecurity, "ssecurity")
        val loginNonce = payload.stringValue("nonce") ?: extension?.stringValue("nonce")
            ?: throw MiFitnessAuthenticationException("Xiaomi login response is missing the STS nonce")
        val location = payload.stringValue("location")
            ?.toHttpUrlOrNull()
            ?: throw MiFitnessAuthenticationException("Xiaomi login response is missing a valid STS location")
        requireXiaomiHttps(location)

        val refreshedPassToken = payload.stringValue("passToken")
            ?: responseCookies["passToken"]
            ?: originalPassToken
        if (refreshedPassToken.length > MAX_PASS_TOKEN_CHARS) {
            throw MiFitnessAuthenticationException("Xiaomi returned an invalid refreshed credential")
        }
        cookieHeader(mapOf("passToken" to refreshedPassToken))
        val stsUrl = location.newBuilder()
            .removeAllQueryParameters("clientSign")
            .removeAllQueryParameters("_userIdNeedEncrypt")
            .addQueryParameter("clientSign", buildClientSign(loginNonce, ssecurity))
            .addQueryParameter("_userIdNeedEncrypt", "true")
            .build()
        return MiFitnessServiceLogin(
            userId = userId,
            cUserId = cUserId,
            ssecurityBase64 = ssecurity,
            stsUrl = stsUrl,
            tokenRefreshed = refreshedPassToken != originalPassToken,
            refreshedPassToken = refreshedPassToken.takeIf { it != originalPassToken },
        )
    }

    internal fun buildClientSign(loginNonce: String, ssecurityBase64: String): String {
        val message = "nonce=$loginNonce&$ssecurityBase64"
        return encodeBase64(digest("SHA-1", message.toByteArray(Charsets.UTF_8)))
    }

    internal fun requireXiaomiHttps(url: HttpUrl) {
        val allowedHost = xiaomiHostSuffixes.any { suffix ->
            url.host == suffix || url.host.endsWith(".$suffix")
        }
        val hasUserInfo = url.username.isNotEmpty() || url.password.isNotEmpty()
        if (url.scheme != "https" || url.port != 443 || hasUserInfo || !allowedHost) {
            throw MiFitnessAuthenticationException("Xiaomi returned an unexpected redirect host")
        }
    }

    internal fun cookieHeader(values: Map<String, String>): String = values.entries.joinToString("; ") { (name, value) ->
        if (value.isEmpty()) throw MiFitnessAuthenticationException("$name is required")
        if (!value.all(::isCookieOctet)) {
            throw MiFitnessAuthenticationException("$name contains invalid cookie characters")
        }
        "$name=$value"
    }

    internal fun rc4Crypt(key: ByteArray, payload: ByteArray, drop: Int = RC4_DROP_BYTES): ByteArray {
        require(key.isNotEmpty()) { "RC4 key must not be empty" }
        require(drop >= 0) { "RC4 drop must not be negative" }
        val state = IntArray(256) { it }
        var stateIndex = 0
        for (index in state.indices) {
            stateIndex = (stateIndex + state[index] + (key[index % key.size].toInt() and 0xff)) and 0xff
            val temporary = state[index]
            state[index] = state[stateIndex]
            state[stateIndex] = temporary
        }

        var index = 0
        stateIndex = 0
        fun nextByte(): Int {
            index = (index + 1) and 0xff
            stateIndex = (stateIndex + state[index]) and 0xff
            val temporary = state[index]
            state[index] = state[stateIndex]
            state[stateIndex] = temporary
            return state[(state[index] + state[stateIndex]) and 0xff]
        }

        repeat(drop) { nextByte() }
        return ByteArray(payload.size) { position ->
            (payload[position].toInt() xor nextByte()).toByte()
        }
    }

    internal fun signedNonce(ssecurity: ByteArray, nonce: ByteArray): ByteArray =
        digest("SHA-256", ssecurity + nonce)

    internal fun decodeBase64(
        value: String,
        field: String,
        maxDecodedBytes: Int = MAX_SMALL_DECODED_BYTES,
    ): ByteArray {
        if (value.length > maxBase64Length(maxDecodedBytes)) {
            throw MiFitnessProtocolException("$field exceeds the size limit")
        }
        if (!isValidStandardBase64(value)) {
            throw MiFitnessProtocolException("$field is not valid base64")
        }
        val decoded = try {
            Base64.decode(value, Base64.NO_WRAP)
        } catch (_: IllegalArgumentException) {
            throw MiFitnessProtocolException("$field is not valid base64")
        }
        if (decoded.isEmpty()) throw MiFitnessProtocolException("$field is empty")
        if (decoded.size > maxDecodedBytes) throw MiFitnessProtocolException("$field exceeds the size limit")
        return decoded
    }

    internal fun encodeBase64(value: ByteArray): String = Base64.encodeToString(value, Base64.NO_WRAP)

    private fun generateNonce(): ByteArray {
        val nonce = ByteArray(12)
        val random = ByteArray(8).also(secureRandom::nextBytes)
        random.copyInto(nonce)
        val minute = System.currentTimeMillis() / 60_000L
        ByteBuffer.wrap(nonce, 8, 4).putInt(minute.toInt())
        return nonce
    }

    private fun signature(
        method: String,
        path: String,
        values: Map<String, String>,
        signedNonce: ByteArray,
    ): String {
        val normalizedPath = if (path.startsWith('/')) path else "/$path"
        val message = buildList {
            add(method.uppercase(Locale.ROOT))
            add(normalizedPath)
            values.toSortedMap().forEach { (name, value) -> add("$name=$value") }
            add(encodeBase64(signedNonce))
        }.joinToString("&")
        return encodeBase64(digest("SHA-1", message.toByteArray(Charsets.UTF_8)))
    }

    private fun digest(algorithm: String, value: ByteArray): ByteArray =
        MessageDigest.getInstance(algorithm).digest(value)

    private fun parseJsonObject(value: String, message: String): JSONObject = try {
        JSONObject(value)
    } catch (_: JSONException) {
        throw MiFitnessProtocolException(message)
    }

    private fun JSONObject.stringValue(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return get(name).toString().takeIf(String::isNotEmpty)
    }

    private fun quoteJson(value: String): String = buildString {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000c' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) {
                    append("\\u")
                    append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
        append('"')
    }

    private fun isCookieOctet(character: Char): Boolean = when (character.code) {
        0x21 -> true
        in 0x23..0x2b -> true
        in 0x2d..0x3a -> true
        in 0x3c..0x5b -> true
        in 0x5d..0x7e -> true
        else -> false
    }

    private fun isValidStandardBase64(value: String): Boolean {
        val firstPadding = value.indexOf('=')
        val dataLength = if (firstPadding >= 0) firstPadding else value.length
        if (!value.take(dataLength).all { it.isAsciiBase64Character() }) return false

        val paddingLength = value.length - dataLength
        if (paddingLength > 2 || value.drop(dataLength).any { it != '=' }) return false
        return if (paddingLength == 0) {
            dataLength % 4 != 1
        } else {
            value.length % 4 == 0 && dataLength % 4 == 4 - paddingLength
        }
    }

    private fun Char.isAsciiBase64Character(): Boolean =
        this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9' || this == '+' || this == '/'

    private fun maxBase64Length(decodedBytes: Int): Int = ((decodedBytes + 2) / 3) * 4

    private const val MAX_NEXT_KEY_CHARS = 4_096
    private val READ_ONLY_PATHS = setOf(
        FITNESS_BY_TIME_PATH,
        AGGREGATE_FITNESS_PATH,
        SPORT_RECORDS_PATH,
    )
}
