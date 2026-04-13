package com.marfeel.compass.experiences

import com.marfeel.compass.core.model.compass.Session
import com.marfeel.compass.core.model.compass.UserType
import com.marfeel.compass.experiences.model.ExperienceType
import com.marfeel.compass.storage.MockSharedPreference
import com.marfeel.compass.storage.SessionStorage
import com.marfeel.compass.storage.Storage
import io.mockk.every
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class ExperiencesTrackerTest {
	private val experiencesServer = MockWebServer()
	private val contentServer = MockWebServer()
	private val httpClient = OkHttpClient()
	private val storage = mockk<Storage>()
	private val sessionStorage = mockk<SessionStorage>()
	private lateinit var frequencyCapManager: FrequencyCapManager
	private lateinit var readEditorialsManager: ReadEditorialsManager
	private lateinit var experimentManager: ExperimentManager
	private lateinit var contentResolver: ContentResolver
	private lateinit var responseParser: ExperiencesResponseParser
	private lateinit var apiClient: ExperiencesApiClient

	private fun loadJson(name: String): String =
		javaClass.classLoader!!.getResourceAsStream(name)!!.bufferedReader().readText()

	@Before
	fun setUp() {
		frequencyCapManager = FrequencyCapManager(MockSharedPreference())
		readEditorialsManager = ReadEditorialsManager(MockSharedPreference())
		experimentManager = ExperimentManager(MockSharedPreference())
		contentResolver = ContentResolver(httpClient)
		responseParser = ExperiencesResponseParser(contentResolver)

		apiClient = ExperiencesApiClient(
			httpClient = httpClient,
			storage = storage,
			sessionStorage = sessionStorage,
			experimentManager = experimentManager,
			frequencyCapManager = frequencyCapManager,
			readEditorialsManager = readEditorialsManager,
			baseUrl = experiencesServer.url("/").toString().removeSuffix("/")
		)

		every { sessionStorage.readAccountId() } returns "2223"
		every { sessionStorage.readPageTechnology() } returns 4
		every { sessionStorage.readSession() } returns Session("sess-1", 1000L)
		every { sessionStorage.readPreviousUrl() } returns null
		every { sessionStorage.readPendingConversions() } returns emptyList()
		every { sessionStorage.readPageVars() } returns emptyMap()
		every { sessionStorage.readSessionVars() } returns emptyMap()
		every { storage.readOriginalUserId() } returns "uid-1"
		every { storage.readRegisteredUserId() } returns null
		every { storage.readUserType() } returns UserType.Anonymous
		every { storage.readFirstSessionTimeStamp() } returns 1700000000L
		every { storage.readUserSegments() } returns emptyList()
		every { storage.readUserVars() } returns emptyMap()
	}

	@After
	fun tearDown() {
		experiencesServer.shutdown()
		contentServer.shutdown()
	}

	@Test
	fun `full pipeline parses experiences from real response`() = runBlocking {
		val json = loadJson("experiences_elpais_response.json")
		experiencesServer.enqueue(MockResponse().setBody(json))

		val jsonResponse = apiClient.fetch("https://elpais.com/", emptyMap())
		assertNotNull(jsonResponse)

		val result = responseParser.parse(jsonResponse!!)
		assertTrue(result.experiences.isNotEmpty())

		frequencyCapManager.updateFrequencyCapConfig(result.frequencyCapConfig)
		experimentManager.handleExperimentGroups(result.experimentGroups)

		val filtered = experimentManager.filterByExperiments(result.experiences)
		assertEquals(result.experiences.size, filtered.size)
	}

	@Test
	fun `filterByType returns only matching experiences`() = runBlocking {
		val json = loadJson("experiences_elpais_response.json")
		experiencesServer.enqueue(MockResponse().setBody(json))

		val jsonResponse = apiClient.fetch("https://elpais.com/", emptyMap())!!
		val result = responseParser.parse(jsonResponse)

		val inlines = result.experiences.filter { it.type == ExperienceType.INLINE }
		assertTrue(inlines.isNotEmpty())
		assertTrue(inlines.all { it.type == ExperienceType.INLINE })
	}

	@Test
	fun `filterByTypeRaw returns only matching experiences`() = runBlocking {
		val json = loadJson("experiences_elpais_response.json")
		experiencesServer.enqueue(MockResponse().setBody(json))

		val jsonResponse = apiClient.fetch("https://elpais.com/", emptyMap())!!
		val result = responseParser.parse(jsonResponse)

		val types = result.experiences.map { it.typeRaw }.distinct()
		assertTrue(types.isNotEmpty())

		val firstType = types.first()
		val byRaw = result.experiences.filter { it.typeRaw == firstType }
		assertTrue(byRaw.isNotEmpty())
		assertTrue(byRaw.all { it.typeRaw == firstType })
	}

	@Test
	fun `experiences without contentUrl have null resolvedContent`() = runBlocking {
		val json = loadJson("experiences_elpais_response.json")
		experiencesServer.enqueue(MockResponse().setBody(json))

		val jsonResponse = apiClient.fetch("https://elpais.com/", emptyMap())!!
		val result = responseParser.parse(jsonResponse)

		result.experiences.filter { it.contentUrl == null }.forEach {
			assertNull(it.resolvedContent)
		}
	}

	@Test
	fun `resolve fetches content from contentUrl`() = runBlocking {
		val htmlContent = "<div>Experience content</div>"
		contentServer.enqueue(MockResponse().setBody(htmlContent))

		val contentUrl = contentServer.url("/content.html").toString()
		val json = """
		{
			"inline": {
				"actions": {
					"exp1": {
						"id": "exp1",
						"name": "Test Experience",
						"content": {
							"url": "$contentUrl",
							"type": "text/html"
						},
						"features": {}
					}
				}
			}
		}
		""".trimIndent()

		experiencesServer.enqueue(MockResponse().setBody(json))

		val jsonResponse = apiClient.fetch("https://test.com/", emptyMap())!!
		val result = responseParser.parse(jsonResponse)

		val experience = result.experiences.first()
		assertEquals(contentUrl, experience.contentUrl)
		assertNull(experience.resolvedContent)

		experience.resolve()
		assertEquals(htmlContent, experience.resolvedContent)
	}

	@Test
	fun `frequencyCap config is populated from response`() = runBlocking {
		val json = loadJson("experiences_elpais_response.json")
		experiencesServer.enqueue(MockResponse().setBody(json))

		val jsonResponse = apiClient.fetch("https://elpais.com/", emptyMap())!!
		val result = responseParser.parse(jsonResponse)

		frequencyCapManager.updateFrequencyCapConfig(result.frequencyCapConfig)
		frequencyCapManager.trackImpression("some-exp")

		val uexp = frequencyCapManager.buildUexp()
		assertTrue(uexp.isNotEmpty())
	}

	@Test
	fun `pipeline with 20m response`() = runBlocking {
		val json = loadJson("experiences_20m_response.json")
		experiencesServer.enqueue(MockResponse().setBody(json))

		val jsonResponse = apiClient.fetch("https://20minutos.es/", emptyMap())
		assertNotNull(jsonResponse)

		val result = responseParser.parse(jsonResponse!!)
		assertTrue(result.experiences.isNotEmpty())

		frequencyCapManager.updateFrequencyCapConfig(result.frequencyCapConfig)
		experimentManager.handleExperimentGroups(result.experimentGroups)

		val filtered = experimentManager.filterByExperiments(result.experiences)
		assertTrue(filtered.isNotEmpty())
	}

	@Test
	fun `empty response returns empty list`() = runBlocking {
		experiencesServer.enqueue(MockResponse().setBody("{}"))

		val jsonResponse = apiClient.fetch("https://test.com/", emptyMap())!!
		val result = responseParser.parse(jsonResponse)
		assertTrue(result.experiences.isEmpty())
	}

	@Test
	fun `server error returns null from apiClient`() {
		experiencesServer.enqueue(MockResponse().setResponseCode(500))
		val result = apiClient.fetch("https://test.com/", emptyMap())
		assertNull(result)
	}
}
