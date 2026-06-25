package com.marfeel.compass.cdp.store

import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.marfeel.compass.cdp.CDP_MIRROR_TTL_MS

/**
 * Generic SharedPreferences-backed mirror for a CDP payload (segments / meters),
 * keyed per `(account, masterId)` and TTL-stamped. Synchronous — SharedPreferences
 * I/O is synchronous, so no coroutines are needed here.
 *
 * Three key families per [prefix]:
 *  - data:      `${prefix}${masterId}_${account}` → `{ "<payloadKey>": <json>, "ts": <ms> }`
 *  - index:     `${prefix}index_${account}`       → JSON `List<String>` of masterIds
 *  - active mid:`${prefix}active_mid_${account}`  → String
 *
 * A read returns [default] unless the stored envelope is a well-formed object with a
 * numeric `ts` younger than [CDP_MIRROR_TTL_MS]; anything stale/malformed → [default].
 */
internal class CdpMirrorStore<T>(
	private val prefs: SharedPreferences,
	private val prefix: String,
	private val payloadKey: String,
	private val serialize: (T) -> JsonElement,
	private val deserialize: (JsonElement) -> T,
	private val default: T,
	private val clock: () -> Long = System::currentTimeMillis
) {
	private fun dataKey(account: String, masterId: String) = "$prefix${masterId}_$account"
	private fun indexKey(account: String) = "${prefix}index_$account"
	private fun activeMidKey(account: String) = "${prefix}active_mid_$account"

	fun read(account: String?, masterId: String?): T {
		if (account.isNullOrEmpty() || masterId.isNullOrEmpty()) return default
		val raw = prefs.getString(dataKey(account, masterId), null) ?: return default
		return try {
			val obj = JsonParser.parseString(raw).asJsonObject
			val ts = obj.get("ts")
				?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
				?.asLong ?: return default
			if (clock() - ts >= CDP_MIRROR_TTL_MS) return default
			val payload = obj.get(payloadKey) ?: return default
			deserialize(payload)
		} catch (_: Exception) {
			default
		}
	}

	fun write(account: String?, masterId: String?, value: T) {
		if (account.isNullOrEmpty() || masterId.isNullOrEmpty()) return
		val envelope = JsonObject().apply {
			add(payloadKey, serialize(value))
			addProperty("ts", clock())
		}
		prefs.edit { putString(dataKey(account, masterId), envelope.toString()) }
		addToIndex(account, masterId)
	}

	fun clear(account: String?, masterId: String?) {
		if (account.isNullOrEmpty() || masterId.isNullOrEmpty()) return
		prefs.edit { remove(dataKey(account, masterId)) }
		val index = readIndex(account)
		if (index.remove(masterId)) writeIndex(account, index)
	}

	fun getActiveMid(account: String?): String? =
		if (account.isNullOrEmpty()) null else prefs.getString(activeMidKey(account), null)

	fun setActiveMid(account: String?, masterId: String?) {
		if (account.isNullOrEmpty() || masterId.isNullOrEmpty()) return
		prefs.edit { putString(activeMidKey(account), masterId) }
	}

	/**
	 * Purge index entries whose data is older than the TTL or missing, rewriting the
	 * index to the survivors. The active masterId is never purged.
	 */
	fun cleanupExpired(account: String?, activeMidOverride: String? = null) {
		if (account.isNullOrEmpty()) return
		val active = activeMidOverride ?: getActiveMid(account)
		val index = readIndex(account)
		val survivors = index.filter { masterId -> masterId == active || isFresh(account, masterId) }

		if (survivors.size == index.size) return

		prefs.edit {
			index.filterNot { it in survivors }.forEach { remove(dataKey(account, it)) }
		}
		writeIndex(account, survivors.toMutableList())
	}

	private fun isFresh(account: String, masterId: String): Boolean {
		val raw = prefs.getString(dataKey(account, masterId), null) ?: return false
		return try {
			val ts = JsonParser.parseString(raw).asJsonObject.get("ts")
				?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
				?.asLong ?: return false
			clock() - ts < CDP_MIRROR_TTL_MS
		} catch (_: Exception) {
			false
		}
	}

	private fun readIndex(account: String): MutableList<String> {
		val raw = prefs.getString(indexKey(account), null) ?: return mutableListOf()
		return try {
			JsonParser.parseString(raw).asJsonArray
				.mapNotNull { if (it.isJsonPrimitive) it.asString else null }
				.toMutableList()
		} catch (_: Exception) {
			mutableListOf()
		}
	}

	private fun writeIndex(account: String, index: List<String>) {
		val array = JsonArray().apply { index.forEach { add(it) } }
		prefs.edit { putString(indexKey(account), array.toString()) }
	}

	private fun addToIndex(account: String, masterId: String) {
		val index = readIndex(account)
		if (masterId !in index) {
			index.add(masterId)
			writeIndex(account, index)
		}
	}
}
