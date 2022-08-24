package com.marfeel.compass.memory

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
}
