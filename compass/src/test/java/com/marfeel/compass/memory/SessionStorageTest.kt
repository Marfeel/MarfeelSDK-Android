package com.marfeel.compass.sessionStorage

import com.marfeel.compass.core.model.compass.ConversionOptions
import com.marfeel.compass.core.model.compass.ConversionScope
import com.marfeel.compass.core.model.compass.Page
import com.marfeel.compass.core.model.compass.Session
import com.marfeel.compass.core.model.compass.currentTimeStampInSeconds
import com.marfeel.compass.storage.SessionStorage
import com.marfeel.compass.storage.Storage
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.*
import junit.framework.TestCase.assertNotNull

internal class SessionStorageTest {

	private lateinit var sessionStorage: SessionStorage
	private val storage = mockk<Storage>(relaxUnitFun = true)
	private val sessionVars = mutableMapOf<String, String>()
	private var storedSession: Session? = null
	private val trackedConversions = mutableSetOf<String>()

	@Before
	fun setup() {
		sessionVars.clear()
		storedSession = null
		every { storage.readSession() } answers { storedSession }
		every { storage.setSession(any()) } answers { storedSession = firstArg() }
		every { storage.readLastPingTimeStamp() } returns null
		every { storage.setLandingPage(any()) } just Runs
		every { storage.readSessionVars() } answers { sessionVars.toMap() }
		every { storage.setSessionVar(any(), any()) } answers {
			sessionVars[firstArg()] = secondArg()
		}
		every { storage.clearSessionVars() } answers { sessionVars.clear() }
		trackedConversions.clear()
		every { storage.readTrackedConversions() } answers { trackedConversions.toSet() }
		every { storage.addTrackedConversion(any()) } answers { trackedConversions.add(firstArg()) }
		every { storage.readOriginalUserId() } returns "original-user"
		sessionStorage = SessionStorage(storage)
	}

	@Test
	fun `readAccountId returns null if not set previously`() {
		assertEquals(null, sessionStorage.readAccountId())
	}

	@Test
	fun `updates accountId`() {
		val savedAccountId = UUID.randomUUID().toString()
		sessionStorage.updateAccountId(savedAccountId)
		val retrievedUserType = sessionStorage.readAccountId()

		assertEquals(savedAccountId, retrievedUserType)
	}

	@Test
	fun `readSession returns new session if not set previously`() {
		assertNotNull(sessionStorage.readSession())
	}

	@Test
	fun `updates session`() {
		val savedSession = Session(
			id = UUID.randomUUID().toString(),
			timeStamp = currentTimeStampInSeconds()
		)
		every { storage.readLastPingTimeStamp() } returns null
		sessionStorage.updateSession(savedSession)
		val retrievedSession = sessionStorage.readSession()

		assertEquals(savedSession, retrievedSession)
	}


	@Test
	fun `readPage returns null if not set previously`() {
		assertEquals(null, sessionStorage.readPage())
	}

	@Test
	fun `updates page`() {
		val savedPage = Page("First url")
		sessionStorage.updatePage(savedPage)
		val retrievedPage = sessionStorage.readPage()

		assertEquals(savedPage, retrievedPage)
	}

	@Test
	fun `readPreviousUrl returns null if not set previously`() {
		assertEquals(null, sessionStorage.readPreviousUrl())
	}

	@Test
	fun `updates previousUrl when page is updated and was not null before`() {
		val previousUrl = "Any previous url"
		sessionStorage.updatePage(Page(previousUrl))
		val newUrl = "New url"
		sessionStorage.updatePage(Page(newUrl))
		val retrievedUrl = sessionStorage.readPreviousUrl()

		assertEquals(previousUrl, retrievedUrl)
	}

	@Test
	fun `readPendingConversions returns emptyList if not set previously`() {
		assertEquals(0, sessionStorage.readPendingConversions().size)
	}

	@Test
	fun `readPendingConversions returns as many items as added previously`() {
		sessionStorage.addPendingConversion("First item")
		sessionStorage.addPendingConversion("Second item")
		assertEquals(2, sessionStorage.readPendingConversions().size)
	}

	@Test
	fun `settling a sent conversion removes only that one`() {
		sessionStorage.addPendingConversion("First item")
		sessionStorage.addPendingConversion("First item")
		val claimed = sessionStorage.claimPendingConversions()

		sessionStorage.settleConversion(claimed[0].key, sent = true)

		assertEquals(listOf("First item"), sessionStorage.readPendingConversions().map { it.name })
	}

	@Test
	fun `a claimed conversion is not claimed again until it is settled unsent`() {
		sessionStorage.addPendingConversion("purchase")
		val claimed = sessionStorage.claimPendingConversions().single()

		assertTrue(sessionStorage.claimPendingConversions().isEmpty())

		sessionStorage.settleConversion(claimed.key, sent = false)

		assertEquals("purchase", sessionStorage.claimPendingConversions().single().conversion.name)
	}

	@Test
	fun `a conversion id is marked as tracked only once it is sent`() {
		assertTrue(sessionStorage.addPendingConversion("purchase", ConversionOptions(id = "tx1")))
		val claimed = sessionStorage.claimPendingConversions().single()
		assertTrue(trackedConversions.isEmpty())

		sessionStorage.settleConversion(claimed.key, sent = true)

		assertEquals(setOf("purchase:tx1"), trackedConversions)
		assertTrue(sessionStorage.readPendingConversions().isEmpty())
	}

	@Test
	fun `a conversion id is rejected while pending and once tracked`() {
		assertTrue(sessionStorage.addPendingConversion("purchase", ConversionOptions(id = "tx1")))
		assertFalse(sessionStorage.addPendingConversion("purchase", ConversionOptions(id = "tx1")))
		assertTrue(sessionStorage.addPendingConversion("signup", ConversionOptions(id = "tx1")))

		trackedConversions.add("lead:tx2")
		assertFalse(sessionStorage.addPendingConversion("lead", ConversionOptions(id = "tx2")))

		assertEquals(listOf("purchase", "signup"), sessionStorage.readPendingConversions().map { it.name })
	}

	@Test
	fun `scope ids resolve when the conversion is tracked`() {
		val page = Page("https://x.com/paywall")
		sessionStorage.updatePage(page)

		sessionStorage.addPendingConversion("byPage", ConversionOptions(scope = ConversionScope.Page))
		sessionStorage.addPendingConversion("bySession", ConversionOptions(scope = ConversionScope.Session))
		sessionStorage.addPendingConversion("byUser", ConversionOptions(scope = ConversionScope.User))
		sessionStorage.updatePage(Page("https://x.com/home"))

		val ids = sessionStorage.readPendingConversions().associate { it.name to it.id }
		assertEquals(page.pageId, ids["byPage"])
		assertEquals(sessionStorage.readSession().id, ids["bySession"])
		assertEquals("original-user", ids["byUser"])
		assertTrue(sessionStorage.readPendingConversions().all { it.pageId == page.pageId })
	}

	@Test
	fun `a scope id dedups like an explicit id`() {
		sessionStorage.updatePage(Page("https://x.com/paywall"))

		assertTrue(sessionStorage.addPendingConversion("purchase", ConversionOptions(scope = ConversionScope.Page)))
		assertFalse(sessionStorage.addPendingConversion("purchase", ConversionOptions(scope = ConversionScope.Page)))
	}

	@Test
	fun `reading and setting pagevars`() {
		sessionStorage.addPageVar("pepe", "pepa")
		sessionStorage.addPageVar("lolo", "lola")

		assertEquals(mapOf("pepe" to "pepa", "lolo" to "lola"), sessionStorage.readPageVars())

		sessionStorage.clearPageVars()

		assertEquals(emptyMap<String, String>(), sessionStorage.readPageVars())
	}

	@Test
	fun `reading and setting pageMetrics`() {
		sessionStorage.addPageMetric("pepe", 1)
		sessionStorage.addPageMetric("lolo", 2)

		assertEquals(mapOf("pepe" to 1, "lolo" to 2), sessionStorage.readPageMetrics())

		sessionStorage.clearPageMetrics()

		assertEquals(emptyMap<String, Int>(), sessionStorage.readPageMetrics())
	}

	@Test
	fun `reading and setting session vars`() {
		sessionStorage.addSessionVar("pepe", "pepa")
		sessionStorage.addSessionVar("lolo", "lola")

		assertEquals(mapOf("pepe" to "pepa", "lolo" to "lola"), sessionStorage.readSessionVars())

		sessionStorage.clearSessionVars()

		assertEquals(emptyMap<String, String>(), sessionStorage.readSessionVars())
	}

	@Test
	fun `reading and setting page type`() {
		sessionStorage.setPageTechnology(101)

		assertEquals(101, sessionStorage.readPageTechnology())
	}
}
