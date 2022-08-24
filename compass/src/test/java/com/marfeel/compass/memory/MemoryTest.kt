package com.marfeel.compass.memory

import junit.framework.TestCase.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.*

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
}
