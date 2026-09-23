package com.marfeel.compass.cdp

import android.util.Log

/**
 * Read-time rules for combining device-owned segments / vars with the Server
 * Segments / Server Properties the CDP asserts. The two sides are **never merged in
 * storage** — only here, when read back or sent — so the bulk device paths
 * (`setUserSegments`, `clearUserSegments`) can only ever name locally-asserted keys in
 * `segments_remove`.
 */
internal object SegmentOwnership {

	/**
	 * Union, **server first**, deduplicated, untrimmed. Server-first is what makes
	 * [trimSegments] drop device-owned segments before server ones.
	 */
	fun mergeSegments(owned: List<String>?, server: List<String>?): List<String> =
		LinkedHashSet<String>().apply {
			server?.let { addAll(it) }
			owned?.let { addAll(it) }
		}.toList()

	/**
	 * Bulk replace is the one shape where intent is ambiguous — an echo of
	 * `getUserSegments()` and a deliberate assertion are byte-identical. A backend-owned
	 * key let through would later be posted as `segments_remove` by `clearUserSegments`,
	 * deleting a membership this device never asserted. Keys already owned stay: the
	 * device owns them regardless of what the backend also knows.
	 */
	fun rejectUnownedSegments(requested: List<String>, owned: List<String>?, server: List<String>?): List<String> {
		if (server.isNullOrEmpty()) return requested
		val ownedSet = owned?.toSet() ?: emptySet()
		val unowned = server.filterNot { it in ownedSet }.toSet()

		return requested.filter { segment ->
			val keep = segment !in unowned
			if (!keep) warnUnownedSegment(segment)
			keep
		}
	}

	/** Strict: exactly [MAX_SENT_SEGMENTS] is fine. */
	fun isOverSegmentLimit(segments: List<String>): Boolean = segments.size > MAX_SENT_SEGMENTS

	fun trimSegments(segments: List<String>): List<String> =
		if (segments.size <= MAX_SENT_SEGMENTS) segments else segments.subList(0, MAX_SENT_SEGMENTS)

	/**
	 * Device-owned vars first, then every server property whose key the device does
	 * not own. Device-owned wins on a collision.
	 */
	fun mergeVars(owned: Map<String, String>?, server: Map<String, String>?): Map<String, String> {
		val out = LinkedHashMap<String, String>()
		owned?.let { out.putAll(it) }
		server?.forEach { (key, value) -> if (!out.containsKey(key)) out[key] = value }
		return out
	}

	private fun warnUnownedSegment(segment: String) {
		try {
			Log.w(
				"Compass",
				"\"$segment\" is managed server-side and was not added to this device's segments. " +
					"Use addUserSegment() to assert it from the device."
			)
		} catch (_: Throwable) {
			// android.util.Log is a stub on the plain JVM; never let a warning break a write.
		}
	}
}

/**
 * Applies the [MAX_SENT_SEGMENTS] cap to a merged segment list and keeps the
 * `mrf_tooManySegments` user var in step with it. The var is written only on a state
 * transition (over → flagged, fits → unflagged), so the evaluation is cheap enough to
 * run on every beacon.
 */
internal class SegmentTrimmer(
	private val readOwnedUserVars: () -> Map<String, String>,
	private val setUserVar: (name: String, value: String) -> Unit,
	private val removeUserVar: (name: String) -> Unit
) {
	fun trim(segments: List<String>): List<String> {
		val isFlagged = readOwnedUserVars().containsKey(MRF_TOO_MANY_SEGMENTS)

		if (SegmentOwnership.isOverSegmentLimit(segments)) {
			if (!isFlagged) setUserVar(MRF_TOO_MANY_SEGMENTS, "true")
		} else if (isFlagged) {
			removeUserVar(MRF_TOO_MANY_SEGMENTS)
		}

		return SegmentOwnership.trimSegments(segments)
	}
}
