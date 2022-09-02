package com.marfeel.compass.storage

import androidx.test.core.app.ApplicationProvider
import com.marfeel.compass.core.UserType
import com.marfeel.compass.core.currentTimeStampInSeconds
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertNotNull
import kotlinx.coroutines.Dispatchers
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.Security
import java.util.*

@RunWith(RobolectricTestRunner::class)
internal class StorageTest {

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
		storage =
			Storage(ApplicationProvider.getApplicationContext(), Dispatchers.Unconfined)
	}

	@Test
	fun `firstSession TimeStamp is updated if read but not previously set`() {
		assertNotNull(storage.readFirstSessionTimeStamp())
	}

	@Test
	fun `updates firstSession TimeStamp`() {
		val timeStamp = currentTimeStampInSeconds()
		storage.updateFirstSessionTimeStamp(timeStamp)

		assertEquals(timeStamp, storage.readFirstSessionTimeStamp())
	}

//	@Test
//	fun `previousSession TimeStamp is null if not set`() {
//		assertEquals(null, storage.readPreviousSessionTimeStamp())
//	}
//
//	@Test
//	fun `updates previousSession TimeStamp`() {
//		val timeStamp = currentTimeStampInSeconds()
//		storage.updatePreviousSessionTimeStamp(timeStamp)
//
//		assertEquals(timeStamp, storage.readPreviousSessionTimeStamp())
//	}

	@Test
	fun `readOriginalUserId returns random id if not set previously`() {
		val returnedUserId = storage.readOriginalUserId()
		assertNotNull(returnedUserId)
	}

	@Test
	fun `readUserId returns registeredUserId if has been set`() {
		val newUserId = "NewUserID"
		storage.updateUserId(newUserId)
		val returnedUserId = storage.readUserId()
		assertEquals(newUserId, returnedUserId)
	}

	@Test
	fun `readRegisteredUserId returns null id if not set previously`() {
		val returnedUserId = storage.readRegisteredUserId()
		assertEquals(null, returnedUserId)
	}

	@Test
	fun `the random userId is stored and returned everytime`() {
		val firstReturnedUserId = storage.readUserId()
		val secondReturnedUserId = storage.readUserId()
		assertEquals(firstReturnedUserId, secondReturnedUserId)
	}

	@Test
	fun `updates userId`() {
		val savedUserId = UUID.randomUUID().toString()
		storage.updateUserId(savedUserId)
		val retrievedUserId = storage.readUserId()

		assertEquals(savedUserId, retrievedUserId)
	}

	@Test
	fun `readUserType returns Anonymous if not set previously`() {
		val returnedUserType = storage.readUserType()
		assert(returnedUserType is UserType.Anonymous)
	}

	@Test
	fun `updates userType`() {
		val savedUserType = UserType.Paid
		storage.updateUserType(savedUserType)
		val retrievedUserType = storage.readUserType()

		assertEquals(savedUserType, retrievedUserType)
	}

	@Test
	fun `updates previous session last timeStamp when new session timestamp is greater than last ping timestamp`() {
		val lastPingTimeStamp = currentTimeStampInSeconds()
		storage.updateLastPingTimeStamp(lastPingTimeStamp)
		storage.updateCurrentSessionTimeStamp(currentTimeStampInSeconds() + 10L)

		assertEquals(storage.readPreviousSessionLastPingTimeStamp(), lastPingTimeStamp)

	}
}
