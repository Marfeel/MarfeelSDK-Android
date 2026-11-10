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
) {
	private val gson = Gson()
	private val evaluator = ExperimentEvaluator(random)

	companion object {
		private const val EXPERIMENTS_KEY = "experiences_experiments"
	}

	fun handleExperimentGroups(groups: JsonObject?) {
		if (groups == null) return
		val assignments = getAssignments().toMutableMap()

		for ((groupId, groupValue) in groups.entrySet()) {
			if (assignments.containsKey(groupId)) continue
			if (!groupValue.isJsonObject) continue

			val groupObj = groupValue.asJsonObject
			val variants = groupObj.getAsJsonArray("variants") ?: continue

			val totalWeight = variants.sumOf { it.asJsonObject.get("weight").asInt }
			if (totalWeight <= 0) continue

			val roll = random.nextInt(totalWeight)
			var cumulative = 0

			for (variant in variants) {
				val variantObj = variant.asJsonObject
				cumulative += variantObj.get("weight").asInt
				if (roll < cumulative) {
					assignments[groupId] = variantObj.get("id").asString
					break
				}
			}
		}

		saveAssignments(assignments)
	}

	/**
	 * Applies the web SDK A/B semantics: keeps winners and the control arm (tagged with
	 * `experimentation`), drops the losing variants.
	 */
	fun evaluate(experiences: List<Experience>): List<Experience> {
		val userId = listOf(userIdProvider(), siteUserIdProvider()).firstOrNull { !it.isNullOrEmpty() }
		return evaluator.evaluate(experiences, getAssignments(), userId)
	}

	fun getAssignments(): Map<String, String> {
		val json = preferences.getString(EXPERIMENTS_KEY, null) ?: return emptyMap()
		val type = object : TypeToken<Map<String, String>>() {}.type
		return gson.fromJson(json, type)
	}

	fun getTargetingEntries(): Map<String, String> {
		return getAssignments().map { (groupId, variantId) ->
			"experiment::$groupId" to variantId
		}.toMap()
	}

	fun setAssignment(groupId: String, variantId: String) {
		val assignments = getAssignments().toMutableMap()
		assignments[groupId] = variantId
		saveAssignments(assignments)
	}

	fun clear() {
		preferences.edit { remove(EXPERIMENTS_KEY) }
	}

	private fun saveAssignments(assignments: Map<String, String>) {
		preferences.edit {
			putString(EXPERIMENTS_KEY, gson.toJson(assignments))
		}
	}
}
