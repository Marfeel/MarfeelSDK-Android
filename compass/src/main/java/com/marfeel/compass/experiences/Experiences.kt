package com.marfeel.compass.experiences

import com.marfeel.compass.di.CompassComponent
import com.marfeel.compass.experiences.model.Experience
import com.marfeel.compass.experiences.model.ExperienceFamily
import com.marfeel.compass.experiences.model.ExperienceType
import com.marfeel.compass.experiences.model.Experimentation
import com.marfeel.compass.experiences.model.RecirculationLink
import com.marfeel.compass.storage.SessionStorage
import com.marfeel.compass.tracker.CompassTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

interface Experiences {
	fun addTargeting(key: String, value: String)
	fun trackImpression(experience: Experience, links: List<RecirculationLink> = emptyList())
	fun trackImpression(experience: Experience, link: RecirculationLink)
	fun trackClose(experience: Experience)
	fun clearFrequencyCaps()
	fun getFrequencyCapCounts(experienceId: String): Map<String, Long>
	fun getFrequencyCapConfig(): Map<String, List<String>>
	fun clearReadEditorials()
	fun getReadEditorials(): List<String>

	fun getExperimentAssignments(): Map<String, String>
	fun setExperimentAssignment(groupId: String, variantId: String)
	fun clearExperimentAssignments()

	fun trackEligible(experience: Experience, links: List<RecirculationLink>)
	fun trackClick(experience: Experience, link: RecirculationLink)

	suspend fun fetchExperiences(
		filterByType: ExperienceType? = null,
		filterByFamily: ExperienceFamily? = null,
		resolve: Boolean = false,
		url: String? = null
	): List<Experience>

	companion object {
		fun getInstance(): Experiences = ExperiencesTracker
	}
}

internal object ExperiencesTracker : Experiences {
	private val apiClient: ExperiencesApiClient by lazy { CompassComponent.experiencesApiClient }
	private val responseParser: ExperiencesResponseParser by lazy { CompassComponent.experiencesResponseParser }
	private val experimentManager: ExperimentManager by lazy { CompassComponent.experimentManager }
	private val frequencyCapManager: FrequencyCapManager by lazy { CompassComponent.frequencyCapManager }
	private val readEditorialsManager: ReadEditorialsManager by lazy { CompassComponent.readEditorialsManager }
	private val sessionStorage: SessionStorage by lazy { CompassComponent.sessionStorage }
	private val recirculationTracker = RecirculationTracker

	// Experimentation of fetched experiences, so tracking calls made with a rebuilt
	// Experience (e.g. from the Flutter / React Native bridges) still send the test version.
	private val experimentationById = ConcurrentHashMap<String, Experimentation>()

	private val customTargeting = ConcurrentHashMap<String, String>()

	private fun storeExperimentation(experiences: List<Experience>) {
		experiences.forEach { experience ->
			experience.experimentation?.let { experimentationById[experience.id] = it }
		}
	}

	private fun testVersion(experience: Experience): String? =
		(experience.experimentation ?: experimentationById[experience.id])?.name

	/**
	 * Same module name as the web SDK (`data-mrf-recirculation`): the base experience id,
	 * shared by every arm of an A/B test so they can be compared.
	 */
	private fun moduleName(experience: Experience): String =
		ExperimentEvaluator.baseExperienceId(experience.id)

	override fun addTargeting(key: String, value: String) {
		customTargeting[key] = value
	}

	override fun trackImpression(experience: Experience, links: List<RecirculationLink>) {
		frequencyCapManager.trackImpression(experience.id)
		if (links.isNotEmpty()) {
			recirculationTracker.trackImpression(moduleName(experience), links, testVersion(experience))
		}
	}

	override fun trackImpression(experience: Experience, link: RecirculationLink) {
		trackImpression(experience, listOf(link))
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

	override fun trackEligible(experience: Experience, links: List<RecirculationLink>) {
		recirculationTracker.trackEligible(moduleName(experience), links, testVersion(experience))
	}

	override fun trackClick(experience: Experience, link: RecirculationLink) {
		recirculationTracker.trackClick(moduleName(experience), link, testVersion(experience))
	}

	override suspend fun fetchExperiences(
		filterByType: ExperienceType?,
		filterByFamily: ExperienceFamily?,
		resolve: Boolean,
		url: String?
	): List<Experience> = withContext(Dispatchers.IO) {
		if (!CompassTracker.initialized) return@withContext emptyList()

		val pageUrl = url ?: sessionStorage.readPage()?.url ?: return@withContext emptyList()
		val jsonResponse = apiClient.fetch(pageUrl, customTargeting) ?: return@withContext emptyList()

		val parseResult = responseParser.parse(jsonResponse)

		frequencyCapManager.applyResponseConfig(parseResult.frequencyCapConfig)

		parseResult.editorialId?.let { readEditorialsManager.add(it) }

		experimentManager.handleExperimentGroups(parseResult.experimentGroups)

		var experiences = experimentManager.evaluate(parseResult.experiences)
		storeExperimentation(experiences)

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
