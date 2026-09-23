package com.marfeel.compass.cdp

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.marfeel.compass.BuildConfig
import com.marfeel.compass.cdp.model.CdpConsentCatalogItem
import com.marfeel.compass.cdp.model.CdpConsentCatalogVersionItem
import com.marfeel.compass.cdp.model.CdpConsentCheckParams
import com.marfeel.compass.cdp.model.CdpConsentCheckResponse
import com.marfeel.compass.cdp.model.CdpConsentRecordParams
import com.marfeel.compass.cdp.model.CdpConsentRecordResponse
import com.marfeel.compass.cdp.model.CdpDeleteParams
import com.marfeel.compass.cdp.model.CdpDeleteResponse
import com.marfeel.compass.cdp.model.CdpIdentityResponse
import com.marfeel.compass.cdp.model.CdpLinkParams
import com.marfeel.compass.cdp.model.CdpProfileUpdateParams
import com.marfeel.compass.cdp.model.CdpResetResponse
import com.marfeel.compass.cdp.model.CdpResolveParams
import com.marfeel.compass.cdp.model.CdpRfv
import com.marfeel.compass.cdp.model.MeterState
import com.marfeel.compass.cdp.model.MeterWindow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal data class IncrementResult(val status: Int, val state: MeterState?)

/**
 * Transport for every CDP endpoint. Rule of thumb for failure values: calls whose
 * response feeds the Cached Identity on success but must not poison it on failure
 * (delete, reset, consents) return `null`; resolve / link / update return the
 * [UNKNOWN_CDP_IDENTITY] shape. Nothing here ever throws.
 */
internal class CdpApiClient(
	private val httpClient: OkHttpClient,
	baseUrl: String = BuildConfig.CDP_BASE_URL
) {
	private val baseUrl: String = baseUrl.trimEnd('/')
	private val gson = Gson()
	private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

	// region identity

	suspend fun resolve(params: CdpResolveParams): CdpIdentityResponse =
		postIdentity(CDP_IDENTITY_RESOLVE_PATH, gson.toJson(params))

	suspend fun link(params: CdpLinkParams): CdpIdentityResponse =
		postIdentity(CDP_IDENTITY_LINK_PATH, gson.toJson(params))

	suspend fun update(params: CdpProfileUpdateParams): CdpIdentityResponse =
		postIdentity(CDP_IDENTITY_UPDATE_PATH, gson.toJson(params))

	/** Null on any failure — never [UNKNOWN_CDP_IDENTITY], which would poison the cache. */
	suspend fun delete(params: CdpDeleteParams): CdpDeleteResponse? {
		val root = postJson(CDP_IDENTITY_DELETE_PATH, gson.toJson(params)) ?: return null
		return CdpDeleteResponse(
			identity = parseIdentityResponse(root) ?: return null,
			deleted = root.get("deleted")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: 0
		)
	}

	/**
	 * Expires the site's server-held tracking cookies. On native there are no such
	 * cookies to present, so this is parity plumbing — the server answers `cleared: []`.
	 * Body is `{ site_id }` and nothing else. Null on failure; never throws.
	 */
	suspend fun reset(siteId: Long): CdpResetResponse? {
		val body = JsonObject().apply { addProperty("site_id", siteId) }
		val root = postJson(CDP_IDENTITY_RESET_PATH, body.toString()) ?: return null
		return CdpResetResponse(
			reset = root.get("reset")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false,
			siteId = root.get("site_id")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong,
			cleared = root.get("cleared")?.takeIf { it.isJsonArray }?.asJsonArray
				?.mapNotNull { if (it.isJsonPrimitive) it.asString else null } ?: emptyList()
		)
	}

	private suspend fun postIdentity(path: String, body: String): CdpIdentityResponse {
		val root = postJson(path, body) ?: return UNKNOWN_CDP_IDENTITY
		return parseIdentityResponse(root) ?: UNKNOWN_CDP_IDENTITY
	}

	/**
	 * Hand-rolled so a non-string server property (`{"age": 42}`) is coerced instead of
	 * failing the whole response into [UNKNOWN_CDP_IDENTITY].
	 */
	private fun parseIdentityResponse(root: JsonObject): CdpIdentityResponse? = try {
		CdpIdentityResponse(
			masterId = root.get("master_id")?.takeIf { it.isJsonPrimitive }?.asString,
			rfv = root.get("rfv")?.takeIf { it.isJsonObject }?.let { gson.fromJson(it, CdpRfv::class.java) },
			cohorts = root.get("cohorts")?.takeIf { it.isJsonArray }?.asJsonArray
				?.mapNotNull { if (it.isJsonPrimitive && it.asJsonPrimitive.isNumber) it.asInt else null }
				?: emptyList(),
			segments = root.get("segments")?.takeIf { it.isJsonArray }?.asJsonArray
				?.mapNotNull { if (it.isJsonPrimitive) it.asString else null },
			properties = root.get("properties")?.takeIf { it.isJsonObject }?.asJsonObject?.toStringMap()
		)
	} catch (_: Exception) {
		null
	}

	// endregion

	// region consents

	/**
	 * `master_id` goes out as an explicit `null` when absent; `metadata` / `timezone` /
	 * `id_type` / `id_value` are omitted when null. Null on failure; never throws.
	 */
	suspend fun recordConsent(params: CdpConsentRecordParams): CdpConsentRecordResponse? {
		val body = JsonObject().apply {
			addProperty("site_id", params.siteId)
			if (params.masterId != null) addProperty("master_id", params.masterId) else add("master_id", JsonNull.INSTANCE)
			addProperty("consent_id", params.consentId)
			addProperty("consent_version_id", params.consentVersionId)
			addProperty("status", params.status.wireValue)
			params.metadata?.let { metadata ->
				add("metadata", JsonObject().apply { metadata.forEach { (k, v) -> addProperty(k, v) } })
			}
			params.timezone?.let { addProperty("timezone", it) }
			params.idType?.let { addProperty("id_type", it) }
			params.idValue?.let { addProperty("id_value", it) }
		}
		val root = postJson(CDP_CONSENT_RECORD_PATH, body.toString()) ?: return null
		return try {
			gson.fromJson(root, CdpConsentRecordResponse::class.java)
		} catch (_: Exception) {
			null
		}
	}

	/**
	 * GET catalog: `?site_id=&consent_id=&consent_version_id?=`. Returns the items
	 * (0 or 1; empty when the requested version is unknown), or null on failure.
	 */
	suspend fun fetchConsentCatalog(siteId: Long, consentId: String, versionId: String?): List<CdpConsentCatalogItem>? =
		withContext(Dispatchers.IO) {
			val url = "$baseUrl$CDP_CONSENT_CATALOG_PATH".toHttpUrlOrNull()
				?.newBuilder()
				?.addQueryParameter("site_id", siteId.toString())
				?.addQueryParameter("consent_id", consentId)
				?.apply { versionId?.let { addQueryParameter("consent_version_id", it) } }
				?.build()
				?: return@withContext null

			try {
				httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
					if (!response.isSuccessful) return@withContext null
					val text = response.body?.string() ?: return@withContext null
					val root = JsonParser.parseString(text)
					if (!root.isJsonObject) return@withContext null
					val consents = root.asJsonObject.get("consents")
					if (consents == null || !consents.isJsonArray) return@withContext emptyList()
					consents.asJsonArray.mapNotNull { if (it.isJsonObject) parseCatalogItem(it.asJsonObject) else null }
				}
			} catch (_: Exception) {
				null
			}
		}

	/** A POST, unlike the catalog: the subject (master or email) stays out of URLs and logs. */
	suspend fun fetchConsentStatus(params: CdpConsentCheckParams): CdpConsentCheckResponse? {
		val root = postJson(CDP_CONSENT_CHECK_PATH, gson.toJson(params)) ?: return null
		return try {
			CdpConsentCheckResponse(
				masterId = root.get("master_id")?.takeIf { it.isJsonPrimitive }?.asString,
				consentId = root.get("consent_id")?.takeIf { it.isJsonPrimitive }?.asString ?: params.consentId,
				consentVersionId = root.get("consent_version_id")?.takeIf { it.isJsonPrimitive }?.asString,
				granted = root.get("granted")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false,
				answered = root.get("answered")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false,
				status = root.get("status")?.takeIf { it.isJsonPrimitive }?.asString,
				answeredVersionId = root.get("answered_version_id")?.takeIf { it.isJsonPrimitive }?.asString
			)
		} catch (_: Exception) {
			null
		}
	}

	private fun parseCatalogItem(raw: JsonObject): CdpConsentCatalogItem? = try {
		val consentId = raw.get("consent_id")?.takeIf { it.isJsonPrimitive }?.asString
		val version = raw.get("version")?.takeIf { it.isJsonObject }?.asJsonObject?.let { v ->
			CdpConsentCatalogVersionItem(
				versionId = v.get("consent_version_id")?.takeIf { it.isJsonPrimitive }?.asString ?: "",
				label = v.get("label")?.takeIf { it.isJsonPrimitive }?.asString ?: "",
				date = v.get("date")?.takeIf { it.isJsonPrimitive }?.asString,
				displayPrompt = v.get("display_prompt")?.takeIf { it.isJsonPrimitive }?.asString,
				errorMessage = v.get("error_message")?.takeIf { it.isJsonPrimitive }?.asString,
				metadata = v.get("metadata")?.takeIf { it.isJsonObject }?.asJsonObject?.toStringMap() ?: emptyMap()
			)
		}
		if (consentId == null) null else CdpConsentCatalogItem(
			consentId = consentId,
			name = raw.get("name")?.takeIf { it.isJsonPrimitive }?.asString ?: "",
			purpose = raw.get("purpose")?.takeIf { it.isJsonPrimitive }?.asString,
			mandatory = raw.get("mandatory")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false,
			acceptMethod = raw.get("accept_method")?.takeIf { it.isJsonPrimitive }?.asString ?: "",
			showPolicy = raw.get("show_policy")?.takeIf { it.isJsonPrimitive }?.asString,
			version = version
		)
	} catch (_: Exception) {
		null
	}

	// endregion

	// region meters

	/**
	 * GET all meters. Returns null on error/parse-failure (so the SWR mirror keeps its
	 * last-good value); a non-array `meters` field normalizes to an empty list.
	 */
	suspend fun fetchMeters(siteId: String, masterId: String): List<MeterState>? =
		withContext(Dispatchers.IO) {
			val url = "$baseUrl$CDP_METERS_PATH".toHttpUrlOrNull()
				?.newBuilder()
				?.addQueryParameter("site_id", siteId)
				?.addQueryParameter("master_id", masterId)
				?.build()
				?: return@withContext null

			try {
				httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
					if (!response.isSuccessful) {
						return@withContext null
					}
					val text = response.body?.string() ?: return@withContext null
					val root = JsonParser.parseString(text)

					if (!root.isJsonObject) return@withContext emptyList()
					val meters = root.asJsonObject.get("meters")

					if (meters == null || !meters.isJsonArray) return@withContext emptyList()

					meters.asJsonArray.mapNotNull { element ->
						if (element.isJsonObject) parseMeterState(element.asJsonObject) else null
					}
				}
			} catch (_: Exception) {
				null
			}
		}

	/**
	 * POST increment for a single meter (null body). The returned [IncrementResult]
	 * carries the HTTP status so the caller can map 404 → MeterNotFoundError; any other
	 * failure yields a null state (the caller falls open to the mirror value).
	 */
	suspend fun incrementMeter(name: String, siteId: String, masterId: String): IncrementResult =
		withContext(Dispatchers.IO) {
			val url = "$baseUrl$CDP_METERS_PATH/$name/increment".toHttpUrlOrNull()
				?.newBuilder()
				?.addQueryParameter("site_id", siteId)
				?.addQueryParameter("master_id", masterId)
				?.build()
				?: return@withContext IncrementResult(0, null)

			try {
				val request = Request.Builder()
					.url(url)
					.post("".toRequestBody(jsonMediaType))
					.build()

				httpClient.newCall(request).execute().use { response ->
					val text = response.body?.string()
					if (!response.isSuccessful || text == null) return@withContext IncrementResult(response.code, null)
					val root = JsonParser.parseString(text)
					val state = if (root.isJsonObject) parseMeterState(root.asJsonObject) else null

					IncrementResult(response.code, state)
				}
			} catch (_: Exception) {
				IncrementResult(0, null)
			}
		}

	private fun parseMeterState(raw: JsonObject): MeterState {
		val window = raw.get("window")?.takeIf { it.isJsonObject }?.asJsonObject?.let {
			MeterWindow(
				duration = it.get("duration")?.asString ?: "",
				period = it.get("period")?.asString ?: "",
				tz = it.get("tz")?.asString ?: ""
			)
		} ?: MeterWindow()

		val hasThreshold = raw.has("threshold") && !raw.get("threshold").isJsonNull

		return MeterState(
			name = raw.get("name")?.asString ?: "",
			count = raw.get("count")?.takeUnless { it.isJsonNull }?.asInt ?: 0,
			threshold = if (hasThreshold) raw.get("threshold").asInt else null,
			reached = if (hasThreshold) raw.get("reached")?.takeUnless { it.isJsonNull }?.asBoolean ?: false else null,
			remaining = if (hasThreshold) raw.get("remaining")?.takeUnless { it.isJsonNull }?.asInt ?: 0 else null,
			startedAt = parseIsoDate(raw.get("started_at")?.takeUnless { it.isJsonNull }?.asString),
			expiresAt = parseIsoDate(raw.get("expires_at")?.takeUnless { it.isJsonNull }?.asString),
			window = window
		)
	}

	// endregion

	/** POST a JSON body; the parsed object on 2xx, null on transport error, non-2xx or a non-object body. */
	private suspend fun postJson(path: String, body: String): JsonObject? =
		withContext(Dispatchers.IO) {
			try {
				val request = Request.Builder()
					.url("$baseUrl$path")
					.post(body.toRequestBody(jsonMediaType))
					.build()

				httpClient.newCall(request).execute().use { response ->
					val text = response.body?.string()
					if (!response.isSuccessful || text == null) return@withContext null
					val root = JsonParser.parseString(text)
					if (root.isJsonObject) root.asJsonObject else null
				}
			} catch (_: Exception) {
				null
			}
		}

	private fun JsonObject.toStringMap(): Map<String, String> {
		val out = LinkedHashMap<String, String>()
		entrySet().forEach { (key, value: JsonElement) ->
			if (value.isJsonNull) return@forEach
			out[key] = if (value.isJsonPrimitive) value.asString else value.toString()
		}
		return out
	}
}
