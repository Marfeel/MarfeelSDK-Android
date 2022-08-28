package com.marfeel.compass.memory

import com.marfeel.compass.core.Page
import com.marfeel.compass.core.Session
import junit.framework.TestCase.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.test.assertNotNull

internal class MemoryTest {

	private lateinit var memory: Memory

	@Before
	fun setup() {
		memory = Memory()
	}

	@Test
	fun `readAccountId returns null if not set previously`() {
		assertEquals(null, memory.readAccountId())
	}

	@Test
	fun `updates accountId`() {
		val savedAccountId = UUID.randomUUID().toString()
		memory.updateAccountId(savedAccountId)
		val retrievedUserType = memory.readAccountId()

		assertEquals(savedAccountId, retrievedUserType)
	}

	@Test
	fun `readSession returns new session if not set previously`() {
		assertNotNull(memory.readSession())
	}

	@Test
	fun `updates session`() {
		val savedSession = Session(
			id = UUID.randomUUID().toString(),
			timeStamp = System.currentTimeMillis()
		)
		memory.updateSession(savedSession)
		val retrievedSession = memory.readSession()

		assertEquals(savedSession, retrievedSession)
	}


	@Test
	fun `readPage returns null if not set previously`() {
		assertEquals(null, memory.readPage())
	}

	@Test
	fun `updates page`() {
		val savedPage = Page("First url")
		memory.updatePage(savedPage)
		val retrievedPage = memory.readPage()

		assertEquals(savedPage, retrievedPage)
	}

	@Test
	fun `readPreviousUrl returns null if not set previously`() {
		assertEquals(null, memory.readPreviousUrl())
	}

	@Test
	fun `updates previousUrl when page is updated and was not null before`() {
		val previousUrl = "Any previous url"
		memory.updatePage(Page(previousUrl))
		val newUrl = "New url"
		memory.updatePage(Page(newUrl))
		val retrievedUrl = memory.readPreviousUrl()

		assertEquals(previousUrl, retrievedUrl)
	}

	@Test
	fun `readPendingConversions returns emptyList if not set previously`() {
		assertEquals(0, memory.readPendingConversions().size)
	}

	@Test
	fun `readPendingConversions returns as many items as added previously`() {
		memory.addPendingConversion("First item")
		memory.addPendingConversion("Second item")
		assertEquals(2, memory.readPendingConversions().size)
	}

	@Test
	fun `addPendingConversion returns as emptyList after clearPendingConversions`() {
		memory.addPendingConversion("First item")
		memory.addPendingConversion("Second item")
		memory.clearPendingConversions()
		assertEquals(0, memory.readPendingConversions().size)
	}
}
