package com.marfeel.compass.sessionStorage

import com.marfeel.compass.core.model.compass.Page
import com.marfeel.compass.core.model.compass.Session
import com.marfeel.compass.core.model.compass.currentTimeStampInSeconds
import com.marfeel.compass.storage.SessionStorage
import com.marfeel.compass.storage.Storage
import io.mockk.every
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.*
import junit.framework.TestCase.assertNotNull

internal class SessionStorageTest {

	private lateinit var sessionStorage: SessionStorage
	private val storage = mockk<Storage>()

	@Before
	fun setup() {
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
	fun `clearTrackedConversions will not remove not tracked conversions`() {
		val trackedConversions = listOf("First item", "Second item")
		val notTrackedConversion = "Another not tracked conversion"
		sessionStorage.addPendingConversion(trackedConversions[0])
		sessionStorage.addPendingConversion(trackedConversions[1])
		sessionStorage.addPendingConversion(notTrackedConversion)

		sessionStorage.clearTrackedConversions(trackedConversions)
		assertEquals(1, sessionStorage.readPendingConversions().size)
		assertEquals(notTrackedConversion, sessionStorage.readPendingConversions().first())
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
