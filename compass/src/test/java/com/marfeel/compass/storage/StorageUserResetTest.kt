package com.marfeel.compass.storage

import android.content.Context
import com.marfeel.compass.cdp.model.CdpRfv
import com.marfeel.compass.core.model.compass.Session
import com.marfeel.compass.core.model.compass.UserType
import io.mockk.every
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.security.Security

internal class StorageUserResetTest {
	private lateinit var storage: Storage

	companion object {
		@JvmStatic
		@BeforeClass
		fun beforeClass() {
			Security.addProvider(keyStoreProvider)
		}
	}

	@Before
	fun setup() {
		val context = mockk<Context>()
		every { context.getSharedPreferences(any(), any()) } returns MockSharedPreference()
		storage = Storage(context)
	}

	private fun populate() {
		storage.readOriginalUserId()
		storage.updateUserId("site-user-1")
		storage.updateUserType(UserType.Paid)
		storage.updateFirstSessionTimeStamp(100L)
		storage.updateLastPingTimeStamp(200L)
		storage.updatePreviousSessionLastPingTimeStamp(150L)
		storage.setUserVar("plan", "premium")
		storage.setUserSegment("sports")
		storage.setSession(Session("session-1", 300L))
		storage.setSessionVar("sv", "1")
		storage.setLandingPage("https://x.com/landing")
		storage.updateUserConsent(true)
		storage.writeCdpMasterId("550e8400-e29b-41d4-a716-446655440000")
		storage.writeCdpCachedIdentity(CdpRfv(1, 1, 1, 1), listOf(1), "session-1")
	}

	@Test
	fun `resetUser drops the registered id and mints a new internal user id on the next read`() {
		populate()
		val before = storage.readOriginalUserId()

		storage.resetUser()

		assertNull(storage.readRegisteredUserId())
		val after = storage.readOriginalUserId()
		assertFalse(before == after)
		assertEquals(after, storage.readOriginalUserId())
	}

	@Test
	fun `resetUser blanks the user-scoped fields`() {
		populate()
		storage.resetUser()

		assertEquals(UserType.Anonymous, storage.readUserType())
		assertTrue(storage.readUserVars().isEmpty())
		assertTrue(storage.readUserSegments().isEmpty())
		assertNull(storage.readLastPingTimeStamp())
		assertNull(storage.readPreviousSessionLastPingTimeStamp())
	}

	@Test
	fun `resetUser blanks the visit-scoped fields so the bootstrap re-derives them`() {
		populate()
		storage.resetUser()

		assertNull(storage.readSession())
		assertTrue(storage.readSessionVars().isEmpty())
		assertNull(storage.readLadingPage())
		// first visit re-derives to "now", never the old value
		assertFalse(storage.readFirstSessionTimeStamp() == 100L)
	}

	@Test
	fun `resetUser wipes the CDP identity but keeps the CMP consent`() {
		populate()
		storage.resetUser()

		assertNull(storage.readCdpMasterId())
		assertNull(storage.readCdpCachedIdentity("session-1"))
		assertEquals(true, storage.readUserConsent())
	}

	@Test
	fun `clearCdpCachedIdentity reads back as absent, not empty`() {
		storage.writeCdpCachedIdentity(CdpRfv(1, 1, 1, 1), listOf(1), "session-1")
		storage.clearCdpCachedIdentity()
		assertNull(storage.readCdpCachedIdentity("session-1"))
	}

	@Test
	fun `removeUserVar drops only the named var`() {
		storage.setUserVar("a", "1")
		storage.setUserVar("b", "2")
		storage.removeUserVar("a")
		storage.removeUserVar("ghost")
		assertEquals(mapOf("b" to "2"), storage.readUserVars())
	}

	@Test
	fun `clearRegisteredUserId leaves the internal user id alone`() {
		val internal = storage.readOriginalUserId()
		storage.updateUserId("site-user")
		storage.clearRegisteredUserId()
		assertNull(storage.readRegisteredUserId())
		assertEquals(internal, storage.readOriginalUserId())
	}
}
