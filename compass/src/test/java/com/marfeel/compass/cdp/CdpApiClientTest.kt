package com.marfeel.compass.cdp

import com.marfeel.compass.cdp.model.CdpConsentCheckParams
import com.marfeel.compass.cdp.model.CdpConsentRecordParams
import com.marfeel.compass.cdp.model.CdpConsentStatus
import com.marfeel.compass.cdp.model.CdpDeleteParams
import com.marfeel.compass.cdp.model.CdpLinkParams
import com.marfeel.compass.cdp.model.CdpProfileUpdateParams
import com.marfeel.compass.cdp.model.CdpResolveParams
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
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

	// region identity response extras

	@Test
	fun `identity responses parse segments and coerce non-string properties`() = runBlocking {
		server.enqueue(
			MockResponse().setBody(
				"""{"master_id":"m","rfv":null,"cohorts":[1],"segments":["a","b"],"properties":{"plan":"premium","age":42,"vip":true,"nested":{"x":1},"gone":null}}"""
			)
		)
		val result = apiClient.resolve(CdpResolveParams(siteId = 1L, cookieId = "u"))
		assertEquals(listOf("a", "b"), result.segments)
		assertEquals("premium", result.properties?.get("plan"))
		assertEquals("42", result.properties?.get("age"))
		assertEquals("true", result.properties?.get("vip"))
		assertEquals("""{"x":1}""", result.properties?.get("nested"))
		assertFalse(result.properties!!.containsKey("gone"))
	}

	@Test
	fun `identity responses leave segments and properties null when absent`() = runBlocking {
		server.enqueue(MockResponse().setBody("""{"master_id":"m"}"""))
		val result = apiClient.resolve(CdpResolveParams(siteId = 1L, cookieId = "u"))
		assertNull(result.segments)
		assertNull(result.properties)
	}

	// endregion

	// region delete / reset

	@Test
	fun `delete posts the snake_case body and parses the count`() = runBlocking {
		server.enqueue(MockResponse().setBody("""{"master_id":"m","rfv":null,"cohorts":[],"segments":["s"],"deleted":2}"""))
		val result = apiClient.delete(CdpDeleteParams(siteId = 1L, masterId = "m", idType = "email", idValue = "x@y.z"))!!
		val request = server.takeRequest()
		assertEquals("POST", request.method)
		assertEquals("/cdp/identity/delete/", request.path)
		val body = request.body.readUtf8()
		assertTrue(body.contains("\"site_id\":1"))
		assertTrue(body.contains("\"master_id\":\"m\""))
		assertTrue(body.contains("\"id_type\":\"email\""))
		assertTrue(body.contains("\"id_value\":\"x@y.z\""))
		assertEquals(2, result.deleted)
		assertEquals(listOf("s"), result.identity.segments)
	}

	@Test
	fun `delete omits id_value entirely when null`() = runBlocking {
		server.enqueue(MockResponse().setBody("""{"master_id":"m","deleted":0}"""))
		apiClient.delete(CdpDeleteParams(siteId = 1L, masterId = "m", idType = "crm_id"))
		val body = server.takeRequest().body.readUtf8()
		assertFalse(body.contains("id_value"))
	}

	@Test
	fun `delete returns null, not the unknown identity, on failure`() = runBlocking {
		server.enqueue(MockResponse().setResponseCode(500))
		assertNull(apiClient.delete(CdpDeleteParams(siteId = 1L, masterId = "m", idType = "email", idValue = "v")))
	}

	@Test
	fun `reset posts only the site id and parses the cleared list`() = runBlocking {
		server.enqueue(MockResponse().setBody("""{"reset":true,"site_id":1,"cleared":["1_u","1_s"]}"""))
		val result = apiClient.reset(1L)!!
		val request = server.takeRequest()
		assertEquals("/cdp/identity/reset/", request.path)
		assertEquals("""{"site_id":1}""", request.body.readUtf8())
		assertTrue(result.reset)
		assertEquals(1L, result.siteId)
		assertEquals(listOf("1_u", "1_s"), result.cleared)
	}

	@Test
	fun `reset returns null on failure`() = runBlocking {
		server.enqueue(MockResponse().setResponseCode(503))
		assertNull(apiClient.reset(1L))
	}

	// endregion

	// region consents

	@Test
	fun `recordConsent sends an explicit null master_id and omits the optional keys`() = runBlocking {
		server.enqueue(MockResponse().setBody("""{"consent_id":"p","consent_version_id":"1","status":"accept","recorded":true,"stored":true}"""))
		val result = apiClient.recordConsent(
			CdpConsentRecordParams(siteId = 1L, masterId = null, consentId = "p", consentVersionId = "1", status = CdpConsentStatus.ACCEPTED)
		)!!
		val request = server.takeRequest()
		assertEquals("/cdp/consents/record/", request.path)
		val body = request.body.readUtf8()
		assertTrue(body.contains("\"master_id\":null"))
		assertTrue(body.contains("\"status\":\"accepted\""))
		assertTrue(body.contains("\"consent_version_id\":\"1\""))
		assertFalse(body.contains("metadata"))
		assertFalse(body.contains("timezone"))
		assertFalse(body.contains("id_type"))
		assertTrue(result.recorded)
		assertEquals("accept", result.status)
		assertNull(result.masterId)
	}

	@Test
	fun `recordConsent sends metadata, timezone and the email subject when given`() = runBlocking {
		server.enqueue(MockResponse().setBody("""{"master_id":"m","recorded":true,"stored":true}"""))
		apiClient.recordConsent(
			CdpConsentRecordParams(
				siteId = 1L, masterId = "m", consentId = "p", consentVersionId = "1", status = CdpConsentStatus.REJECTED,
				metadata = mapOf("source" to "footer"), timezone = "Europe/Madrid", idType = "email_sha256", idValue = "abc"
			)
		)
		val body = server.takeRequest().body.readUtf8()
		assertTrue(body.contains("\"master_id\":\"m\""))
		assertTrue(body.contains("\"metadata\":{\"source\":\"footer\"}"))
		assertTrue(body.contains("\"timezone\":\"Europe/Madrid\""))
		assertTrue(body.contains("\"id_type\":\"email_sha256\""))
		assertTrue(body.contains("\"id_value\":\"abc\""))
		assertTrue(body.contains("\"status\":\"rejected\""))
		assertFalse(body.contains("ip"))
		assertFalse(body.contains("user_agent"))
	}

	@Test
	fun `recordConsent returns null on failure`() = runBlocking {
		server.enqueue(MockResponse().setResponseCode(500))
		assertNull(apiClient.recordConsent(CdpConsentRecordParams(1L, null, "p", "1", CdpConsentStatus.ACCEPTED)))
	}

	@Test
	fun `fetchConsentCatalog is a GET with the version as a query param, omitted when absent`() = runBlocking {
		server.enqueue(MockResponse().setBody("""{"site_id":1,"consents":[]}"""))
		apiClient.fetchConsentCatalog(1L, "privacy policy", "3")
		var request = server.takeRequest()
		assertEquals("GET", request.method)
		assertTrue(request.path!!.startsWith("/cdp/consents/catalog/?"))
		assertTrue(request.path!!.contains("site_id=1"))
		assertTrue(request.path!!.contains("consent_id=privacy%20policy"))
		assertTrue(request.path!!.contains("consent_version_id=3"))

		server.enqueue(MockResponse().setBody("""{"site_id":1,"consents":[]}"""))
		apiClient.fetchConsentCatalog(1L, "privacy", null)
		request = server.takeRequest()
		assertFalse(request.path!!.contains("consent_version_id"))
	}

	@Test
	fun `fetchConsentCatalog parses the wire item`() = runBlocking {
		server.enqueue(
			MockResponse().setBody(
				"""{"site_id":1,"consents":[{"consent_id":"privacy","name":"Privacy","purpose":null,"mandatory":true,"accept_method":"form-submit","show_policy":"if-not-accepted","version":{"consent_version_id":7,"label":"v7","date":"2026-01-01","display_prompt":null,"error_message":"nope","metadata":{"a":"b"}}}]}"""
			)
		)
		val item = apiClient.fetchConsentCatalog(1L, "privacy", null)!!.single()
		assertEquals("privacy", item.consentId)
		assertEquals("Privacy", item.name)
		assertNull(item.purpose)
		assertTrue(item.mandatory)
		assertEquals("form-submit", item.acceptMethod)
		assertEquals("if-not-accepted", item.showPolicy)
		assertEquals("7", item.version?.versionId)
		assertEquals("v7", item.version?.label)
		assertNull(item.version?.displayPrompt)
		assertEquals("nope", item.version?.errorMessage)
		assertEquals(mapOf("a" to "b"), item.version?.metadata)
	}

	@Test
	fun `fetchConsentCatalog returns an empty list for an unknown version and null on failure`() = runBlocking {
		server.enqueue(MockResponse().setBody("""{"site_id":1,"consents":[]}"""))
		assertEquals(emptyList<Any>(), apiClient.fetchConsentCatalog(1L, "privacy", "99"))
		server.enqueue(MockResponse().setResponseCode(500))
		assertNull(apiClient.fetchConsentCatalog(1L, "privacy", null))
	}

	@Test
	fun `fetchConsentStatus is a POST carrying only the present subject keys`() = runBlocking {
		server.enqueue(MockResponse().setBody("""{"master_id":"m","consent_id":"p","consent_version_id":"1","granted":true,"answered":true,"status":"accepted","answered_version_id":"1"}"""))
		val result = apiClient.fetchConsentStatus(CdpConsentCheckParams(siteId = 1L, consentId = "p", consentVersionId = "1", masterId = "m"))!!
		val request = server.takeRequest()
		assertEquals("POST", request.method)
		assertEquals("/cdp/consents/check/", request.path)
		val body = request.body.readUtf8()
		assertTrue(body.contains("\"master_id\":\"m\""))
		assertFalse(body.contains("id_type"))
		assertTrue(result.granted)
		assertTrue(result.answered)
		assertEquals("accepted", result.status)
		assertEquals("1", result.answeredVersionId)
	}

	@Test
	fun `fetchConsentStatus returns null on failure`() = runBlocking {
		server.enqueue(MockResponse().setResponseCode(500))
		assertNull(apiClient.fetchConsentStatus(CdpConsentCheckParams(siteId = 1L, consentId = "p", masterId = "m")))
	}

	// endregion
}
