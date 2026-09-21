package com.marfeel.compass.cdp

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test

class SegmentOwnershipTest {
	private val hundred = (1..100).map { "s$it" }

	// region mergeSegments

	@Test
	fun `mergeSegments unions server and owned, server first`() {
		assertEquals(listOf("server", "device"), SegmentOwnership.mergeSegments(listOf("device"), listOf("server")))
	}

	@Test
	fun `mergeSegments dedupes a segment present in both`() {
		assertEquals(listOf("both", "only-device"), SegmentOwnership.mergeSegments(listOf("both", "only-device"), listOf("both")))
	}

	@Test
	fun `mergeSegments tolerates null on either side`() {
		assertEquals(listOf("a"), SegmentOwnership.mergeSegments(null, listOf("a")))
		assertEquals(listOf("a"), SegmentOwnership.mergeSegments(listOf("a"), null))
		assertEquals(emptyList<String>(), SegmentOwnership.mergeSegments(null, null))
	}

	@Test
	fun `mergeSegments does not trim`() {
		val server = hundred + listOf("s101", "s102", "s103", "s104", "s105")
		assertEquals(106, SegmentOwnership.mergeSegments(listOf("device"), server).size)
	}

	// endregion

	// region rejectUnownedSegments

	@Test
	fun `rejectUnownedSegments drops a server-only segment`() {
		val next = SegmentOwnership.rejectUnownedSegments(listOf("mine", "server-only"), listOf("mine"), listOf("server-only"))
		assertEquals(listOf("mine"), next)
	}

	@Test
	fun `rejectUnownedSegments keeps a segment the device already owns even if the server has it too`() {
		val next = SegmentOwnership.rejectUnownedSegments(listOf("both"), listOf("both"), listOf("both"))
		assertEquals(listOf("both"), next)
	}

	@Test
	fun `rejectUnownedSegments passes everything through when the server set is empty or null`() {
		assertEquals(listOf("a", "b"), SegmentOwnership.rejectUnownedSegments(listOf("a", "b"), emptyList(), emptyList()))
		assertEquals(listOf("a", "b"), SegmentOwnership.rejectUnownedSegments(listOf("a", "b"), null, null))
	}

	// endregion

	// region limit

	@Test
	fun `isOverSegmentLimit is false at exactly the limit and true past it`() {
		assertFalse(SegmentOwnership.isOverSegmentLimit(hundred))
		assertTrue(SegmentOwnership.isOverSegmentLimit(hundred + "s101"))
	}

	@Test
	fun `trimSegments drops everything past the limit and leaves shorter lists untouched`() {
		assertEquals(hundred, SegmentOwnership.trimSegments(hundred + listOf("s101", "s102")))
		assertEquals(listOf("a"), SegmentOwnership.trimSegments(listOf("a")))
	}

	// endregion

	// region mergeVars

	@Test
	fun `mergeVars appends server properties after the device-owned ones`() {
		assertEquals(
			mapOf("plan" to "premium", "email" to "user@site.com"),
			SegmentOwnership.mergeVars(mapOf("plan" to "premium"), mapOf("email" to "user@site.com"))
		)
	}

	@Test
	fun `mergeVars lets device-owned win on a key collision`() {
		assertEquals(
			mapOf("email" to "device@site.com"),
			SegmentOwnership.mergeVars(mapOf("email" to "device@site.com"), mapOf("email" to "crm@site.com"))
		)
	}

	@Test
	fun `mergeVars keeps every non-colliding server key and tolerates null`() {
		assertEquals(
			mapOf("a" to "1", "b" to "2", "c" to "3"),
			SegmentOwnership.mergeVars(mapOf("a" to "1"), mapOf("b" to "2", "c" to "3"))
		)
		assertEquals(mapOf("a" to "1"), SegmentOwnership.mergeVars(mapOf("a" to "1"), null))
		assertEquals(mapOf("b" to "2"), SegmentOwnership.mergeVars(null, mapOf("b" to "2")))
	}

	// endregion
}

class SegmentTrimmerTest {
	private val vars = mutableMapOf<String, String>()
	private val setCalls = mutableListOf<Pair<String, String>>()
	private val removeCalls = mutableListOf<String>()
	private val trimmer = SegmentTrimmer(
		readOwnedUserVars = { vars.toMap() },
		setUserVar = { name, value -> vars[name] = value; setCalls.add(name to value) },
		removeUserVar = { name -> vars.remove(name); removeCalls.add(name) }
	)
	private val hundredServer = (1..100).map { "srv$it" }

	@Test
	fun `with 100 server and 1 device segment, the device one is dropped and the flag is set`() {
		val merged = SegmentOwnership.mergeSegments(listOf("device"), hundredServer)

		val out = trimmer.trim(merged)

		assertEquals(hundredServer, out)
		assertEquals(listOf(MRF_TOO_MANY_SEGMENTS to "true"), setCalls)
	}

	@Test
	fun `when the union fits the flag is not set`() {
		trimmer.trim(listOf("a", "b"))
		assertTrue(setCalls.isEmpty())
		assertTrue(removeCalls.isEmpty())
	}

	@Test
	fun `already flagged and still over does not re-flag`() {
		vars[MRF_TOO_MANY_SEGMENTS] = "true"
		trimmer.trim(hundredServer + "device")
		assertTrue(setCalls.isEmpty())
	}

	@Test
	fun `flagged and fits again removes the flag`() {
		vars[MRF_TOO_MANY_SEGMENTS] = "true"
		trimmer.trim(listOf("a"))
		assertEquals(listOf(MRF_TOO_MANY_SEGMENTS), removeCalls)
		assertFalse(vars.containsKey(MRF_TOO_MANY_SEGMENTS))
	}

	@Test
	fun `exactly 100 is not over`() {
		assertEquals(hundredServer, trimmer.trim(hundredServer))
		assertTrue(setCalls.isEmpty())
	}
}
