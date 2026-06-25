package com.marfeel.compass.cdp.store

import android.content.SharedPreferences
import com.google.gson.JsonArray
import com.google.gson.JsonElement

/**
 * Local-first mirror of the CDP segment labels for a `(account, masterId)`. Segments
 * set before identity resolves live under the [com.marfeel.compass.cdp.LOCAL_MID_SENTINEL]
 * bucket and are carried over on first resolve.
 */
internal class CdpSegmentsStore(prefs: SharedPreferences) {
	private val store = CdpMirrorStore<List<String>>(
		prefs = prefs,
		prefix = "cdpsegs_",
		payloadKey = "segments",
		serialize = { segments -> JsonArray().apply { segments.forEach { add(it) } } },
		deserialize = { element -> element.toStringList() },
		default = emptyList()
	)

	fun read(account: String?, masterId: String?): List<String> = store.read(account, masterId)

	fun write(account: String?, masterId: String?, segments: List<String>) =
		store.write(account, masterId, segments)

	fun clear(account: String?, masterId: String?) = store.clear(account, masterId)

	fun getActiveMid(account: String?): String? = store.getActiveMid(account)

	fun setActiveMid(account: String?, masterId: String?) = store.setActiveMid(account, masterId)

	fun cleanupExpired(account: String?, activeMidOverride: String? = null) =
		store.cleanupExpired(account, activeMidOverride)

	private fun JsonElement.toStringList(): List<String> =
		if (isJsonArray) asJsonArray.mapNotNull { if (it.isJsonPrimitive) it.asString else null }
		else emptyList()
}
