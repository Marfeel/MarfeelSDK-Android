package com.marfeel.compass.experiences

import com.marfeel.compass.storage.Conversion
import com.marfeel.compass.storage.SessionStorage
import com.marfeel.compass.storage.Storage
import com.marfeel.compass.core.model.compass.Session
import com.marfeel.compass.core.model.compass.UserType
import io.mockk.every
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class ExperiencesApiClientTest {
	private val server = MockWebServer()
	private val httpClient = OkHttpClient()
	private val storage = mockk<Storage>()
	private val sessionStorage = mockk<SessionStorage>()
	private val experimentManager = mockk<ExperimentManager>()
	private val frequencyCapManager = mockk<FrequencyCapManager>()
	private lateinit var apiClient: ExperiencesApiClient

	@Before
	fun setUp() {
		val baseUrl = server.url("/").toString()
		apiClient = ExperiencesApiClient(
			httpClient = httpClient,
			storage = storage,
			sessionStorage = sessionStorage,
			experimentManager = experimentManager,
			frequencyCapManager = frequencyCapManager,
			baseUrl = baseUrl
		)

		every { sessionStorage.readAccountId() } returns "2223"
		every { sessionStorage.readPageTechnology() } returns 4
		every { sessionStorage.readSession() } returns Session("session-123", 1000L)
		every { sessionStorage.readPreviousUrl() } returns "https://previous.com"
		every { sessionStorage.readPendingConversions() } returns emptyList()
		every { sessionStorage.readPageVars() } returns mapOf("pv1" to "val1")
		every { sessionStorage.readSessionVars() } returns mapOf("sv1" to "val2")
		every { storage.readOriginalUserId() } returns "user-abc"
		every { storage.readRegisteredUserId() } returns null
		every { storage.readUserType() } returns UserType.Anonymous
		every { storage.readFirstSessionTimeStamp() } returns 1700000000L
		every { storage.readUserSegments() } returns listOf("seg1", "seg2")
		every { storage.readUserVars() } returns mapOf("uv1" to "val3")
		every { experimentManager.getTargetingEntries() } returns mapOf("experiment::grp1" to "var1")
		every { frequencyCapManager.buildUexp() } returns "exp1,l|1|cl|0|m|1|cm|0|w|1|cw|0|d|1|cd|0|ls|1234"
	}

	@After
	fun tearDown() {
		server.shutdown()
	}

	@Test
	fun `sends GET request`() {
		server.enqueue(MockResponse().setBody("{}"))
		apiClient.fetch("https://test.com", emptyMap())
		assertEquals("GET", server.takeRequest().method)
	}

	@Test
	fun `includes sid parameter`() {
		server.enqueue(MockResponse().setBody("{}"))
		apiClient.fetch("https://test.com", emptyMap())
		val request = server.takeRequest()
		assertTrue(request.path!!.contains("sid=2223"))
	}

	@Test
	fun `includes url parameter`() {
		server.enqueue(MockResponse().setBody("{}"))
		apiClient.fetch("https://test.com/page", emptyMap())
		val request = server.takeRequest()
		assertTrue(request.path!!.contains("url=https"))
	}

	@Test
	fun `includes seid parameter`() {
		server.enqueue(MockResponse().setBody("{}"))
		apiClient.fetch("https://test.com", emptyMap())
		val request = server.takeRequest()
		assertTrue(request.path!!.contains("seid=session-123"))
	}

	@Test
	fun `includes uid parameter`() {
		server.enqueue(MockResponse().setBody("{}"))
		apiClient.fetch("https://test.com", emptyMap())
		val request = server.takeRequest()
		assertTrue(request.path!!.contains("uid=user-abc"))
	}

	@Test
	fun `includes v=2 parameter`() {
		server.enqueue(MockResponse().setBody("{}"))
		apiClient.fetch("https://test.com", emptyMap())
		val request = server.takeRequest()
		assertTrue(request.path!!.contains("v=2"))
	}

	@Test
	fun `includes uexp parameter`() {
		server.enqueue(MockResponse().setBody("{}"))
		apiClient.fetch("https://test.com", emptyMap())
		val request = server.takeRequest()
		assertTrue(request.path!!.contains("uexp="))
	}

	@Test
	fun `includes useg parameter`() {
		server.enqueue(MockResponse().setBody("{}"))
		apiClient.fetch("https://test.com", emptyMap())
		val request = server.takeRequest()
		assertTrue(request.path!!.contains("useg=seg1"))
	}

	@Test
	fun `builds trg parameter with vars and targeting`() {
		server.enqueue(MockResponse().setBody("{}"))
		apiClient.fetch("https://test.com", mapOf("geo" to "ES"))
		val request = server.takeRequest()
		val path = request.path!!
		assertTrue(path.contains("trg="))
	}

	@Test
	fun `returns response body on success`() {
		val body = """{"inline":{"actions":{}}}"""
		server.enqueue(MockResponse().setBody(body))
		val result = apiClient.fetch("https://test.com", emptyMap())
		assertNotNull(result)
		assertTrue(result!!.contains("inline"))
	}

	@Test
	fun `returns null on server error`() {
		server.enqueue(MockResponse().setResponseCode(500))
		val result = apiClient.fetch("https://test.com", emptyMap())
		assertEquals(null, result)
	}
}
