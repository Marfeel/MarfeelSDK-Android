package com.marfeel.compass.cdp.model

import com.google.gson.annotations.SerializedName

data class CdpRfv(
	val rfv: Int,
	val r: Int,
	val f: Int,
	val v: Int
)

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
