package com.marfeel.compass.experiences

import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.marfeel.compass.experiences.model.Experience
import kotlin.random.Random

internal class ExperimentManager(
	private val preferences: SharedPreferences,
	private val random: Random = Random.Default,
	private val userIdProvider: () -> String? = { null },
	private val siteUserIdProvider: () -> String? = { null },
	private val readUserVars: () -> Map<String, String> = { emptyMap() },
	private val writeUserVar: (name: String, value: String) -> Unit = { _, _ -> },
) {
	private val gson = Gson()
	private val evaluator = ExperimentEvaluator(random)
	private val lock = Any()

	companion object {
		private const val EXPERIMENTS_KEY = "experiences_experiments"
		const val USER_VAR_PREFIX = "mrfExperiment_"
	}

	/**
	 * Assigns a variant to every test group in the response, the way the web SDK does
	 * (croupier `experiments/index.ts`): deterministic `seed:userId` hash honouring
	 * `hashVersion`. A stored assignment whose variant is still live is kept, so readers
	 * already in a running test are not reshuffled; groups missing from the response are
	 * dropped. Every assignment is mirrored to the `mrfExperiment_<groupId>` user var so
	 * reports see the arm the reader actually got.
	 */
	fun handleExperimentGroups(groups: JsonObject?) {
		if (groups == null) return
		val userId = currentUserId()

		val assignments = synchronized(lock) {
			val stored = loadAssignments()
			val next = mutableMapOf<String, String>()

			for ((groupId, groupValue) in groups.entrySet()) {
				if (!groupValue.isJsonObject) continue
				val groupObj = groupValue.asJsonObject
				val rawVariants = groupObj.get("variants")?.takeIf { it.isJsonArray }?.asJsonArray ?: continue
				val variants = rawVariants.mapNotNull(::parseVariant)

				val kept = stored[groupId]
				if (kept != null && variants.any { it.id == kept && it.weight > 0 }) {
					next[groupId] = kept
					continue
				}

				val seed = groupObj.stringOrNull("id")?.takeIf { it.isNotEmpty() } ?: groupId
				val hashVersion = groupObj.get("hashVersion")
					?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
					?.asInt

				pickVariant(variants, userId, seed, hashVersion)?.let { next[groupId] = it }
			}

			saveAssignments(next)
			next
		}

		syncUserVars(assignments)
	}

	fun evaluate(experiences: List<Experience>): List<Experience> =
		evaluator.evaluate(experiences, getAssignments(), currentUserId())

	fun getAssignments(): Map<String, String> = synchronized(lock) { loadAssignments() }

	fun getTargetingEntries(): Map<String, String> {
		return getAssignments().map { (groupId, variantId) ->
			"experiment::$groupId" to variantId
		}.toMap()
	}

	fun setAssignment(groupId: String, variantId: String) {
		synchronized(lock) {
			val assignments = loadAssignments().toMutableMap()
			assignments[groupId] = variantId
			saveAssignments(assignments)
		}
		syncUserVars(mapOf(groupId to variantId))
	}

	fun clear() {
		synchronized(lock) {
			preferences.edit { remove(EXPERIMENTS_KEY) }
		}
	}

	private fun currentUserId(): String? =
		listOf(userIdProvider(), siteUserIdProvider()).firstOrNull { !it.isNullOrEmpty() }

	private fun pickVariant(
		variants: List<WeightedVariant>,
		userId: String?,
		seed: String,
		hashVersion: Int?,
	): String? {
		if (userId != null) {
			DeterministicHash.selectVariant(variants, userId, seed, hashVersion)?.let { return it }
		}

		val weighted = variants.filter { it.weight > 0 }
		val totalWeight = weighted.sumOf { it.weight }
		if (weighted.isEmpty() || totalWeight <= 0) return null

		val value = random.nextDouble() * totalWeight
		var accumulated = 0.0

		for (variant in weighted) {
			accumulated += variant.weight
			if (value < accumulated) return variant.id
		}

		return weighted.last().id
	}

	private fun parseVariant(element: com.google.gson.JsonElement): WeightedVariant? {
		if (!element.isJsonObject) return null
		val variant = element.asJsonObject
		val id = variant.stringOrNull("id") ?: return null
		val weight = variant.get("weight")
			?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
			?.asDouble
			?: return null
		return WeightedVariant(id, weight)
	}

	private fun JsonObject.stringOrNull(key: String): String? =
		get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

	/** Writes only what changed, so a fetch does not re-push the user vars to the CDP. */
	private fun syncUserVars(assignments: Map<String, String>) {
		if (assignments.isEmpty()) return
		val userVars = readUserVars()

		for ((groupId, variantId) in assignments) {
			val name = "$USER_VAR_PREFIX$groupId"
			if (userVars[name] != variantId) writeUserVar(name, variantId)
		}
	}

	private fun loadAssignments(): Map<String, String> {
		val json = preferences.getString(EXPERIMENTS_KEY, null) ?: return emptyMap()
		val type = object : TypeToken<Map<String, String>>() {}.type
		return gson.fromJson(json, type)
	}

	private fun saveAssignments(assignments: Map<String, String>) {
		preferences.edit {
			putString(EXPERIMENTS_KEY, gson.toJson(assignments))
		}
	}
}
