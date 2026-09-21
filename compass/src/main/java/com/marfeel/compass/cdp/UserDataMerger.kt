package com.marfeel.compass.cdp

/**
 * The read-side views the tracker exposes and the beacon sends: device-owned segments
 * unioned with the Server Segments (server first, deduplicated, trimmed to
 * [MAX_SENT_SEGMENTS] with the `mrf_tooManySegments` flag kept in step) and
 * device-owned vars followed by the Server Properties (device-owned wins).
 *
 * When CDP is disabled the server side is empty and the views are the raw stores,
 * still trimmed — the cap is a beacon rule, not a CDP one.
 */
internal class UserDataMerger(
	private val cdpEnabled: () -> Boolean,
	private val readOwnedSegments: () -> List<String>,
	private val readOwnedVars: () -> Map<String, String>,
	private val listServerSegments: () -> List<String>,
	private val getServerSegments: suspend () -> List<String>,
	private val listServerProperties: () -> Map<String, String>,
	private val getServerProperties: suspend () -> Map<String, String>,
	private val trimmer: SegmentTrimmer
) {
	/** Uses whatever Server Segments are known right now. */
	fun segments(): List<String> {
		val server = if (cdpEnabled()) listServerSegments() else emptyList()
		return trimmer.trim(SegmentOwnership.mergeSegments(readOwnedSegments(), server))
	}

	/** Resolves identity first so the Server Segments are current. */
	suspend fun segmentsAsync(): List<String> {
		val server = if (cdpEnabled()) getServerSegments() else emptyList()
		return trimmer.trim(SegmentOwnership.mergeSegments(readOwnedSegments(), server))
	}

	fun vars(): Map<String, String> {
		val server = if (cdpEnabled()) listServerProperties() else emptyMap()
		return SegmentOwnership.mergeVars(readOwnedVars(), server)
	}

	suspend fun varsAsync(): Map<String, String> {
		val server = if (cdpEnabled()) getServerProperties() else emptyMap()
		return SegmentOwnership.mergeVars(readOwnedVars(), server)
	}
}
