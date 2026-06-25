package com.marfeel.compass.cdp

import com.marfeel.compass.cdp.model.MeterNotFoundError
import com.marfeel.compass.cdp.model.MeterState
import com.marfeel.compass.cdp.store.CdpMetersStore
import com.marfeel.compass.storage.MockSharedPreference
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

internal class MeteredCounterTest {
	private lateinit var api: CdpApiClient
	private lateinit var store: CdpMetersStore
	private lateinit var cdpManager: CdpManager
	private lateinit var counter: MeteredCounter

	private val account = "site1"
	private val masterId = "550e8400-e29b-41d4-a716-446655440000"

	@Before
	fun setUp() {
		api = mockk()
		store = CdpMetersStore(MockSharedPreference())
		cdpManager = mockk()
		every { cdpManager.hasConsent() } returns true
		every { cdpManager.currentAccountId() } returns account
		every { cdpManager.currentMasterId() } returns masterId
		counter = MeteredCounter(cdpManager, store, api)
	}

	@Test
	fun `not ready returns the current mirror without fetching`() = runBlocking {
		every { cdpManager.currentMasterId() } returns null
		val result = counter.getMeterSnapshot()
		assertEquals(emptyList<MeterState>(), result)
		coVerify(exactly = 0) { api.fetchMeters(any(), any()) }
	}

	@Test
	fun `seed fills the mirror from persistence for sync reads`() = runBlocking {
		store.write(account, masterId, listOf(MeterState(name = "paywall", count = 7)))
		counter.seed()
		assertEquals(7, counter.get("paywall")?.count)
	}

	@Test
	fun `snapshot fetches once and marks fresh`() = runBlocking {
		coEvery { api.fetchMeters(account, masterId) } returns listOf(MeterState(name = "p", count = 1))
		counter.getMeterSnapshot()
		counter.getMeterSnapshot()
		coVerify(exactly = 1) { api.fetchMeters(account, masterId) }
		assertEquals(1, counter.get("p")?.count)
	}

	@Test
	fun `snapshot persists the fetched meters`() = runBlocking {
		coEvery { api.fetchMeters(account, masterId) } returns listOf(MeterState(name = "p", count = 3))
		counter.getMeterSnapshot()
		assertEquals(3, store.read(account, masterId).single().count)
	}

	@Test
	fun `snapshot fails open and retries when the fetch fails`() = runBlocking {
		coEvery { api.fetchMeters(account, masterId) } returns null
		counter.getMeterSnapshot()
		counter.getMeterSnapshot()
		coVerify(exactly = 2) { api.fetchMeters(account, masterId) }
	}

	@Test
	fun `invalidate forces a re-fetch on the next snapshot`() = runBlocking {
		coEvery { api.fetchMeters(account, masterId) } returns listOf(MeterState(name = "p", count = 1))
		counter.getMeterSnapshot()
		counter.invalidate()
		counter.getMeterSnapshot()
		coVerify(exactly = 2) { api.fetchMeters(account, masterId) }
	}

	@Test
	fun `increment upserts the returned meter into the mirror`() = runBlocking {
		coEvery { api.incrementMeter("p", account, masterId) } returns
			IncrementResult(200, MeterState(name = "p", count = 5))
		val result = counter.increment("p")
		assertEquals(5, result?.count)
		assertEquals(5, counter.get("p")?.count)
		assertEquals(5, store.read(account, masterId).single().count)
	}

	@Test(expected = MeterNotFoundError::class)
	fun `increment throws MeterNotFoundError on 404`() = runBlocking {
		coEvery { api.incrementMeter("ghost", account, masterId) } returns IncrementResult(404, null)
		counter.increment("ghost")
		Unit
	}

	@Test
	fun `increment falls open to the mirror value on a non-404 failure`() = runBlocking {
		coEvery { api.fetchMeters(account, masterId) } returns listOf(MeterState(name = "p", count = 2))
		counter.getMeterSnapshot()
		coEvery { api.incrementMeter("p", account, masterId) } returns IncrementResult(500, null)
		val result = counter.increment("p")
		assertEquals(2, result?.count)
	}

	@Test
	fun `reset clears the in-memory mirror`() = runBlocking {
		coEvery { api.fetchMeters(account, masterId) } returns listOf(MeterState(name = "p", count = 1))
		counter.getMeterSnapshot()
		counter.reset()
		assertNull(counter.get("p"))
	}
}
