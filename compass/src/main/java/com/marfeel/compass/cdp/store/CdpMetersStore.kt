package com.marfeel.compass.cdp.store

import android.content.SharedPreferences
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.marfeel.compass.cdp.formatIsoDate
import com.marfeel.compass.cdp.model.MeterState
import com.marfeel.compass.cdp.model.MeterWindow
import com.marfeel.compass.cdp.parseIsoDate

/**
 * Local mirror of the server-authoritative meters for a `(account, masterId)`,
 * persisted so synchronous `get`/`list` reads return a last-known value before any
 * network. Dates round-trip as ISO-8601 strings; the optional threshold trio and the
 * window default are preserved.
 */
internal class CdpMetersStore(prefs: SharedPreferences) {
	private val store = CdpMirrorStore<List<MeterState>>(
		prefs = prefs,
		prefix = "cdpmeters_",
		payloadKey = "meters",
		serialize = { meters -> JsonArray().apply { meters.forEach { add(it.toJson()) } } },
		deserialize = { element -> element.toMeterList() },
		default = emptyList()
	)

	fun read(account: String?, masterId: String?): List<MeterState> = store.read(account, masterId)

	fun write(account: String?, masterId: String?, meters: List<MeterState>) =
		store.write(account, masterId, meters)

	fun clear(account: String?, masterId: String?) = store.clear(account, masterId)

	fun getActiveMid(account: String?): String? = store.getActiveMid(account)

	fun setActiveMid(account: String?, masterId: String?) = store.setActiveMid(account, masterId)

	fun cleanupExpired(account: String?, activeMidOverride: String? = null) =
		store.cleanupExpired(account, activeMidOverride)

	private fun MeterState.toJson(): JsonObject = JsonObject().apply {
		addProperty("name", name)
		addProperty("count", count)
		threshold?.let { addProperty("threshold", it) }
		reached?.let { addProperty("reached", it) }
		remaining?.let { addProperty("remaining", it) }
		formatIsoDate(startedAt)?.let { addProperty("started_at", it) }
		formatIsoDate(expiresAt)?.let { addProperty("expires_at", it) }
		add("window", JsonObject().apply {
			addProperty("duration", window.duration)
			addProperty("period", window.period)
			addProperty("tz", window.tz)
		})
	}

	private fun JsonElement.toMeterList(): List<MeterState> =
		if (isJsonArray) asJsonArray.mapNotNull { if (it.isJsonObject) it.asJsonObject.toMeterState() else null }
		else emptyList()

	private fun JsonObject.toMeterState(): MeterState {
		val hasThreshold = has("threshold") && !get("threshold").isJsonNull
		val window = get("window")?.takeIf { it.isJsonObject }?.asJsonObject?.let {
			MeterWindow(
				duration = it.get("duration")?.asString ?: "",
				period = it.get("period")?.asString ?: "",
				tz = it.get("tz")?.asString ?: ""
			)
		} ?: MeterWindow()

		return MeterState(
			name = get("name")?.asString ?: "",
			count = get("count")?.takeUnless { it.isJsonNull }?.asInt ?: 0,
			threshold = if (hasThreshold) get("threshold").asInt else null,
			reached = if (hasThreshold) get("reached")?.takeUnless { it.isJsonNull }?.asBoolean ?: false else null,
			remaining = if (hasThreshold) get("remaining")?.takeUnless { it.isJsonNull }?.asInt ?: 0 else null,
			startedAt = parseIsoDate(get("started_at")?.takeUnless { it.isJsonNull }?.asString),
			expiresAt = parseIsoDate(get("expires_at")?.takeUnless { it.isJsonNull }?.asString),
			window = window
		)
	}
}
