package com.marfeel.compass.experiences

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class ContentResolverBundlingTest {
    private lateinit var server: MockWebServer
    private lateinit var resolver: ContentResolver

    @Before
    fun setUp() {
        server = MockWebServer()
        resolver = ContentResolver(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `isBundledUrl detects comma-separated id parameter`() {
        assertTrue(ContentResolver.isBundledUrl("https://x.com/r?id=IL_a,IL_b"))
        assertFalse(ContentResolver.isBundledUrl("https://x.com/r?id=IL_a"))
        assertFalse(ContentResolver.isBundledUrl("https://x.com/r"))
    }

    @Test
    fun `isBundledUrl detects bundled id inside jukebox url query param`() {
        val jukebox = "https://flowcards.mrf.io/transformer/x?url=https%3A%2F%2Fr.com%2Fbundle%3Fid%3DIL_a%2CIL_b"
        assertTrue(ContentResolver.isBundledUrl(jukebox))
    }

    @Test
    fun `bundled fetch returns per-id slice`() = runBlocking {
        val body = """{"IL_a":[{"type":"TextHTML","content":"A"}],"IL_b":[{"type":"TextHTML","content":"B"}],"vars":{}}"""
        server.enqueue(MockResponse().setBody(body))

        val url = server.url("/r").newBuilder().addQueryParameter("id", "IL_a,IL_b").build().toString()

        val a = resolver.fetch(url, "IL_a")
        val b = resolver.fetch(url, "IL_b")

        assertNotNull(a); assertTrue(a!!.contains("\"A\""))
        assertNotNull(b); assertTrue(b!!.contains("\"B\""))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `concurrent bundled fetches coalesce into single HTTP call`() = runBlocking {
        val body = """{"IL_a":[{"c":"A"}],"IL_b":[{"c":"B"}],"IL_c":[{"c":"C"}]}"""
        server.enqueue(MockResponse().setBody(body))

        val url = server.url("/r").newBuilder().addQueryParameter("id", "IL_a,IL_b,IL_c").build().toString()

        val results = listOf("IL_a", "IL_b", "IL_c").map { id ->
            async { resolver.fetch(url, id) }
        }.awaitAll()

        assertEquals(3, results.size)
        assertEquals(1, server.requestCount)
        assertTrue(results.all { it != null })
    }

    @Test
    fun `bundled slice consumed once per call (splice semantics)`() = runBlocking {
        val body = """{"IL_a":[{"c":"first"},{"c":"second"}]}"""
        server.enqueue(MockResponse().setBody(body))
        val url = server.url("/r").newBuilder().addQueryParameter("id", "IL_a,IL_b").build().toString()

        val first = resolver.fetch(url, "IL_a")
        val second = resolver.fetch(url, "IL_a")
        val third = resolver.fetch(url, "IL_a")

        assertTrue(first!!.contains("first"))
        assertTrue(second!!.contains("second"))
        assertNull(third)
    }

    @Test
    fun `non-bundled url falls through to raw fetch`() = runBlocking {
        server.enqueue(MockResponse().setBody("<html>plain</html>"))
        val url = server.url("/plain").toString()

        val result = resolver.fetch(url, "exp1")
        assertEquals("<html>plain</html>", result)
    }

    @Test
    fun `missing slice triggers vars-applied refetch`() = runBlocking {
        val first = """{"IL_a":[{"c":"A"}],"vars":{"token":"abc"}}"""
        val second = """{"IL_z":[{"c":"Z"}]}"""
        server.enqueue(MockResponse().setBody(first))
        server.enqueue(MockResponse().setBody(second))

        val url = server.url("/r").newBuilder().addQueryParameter("id", "IL_a,IL_z").build().toString()

        val z = resolver.fetch(url, "IL_z")

        assertNotNull(z); assertTrue(z!!.contains("Z"))
        assertEquals(2, server.requestCount)
        server.takeRequest()
        val replay = server.takeRequest()
        assertTrue(replay.path!!.contains("token=abc"))
    }

    @Test
    fun `missing slice without vars does not refetch`() = runBlocking {
        val body = """{"IL_a":[{"c":"A"}]}"""
        server.enqueue(MockResponse().setBody(body))

        val url = server.url("/r").newBuilder().addQueryParameter("id", "IL_a,IL_z").build().toString()

        val z = resolver.fetch(url, "IL_z")

        assertNull(z)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `vars replay only fires once per bundle`() = runBlocking {
        val first = """{"IL_a":[{"c":"A"}],"vars":{"token":"abc"}}"""
        val second = """{}"""
        server.enqueue(MockResponse().setBody(first))
        server.enqueue(MockResponse().setBody(second))

        val url = server.url("/r").newBuilder().addQueryParameter("id", "IL_a,IL_y,IL_z").build().toString()

        resolver.fetch(url, "IL_y")
        resolver.fetch(url, "IL_z")

        assertEquals(2, server.requestCount)
    }

    @Test
    fun `resolveVarsFromUrl replaces existing query param`() {
        val url = "https://r.com/bundle?id=IL_a&token=old"
        val out = ContentResolver.resolveVarsFromUrl(url, mapOf("token" to "new"))
        assertTrue(out.contains("token=new"))
        assertFalse(out.contains("token=old"))
    }

    @Test
    fun `resolveVarsFromUrl rewrites jukebox inner url`() {
        val inner = "https://r.com/bundle?id=IL_a"
        val outer = "https://flowcards.mrf.io/transformer/x?url=" + java.net.URLEncoder.encode(inner, "UTF-8")
        val out = ContentResolver.resolveVarsFromUrl(outer, mapOf("token" to "xyz"))
        assertTrue(out.contains("token%3Dxyz") || out.contains("token=xyz"))
    }

    @Test
    fun `missing experienceId slice returns null`() = runBlocking {
        val body = """{"IL_a":[{"c":"A"}]}"""
        server.enqueue(MockResponse().setBody(body))
        val url = server.url("/r").newBuilder().addQueryParameter("id", "IL_a,IL_z").build().toString()

        val result = resolver.fetch(url, "IL_z")
        assertNull(result)
    }
}
