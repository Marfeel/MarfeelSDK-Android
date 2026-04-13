package com.marfeel.compass.experiences

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.marfeel.compass.experiences.model.Experience
import com.marfeel.compass.experiences.model.ExperienceContentType
import com.marfeel.compass.experiences.model.ExperienceFilter
import com.marfeel.compass.experiences.model.ExperienceSelector
import com.marfeel.compass.experiences.model.ExperienceType

internal data class ParseResult(
	val experiences: List<Experience>,
	val frequencyCapConfig: Map<String, List<String>>,
	val experimentGroups: JsonObject?,
	val editorialId: String? = null,
)

internal class ExperiencesResponseParser(
	private val contentResolver: ContentResolver? = null,
) {
	private val gson = Gson()
	private val metadataKeys = setOf("targeting", "content", "experiments")

	fun parse(jsonString: String): ParseResult {
		val root = gson.fromJson(jsonString, JsonObject::class.java)

		val frequencyCapConfig = extractFrequencyCapConfig(root)
		val experimentGroups = root.getAsJsonObject("experiments")
		val editorialId = root.getAsJsonObject("content")?.get("editorialId")?.asString

		val experiences = mutableListOf<Experience>()

		for ((typeKey, typeValue) in root.entrySet()) {
			if (typeKey in metadataKeys) continue
			if (!typeValue.isJsonObject) continue

			val typeObj = typeValue.asJsonObject
			val actionsObj = typeObj.getAsJsonObject("actions")
				?: typeObj.getAsJsonObject("cards")
				?: continue

			if (actionsObj.entrySet().isEmpty()) continue

			val experienceType = ExperienceType.fromKey(typeKey) ?: ExperienceType.UNKNOWN

			for ((actionName, actionValue) in actionsObj.entrySet()) {
				if (!actionValue.isJsonObject) continue
				val actionObj = actionValue.asJsonObject

				experiences.add(parseExperience(actionName, actionObj, experienceType, typeKey))
			}
		}

		return ParseResult(experiences, frequencyCapConfig, experimentGroups, editorialId)
	}

	private fun parseExperience(
		name: String,
		action: JsonObject,
		type: ExperienceType,
		typeRaw: String,
	): Experience {
		val contentObj = action.getAsJsonObject("content")
		val contentTypeStr = contentObj?.get("type")?.asString
		val contentUrl = contentObj?.get("url")?.asString

		val rawJsonType = object : TypeToken<Map<String, Any>>() {}.type
		val rawJson: Map<String, Any> = gson.fromJson(action, rawJsonType)

		return Experience(
			id = action.get("id")?.asString ?: "",
			name = name,
			type = type,
			typeRaw = typeRaw,
			placement = action.get("placement")?.asString,
			contentUrl = contentUrl,
			contentType = contentTypeStr?.let { ExperienceContentType.fromKey(it) }
				?: ExperienceContentType.UNKNOWN,
			features = parseMapOrNull(action.get("features")),
			strategy = action.get("strategy")?.asString,
			selectors = parseSelectors(action),
			filters = parseFilters(action),
			rawJson = rawJson,
		).also { it.contentResolver = contentResolver }
	}

	private fun parseSelectors(action: JsonObject): List<ExperienceSelector>? {
		val selectorsArray = action.getAsJsonArray("selectors") ?: return null
		return selectorsArray.mapNotNull { element ->
			if (!element.isJsonObject) return@mapNotNull null
			val obj = element.asJsonObject
			ExperienceSelector(
				selector = obj.get("selector")?.asString ?: return@mapNotNull null,
				strategy = obj.get("strategy")?.asString ?: "",
			)
		}
	}

	private fun parseFilters(action: JsonObject): List<ExperienceFilter>? {
		val filtersArray = action.getAsJsonArray("filters") ?: return null
		return filtersArray.mapNotNull { element ->
			if (!element.isJsonObject) return@mapNotNull null
			val obj = element.asJsonObject
			val valuesArray = obj.getAsJsonArray("values") ?: return@mapNotNull null
			ExperienceFilter(
				key = obj.get("key")?.asString ?: return@mapNotNull null,
				operator = obj.get("operator")?.asString ?: "EQUALS",
				values = valuesArray.map { it.asString },
			)
		}
	}

	private fun parseMapOrNull(element: JsonElement?): Map<String, Any>? {
		if (element == null || !element.isJsonObject) return null
		val type = object : TypeToken<Map<String, Any>>() {}.type
		return gson.fromJson(element, type)
	}

	private fun extractFrequencyCapConfig(root: JsonObject): Map<String, List<String>> {
		val targeting = root.getAsJsonObject("targeting") ?: return emptyMap()
		val freqCap = targeting.getAsJsonObject("frequencyCap") ?: return emptyMap()
		val result = mutableMapOf<String, List<String>>()
		for ((key, value) in freqCap.entrySet()) {
			if (value.isJsonArray) {
				result[key] = value.asJsonArray.map { it.asString }
			}
		}
		return result
	}
}
