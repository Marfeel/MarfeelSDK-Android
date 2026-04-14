package com.marfeel.compass.experiences

import com.marfeel.compass.di.CompassComponent
import com.marfeel.compass.experiences.model.Experience
import com.marfeel.compass.experiences.model.ExperienceFamily
import com.marfeel.compass.experiences.model.ExperienceType
import com.marfeel.compass.experiences.model.RecirculationLink
import com.marfeel.compass.experiences.model.RecirculationModule
import com.marfeel.compass.tracker.CompassTracker
import com.marfeel.compass.tracker.compassNotInitializedErrorMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

interface ExperiencesTracking {
	fun addTargeting(key: String, value: String)
	fun trackImpression(experience: Experience, links: List<RecirculationLink> = emptyList())
	fun trackClose(experience: Experience)
	fun clearFrequencyCaps()
	fun getFrequencyCapCounts(experienceId: String): Map<String, Long>
	fun getFrequencyCapConfig(): Map<String, List<String>>
	fun clearReadEditorials()
	fun getReadEditorials(): List<String>

	fun getExperimentAssignments(): Map<String, String>
	fun setExperimentAssignment(groupId: String, variantId: String)
	fun clearExperimentAssignments()

	fun trackElegible(experiences: Map<Experience, List<RecirculationLink>>)
	fun trackClick(experience: Experience, link: RecirculationLink)

	suspend fun fetchExperiences(
		url: String,
		filterByType: ExperienceType? = null,
		filterByFamily: ExperienceFamily? = null,
		resolve: Boolean = true
	): List<Experience>

	companion object {
		fun getInstance(): ExperiencesTracking = ExperiencesTracker
	}
}

internal object ExperiencesTracker : ExperiencesTracking {
	private val apiClient: ExperiencesApiClient by lazy { CompassComponent.experiencesApiClient }
	private val responseParser: ExperiencesResponseParser by lazy { CompassComponent.experiencesResponseParser }
	private val experimentManager: ExperimentManager by lazy { CompassComponent.experimentManager }
	private val frequencyCapManager: FrequencyCapManager by lazy { CompassComponent.frequencyCapManager }
	private val readEditorialsManager: ReadEditorialsManager by lazy { CompassComponent.readEditorialsManager }
	private val recirculationTracker: RecirculationTracking = RecirculationTracker

	private val customTargeting = ConcurrentHashMap<String, String>()

	override fun addTargeting(key: String, value: String) {
		customTargeting[key] = value
	}

	override fun trackImpression(experience: Experience, links: List<RecirculationLink>) {
		frequencyCapManager.trackImpression(experience.id)
		if (links.isNotEmpty()) {
			recirculationTracker.trackImpression(RecirculationModule(experience.id, links))
		}
	}

	override fun trackClose(experience: Experience) {
		frequencyCapManager.trackClose(experience.id)
	}

	override fun clearFrequencyCaps() {
		frequencyCapManager.clear()
	}

	override fun getFrequencyCapCounts(experienceId: String): Map<String, Long> =
		frequencyCapManager.getCounts(experienceId)

	override fun getFrequencyCapConfig(): Map<String, List<String>> =
		frequencyCapManager.getConfig()

	override fun clearReadEditorials() {
		readEditorialsManager.clear()
	}

	override fun getReadEditorials(): List<String> = readEditorialsManager.getIds()

	override fun getExperimentAssignments(): Map<String, String> = experimentManager.getAssignments()

	override fun setExperimentAssignment(groupId: String, variantId: String) {
		experimentManager.setAssignment(groupId, variantId)
	}

	override fun clearExperimentAssignments() {
		experimentManager.clear()
	}

	override fun trackElegible(experiences: Map<Experience, List<RecirculationLink>>) {
		val modules = experiences.map { (exp, links) -> RecirculationModule(exp.id, links) }
		recirculationTracker.trackElegible(modules)
	}

	override fun trackClick(experience: Experience, link: RecirculationLink) {
		recirculationTracker.trackClick(RecirculationModule(experience.id, listOf(link)))
	}

	override suspend fun fetchExperiences(
		url: String,
		filterByType: ExperienceType?,
		filterByFamily: ExperienceFamily?,
		resolve: Boolean
	): List<Experience> = withContext(Dispatchers.IO) {
		check(CompassTracker.initialized) { compassNotInitializedErrorMessage }

		val jsonResponse = apiClient.fetch(url, customTargeting) ?: return@withContext emptyList()

		val parseResult = responseParser.parse(jsonResponse)

		frequencyCapManager.applyResponseConfig(parseResult.frequencyCapConfig)

		parseResult.editorialId?.let { readEditorialsManager.add(it) }

		experimentManager.handleExperimentGroups(parseResult.experimentGroups)

		var experiences = experimentManager.filterByExperiments(parseResult.experiences)

		if (filterByType != null) {
			experiences = experiences.filter { it.type == filterByType }
		}
		if (filterByFamily != null) {
			experiences = experiences.filter { it.family == filterByFamily }
		}

		if (resolve) {
			resolveAll(experiences)
		}

		experiences
	}

	private suspend fun resolveAll(experiences: List<Experience>) = coroutineScope {
		experiences
			.filter { it.contentUrl != null }
			.map { experience -> async { experience.resolve() } }
			.awaitAll()
	}
}
