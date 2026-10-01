package com.marfeel.compass.cdp

import com.marfeel.compass.cdp.model.CdpIdentityResponse

internal const val CDP_IDENTITY_RESOLVE_PATH = "/cdp/identity/resolve/"
internal const val CDP_IDENTITY_LINK_PATH = "/cdp/identity/link/"
internal const val CDP_IDENTITY_DELETE_PATH = "/cdp/identity/delete/"
internal const val CDP_IDENTITY_UPDATE_PATH = "/cdp/identity/update/"
internal const val CDP_IDENTITY_RESET_PATH = "/cdp/identity/reset/"
internal const val CDP_CONSENT_RECORD_PATH = "/cdp/consents/record/"
internal const val CDP_CONSENT_CATALOG_PATH = "/cdp/consents/catalog/"
internal const val CDP_CONSENT_CHECK_PATH = "/cdp/consents/check/"
internal const val CDP_METERS_PATH = "/cdp/meters"

/**
 * Sentinel masterId used before CDP resolves a real one. Segments set pre-identity
 * are written to this bucket and carried over into the real masterId bucket on first
 * identity resolve (see [CdpManager] carry-over). Anonymous consent decisions always
 * live under this bucket.
 */
internal const val LOCAL_MID_SENTINEL = "local"

/**
 * Matches Scylla DefaultAnonymousTTL. After this window the backend forgets data for
 * anonymous master_ids
 */
internal const val CDP_MIRROR_TTL_MS = 180L * 24 * 60 * 60 * 1000

/**
 * Upper bound on the user segments **read back or sent** in a beacon (`useg`). The
 * union is server-first, so device-owned segments are the ones dropped. Storage is
 * never trimmed — only the read-out.
 */
internal const val MAX_SENT_SEGMENTS = 100

/** User var set to `true` while the segment union exceeds [MAX_SENT_SEGMENTS]. */
internal const val MRF_TOO_MANY_SEGMENTS = "mrf_tooManySegments"

/**
 * How long `resetUser()` waits for the best-effort remote tail (the CDP reset POST)
 * before resolving anyway. The rotation callers depend on is already done by then.
 */
internal const val REMOTE_CLEANUP_TIMEOUT_MS = 5_000L

/**
 * The "unknown" identity every identity/profile call fails open to: a network error,
 * non-2xx, or unparseable body must never throw — tracking stays fail-open.
 */
internal val UNKNOWN_CDP_IDENTITY = CdpIdentityResponse(masterId = null, rfv = null, cohorts = emptyList())

private val uuidRegex =
	Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

/** A master_id is only ever adopted or read back when it is a well-formed UUID. */
internal fun isValidUuid(value: String?): Boolean = value != null && uuidRegex.matches(value)
