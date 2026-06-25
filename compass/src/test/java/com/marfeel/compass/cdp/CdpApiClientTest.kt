package com.marfeel.compass.cdp

import com.marfeel.compass.cdp.model.CdpLinkParams
import com.marfeel.compass.cdp.model.CdpProfileUpdateParams
import com.marfeel.compass.cdp.model.CdpResolveParams
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class CdpApiClientTest {
	private val server = MockWebServer()
	private val httpClient = OkHttpClient()
	private lateinit var apiClient: CdpApiClient

	@Before
	fun setUp() {
		apiClient = CdpApiClient(httpClient, baseUrl = server.url("/").toString())
	}

	@After
	fun tearDown() {
		server.shutdown()
	}

	@Test
	fun `resolve posts to the resolve path with trailing slash`() = runBlocking {
		server.enqueue(MockResponse().setBody("""{"master_id":"abc"}"""))
		apiClient.resolve(CdpResolveParams(siteId = 123L, cookieId = "u1"))
		val request = server.takeRequest()
		assertEquals("POST", request.method)
		assertEquals("/cdp/identity/resolve/", request.path)
	}

	@Test
	fun `resolve sends snake_case body`() = runBlocking {
		server.enqueue(MockResponse().setBody("{}"))
		apiClient.resolve(CdpResolveParams(siteId = 123L, cookieId = "u1", masterId = "mid"))
		val body = server.takeRequest().body.readUtf8()
		assertTrue(body.contains("\"site_id\""))
		assertTrue(body.contains("\"cookie_id\""))
		assertTrue(body.contains("\"master_id\""))
	}

	@Test
	fun `resolve serializes site_id as a number not a string`() = runBlocking {
		server.enqueue(MockResponse().setBody("{}"))
		apiClient.resolve(CdpResolveParams(siteId = 123L, cookieId = "u1"))
		val body = server.takeRequest().body.readUtf8()
		assertTrue("site_id must be a JSON number", body.contains("\"site_id\":123"))
		assertTrue("site_id must not be quoted", !body.contains("\"site_id\":\"123\""))
	}

	@Test
	fun `resolve parses the identity response`() = runBlocking {
		server.enqueue(
			MockResponse().setBody(
				"""{"master_id":"550e8400-e29b-41d4-a716-446655440000","rfv":{"rfv":42,"r":3,"f":5,"v":7},"cohorts":[101,204]}"""
			)
		)
		val result = apiClient.resolve(CdpResolveParams(siteId = 1L, cookieId = "u"))
		assertEquals("550e8400-e29b-41d4-a716-446655440000", result.masterId)
		assertEquals(42, result.rfv?.rfv)
		assertEquals(listOf(101, 204), result.cohorts)
	}

	@Test
	fun `resolve fails open on non-2xx`() = runBlocking {
		server.enqueue(MockResponse().setResponseCode(500))
		val result = apiClient.resolve(CdpResolveParams(siteId = 1L, cookieId = "u"))
		assertEquals(UNKNOWN_CDP_IDENTITY, result)
	}

	@Test
	fun `resolve fails open on unparseable body`() = runBlocking {
		server.enqueue(MockResponse().setBody("not json"))
		val result = apiClient.resolve(CdpResolveParams(siteId = 1L, cookieId = "u"))
		assertNull(result.masterId)
		assertTrue(result.cohorts.isEmpty())
	}

	@Test
	fun `link posts deterministic snake_case body`() = runBlocking {
		server.enqueue(MockResponse().setBody("{}"))
		apiClient.link(
			CdpLinkParams(siteId = 1L, idType = "registered_user_id", idValue = "user@x.com", isDeterministic = true)
		)
		val request = server.takeRequest()
		assertEquals("/cdp/identity/link/", request.path)
		val body = request.body.readUtf8()
		assertTrue(body.contains("\"id_type\":\"registered_user_id\""))
		assertTrue(body.contains("\"is_deterministic\":true"))
	}

	@Test
	fun `update omits absent fields`() = runBlocking {
		server.enqueue(MockResponse().setBody("{}"))
		apiClient.update(CdpProfileUpdateParams(siteId = 1L, masterId = "mid", segmentsAdd = listOf("a")))
		val body = server.takeRequest().body.readUtf8()
		assertTrue(body.contains("\"segments_add\""))
		assertTrue(!body.contains("properties"))
		assertTrue(!body.contains("segments_remove"))
	}

	@Test
	fun `fetchMeters parses list and threshold trio`() = runBlocking {
		server.enqueue(
			MockResponse().setBody(
				"""{"meters":[{"name":"paywall","count":3,"threshold":5,"reached":false,"remaining":2,"window":{"duration":"calendar","period":"P1M","tz":"Europe/Madrid"}}]}"""
			)
		)
		val meters = apiClient.fetchMeters("1", "mid")!!
		assertEquals(1, meters.size)
		assertEquals("paywall", meters[0].name)
		assertEquals(3, meters[0].count)
		assertEquals(5, meters[0].threshold)
		assertEquals(false, meters[0].reached)
		assertEquals(2, meters[0].remaining)
		assertEquals("calendar", meters[0].window.duration)
	}

	@Test
	fun `fetchMeters leaves threshold trio absent when not configured`() = runBlocking {
		server.enqueue(MockResponse().setBody("""{"meters":[{"name":"views","count":1}]}"""))
		val meters = apiClient.fetchMeters("1", "mid")!!
		assertNull(meters[0].threshold)
		assertNull(meters[0].reached)
		assertNull(meters[0].remaining)
	}

	@Test
	fun `fetchMeters normalizes non-array meters to empty list`() = runBlocking {
		server.enqueue(MockResponse().setBody("""{"meters":"nope"}"""))
		val meters = apiClient.fetchMeters("1", "mid")
		assertEquals(emptyList<Any>(), meters)
	}

	@Test
	fun `fetchMeters returns null on error so mirror is kept`() = runBlocking {
		server.enqueue(MockResponse().setResponseCode(500))
		assertNull(apiClient.fetchMeters("1", "mid"))
	}

	@Test
	fun `fetchMeters url-encodes params`() = runBlocking {
		server.enqueue(MockResponse().setBody("""{"meters":[]}"""))
		apiClient.fetchMeters("a b", "m/d")
		val path = server.takeRequest().path!!
		assertTrue(path.contains("site_id=a%20b"))
		assertTrue(path.contains("master_id=m%2Fd"))
	}

	@Test
	fun `incrementMeter returns 404 status for the caller to map`() = runBlocking {
		server.enqueue(MockResponse().setResponseCode(404))
		val result = apiClient.incrementMeter("paywall", "1", "mid")
		assertEquals(404, result.status)
		assertNull(result.state)
	}

	@Test
	fun `incrementMeter posts to increment path with null body`() = runBlocking {
		server.enqueue(MockResponse().setBody("""{"name":"paywall","count":4}"""))
		val result = apiClient.incrementMeter("paywall", "1", "mid")
		val request = server.takeRequest()
		assertEquals("POST", request.method)
		assertTrue(request.path!!.startsWith("/cdp/meters/paywall/increment"))
		assertEquals(4, result.state?.count)
	}
}
