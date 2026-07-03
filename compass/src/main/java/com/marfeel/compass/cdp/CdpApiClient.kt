package com.marfeel.compass.cdp

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.marfeel.compass.BuildConfig
import com.marfeel.compass.cdp.model.CdpIdentityResponse
import com.marfeel.compass.cdp.model.CdpLinkParams
import com.marfeel.compass.cdp.model.CdpProfileUpdateParams
import com.marfeel.compass.cdp.model.CdpResolveParams
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

internal class CdpApiClient(
	private val httpClient: OkHttpClient,
	baseUrl: String = BuildConfig.CDP_BASE_URL
) {
	private val baseUrl: String = baseUrl.trimEnd('/')
	private val gson = Gson()
	private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

	suspend fun resolve(params: CdpResolveParams): CdpIdentityResponse =
		postIdentity(CDP_IDENTITY_RESOLVE_PATH, gson.toJson(params))

	suspend fun link(params: CdpLinkParams): CdpIdentityResponse =
		postIdentity(CDP_IDENTITY_LINK_PATH, gson.toJson(params))

	suspend fun update(params: CdpProfileUpdateParams): CdpIdentityResponse =
		postIdentity(CDP_IDENTITY_UPDATE_PATH, gson.toJson(params))

	private suspend fun postIdentity(path: String, body: String): CdpIdentityResponse =
		withContext(Dispatchers.IO) {
			try {
				val request = Request.Builder()
					.url("$baseUrl$path")
					.post(body.toRequestBody(jsonMediaType))
					.build()

				httpClient.newCall(request).execute().use { response ->
					val text = response.body?.string()
					if (!response.isSuccessful || text == null) return@withContext UNKNOWN_CDP_IDENTITY

					gson.fromJson(text, CdpIdentityResponse::class.java) ?: UNKNOWN_CDP_IDENTITY
				}
			} catch (e: Exception) {
				UNKNOWN_CDP_IDENTITY
			}
		}

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
}
