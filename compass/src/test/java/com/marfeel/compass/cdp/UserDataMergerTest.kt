package com.marfeel.compass.cdp

import com.marfeel.compass.cdp.model.CdpIdentityResponse
import io.mockk.coEvery
import io.mockk.coVerify
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

/**
 * Part B end to end against a real [CdpManager]: the views the tracker exposes as
 * `getUserSegments()` / `getUserVars()` and the beacon sends as `useg` / `uvar`.
 */
internal class UserDataMergerTest {
	private lateinit var env: CdpTestEnv
	private var cdpEnabled = true
	private val ownedSegments = mutableListOf<String>()
	private val ownedVars = linkedMapOf<String, String>()
	private lateinit var merger: UserDataMerger

	private val hundredServer = (1..100).map { "srv$it" }

	@Before
	fun setUp() {
		env = CdpTestEnv()
		env.consent = true
		cdpEnabled = true
		merger = UserDataMerger(
			cdpEnabled = { cdpEnabled },
			readOwnedSegments = { ownedSegments.toList() },
			readOwnedVars = { ownedVars.toMap() },
			listServerSegments = { env.manager.serverSegments },
			getServerSegments = { env.manager.getServerSegments() },
			listServerProperties = { env.manager.serverProperties },
			getServerProperties = { env.manager.getServerProperties() },
			trimmer = SegmentTrimmer(
				readOwnedUserVars = { ownedVars.toMap() },
				setUserVar = { name, value -> ownedVars[name] = value },
				removeUserVar = { name -> ownedVars.remove(name) }
			)
		)
	}

	private fun serverHas(segments: List<String>, properties: Map<String, String> = emptyMap()) {
		env.masterId = UUID_A
		env.manager.syncServerSegments(segments)
		env.manager.syncServerProperties(properties)
	}

	@Test
	fun `segments merge server ahead of device-owned and dedupe`() {
		ownedSegments += listOf("device", "both")
		serverHas(listOf("both", "server"))
		assertEquals(listOf("both", "server", "device"), merger.segments())
	}

	@Test
	fun `with 100 server and 1 device segment the device one is dropped and the flag set`() {
		ownedSegments += "device"
		serverHas(hundredServer)

		val out = merger.segments()

		assertEquals(hundredServer, out)
		assertEquals("true", ownedVars[MRF_TOO_MANY_SEGMENTS])
	}

	@Test
	fun `flag is cleared once the union fits again, and not re-set while still over`() {
		ownedSegments += "device"
		serverHas(hundredServer)
		merger.segments()
		ownedVars[MRF_TOO_MANY_SEGMENTS] = "sentinel"
		merger.segments()
		assertEquals("sentinel", ownedVars[MRF_TOO_MANY_SEGMENTS]) // not re-written

		serverHas(hundredServer.take(10))
		merger.segments()
		assertFalse(ownedVars.containsKey(MRF_TOO_MANY_SEGMENTS))
	}

	@Test
	fun `the trim never touches the stores, only the read-out`() {
		ownedSegments += "device"
		serverHas(hundredServer)
		merger.segments()
		assertEquals(listOf("device"), ownedSegments)
		assertEquals(hundredServer, env.manager.serverSegments)
	}

	@Test
	fun `segmentsAsync resolves identity first and applies the same trim`() = runBlocking {
		ownedSegments += "device"
		coEvery { env.api.resolve(any()) } returns CdpIdentityResponse(UUID_A, null, emptyList(), segments = hundredServer)

		val out = merger.segmentsAsync()

		coVerify(exactly = 1) { env.api.resolve(any()) }
		assertEquals(hundredServer, out)
		assertEquals("true", ownedVars[MRF_TOO_MANY_SEGMENTS])
	}

	@Test
	fun `when CDP is disabled the server side is ignored but the cap still applies`() {
		cdpEnabled = false
		serverHas(listOf("server"))
		ownedSegments += (1..101).map { "d$it" }

		val out = merger.segments()

		assertEquals(100, out.size)
		assertFalse(out.contains("server"))
		assertEquals("true", ownedVars[MRF_TOO_MANY_SEGMENTS])
	}

	@Test
	fun `vars append server properties after device-owned, device-owned wins on collision`() {
		ownedVars["email"] = "device@site.com"
		ownedVars["plan"] = "premium"
		serverHas(emptyList(), mapOf("email" to "crm@site.com", "role" to "editor"))

		assertEquals(
			linkedMapOf("email" to "device@site.com", "plan" to "premium", "role" to "editor"),
			merger.vars()
		)
	}

	@Test
	fun `varsAsync resolves first and vars are device-only when CDP is disabled`() = runBlocking {
		ownedVars["plan"] = "premium"
		coEvery { env.api.resolve(any()) } returns CdpIdentityResponse(UUID_A, null, emptyList(), properties = mapOf("role" to "editor"))
		assertEquals(mapOf("plan" to "premium", "role" to "editor"), merger.varsAsync())

		cdpEnabled = false
		assertEquals(mapOf("plan" to "premium"), merger.vars())
		assertTrue(merger.vars().keys.none { it == "role" })
	}
}
