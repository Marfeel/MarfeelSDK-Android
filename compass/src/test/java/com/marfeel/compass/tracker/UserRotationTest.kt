package com.marfeel.compass.tracker

import android.content.Context
import com.marfeel.compass.cdp.model.CdpRfv
import com.marfeel.compass.core.model.compass.RFV
import com.marfeel.compass.core.model.compass.Session
import com.marfeel.compass.core.model.compass.UserType
import com.marfeel.compass.network.ApiClient
import com.marfeel.compass.storage.MockSharedPreference
import com.marfeel.compass.storage.SessionStorage
import com.marfeel.compass.storage.Storage
import com.marfeel.compass.storage.keyStoreProvider
import com.marfeel.compass.usecase.GetRFV
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.security.Security

/**
 * The synchronous local half of `resetUser()` against a real [Storage] +
 * [SessionStorage]: what a beacon fired right after must look like (A.7 in the spec).
 */
internal class UserRotationTest {
	private lateinit var storage: Storage
	private lateinit var sessionStorage: SessionStorage
	private val emitterSessions = mutableListOf<String>()
	private var rfvCleared = 0
	private var cdpCleared = 0

	companion object {
		@JvmStatic
		@BeforeClass
		fun beforeClass() {
			Security.addProvider(keyStoreProvider)
		}
	}

	@Before
	fun setUp() {
		val context = mockk<Context>()
		every { context.getSharedPreferences(any(), any()) } returns MockSharedPreference()
		storage = Storage(context)
		sessionStorage = SessionStorage(storage)
		sessionStorage.updateAccountId("456")
	}

	private fun rotation() = UserRotation(
		storage = storage,
		sessionStorage = sessionStorage,
		updateEmitterSession = { emitterSessions.add(it) },
		clearRfvCache = { rfvCleared++ },
		clearCdpIdentity = { cdpCleared++ }
	)

	private fun signedInUser() {
		storage.readOriginalUserId()
		storage.updateUserId("site-user-1")
		storage.updateUserType(UserType.Logged)
		storage.updateFirstSessionTimeStamp(100L)
		storage.setUserVar("plan", "premium")
		storage.setUserSegment("sports")
		sessionStorage.updateSession(Session("old-session", 300L))
		storage.updateLastPingTimeStamp(400L)
		storage.setLandingPage("https://x.com/landing")
		storage.writeCdpMasterId("550e8400-e29b-41d4-a716-446655440000")
		storage.writeCdpCachedIdentity(CdpRfv(1, 1, 1, 1), listOf(1), "old-session")
	}

	@Test
	fun `a beacon right after the rotation carries a new u, an empty sui, a new s and a new fv`() {
		signedInUser()
		val oldUserId = storage.readOriginalUserId()

		rotation().rotate()

		assertFalse(oldUserId == storage.readOriginalUserId())
		assertNull(storage.readRegisteredUserId())
		assertEquals("", storage.readRegisteredUserId() ?: "")
		val session = storage.readSession()
		assertNotNull(session)
		assertFalse("old-session" == session!!.id)
		assertFalse(storage.readFirstSessionTimeStamp() == 100L)
		assertEquals(UserType.Anonymous, storage.readUserType())
		assertTrue(storage.readUserVars().isEmpty())
		assertTrue(storage.readUserSegments().isEmpty())
	}

	@Test
	fun `the previous user's last ping never becomes the new user's lv`() {
		signedInUser()

		rotation().rotate()

		assertNull(storage.readPreviousSessionLastPingTimeStamp())
		assertNull(storage.readLastPingTimeStamp())
	}

	@Test
	fun `blanks the visit-scoped fields`() {
		signedInUser()
		storage.setSessionVar("sv", "1")

		rotation().rotate()

		assertTrue(storage.readSessionVars().isEmpty())
		assertNull(storage.readLadingPage())
	}

	@Test
	fun `re-points the running page at the new session`() {
		signedInUser()

		rotation().rotate()

		assertEquals(listOf(storage.readSession()!!.id), emitterSessions)
		assertFalse(emitterSessions.single() == "old-session")
	}

	@Test
	fun `drops the cached RFV and wipes the local CDP identity, in that order after the session`() {
		signedInUser()
		rotation().rotate()
		assertEquals(1, rfvCleared)
		assertEquals(1, cdpCleared)
	}

	@Test
	fun `keeps the CMP consent - it belongs to the device, not the user`() {
		storage.updateUserConsent(true)
		signedInUser()
		rotation().rotate()
		assertEquals(true, storage.readUserConsent())
	}

	@Test
	fun `is safe with no identity at all`() {
		rotation().rotate()
		assertNotNull(storage.readOriginalUserId())
		assertNotNull(storage.readSession())
		assertNull(storage.readRegisteredUserId())
	}

	@Test
	fun `GetRFV clearCache forces the next read past the memo`() {
		val api = mockk<ApiClient>()
		every { api.getRfv(any()) } returns Result.success(RFV(1f, 1, 1, 1))
		val getRfv = GetRFV(storage, sessionStorage, api)

		getRfv()
		getRfv()
		verify(exactly = 1) { api.getRfv(any()) }

		getRfv.clearCache()
		getRfv()
		verify(exactly = 2) { api.getRfv(any()) }
	}
}
