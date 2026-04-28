package com.marfeel.compass.experiences

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Test

class ContentResolverTest {
	private val server = MockWebServer()
	private val httpClient = OkHttpClient()
	private val resolver = ContentResolver(httpClient)

	@After
	fun tearDown() {
		server.shutdown()
	}

	@Test
	fun `fetch returns response body for successful request`() = runBlocking {
		server.enqueue(MockResponse().setBody("<html>Hello</html>"))
		val url = server.url("/content").toString()
		val result = resolver.fetch(url)
		assertEquals("<html>Hello</html>", result)
	}

	@Test
	fun `fetch returns null for failed request`() = runBlocking {
		server.enqueue(MockResponse().setResponseCode(500))
		val url = server.url("/content").toString()
		val result = resolver.fetch(url)
		assertNull(result)
	}

	@Test
	fun `fetch returns null for invalid URL`() = runBlocking {
		val result = resolver.fetch("not-a-url")
		assertNull(result)
	}
}
