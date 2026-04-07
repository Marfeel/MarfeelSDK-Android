package com.marfeel.compass.experiences

import com.marfeel.compass.core.model.compass.Page
import com.marfeel.compass.core.model.compass.Session
import com.marfeel.compass.core.model.compass.UserType
import com.marfeel.compass.experiences.model.Experience
import com.marfeel.compass.experiences.model.ExperienceContentType
import com.marfeel.compass.experiences.model.ExperienceType
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

	private fun makeExperience(
		id: String = "exp-1",
		contentUrl: String? = "https://example.com/article"
	): Experience = Experience(
		id = id,
		name = "Test Experience",
		type = ExperienceType.INLINE,
		typeRaw = "inline",
		placement = null,
		contentUrl = contentUrl,
		contentType = ExperienceContentType.TEXT_HTML,
		features = null,
		strategy = null,
		selectors = null,
		filters = null,
		rawJson = emptyMap()
	)

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
		every { sessionStorage.readPage() } returns Page("https://elpais.com/article")
		every { sessionStorage.readPageTechnology() } returns 4
		every { sessionStorage.readSession() } returns Session("session-123", 1000L)
		every { sessionStorage.readLandingPage() } returns "https://elpais.com/"
		every { storage.readOriginalUserId() } returns "user-abc"
		every { storage.readRegisteredUserId() } returns null
		every { storage.readUserType() } returns UserType.Anonymous
		every { storage.readFirstSessionTimeStamp() } returns 1700000000L
		every { storage.readUserConsent() } returns true
	}

	@After
	fun tearDown() {
		server.shutdown()
	}

	@Test
	fun `sends POST to recirculation endpoint`() {
		server.enqueue(MockResponse())
		client.send("elegible", listOf(makeExperience()))
		val request = server.takeRequest()
		assertEquals("POST", request.method)
		assertTrue(request.path!!.contains("/recirculation/recirculation.php"))
	}

	@Test
	fun `includes event type parameter`() {
		server.enqueue(MockResponse())
		client.send("impression", listOf(makeExperience()))
		val body = decodedBody()
		assertTrue(body.contains("t=impression"))
	}

	@Test
	fun `includes modules with experience id and content url`() {
		server.enqueue(MockResponse())
		client.send("elegible", listOf(makeExperience(id = "abc", contentUrl = "https://example.com/page")))
		val body = decodedBody()
		assertTrue(body.contains("m="))
		assertTrue(body.contains("\"n\":\"abc\""))
		assertTrue(body.contains("\"url\":\"https://example.com/page\""))
		assertTrue(body.contains("\"p\":\"255\""))
	}

	@Test
	fun `includes multiple modules for multiple experiences`() {
		server.enqueue(MockResponse())
		client.send("elegible", listOf(
			makeExperience(id = "exp-1", contentUrl = "https://a.com"),
			makeExperience(id = "exp-2", contentUrl = "https://b.com")
		))
		val body = decodedBody()
		assertTrue(body.contains("\"n\":\"exp-1\""))
		assertTrue(body.contains("\"n\":\"exp-2\""))
	}

	@Test
	fun `uses empty string for null contentUrl`() {
		server.enqueue(MockResponse())
		client.send("elegible", listOf(makeExperience(contentUrl = null)))
		val body = decodedBody()
		assertTrue(body.contains("\"url\":\"\""))
	}

	@Test
	fun `does not send request for empty experiences list`() {
		client.send("elegible", emptyList())
		assertEquals(0, server.requestCount)
	}

	@Test
	fun `includes common tracking parameters`() {
		server.enqueue(MockResponse())
		client.send("click", listOf(makeExperience()))
		val body = decodedBody()
		assertTrue(body.contains("ac=2223"))
		assertTrue(body.contains("url=https://elpais.com/article"))
		assertTrue(body.contains("ut=1"))
		assertTrue(body.contains("fv=1700000000"))
		assertTrue(body.contains("u=user-abc"))
		assertTrue(body.contains("s=session-123"))
		assertTrue(body.contains("pageType=4"))
	}

	@Test
	fun `includes consent code 1 when consent is true`() {
		server.enqueue(MockResponse())
		client.send("elegible", listOf(makeExperience()))
		val body = decodedBody()
		assertTrue(body.contains("uc=true"))
		assertTrue(body.contains("cc=1"))
	}

	@Test
	fun `includes consent code 0 when consent is false`() {
		every { storage.readUserConsent() } returns false
		server.enqueue(MockResponse())
		client.send("elegible", listOf(makeExperience()))
		val body = decodedBody()
		assertTrue(body.contains("cc=0"))
	}

	@Test
	fun `includes consent code 3 when consent is null`() {
		every { storage.readUserConsent() } returns null
		server.enqueue(MockResponse())
		client.send("elegible", listOf(makeExperience()))
		val body = decodedBody()
		assertTrue(body.contains("cc=3"))
	}

	@Test
	fun `includes landing page`() {
		server.enqueue(MockResponse())
		client.send("elegible", listOf(makeExperience()))
		val body = decodedBody()
		assertTrue(body.contains("lp=https://elpais.com/"))
	}

	@Test
	fun `includes timestamp`() {
		server.enqueue(MockResponse())
		client.send("elegible", listOf(makeExperience()))
		val body = decodedBody()
		assertTrue(body.contains("n="))
	}

	@Test
	fun `includes sui when registered user id is set`() {
		every { storage.readRegisteredUserId() } returns "registered-user-1"
		server.enqueue(MockResponse())
		client.send("elegible", listOf(makeExperience()))
		val body = decodedBody()
		assertTrue(body.contains("sui=registered-user-1"))
	}
}
