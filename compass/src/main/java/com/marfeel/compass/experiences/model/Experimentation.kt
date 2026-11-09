package com.marfeel.compass.experiences.model

/**
 * Result of the A/B evaluation for an experience, mirroring the web SDK.
 * [isControl] == true means the app must keep its own native module (do not render the
 * experience content) but still track it, so the control arm can be measured.
 * [name] is the test version sent as `tv` in recirculation events.
 */
data class Experimentation(
	val isControl: Boolean,
	val name: String,
)

internal enum class ExperimentType(val key: String) {
	REQUEST("REQUEST"),
	USER("USER"),
	TEST_GROUP("GROUP");

	companion object {
		fun fromKey(key: String): ExperimentType? = values().find { it.key == key }
	}
}

internal data class ExperienceExperiment(
	val type: ExperimentType,
	val weight: Double?,
	val groupId: String?,
	val variantIds: List<String>?,
)
