package com.marfeel.compass.cdp.model

import com.google.gson.annotations.SerializedName

/**
 * Composite Recency/Frequency/Value score plus its components, returned by the
 * backend on every identity/profile response. Read-only — never written by the SDK.
 * This is the master_id-keyed CDP RFV (attached to beacons as `cdp_rfv`); it is
 * distinct from the legacy user-id-keyed RFV (`rfv`/`rfv_r`/...).
 */
data class CdpRfv(
	val rfv: Int,
	val r: Int,
	val f: Int,
	val v: Int
)

/**
 * The shared response shape of `/cdp/identity/resolve`, `/link` and `/update`.
 * `cohorts` defaults to empty (never null on the SDK side).
 */
internal data class CdpIdentityResponse(
	@SerializedName("master_id")
	val masterId: String?,
	val rfv: CdpRfv?,
	val cohorts: List<Int> = emptyList()
)

/** Locally-cached read-only identity payload (rfv + cohorts). */
internal data class CdpCachedIdentity(
	val rfv: CdpRfv?,
	val cohorts: List<Int>
)

/**
 * Body for `POST /cdp/identity/resolve/`.
 *
 * `site_id` is serialized as a JSON **number** (not a string) — the backend rejects a
 * string `site_id` with 400. See the spec's example payloads (`"site_id": 1234`).
 */
internal data class CdpResolveParams(
	@SerializedName("site_id")
	val siteId: Long,
	@SerializedName("cookie_id")
	val cookieId: String,
	@SerializedName("master_id")
	val masterId: String? = null
)

/** Body for `POST /cdp/identity/link/`. `site_id` is a JSON number (see [CdpResolveParams]). */
internal data class CdpLinkParams(
	@SerializedName("site_id")
	val siteId: Long,
	@SerializedName("id_type")
	val idType: String,
	@SerializedName("id_value")
	val idValue: String,
	@SerializedName("is_deterministic")
	val isDeterministic: Boolean,
	@SerializedName("master_id")
	val masterId: String? = null
)

/**
 * Body for `POST /cdp/identity/update/` — serves both property writes and segment
 * writes. The backend treats absent fields as no-ops, so only the keys the caller
 * cares about are sent (null fields are omitted by Gson).
 */
internal data class CdpProfileUpdateParams(
	@SerializedName("site_id")
	val siteId: Long,
	@SerializedName("master_id")
	val masterId: String,
	val properties: Map<String, String>? = null,
	@SerializedName("segments_add")
	val segmentsAdd: List<String>? = null,
	@SerializedName("segments_remove")
	val segmentsRemove: List<String>? = null
)

/**
 * The CDP's contribution to each tracking beacon.
 *
 * [rfvSerialized] / [cohortsSerialized] hold the JSON-string forms used when
 * appending to the ingest form payload (`cdp_rfv` / `cdp_cohorts`).
 */
data class CdpData(
	val masterId: String?,
	val rfv: CdpRfv?,
	val cohorts: List<Int>,
	val rfvSerialized: String = "",
	val cohortsSerialized: String = "[]"
)
