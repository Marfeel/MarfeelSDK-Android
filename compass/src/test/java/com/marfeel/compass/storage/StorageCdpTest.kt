package com.marfeel.compass.storage

import android.content.Context
import com.marfeel.compass.cdp.model.CdpRfv
import io.mockk.every
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.security.Security

internal class StorageCdpTest {

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

	private val validUuid = "550e8400-e29b-41d4-a716-446655440000"

	@Test
	fun `master_id round-trips when a valid UUID`() {
		storage.writeCdpMasterId(validUuid)
		assertEquals(validUuid, storage.readCdpMasterId())
	}

	@Test
	fun `master_id reads as absent when not a UUID`() {
		storage.writeCdpMasterId("not-a-uuid")
		assertNull(storage.readCdpMasterId())
	}

	@Test
	fun `writeCdpMasterId returns the old value`() {
		val other = "550e8400-e29b-41d4-a716-446655440001"
		storage.writeCdpMasterId(validUuid)
		val old = storage.writeCdpMasterId(other)
		assertEquals(validUuid, old)
		assertEquals(other, storage.readCdpMasterId())
	}

	@Test
	fun `cached identity round-trips within the same session`() {
		storage.writeCdpCachedIdentity(CdpRfv(42, 3, 5, 7), listOf(101, 204), "session-1")
		val cached = storage.readCdpCachedIdentity("session-1")!!
		assertEquals(42, cached.rfv?.rfv)
		assertEquals(listOf(101, 204), cached.cohorts)
	}

	@Test
	fun `cached identity reads as absent in a new session`() {
		storage.writeCdpCachedIdentity(CdpRfv(42, 3, 5, 7), listOf(101), "session-1")
		assertNull(storage.readCdpCachedIdentity("session-2"))
	}

	@Test
	fun `cached identity is null when nothing cached`() {
		assertNull(storage.readCdpCachedIdentity("session-1"))
	}

	@Test
	fun `cached identity tolerates null rfv with valid cohorts`() {
		storage.writeCdpCachedIdentity(null, listOf(7), "session-1")
		val cached = storage.readCdpCachedIdentity("session-1")!!
		assertNull(cached.rfv)
		assertEquals(listOf(7), cached.cohorts)
	}
}
