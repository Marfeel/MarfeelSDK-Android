package com.marfeel.compass.cdp.store

import android.content.SharedPreferences
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/**
 * Mirror of the Server Properties (profile attributes the CDP computes; the device
 * never authors them) for a `(account, masterId)`. Values are coerced to strings on
 * the way in. A cache **miss reads as null**, distinct from an empty map.
 */
internal class CdpServerPropertiesStore(prefs: SharedPreferences) {
	private val store = CdpMirrorStore<Map<String, String>?>(
		prefs = prefs,
		prefix = "cdpsrvprops_",
		payloadKey = "properties",
		serialize = { properties ->
			JsonObject().apply { properties?.forEach { (key, value) -> addProperty(key, value) } }
		},
		deserialize = { element -> element.toStringMap() },
		default = null
	)

	fun read(account: String?, masterId: String?): Map<String, String>? = store.read(account, masterId)

	fun write(account: String?, masterId: String?, properties: Map<String, String>) =
		store.write(account, masterId, properties)

	fun clear(account: String?, masterId: String?) = store.clear(account, masterId)

	fun cleanupExpired(account: String?, activeMasterId: String?) =
		store.cleanupExpired(account, activeMasterId)

	private fun JsonElement.toStringMap(): Map<String, String> {
		if (!isJsonObject) return emptyMap()
		val out = LinkedHashMap<String, String>()
		asJsonObject.entrySet().forEach { (key, value) ->
			out[key] = if (value.isJsonPrimitive) value.asString else value.toString()
		}
		return out
	}
}
