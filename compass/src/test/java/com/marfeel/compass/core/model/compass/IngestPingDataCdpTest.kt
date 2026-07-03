package com.marfeel.compass.core.model.compass

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.marfeel.compass.core.model.registerPingDataSerializer
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test

class IngestPingDataCdpTest {
	private val gson = GsonBuilder()
		.registerTypeAdapter(IngestPingData::class.java, IngestPingDataSerializer())
		.registerPingDataSerializer()
		.create()

	private fun pingData(
		cdpMasterId: String? = null,
		cdpRfv: String? = null,
		cdpCohorts: String? = null
	) = IngestPingData(
		accountId = "1",
		sessionTimeStamp = 0L,
		url = "https://x.com",
		canonicalUrl = "https://x.com",
		previousUrl = "",
		pageId = "p1",
		originalUserId = "u1",
		sessionId = "s1",
		pingCounter = 0,
		currentTimeStamp = 0L,
		userType = UserType.Anonymous,
		registeredUserId = "",
		pageVars = emptyMap(),
		sessionVars = emptyMap(),
		userVars = emptyMap(),
		userSegments = listOf("legacy-seg"),
		scrollPercent = 0,
		firsVisitTimeStamp = 0L,
		previousSessionTimeStamp = null,
		timeOnPage = 0,
		pageStartTimeStamp = 0L,
		conversion = null,
		conversionId = null,
		version = "1",
		pageType = 100,
		userConsent = true,
		landingPage = null,
		recirculationSource = null,
		cc = 1,
		pageMetrics = emptyMap(),
		cdpMasterId = cdpMasterId,
		cdpRfv = cdpRfv,
		cdpCohorts = cdpCohorts
	)

	private fun serialize(data: IngestPingData): JsonObject =
		gson.toJsonTree(data).asJsonObject

	@Test
	fun `cdp fields appear when populated`() {
		val json = serialize(
			pingData(
				cdpMasterId = "550e8400-e29b-41d4-a716-446655440000",
				cdpRfv = "{\"rfv\":42}",
				cdpCohorts = "[101,204]"
			)
		)
		assertEquals("550e8400-e29b-41d4-a716-446655440000", json.get("cdp_mid").asString)
		assertEquals("{\"rfv\":42}", json.get("cdp_rfv").asString)
		assertEquals("[101,204]", json.get("cdp_cohorts").asString)
	}

	@Test
	fun `cdp fields are omitted when null`() {
		val json = serialize(pingData())
		assertFalse(json.has("cdp_mid"))
		assertFalse(json.has("cdp_rfv"))
		assertFalse(json.has("cdp_cohorts"))
	}

	@Test
	fun `legacy useg is unaffected by cdp fields`() {
		val json = serialize(pingData(cdpMasterId = "550e8400-e29b-41d4-a716-446655440000"))
		assertTrue(json.has("useg"))
	}
}
