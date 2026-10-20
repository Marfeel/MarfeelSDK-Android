package com.marfeel.compass.storage

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.security.Security

internal class StorageConversionsTest {
	private lateinit var context: Context

	companion object {
		@JvmStatic
		@BeforeClass
		fun beforeClass() {
			Security.addProvider(keyStoreProvider)
		}
	}

	@Before
	fun setup() {
		val preferences = MockSharedPreference()
		context = mockk()
		every { context.getSharedPreferences(any(), any()) } returns preferences
	}

	@Test
	fun `tracked conversions survive an app restart`() {
		Storage(context).addTrackedConversion("purchase:tx1")

		assertEquals(setOf("purchase:tx1"), Storage(context).readTrackedConversions())
	}

	@Test
	fun `adding a tracked conversion keeps the previous ones once`() {
		val storage = Storage(context)
		storage.addTrackedConversion("purchase:tx1")
		storage.addTrackedConversion("purchase:tx2")
		storage.addTrackedConversion("purchase:tx1")

		assertEquals(setOf("purchase:tx1", "purchase:tx2"), storage.readTrackedConversions())
	}

	@Test
	fun `tracked conversions are kept on user reset`() {
		val storage = Storage(context)
		storage.addTrackedConversion("purchase:tx1")

		storage.resetUser()

		assertEquals(setOf("purchase:tx1"), storage.readTrackedConversions())
	}
}
