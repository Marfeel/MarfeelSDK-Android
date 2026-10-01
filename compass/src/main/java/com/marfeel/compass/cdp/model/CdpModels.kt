package com.marfeel.compass.cdp.model

import com.google.gson.annotations.SerializedName

data class CdpRfv(
	val rfv: Int,
	val r: Int,
	val f: Int,
	val v: Int
)

/**
 * Wire shape shared by `/resolve/`, `/link/`, `/update/` and `/delete/`.
 *
 * [segments] are the Server Segments the CDP asserts for this master; [properties]
 * the Server Properties it computed. Both are absent on older servers and on the
 * fail-open [com.marfeel.compass.cdp.UNKNOWN_CDP_IDENTITY].
 */
internal data class CdpIdentityResponse(
	@SerializedName("master_id")
	val masterId: String?,
	val rfv: CdpRfv?,
	val cohorts: List<Int> = emptyList(),
	val segments: List<String>? = null,
	val properties: Map<String, String>? = null
)

/** `/cdp/identity/delete/` answer: the identity shape plus a count (0 when nothing was owned). */
internal data class CdpDeleteResponse(
	val identity: CdpIdentityResponse,
	val deleted: Int
)

/** `/cdp/identity/reset/` answer. [cleared] lists the cookies actually presented. */
internal data class CdpResetResponse(
	val reset: Boolean,
	val siteId: Long?,
	val cleared: List<String>
)

/** Locally-cached read-only identity payload (rfv + cohorts). */
internal data class CdpCachedIdentity(
	val rfv: CdpRfv?,
	val cohorts: List<Int>
)

internal data class CdpResolveParams(
	@SerializedName("site_id")
	val siteId: Long,
	@SerializedName("cookie_id")
	val cookieId: String,
	@SerializedName("master_id")
	val masterId: String? = null
)

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
 * `/cdp/identity/delete/` body. A null [idValue] is **omitted entirely** from the JSON
 * (never sent as `null`/`""`): that form unlinks every identity of [idType] the master owns.
 */
internal data class CdpDeleteParams(
	@SerializedName("site_id")
	val siteId: Long,
	@SerializedName("master_id")
	val masterId: String,
	@SerializedName("id_type")
	val idType: String,
	@SerializedName("id_value")
	val idValue: String? = null
)

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
 *
 * [identityFresh] is true only when *this* process actually round-tripped an identity
 * call that returned a master_id (resolve or link) — a warm cache never counts. Sent to
 * ingest as `cdp_fresh`.
 */
data class CdpData(
	val masterId: String?,
	val rfv: CdpRfv?,
	val cohorts: List<Int>,
	val rfvSerialized: String = "",
	val cohortsSerialized: String = "[]",
	val identityFresh: Boolean = false
)
