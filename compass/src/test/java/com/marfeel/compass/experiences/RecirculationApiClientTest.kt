package com.marfeel.compass.experiences

import com.marfeel.compass.core.model.compass.Page
import com.marfeel.compass.core.model.compass.Session
import com.marfeel.compass.core.model.compass.UserType
import com.marfeel.compass.experiences.model.RecirculationLink
import com.marfeel.compass.experiences.model.RecirculationModule
import com.marfeel.compass.storage.SessionStorage
import com.marfeel.compass.storage.Storage
import io.mockk.every
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.net.URLDecoder

class RecirculationApiClientTest {
	private val server = MockWebServer()
	private val httpClient = OkHttpClient()
	private val storage = mockk<Storage>()
	private val sessionStorage = mockk<SessionStorage>()
	private lateinit var client: RecirculationApiClient

	private fun makeModule(
		name: String = "mod-1",
		links: List<RecirculationLink> = listOf(
			RecirculationLink(url = "https://example.com/article", position = "0")
		)
	): RecirculationModule = RecirculationModule(name = name, links = links)

	private fun decodedBody(): String {
		val request = server.takeRequest()
		return URLDecoder.decode(request.body.readUtf8(), "UTF-8")
	}

	@Before
	fun setUp() {
		val baseUrl = server.url("/").toString().trimEnd('/')
		client = RecirculationApiClient(
			httpClient = httpClient,
			storage = storage,
			sessionStorage = sessionStorage,
			pingBaseUrl = baseUrl
		)

		every { sessionStorage.readAccountId() } returns "2223"
		every { sessionStorage.readPage() } returns Page("https://example.com/article")
		every { sessionStorage.readPageTechnology() } returns 4
		every { sessionStorage.readSession() } returns Session("session-123", 1000L)
		every { sessionStorage.readLandingPage() } returns "https://example.com/"
		every { storage.readOriginalUserId() } returns "user-abc"
		every { storage.readRegisteredUserId() } returns null
		every { storage.readUserType() } returns UserType.Anonymous
		every { storage.readFirstSessionTimeStamp() } returns 1700000000L
		every { storage.readPreviousSessionLastPingTimeStamp() } returns 1699000000L
		every { storage.readUserConsent() } returns true
	}

	@After
	fun tearDown() {
		server.shutdown()
	}

	@Test
	fun `sends POST to recirculation endpoint`() {
		server.enqueue(MockResponse())
		client.send("elegible", listOf(makeModule()))
		val request = server.takeRequest()
		assertEquals("POST", request.method)
		assertTrue(request.path!!.contains("/recirculation/recirculation.php"))
	}

	@Test
	fun `includes event type parameter`() {
		server.enqueue(MockResponse())
		client.send("impression", listOf(makeModule()))
		val body = decodedBody()
		assertTrue(body.contains("t=impression"))
	}

	@Test
	fun `includes module with name and link`() {
		server.enqueue(MockResponse())
		client.send("elegible", listOf(makeModule(name = "abc", links = listOf(
			RecirculationLink(url = "https://example.com/page", position = "0")
		))))
		val body = decodedBody()
		assertTrue(body.contains("m="))
		assertTrue(body.contains("\"n\":\"abc\""))
		assertTrue(body.contains("\"url\":\"https://example.com/page\""))
		assertTrue(body.contains("\"p\":\"0\""))
	}

	@Test
	fun `includes multiple modules`() {
		server.enqueue(MockResponse())
		client.send("elegible", listOf(
			makeModule(name = "mod-1", links = listOf(RecirculationLink(url = "https://a.com", position = "0"))),
			makeModule(name = "mod-2", links = listOf(RecirculationLink(url = "https://b.com", position = "0")))
		))
		val body = decodedBody()
		assertTrue(body.contains("\"n\":\"mod-1\""))
		assertTrue(body.contains("\"n\":\"mod-2\""))
	}

	@Test
	fun `does not send request for empty modules list`() {
		client.send("elegible", emptyList())
		assertEquals(0, server.requestCount)
	}

	@Test
	fun `includes common tracking parameters`() {
		server.enqueue(MockResponse())
		client.send("click", listOf(makeModule()))
		val body = decodedBody()
		assertTrue(body.contains("ac=2223"))
		assertTrue(body.contains("url=https://example.com/article"))
		assertTrue(body.contains("ut=1"))
		assertTrue(body.contains("fv=1700000000"))
		assertTrue(body.contains("u=user-abc"))
		assertTrue(body.contains("s=session-123"))
		assertTrue(body.contains("pageType=4"))
		assertTrue(body.contains("c=https://example.com/article"))
		assertTrue(body.contains("lv=1699000000"))
	}

	@Test
	fun `sends lv as 0 when previous session timestamp is null`() {
		every { storage.readPreviousSessionLastPingTimeStamp() } returns null
		server.enqueue(MockResponse())
		client.send("click", listOf(makeModule()))
		val body = decodedBody()
		assertTrue(body.contains("lv=0"))
	}

	@Test
	fun `includes consent code 1 when consent is true`() {
		server.enqueue(MockResponse())
		client.send("elegible", listOf(makeModule()))
		val body = decodedBody()
		assertTrue(body.contains("uc=true"))
		assertTrue(body.contains("cc=1"))
	}

	@Test
	fun `includes consent code 0 when consent is false`() {
		every { storage.readUserConsent() } returns false
		server.enqueue(MockResponse())
		client.send("elegible", listOf(makeModule()))
		val body = decodedBody()
		assertTrue(body.contains("cc=0"))
	}

	@Test
	fun `includes consent code 3 when consent is null`() {
		every { storage.readUserConsent() } returns null
		server.enqueue(MockResponse())
		client.send("elegible", listOf(makeModule()))
		val body = decodedBody()
		assertTrue(body.contains("cc=3"))
	}

	@Test
	fun `includes landing page`() {
		server.enqueue(MockResponse())
		client.send("elegible", listOf(makeModule()))
		val body = decodedBody()
		assertTrue(body.contains("lp=https://example.com/"))
	}

	@Test
	fun `includes timestamp`() {
		server.enqueue(MockResponse())
		client.send("elegible", listOf(makeModule()))
		val body = decodedBody()
		assertTrue(body.contains("n="))
	}

	@Test
	fun `includes sui when registered user id is set`() {
		every { storage.readRegisteredUserId() } returns "registered-user-1"
		server.enqueue(MockResponse())
		client.send("elegible", listOf(makeModule()))
		val body = decodedBody()
		assertTrue(body.contains("sui=registered-user-1"))
	}

	@Test
	fun `includes multiple links per module`() {
		server.enqueue(MockResponse())
		client.send("elegible", listOf(makeModule(name = "mod-1", links = listOf(
			RecirculationLink(url = "https://a.com/1", position = "0"),
			RecirculationLink(url = "https://a.com/2", position = "1"),
			RecirculationLink(url = "https://a.com/3", position = "2")
		))))
		val body = decodedBody()
		assertTrue(body.contains("\"url\":\"https://a.com/1\""))
		assertTrue(body.contains("\"url\":\"https://a.com/2\""))
		assertTrue(body.contains("\"url\":\"https://a.com/3\""))
		assertTrue(body.contains("\"p\":\"0\""))
		assertTrue(body.contains("\"p\":\"1\""))
		assertTrue(body.contains("\"p\":\"2\""))
	}

	@Test
	fun `uses client-provided position values`() {
		server.enqueue(MockResponse())
		client.send("elegible", listOf(makeModule(links = listOf(
			RecirculationLink(url = "https://example.com/article", position = "42")
		))))
		val body = decodedBody()
		assertTrue(body.contains("\"p\":\"42\""))
	}

	@Test
	fun `handles module with empty links list`() {
		server.enqueue(MockResponse())
		client.send("elegible", listOf(makeModule(links = emptyList())))
		val body = decodedBody()
		assertTrue(body.contains("\"e\":[]"))
	}
}
