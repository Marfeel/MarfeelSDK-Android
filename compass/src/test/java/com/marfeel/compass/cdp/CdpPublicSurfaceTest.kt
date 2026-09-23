package com.marfeel.compass.cdp

import com.marfeel.compass.tracker.CompassTracking
import junit.framework.TestCase.assertTrue
import org.junit.Test

/**
 * The public surface is **add-only**: every name here is reachable from host apps, so
 * renaming or removing one breaks them silently. Editing this list is the agreed
 * moment to check who is already calling the old name.
 */
class CdpPublicSurfaceTest {
	private val cdpNames = listOf(
		// identity
		"setIdentity", "deleteIdentity", "getMasterId", "getUserProfile",
		// deprecated delegates — kept
		"cdpDoIdentityLink", "getCdpData", "getCdpMasterId",
		// identity types & hashing
		"getIdentityTypes", "normalizeEmail", "normalizePhone", "hashEmail", "hashPhone",
		// publisher consents
		"trackConsent", "getConsent", "hasConsent",
		// segments & properties
		"addCdpSegment", "removeCdpSegment", "setCdpSegments", "clearCdpSegments", "getCdpSegments",
		"listServerSegments", "getServerSegments", "listServerProperties", "getServerProperties",
		// meters
		"getMeterSnapshot", "getMeter", "listMeters", "incrementMeter"
	)

	private val trackingNames = listOf(
		"resetUser", "resetIdentity", "getUserSegments", "getUserSegmentsAsync", "getUserVars", "getUserVarsAsync",
		"setSiteUserId", "setUserVar", "addUserSegment", "setUserSegments", "removeUserSegment", "clearUserSegments",
		"setUserConsent", "getUserId", "getSessionId"
	)

	@Test
	fun `every name on Cdp stays put`() {
		val actual = Cdp::class.java.methods.map { it.name }.toSet()
		val missing = cdpNames.filterNot { it in actual }
		assertTrue("missing from Cdp: $missing", missing.isEmpty())
	}

	@Test
	fun `every CDP-adjacent name on CompassTracking stays put`() {
		val actual = CompassTracking::class.java.methods.map { it.name }.toSet()
		val missing = trackingNames.filterNot { it in actual }
		assertTrue("missing from CompassTracking: $missing", missing.isEmpty())
	}

	@Test
	fun `no flat trackCdpConsent and no getData remain`() {
		val cdp = Cdp::class.java.methods.map { it.name }.toSet()
		val tracking = CompassTracking::class.java.methods.map { it.name }.toSet()
		assertTrue(!cdp.contains("trackCdpConsent") && !tracking.contains("trackCdpConsent"))
		assertTrue(!cdp.contains("getData"))
	}
}
