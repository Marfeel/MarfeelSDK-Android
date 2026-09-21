package com.marfeel.compass.cdp.store

import android.content.SharedPreferences
import com.google.gson.JsonArray
import com.google.gson.JsonElement

/**
 * Mirror of the Server Segments (asserted by the CDP, never by this device) for a
 * `(account, masterId)`. Kept apart from [CdpSegmentsStore] so the device never
 * re-asserts — or removes — a membership it did not claim.
 *
 * A cache **miss reads as null**, distinct from an empty list: the resolve guard in
 * `CdpManager` skips the network only when the mirror has an entry, even an empty one.
 * Keeps no active-mid pointer of its own — callers pass the active master to
 * [cleanupExpired] so it is never purged.
 */
internal class CdpServerSegmentsStore(prefs: SharedPreferences) {
	private val store = CdpMirrorStore<List<String>?>(
		prefs = prefs,
		prefix = "cdpsrvsegs_",
		payloadKey = "segments",
		serialize = { segments -> JsonArray().apply { segments?.forEach { add(it) } } },
		deserialize = { element -> element.toStringList() },
		default = null
	)

	fun read(account: String?, masterId: String?): List<String>? = store.read(account, masterId)

	fun write(account: String?, masterId: String?, segments: List<String>) =
		store.write(account, masterId, segments)

	fun clear(account: String?, masterId: String?) = store.clear(account, masterId)

	fun cleanupExpired(account: String?, activeMasterId: String?) =
		store.cleanupExpired(account, activeMasterId)

	private fun JsonElement.toStringList(): List<String> =
		if (isJsonArray) asJsonArray.mapNotNull { if (it.isJsonPrimitive) it.asString else null }
		else emptyList()
}
