package com.marfeel.compass.usecase

import com.marfeel.compass.cdp.CdpTestEnv
import com.marfeel.compass.core.model.compass.ConversionOptions
import com.marfeel.compass.core.model.compass.IngestPingData
import com.marfeel.compass.core.model.compass.Page
import com.marfeel.compass.core.model.compass.Session
import com.marfeel.compass.core.model.compass.currentTimeStampInSeconds
import com.marfeel.compass.core.ping.IngestPingEmitterState
import com.marfeel.compass.di.CompassComponent
import com.marfeel.compass.network.ApiClient
import com.marfeel.compass.storage.SessionStorage
import com.marfeel.compass.storage.Storage
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * A conversion stays queued until a ping carrying it reaches the server, is sent by one
 * ping only, and keeps the page it was tracked on when a later page sends it.
 */
internal class IngestPingConversionTest {
	private val storage = mockk<Storage>(relaxed = true)
	private val apiClient = mockk<ApiClient>()
	private val sessionStorage = SessionStorage(storage)
	private val trackedConversions = mutableSetOf<String>()
	private val pings = mutableListOf<IngestPingData>()
	private var respond: (IngestPingData) -> Boolean = { true }
	private lateinit var ingestPing: IngestPing

	private val paywall = Page("https://x.com/paywall")
	private val home = Page("https://x.com/home")

	@Before
	fun setUp() {
		every { storage.readSession() } returns Session("s1", 1L)
		every { storage.readOriginalUserId() } returns "u1"
		every { storage.readFirstSessionTimeStamp() } returns 1L
		every { storage.readTrackedConversions() } answers { trackedConversions.toSet() }
		every { storage.addTrackedConversion(any()) } answers { trackedConversions.add(firstArg()) }
		every { apiClient.ingestPing(any()) } answers {
			val ping = firstArg<IngestPingData>()
			pings.add(ping)
			respond(ping)
		}
		sessionStorage.updateAccountId("456")
		sessionStorage.setPageTechnology(4)

		mockkObject(CompassComponent)
		every { CompassComponent.cdpManager } returns CdpTestEnv().manager

		ingestPing = IngestPing(
			apiClient,
			sessionStorage,
			storage,
			userSegmentsProvider = { emptyList() },
			userVarsProvider = { emptyMap() }
		)
	}

	@After
	fun tearDown() {
		unmockkObject(CompassComponent)
	}

	private fun state(page: Page, timeOnPage: Long = 0L) =
		IngestPingEmitterState(page.url, page.pageId, "s1", 0, currentTimeStampInSeconds() - timeOnPage, 0L)

	/** What `trackNewPage` does to the ping pipeline, in the same order. */
	private fun open(page: Page, leaving: IngestPingEmitterState? = null) {
		leaving?.let(ingestPing::closePage)
		sessionStorage.updatePage(page)
		sessionStorage.clearPageVars()
		ingestPing.resetPing()
	}

	private fun conversionPings(name: String) = pings.filter { it.conversion?.name == name }

	@Test
	fun `a conversion of the current page rides in the page's ping`() {
		open(paywall)
		sessionStorage.addPendingConversion("purchase")

		ingestPing(state(paywall))

		assertEquals(1, pings.size)
		assertEquals("purchase", pings[0].conversion?.name)
		assertEquals(0, pings[0].pingCounter)
	}

	@Test
	fun `a conversion whose ping fails is retried and only marked tracked once sent`() {
		open(paywall)
		sessionStorage.addPendingConversion("purchase", ConversionOptions(id = "tx1"))
		respond = { it.conversion == null }

		ingestPing(state(paywall))

		assertTrue(trackedConversions.isEmpty())
		assertEquals(1, sessionStorage.readPendingConversions().size)

		respond = { true }
		ingestPing(state(paywall))
		ingestPing(state(paywall))

		assertEquals(setOf("purchase:tx1"), trackedConversions)
		assertEquals(2, conversionPings("purchase").size)
		assertEquals("tx1", conversionPings("purchase").last().conversionId)
	}

	@Test
	fun `overlapping pings send a conversion once`() {
		open(paywall)
		sessionStorage.addPendingConversion("purchase")
		var overlapped = false
		respond = {
			if (it.conversion != null && !overlapped) {
				overlapped = true
				ingestPing(state(paywall))
			}
			true
		}

		ingestPing(state(paywall))

		assertEquals(1, conversionPings("purchase").size)
		assertEquals(2, pings.size)
	}

	@Test
	fun `a conversion pending when the page changes is sent with its own page`() {
		open(paywall)
		sessionStorage.addPageVar("step", "paywall")
		sessionStorage.addPendingConversion("purchase", ConversionOptions(id = "tx1"))
		respond = { it.conversion == null }
		ingestPing(state(paywall))
		val failed = conversionPings("purchase").single()

		open(home, leaving = state(paywall, timeOnPage = 30L))
		respond = { true }
		ingestPing(state(home))

		val sent = conversionPings("purchase").last()
		assertEquals(paywall.url, sent.url)
		assertEquals(paywall.pageId, sent.pageId)
		assertEquals(mapOf("step" to "paywall"), sent.pageVars)
		assertEquals(30, sent.timeOnPage)
		assertEquals(failed.pingCounter!! + 1, sent.pingCounter)

		val homePing = pings.last { it.pageId == home.pageId }
		assertNull(homePing.conversion)
		assertEquals(0, homePing.pingCounter)
		assertEquals(emptyMap<String, String>(), homePing.pageVars)
		assertEquals(setOf("purchase:tx1"), trackedConversions)

		pings.clear()
		ingestPing(state(home))
		assertEquals(listOf(home.pageId), pings.map { it.pageId })
	}
}
