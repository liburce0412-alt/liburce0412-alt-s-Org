package com.campusai.core.network

import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory

/** A small, injectable boundary for Agent web search. It never downloads result pages. */
interface WebSearchGateway {
    suspend fun search(query: String, maxResults: Int = DEFAULT_WEB_SEARCH_RESULTS): WebSearchResponse
}

data class WebSearchResponse(
    val query: String,
    val results: List<WebSearchResult>,
)

data class WebSearchResult(
    val title: String,
    val url: String,
    val snippet: String,
    val sourceHost: String,
    val publishedAt: String? = null,
)

/**
 * Key-free search for the private app through Bing's public RSS surface.
 *
 * Only the normalized search phrase is sent to Bing's fixed HTTPS origins. Bing can move Chinese
 * clients from www.bing.com to cn.bing.com, so that one regional redirect is handled explicitly;
 * arbitrary redirects remain disabled. Responses are held in memory for the current tool call,
 * are size bounded, and are not cached or logged here.
 */
class BingRssWebSearchGateway(
    private val client: OkHttpClient = defaultWebSearchHttpClient(),
) : WebSearchGateway {
    override suspend fun search(query: String, maxResults: Int): WebSearchResponse = withContext(Dispatchers.IO) {
        val normalizedQuery = normalizeSearchQuery(query)
        val resultLimit = maxResults.coerceIn(1, MAX_WEB_SEARCH_RESULTS)
        val url = BING_RSS_ENDPOINT.newBuilder()
            .addQueryParameter("q", normalizedQuery)
            .addQueryParameter("format", "rss")
            .addQueryParameter("setlang", "zh-Hans")
            .build()
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/rss+xml, application/xml;q=0.9, text/xml;q=0.8")
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .header("Cache-Control", "no-store")
            .build()

        try {
            executeAllowedBingRedirects(client, request).use { response ->
                requireSuccessfulSearch(response)
                val body = response.body
                    ?: throw WebSearchException(
                        code = "search_response_invalid",
                        message = "联网搜索没有返回可解析的数据，请稍后重试。",
                    )
                requireXmlContentType(body)
                val bytes = readBoundedSearchBody(body)
                return@withContext WebSearchResponse(
                    query = normalizedQuery,
                    results = BingRssSearchParser.parse(bytes, resultLimit),
                )
            }
        } catch (failure: WebSearchException) {
            throw failure
        } catch (failure: IOException) {
            currentCoroutineContext().ensureActive()
            throw WebSearchException(
                code = "search_unavailable",
                message = "联网搜索暂时不可用，请检查网络后重试。",
            )
        }
    }
}

private suspend fun executeAllowedBingRedirects(client: OkHttpClient, initialRequest: Request): Response {
    var request = initialRequest
    repeat(MAX_WEB_SEARCH_REDIRECTS + 1) { redirectCount ->
        val response = client.newCall(request).awaitWebSearchResponse()
        if (response.code !in 300..399) return response

        val redirectedUrl = response.header("Location")
            ?.let(response.request.url::resolve)
            ?.takeIf { url ->
                url.scheme == "https" &&
                    url.port == 443 &&
                    url.encodedPath == BING_RSS_PATH &&
                    url.username.isEmpty() &&
                    url.password.isEmpty() &&
                    url.host.lowercase(Locale.ROOT) in BING_RSS_ALLOWED_HOSTS
            }
        response.close()
        if (redirectedUrl == null || redirectCount == MAX_WEB_SEARCH_REDIRECTS) {
            throw WebSearchException(
                code = "search_redirect_rejected",
                message = "联网搜索返回了不安全的跳转地址，已安全停止。",
            )
        }
        request = request.newBuilder().url(redirectedUrl).get().build()
    }
    throw WebSearchException(
        code = "search_redirect_rejected",
        message = "联网搜索跳转次数过多，已安全停止。",
    )
}

class WebSearchException(
    val code: String,
    override val message: String,
    val recoverable: Boolean = true,
) : IllegalStateException(message)

internal fun defaultWebSearchHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(WEB_SEARCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    .readTimeout(WEB_SEARCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    .writeTimeout(WEB_SEARCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    .callTimeout(WEB_SEARCH_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    .followRedirects(false)
    .followSslRedirects(false)
    .retryOnConnectionFailure(false)
    .cache(null)
    .build()

internal fun normalizeSearchQuery(raw: String): String {
    val normalized = raw
        .filterNot { it.isISOControl() && !it.isWhitespace() }
        .trim()
        .replace(SEARCH_WHITESPACE, " ")
    if (normalized.isEmpty()) {
        throw WebSearchException(
            code = "search_query_empty",
            message = "请输入需要联网搜索的关键词。",
            recoverable = false,
        )
    }
    if (normalized.length > MAX_WEB_SEARCH_QUERY_CHARS) {
        throw WebSearchException(
            code = "search_query_too_long",
            message = "联网搜索关键词过长，请缩短到 $MAX_WEB_SEARCH_QUERY_CHARS 个字符以内。",
            recoverable = false,
        )
    }
    return normalized
}

private fun requireSuccessfulSearch(response: Response) {
    if (response.isSuccessful) return
    if (response.code == 429) {
        throw WebSearchException(
            code = "search_rate_limited",
            message = "联网搜索请求较多，请稍后再试。",
        )
    }
    if (response.code in 400..499) {
        throw WebSearchException(
            code = "search_request_rejected",
            message = "联网搜索请求未被接受，请换个关键词重试。",
        )
    }
    throw WebSearchException(
        code = "search_unavailable",
        message = "联网搜索服务暂时不可用，请稍后重试。",
    )
}

private fun requireXmlContentType(body: ResponseBody) {
    val mediaType = body.contentType()
    val accepted = mediaType != null && (
        mediaType.subtype.equals("xml", ignoreCase = true) ||
            mediaType.subtype.endsWith("+xml", ignoreCase = true)
        )
    if (!accepted) {
        throw WebSearchException(
            code = "search_response_invalid",
            message = "联网搜索返回了无法解析的数据，请稍后重试。",
        )
    }
}

private fun readBoundedSearchBody(body: ResponseBody): ByteArray {
    val declaredLength = body.contentLength()
    if (declaredLength > MAX_WEB_SEARCH_BODY_BYTES) throw searchResponseTooLarge()
    val source = body.source()
    if (source.request(MAX_WEB_SEARCH_BODY_BYTES + 1L)) throw searchResponseTooLarge()
    val bytes = source.readByteArray()
    if (bytes.isEmpty()) {
        throw WebSearchException(
            code = "search_response_invalid",
            message = "联网搜索没有返回可解析的数据，请稍后重试。",
        )
    }
    return bytes
}

private fun searchResponseTooLarge() = WebSearchException(
    code = "search_response_too_large",
    message = "联网搜索返回的数据过大，已安全停止。",
)

private suspend fun Call.awaitWebSearchResponse(): Response {
    val responseRef = AtomicReference<Response?>(null)
    val deferred = CompletableDeferred<Response>()
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            deferred.completeExceptionally(e)
        }

        override fun onResponse(call: Call, response: Response) {
            responseRef.set(response)
            if (!deferred.complete(response)) {
                responseRef.compareAndSet(response, null)
                response.close()
            }
        }
    })
    return try {
        deferred.await().also { responseRef.compareAndSet(it, null) }
    } catch (cancelled: CancellationException) {
        deferred.cancel(cancelled)
        responseRef.getAndSet(null)?.close()
        cancel()
        throw cancelled
    }
}

internal object BingRssSearchParser {
    fun parse(bytes: ByteArray, limit: Int): List<WebSearchResult> {
        val safeLimit = limit.coerceIn(1, MAX_WEB_SEARCH_RESULTS)
        rejectUnsafeXml(bytes)
        val parser = try {
            XmlPullParserFactory.newInstance().newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                setFeature(PROCESS_DOCDECL_FEATURE, false)
                setInput(ByteArrayInputStream(bytes), null)
            }
        } catch (_: XmlPullParserException) {
            throw invalidSearchResponse()
        }

        val results = mutableListOf<WebSearchResult>()
        val seenUrls = mutableSetOf<String>()
        var inItem = false
        var itemDepth = -1
        var title = ""
        var link = ""
        var description = ""
        var publishedAt = ""
        try {
            while (parser.eventType != XmlPullParser.END_DOCUMENT && results.size < safeLimit) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> {
                        val name = parser.name.substringAfter(':').lowercase(Locale.ROOT)
                        if (name == "item") {
                            inItem = true
                            itemDepth = parser.depth
                            title = ""
                            link = ""
                            description = ""
                            publishedAt = ""
                        } else if (inItem && parser.depth == itemDepth + 1) {
                            when (name) {
                                "title" -> title = readCurrentElementText(parser)
                                "link" -> link = readCurrentElementText(parser)
                                "description" -> description = readCurrentElementText(parser)
                                "pubdate" -> publishedAt = readCurrentElementText(parser)
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        if (inItem && parser.depth == itemDepth && parser.name.substringAfter(':').equals("item", true)) {
                            buildSearchResult(title, link, description, publishedAt)?.let { result ->
                                if (seenUrls.add(result.url)) results += result
                            }
                            inItem = false
                            itemDepth = -1
                        }
                    }
                }
                parser.next()
            }
        } catch (_: XmlPullParserException) {
            throw invalidSearchResponse()
        } catch (_: IOException) {
            throw invalidSearchResponse()
        }
        return results
    }

    private fun readCurrentElementText(parser: XmlPullParser): String {
        val elementDepth = parser.depth
        val value = StringBuilder()
        while (true) {
            when (parser.next()) {
                XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF -> value.append(parser.text.orEmpty())
                XmlPullParser.END_TAG -> if (parser.depth == elementDepth) return value.toString()
                XmlPullParser.END_DOCUMENT -> return value.toString()
            }
        }
    }

    private fun buildSearchResult(
        rawTitle: String,
        rawUrl: String,
        rawDescription: String,
        rawPublishedAt: String,
    ): WebSearchResult? {
        val url = sanitizeResultUrl(rawUrl) ?: return null
        val title = sanitizeSearchText(rawTitle, MAX_WEB_SEARCH_TITLE_CHARS)
        if (title.isEmpty()) return null
        return WebSearchResult(
            title = title,
            url = url.toString(),
            snippet = sanitizeSearchText(rawDescription, MAX_WEB_SEARCH_SNIPPET_CHARS),
            sourceHost = url.host.removePrefix("www."),
            publishedAt = sanitizeSearchText(rawPublishedAt, MAX_WEB_SEARCH_DATE_CHARS).ifEmpty { null },
        )
    }

    private fun rejectUnsafeXml(bytes: ByteArray) {
        if (bytes.any { it == 0.toByte() }) throw invalidSearchResponse()
        val ascii = bytes.toString(Charsets.ISO_8859_1)
        if (ascii.contains("<!doctype", ignoreCase = true) || ascii.contains("<!entity", ignoreCase = true)) {
            throw invalidSearchResponse()
        }
    }
}

private fun sanitizeResultUrl(raw: String) = raw.trim().toHttpUrlOrNull()
    ?.takeIf { url ->
        url.scheme == "https" && url.username.isEmpty() && url.password.isEmpty()
    }
    ?.newBuilder()
    ?.fragment(null)
    ?.apply {
        val trackingNames = build().queryParameterNames.filter { name ->
            name.lowercase(Locale.ROOT) in TRACKING_QUERY_NAMES
        }
        trackingNames.forEach(::removeAllQueryParameters)
    }
    ?.build()

private fun sanitizeSearchText(raw: String, maxChars: Int): String {
    val cleaned = raw
        .replace(HTML_TAG, " ")
        .replace("&nbsp;", " ", ignoreCase = true)
        .replace("&amp;", "&", ignoreCase = true)
        .replace("&lt;", "<", ignoreCase = true)
        .replace("&gt;", ">", ignoreCase = true)
        .replace("&quot;", "\"", ignoreCase = true)
        .replace("&#39;", "'", ignoreCase = true)
        .replace(BIDI_CONTROL, "")
        .filterNot { it.isISOControl() && !it.isWhitespace() }
        .replace(SEARCH_WHITESPACE, " ")
        .trim()
    if (cleaned.length <= maxChars) return cleaned
    return cleaned.substring(0, maxChars - 1).trimEnd() + "…"
}

private fun invalidSearchResponse() = WebSearchException(
    code = "search_response_invalid",
    message = "联网搜索返回了无法解析的数据，请稍后重试。",
)

const val DEFAULT_WEB_SEARCH_RESULTS = 5
const val MAX_WEB_SEARCH_RESULTS = 8
const val MAX_WEB_SEARCH_QUERY_CHARS = 200
internal const val MAX_WEB_SEARCH_BODY_BYTES = 512L * 1024L

private const val MAX_WEB_SEARCH_TITLE_CHARS = 240
private const val MAX_WEB_SEARCH_SNIPPET_CHARS = 1_000
private const val MAX_WEB_SEARCH_DATE_CHARS = 100
private const val WEB_SEARCH_TIMEOUT_SECONDS = 25L
private const val WEB_SEARCH_CALL_TIMEOUT_SECONDS = 30L
private const val MAX_WEB_SEARCH_REDIRECTS = 1
private const val PROCESS_DOCDECL_FEATURE = "http://xmlpull.org/v1/doc/features.html#process-docdecl"
private const val BING_RSS_PATH = "/search"
private val BING_RSS_ENDPOINT = "https://www.bing.com/search".toHttpUrl()
private val BING_RSS_ALLOWED_HOSTS = setOf("www.bing.com", "cn.bing.com")
private val SEARCH_WHITESPACE = Regex("\\s+")
private val HTML_TAG = Regex("<[^>]{1,512}>")
private val BIDI_CONTROL = Regex("[\\u202A-\\u202E\\u2066-\\u2069]")
private val TRACKING_QUERY_NAMES = setOf(
    "fbclid",
    "gclid",
    "msclkid",
    "utm_campaign",
    "utm_content",
    "utm_id",
    "utm_medium",
    "utm_source",
    "utm_term",
)
