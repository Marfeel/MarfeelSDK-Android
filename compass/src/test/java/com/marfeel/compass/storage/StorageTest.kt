package com.marfeel.compass.storage

import androidx.test.core.app.ApplicationProvider
import com.marfeel.compass.core.model.compass.UserType
import com.marfeel.compass.core.model.compass.currentTimeStampInSeconds
import junit.framework.Assert.assertNull

import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
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
	fun `reads, sets userVars` () {
		storage.setUserVar("pepe", "pepa")
		storage.setUserVar("lolo", "lola")

		assertEquals(mapOf("pepe" to "pepa", "lolo" to "lola"), storage.readUserVars())
	}

	@Test
	fun `reads, sets userSegments` () {
		storage.setUserSegment("pepe")
		storage.setUserSegment("pepa")

		assertEquals(listOf("pepe", "pepa"), storage.readUserSegments())

		storage.removeUserSegment("pepe")

		assertEquals(listOf("pepa"), storage.readUserSegments())

		storage.clearUserSegments()

		assertEquals(listOf<String>(), storage.readUserSegments())
	}

	@Test
	fun `reads, sets userConsent` () {
		assertNull(storage.readUserConsent())

		storage.updateUserConsent(true)

		assertTrue(storage.readUserConsent()!!)

		storage.updateUserConsent(false)

		assertFalse(storage.readUserConsent()!!)
	}
}
