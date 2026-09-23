package com.marfeel.compass.cdp.store

import android.content.SharedPreferences
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.marfeel.compass.cdp.LOCAL_MID_SENTINEL
import com.marfeel.compass.cdp.model.CdpConsentStatus
import com.marfeel.compass.cdp.model.CdpRememberedConsentDecision

/**
 * The SDK's own memory of consent decisions it recorded **without** a master_id:
 * `/cdp/consents/check/` cannot look those up, so `Cdp.hasConsent` answers from here
 * until the replay re-records them under a master. Always under the
 * [LOCAL_MID_SENTINEL] bucket, per account. Cleared as a whole by a user reset.
 */
internal class CdpConsentMemoryStore(prefs: SharedPreferences) {
	private val store = CdpMirrorStore<Map<String, CdpRememberedConsentDecision>>(
		prefs = prefs,
		prefix = "cdpconsents_",
		payloadKey = "decisions",
		serialize = { decisions -> decisions.toJson() },
		deserialize = { element -> element.toDecisions() },
		default = emptyMap()
	)

	/** A fresh copy each time; empty on a miss. */
	fun getRemembered(account: String?): Map<String, CdpRememberedConsentDecision> =
		LinkedHashMap(store.read(account, LOCAL_MID_SENTINEL))

	/** A newer decision on the same consent replaces the older one. */
	fun remember(account: String?, consentId: String, decision: CdpRememberedConsentDecision) {
		val current = LinkedHashMap(getRemembered(account))
		current[consentId] = decision
		store.write(account, LOCAL_MID_SENTINEL, current)
	}

	/** Drops only the named decisions; forgetting the last one clears the bucket; unknown ids write nothing. */
	fun forget(account: String?, consentIds: Collection<String>) {
		val current = getRemembered(account)
		val remaining = current.filterKeys { it !in consentIds }
		if (remaining.size == current.size) return

		if (remaining.isEmpty()) store.clear(account, LOCAL_MID_SENTINEL)
		else store.write(account, LOCAL_MID_SENTINEL, remaining)
	}

	fun clear(account: String?) = store.clear(account, LOCAL_MID_SENTINEL)

	private fun Map<String, CdpRememberedConsentDecision>.toJson(): JsonObject = JsonObject().apply {
		this@toJson.forEach { (consentId, decision) ->
			add(consentId, JsonObject().apply {
				addProperty("versionId", decision.versionId)
				addProperty("status", decision.status.wireValue)
				addProperty("ts", decision.ts)
			})
		}
	}

	private fun JsonElement.toDecisions(): Map<String, CdpRememberedConsentDecision> {
		if (!isJsonObject) return emptyMap()
		val out = LinkedHashMap<String, CdpRememberedConsentDecision>()
		asJsonObject.entrySet().forEach { (consentId, raw) ->
			if (!raw.isJsonObject) return@forEach
			val obj = raw.asJsonObject
			val versionId = obj.get("versionId")?.takeIf { it.isJsonPrimitive }?.asString ?: return@forEach
			val status = CdpConsentStatus.fromWire(obj.get("status")?.takeIf { it.isJsonPrimitive }?.asString)
			val ts = obj.get("ts")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong ?: 0L
			out[consentId] = CdpRememberedConsentDecision(versionId, status, ts)
		}
		return out
	}
}
