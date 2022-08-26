package com.marfeel.compass.storage

import androidx.test.core.app.ApplicationProvider
import com.marfeel.compass.core.UserType
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
		val timeStamp = System.currentTimeMillis()
		storage.updateFirstSessionTimeStamp(timeStamp)

		assertEquals(timeStamp, storage.readFirstSessionTimeStamp())
	}

	@Test
	fun `previousSession TimeStamp is null if not set`() {
		assertEquals(null, storage.readPreviousSessionTimeStamp())
	}

	@Test
	fun `updates previousSession TimeStamp`() {
		val timeStamp = System.currentTimeMillis()
		storage.updatePreviousSessionTimeStamp(timeStamp)

		assertEquals(timeStamp, storage.readPreviousSessionTimeStamp())
	}

	@Test
	fun `readUserId returns random id if not set previously`() {
		val returnedUserId = storage.readUserId()
		assertNotNull(returnedUserId)
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
}
