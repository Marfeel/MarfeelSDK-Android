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
	private val random: Random = Random.Default
) {
	private val gson = Gson()

	companion object {
		private const val EXPERIMENTS_KEY = "experiences_experiments"
		private const val EXPERIMENT_FILTER_PREFIX = "mrf_exp_"
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

	fun filterByExperiments(experiences: List<Experience>): List<Experience> {
		val assignments = getAssignments()
		return experiences.filter { experience ->
			val filters = experience.filters ?: return@filter true
			val experimentFilters = filters.filter { it.key.startsWith(EXPERIMENT_FILTER_PREFIX) }
			if (experimentFilters.isEmpty()) return@filter true

			experimentFilters.all { filter ->
				val groupId = filter.key.removePrefix(EXPERIMENT_FILTER_PREFIX)
				val assignedVariant = assignments[groupId] ?: return@all false
				when (filter.operator) {
					"EQUALS" -> assignedVariant in filter.values
					"NOT_EQUALS" -> assignedVariant !in filter.values
					else -> true
				}
			}
		}
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

	private fun saveAssignments(assignments: Map<String, String>) {
		preferences.edit {
			putString(EXPERIMENTS_KEY, gson.toJson(assignments))
		}
	}
}
