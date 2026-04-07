package com.marfeel.compass.experiences

import com.marfeel.compass.di.CompassComponent
import com.marfeel.compass.experiences.model.Experience
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
	fun trackImpression(experienceId: String)
	fun trackClose(experienceId: String)

	fun trackElegible(experiences: Map<Experience, List<RecirculationLink>>)
	fun trackRecirculationImpression(experience: Experience, links: List<RecirculationLink>)
	fun trackClick(experience: Experience, link: RecirculationLink)

	suspend fun fetchExperiences(
		url: String,
		filterByType: ExperienceType? = null,
		filterByTypeRaw: String? = null,
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
	private val recirculationTracker: RecirculationTracking = RecirculationTracker

	private val customTargeting = ConcurrentHashMap<String, String>()

	override fun addTargeting(key: String, value: String) {
		customTargeting[key] = value
	}

	override fun trackImpression(experienceId: String) {
		frequencyCapManager.trackImpression(experienceId)
	}

	override fun trackClose(experienceId: String) {
		frequencyCapManager.trackClose(experienceId)
	}

	override fun trackElegible(experiences: Map<Experience, List<RecirculationLink>>) {
		val modules = experiences.map { (exp, links) -> RecirculationModule(exp.id, links) }
		recirculationTracker.trackElegible(modules)
	}

	override fun trackRecirculationImpression(experience: Experience, links: List<RecirculationLink>) {
		recirculationTracker.trackImpression(RecirculationModule(experience.id, links))
	}

	override fun trackClick(experience: Experience, link: RecirculationLink) {
		recirculationTracker.trackClick(RecirculationModule(experience.id, listOf(link)))
	}

	override suspend fun fetchExperiences(
		url: String,
		filterByType: ExperienceType?,
		filterByTypeRaw: String?,
		resolve: Boolean
	): List<Experience> = withContext(Dispatchers.IO) {
		check(CompassTracker.initialized) { compassNotInitializedErrorMessage }

		val jsonResponse = apiClient.fetch(url, customTargeting) ?: return@withContext emptyList()

		val parseResult = responseParser.parse(jsonResponse)

		frequencyCapManager.updateFrequencyCapConfig(parseResult.frequencyCapConfig)

		experimentManager.handleExperimentGroups(parseResult.experimentGroups)

		var experiences = experimentManager.filterByExperiments(parseResult.experiences)

		if (filterByType != null) {
			experiences = experiences.filter { it.type == filterByType }
		} else if (filterByTypeRaw != null) {
			experiences = experiences.filter { it.typeRaw == filterByTypeRaw }
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
