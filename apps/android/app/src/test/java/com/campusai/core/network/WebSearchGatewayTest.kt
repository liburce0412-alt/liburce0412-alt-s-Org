package com.campusai.core.network

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WebSearchGatewayTest {
    @Test
    fun `search sends only a normalized query to fixed HTTPS Bing RSS and cleans results`() = runTest {
        val requests = mutableListOf<Request>()
        val gateway = BingRssWebSearchGateway(
            client = searchClient(requests) {
                """
                    <?xml version="1.0" encoding="utf-8"?>
                    <rss version="2.0"><channel>
                      <item>
                        <title>  Campus &amp; AI  </title>
                        <link>https://www.example.com/article?utm_source=bing&amp;x=1#section</link>
                        <description><![CDATA[<b>最新</b>   摘要 &amp; details]]></description>
                        <pubDate>Mon, 31 Aug 2026 08:00:00 GMT</pubDate>
                      </item>
                      <item>
                        <title>重复链接</title>
                        <link>https://www.example.com/article?x=1</link>
                        <description>不会重复返回</description>
                      </item>
                      <item>
                        <title>不安全链接</title>
                        <link>http://legacy.example/path</link>
                        <description>HTTP 结果会被过滤</description>
                      </item>
                    </channel></rss>
                """.trimIndent()
            },
        )

        val response = gateway.search("  OpenAI\t  Android\n")

        assertEquals("OpenAI Android", response.query)
        assertEquals(1, response.results.size)
        assertEquals("Campus & AI", response.results.single().title)
        assertEquals("https://www.example.com/article?x=1", response.results.single().url)
        assertEquals("最新 摘要 & details", response.results.single().snippet)
        assertEquals("example.com", response.results.single().sourceHost)
        assertEquals("Mon, 31 Aug 2026 08:00:00 GMT", response.results.single().publishedAt)

        val request = requests.single()
        assertEquals("https", request.url.scheme)
        assertEquals("www.bing.com", request.url.host)
        assertEquals("/search", request.url.encodedPath)
        assertEquals("OpenAI Android", request.url.queryParameter("q"))
        assertEquals("rss", request.url.queryParameter("format"))
        assertEquals("zh-Hans", request.url.queryParameter("setlang"))
        assertEquals("no-store", request.header("Cache-Control"))
        assertNull(request.header("Authorization"))
        assertNull(request.header("Cookie"))
    }

    @Test
    fun `result count has a hard cap even when tool arguments request more`() = runTest {
        val items = (1..12).joinToString("") { index ->
            "<item><title>结果 $index</title><link>https://example.com/$index</link><description>摘要</description></item>"
        }
        val gateway = BingRssWebSearchGateway(searchClient(mutableListOf()) { "<rss><channel>$items</channel></rss>" })

        val response = gateway.search("测试", maxResults = Int.MAX_VALUE)

        assertEquals(MAX_WEB_SEARCH_RESULTS, response.results.size)
        assertEquals("结果 8", response.results.last().title)
    }

    @Test
    fun `empty and oversized queries are rejected before a network call`() = runTest {
        val requestCount = AtomicInteger(0)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requestCount.incrementAndGet()
            xmlResponse(chain.request(), "<rss><channel/></rss>")
        }.build()
        val gateway = BingRssWebSearchGateway(client)

        val empty = runCatching { gateway.search(" \n\t ") }.exceptionOrNull() as WebSearchException
        val oversized = runCatching {
            gateway.search("x".repeat(MAX_WEB_SEARCH_QUERY_CHARS + 1))
        }.exceptionOrNull() as WebSearchException

        assertEquals("search_query_empty", empty.code)
        assertFalse(empty.recoverable)
        assertEquals("search_query_too_long", oversized.code)
        assertFalse(oversized.recoverable)
        assertEquals(0, requestCount.get())
    }

    @Test
    fun `oversized and unsafe XML responses stop with sanitized Chinese errors`() = runTest {
        val bodies = ArrayDeque(
            listOf(
                "x".repeat(MAX_WEB_SEARCH_BODY_BYTES.toInt() + 1),
                """<?xml version="1.0"?><!DOCTYPE rss [<!ENTITY leak SYSTEM "file:///data/data/private">]><rss/>""",
            ),
        )
        val gateway = BingRssWebSearchGateway(searchClient(mutableListOf()) { bodies.removeFirst() })

        val oversized = runCatching { gateway.search("第一问") }.exceptionOrNull() as WebSearchException
        val unsafeXml = runCatching { gateway.search("第二问") }.exceptionOrNull() as WebSearchException

        assertEquals("search_response_too_large", oversized.code)
        assertEquals("search_response_invalid", unsafeXml.code)
        assertTrue(oversized.message.contains("过大"))
        assertFalse(unsafeXml.message.contains("file:///"))
    }

    @Test
    fun `rate limits and server failures use stable Chinese errors without response contents`() = runTest {
        val codes = ArrayDeque(listOf(429, 503))
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(codes.removeFirst())
                .message("private upstream detail")
                .body("secret response".toResponseBody("text/plain".toMediaType()))
                .build()
        }.build()
        val gateway = BingRssWebSearchGateway(client)

        val limited = runCatching { gateway.search("限流") }.exceptionOrNull() as WebSearchException
        val unavailable = runCatching { gateway.search("故障") }.exceptionOrNull() as WebSearchException

        assertEquals("search_rate_limited", limited.code)
        assertEquals("search_unavailable", unavailable.code)
        assertFalse(limited.message.contains("secret"))
        assertFalse(unavailable.message.contains("private upstream"))
    }

    @Test
    fun `Chinese regional Bing redirect is followed once without enabling arbitrary redirects`() = runTest {
        val requests = mutableListOf<Request>()
        val client = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .addInterceptor { chain ->
                requests += chain.request()
                if (requests.size == 1) {
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(302)
                        .message("Found")
                        .header("Location", "https://cn.bing.com/search?q=OpenAI&format=rss&setlang=zh-Hans")
                        .body("".toResponseBody())
                        .build()
                } else {
                    xmlResponse(
                        chain.request(),
                        "<rss><channel><item><title>OpenAI</title><link>https://openai.com/</link></item></channel></rss>",
                    )
                }
            }
            .build()

        val response = BingRssWebSearchGateway(client).search("OpenAI")

        assertEquals(listOf("www.bing.com", "cn.bing.com"), requests.map { it.url.host })
        assertEquals("OpenAI", response.results.single().title)
        assertEquals("https://openai.com/", response.results.single().url)
    }

    @Test
    fun `redirect outside fixed Bing HTTPS hosts is rejected without a second request`() = runTest {
        val requests = mutableListOf<Request>()
        val client = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .addInterceptor { chain ->
                requests += chain.request()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(302)
                    .message("Found")
                    .header("Location", "https://attacker.example/collect")
                    .body("".toResponseBody())
                    .build()
            }
            .build()

        val failure = runCatching {
            BingRssWebSearchGateway(client).search("private query")
        }.exceptionOrNull() as WebSearchException

        assertEquals("search_redirect_rejected", failure.code)
        assertEquals(1, requests.size)
        assertTrue(failure.message.contains("安全停止"))
    }

    @Test
    fun `default search transport is bounded and never follows redirects`() {
        val client = defaultWebSearchHttpClient()

        assertEquals(25_000, client.connectTimeoutMillis)
        assertEquals(25_000, client.readTimeoutMillis)
        assertEquals(25_000, client.writeTimeoutMillis)
        assertEquals(30_000, client.callTimeoutMillis)
        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
        assertFalse(client.retryOnConnectionFailure)
        assertNull(client.cache)
    }

    private fun searchClient(
        requests: MutableList<Request>,
        body: () -> String,
    ): OkHttpClient = OkHttpClient.Builder().addInterceptor { chain ->
        requests += chain.request()
        xmlResponse(chain.request(), body())
    }.build()

    private fun xmlResponse(request: Request, body: String): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body(body.toResponseBody("text/xml; charset=utf-8".toMediaType()))
        .build()
}
