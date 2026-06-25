package com.marfeel.compass.cdp

import com.marfeel.compass.cdp.model.CdpIdentityResponse

internal const val CDP_IDENTITY_RESOLVE_PATH = "/cdp/identity/resolve/"
internal const val CDP_IDENTITY_LINK_PATH = "/cdp/identity/link/"
internal const val CDP_IDENTITY_UPDATE_PATH = "/cdp/identity/update/"
internal const val CDP_METERS_PATH = "/cdp/meters"

/**
 * Sentinel masterId used before CDP resolves a real one. Segments set pre-identity
 * are written to this bucket and carried over into the real masterId bucket on first
 * identity resolve (see [CdpManager] carry-over).
 */
internal const val LOCAL_MID_SENTINEL = "local"

/**
 * Matches Scylla DefaultAnonymousTTL. After this window the backend forgets data for
 * anonymous master_ids
 */
internal const val CDP_MIRROR_TTL_MS = 180L * 24 * 60 * 60 * 1000

/**
 * The "unknown" identity every identity/profile call fails open to: a network error,
 * non-2xx, or unparseable body must never throw — tracking stays fail-open.
 */
internal val UNKNOWN_CDP_IDENTITY = CdpIdentityResponse(masterId = null, rfv = null, cohorts = emptyList())
