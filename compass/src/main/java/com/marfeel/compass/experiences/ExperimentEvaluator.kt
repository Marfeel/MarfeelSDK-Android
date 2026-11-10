package com.marfeel.compass.experiences

import com.marfeel.compass.experiences.model.Experience
import com.marfeel.compass.experiences.model.ExperimentType
import com.marfeel.compass.experiences.model.Experimentation
import java.util.IdentityHashMap
import java.util.zip.CRC32
import kotlin.random.Random

/**
 * Port of the web SDK experiment evaluation (croupier `experiments/evaluate/shared.ts` + `inline.ts`).
 * Decides, for every experience carrying an `experiment`, whether it is the winner, the control
 * or must be dropped, and tags kept ones with `experimentation` (isControl / name).
 */
internal class ExperimentEvaluator(
	private val random: Random = Random.Default,
) {
	private class Group(
		val type: ExperimentType,
		val groupKey: String,
		val members: MutableList<Experience>,
	)

	private class Outcome(
		val winners: Set<Experience>,
		val variantId: String?,
		val failOpen: Boolean,
	)

	private class Evaluation(
		val type: ExperimentType,
		val groupKey: String,
		val groupSize: Int,
		val isWinner: Boolean,
		val name: String,
	)

	fun evaluate(
		experiences: List<Experience>,
		assignments: Map<String, String>,
		userId: String?,
	): List<Experience> {
		val evaluations = evaluateExperiences(experiences, assignments, userId)
		val controlIds = identitySet().apply {
			addAll(testGroupControlIds(experiences, evaluations))
			addAll(standaloneControlIds(experiences, evaluations))
		}

		return experiences.mapNotNull { experience ->
			val evaluation = evaluations[experience] ?: return@mapNotNull experience

			if (evaluation.isWinner) {
				experience.experimentation = Experimentation(isControl = false, name = evaluation.name)
				return@mapNotNull experience
			}

			val isLegacySingle = evaluation.type != ExperimentType.TEST_GROUP && evaluation.groupSize == 1
			if (isLegacySingle || experience in controlIds) {
				experience.experimentation = Experimentation(isControl = true, name = evaluation.name)
				return@mapNotNull experience
			}

			null
		}
	}

	// region Evaluation

	private fun evaluateExperiences(
		experiences: List<Experience>,
		assignments: Map<String, String>,
		userId: String?,
	): Map<Experience, Evaluation> {
		// One draw per evaluation shared by every REQUEST group, taken only when needed.
		val request by lazy(LazyThreadSafetyMode.NONE) { randomPercentage() }
		val evaluations = IdentityHashMap<Experience, Evaluation>()

		groupExperiences(experiences).forEach { group ->
			val outcome = pickOutcome(group, assignments, userId) { request }
			if (outcome.failOpen) return@forEach

			group.members.forEach { member ->
				val isWinner = member in outcome.winners
				evaluations[member] = Evaluation(
					type = group.type,
					groupKey = group.groupKey,
					groupSize = group.members.size,
					isWinner = isWinner,
					name = resolveName(member, group, isWinner, outcome.variantId),
				)
			}
		}

		return evaluations
	}

	private fun groupExperiences(experiences: List<Experience>): Collection<Group> {
		val groups = LinkedHashMap<String, Group>()

		experiences.forEach { experience ->
			val experiment = experience.experiment ?: return@forEach
			val groupKey = if (experiment.type == ExperimentType.TEST_GROUP) {
				experiment.groupId ?: return@forEach
			} else {
				baseExperienceId(experience.id)
			}

			groups.getOrPut("${experiment.type.key}:$groupKey") {
				Group(experiment.type, groupKey, mutableListOf())
			}.members.add(experience)
		}

		return groups.values
	}

	private fun pickOutcome(
		group: Group,
		assignments: Map<String, String>,
		userId: String?,
		request: () -> Double,
	): Outcome = when (group.type) {
		ExperimentType.TEST_GROUP -> pickTestGroupOutcome(group, assignments)
		ExperimentType.REQUEST -> pickStandaloneOutcome(group, request())
		ExperimentType.USER -> pickStandaloneOutcome(group, userRandom(group, userId))
	}

	private fun pickTestGroupOutcome(group: Group, assignments: Map<String, String>): Outcome {
		val variantId = assignments[group.groupKey]
			?: return Outcome(emptySet(), null, failOpen = true)

		val winners = identitySet().apply {
			addAll(group.members.filter { it.experiment?.variantIds?.contains(variantId) == true })
		}

		return Outcome(winners, variantId, failOpen = false)
	}

	private fun pickStandaloneOutcome(group: Group, randomValue: Double): Outcome {
		var cumulative = 0.0

		group.members.sortedWith(BY_ID).forEach { member ->
			cumulative += member.experiment?.weight ?: 0.0
			if (randomValue <= cumulative) {
				return Outcome(identitySet().apply { add(member) }, null, failOpen = false)
			}
		}

		return Outcome(emptySet(), null, failOpen = false)
	}

	private fun userRandom(group: Group, userId: String?): Double =
		if (userId.isNullOrEmpty()) randomPercentage() else seededPercentage(userId, group.groupKey)

	private fun randomPercentage(): Double = kotlin.math.floor(random.nextDouble() * 100)

	private fun resolveName(member: Experience, group: Group, isWinner: Boolean, variantId: String?): String =
		when {
			group.type == ExperimentType.TEST_GROUP -> variantId ?: ""
			!isWinner -> CONTROL_NAME
			member.id.contains(VARIANT_SEPARATOR) -> member.name
			else -> ORIGINAL_NAME
		}

	// endregion

	// region Control selection

	private fun testGroupControlIds(
		experiences: List<Experience>,
		evaluations: Map<Experience, Evaluation>,
	): Set<Experience> {
		val winnerSlotsByGroup = mutableMapOf<String, MutableSet<String>>()
		val nonWinnersByGroup = LinkedHashMap<String, MutableList<Experience>>()

		// Sorted by id to behave exactly like iOS, which loses the response order when parsing JSON.
		experiences.sortedWith(BY_ID).forEach { experience ->
			val evaluation = evaluations[experience] ?: return@forEach
			if (evaluation.type != ExperimentType.TEST_GROUP) return@forEach

			if (evaluation.isWinner) {
				slot(experience)?.let { winnerSlotsByGroup.getOrPut(evaluation.groupKey) { mutableSetOf() }.add(it) }
			} else {
				nonWinnersByGroup.getOrPut(evaluation.groupKey) { mutableListOf() }.add(experience)
			}
		}

		val controlIds = identitySet()

		nonWinnersByGroup.forEach { (groupKey, nonWinners) ->
			val winnerSlots = winnerSlotsByGroup[groupKey].orEmpty()
			val seenSlots = mutableSetOf<String>()

			nonWinners.forEach { experience ->
				val slot = slot(experience) ?: return@forEach
				if (slot in winnerSlots || !seenSlots.add(slot)) return@forEach
				controlIds.add(experience)
			}
		}

		return controlIds
	}

	private fun standaloneControlIds(
		experiences: List<Experience>,
		evaluations: Map<Experience, Evaluation>,
	): Set<Experience> {
		val nonWinnersByGroup = LinkedHashMap<String, MutableList<Experience>>()
		val winningGroups = mutableSetOf<String>()

		experiences.forEach { experience ->
			val evaluation = evaluations[experience] ?: return@forEach
			if (evaluation.type == ExperimentType.TEST_GROUP || evaluation.groupSize == 1) return@forEach

			val groupKey = "${evaluation.type.key}:${evaluation.groupKey}"
			if (evaluation.isWinner) {
				winningGroups.add(groupKey)
			} else {
				nonWinnersByGroup.getOrPut(groupKey) { mutableListOf() }.add(experience)
			}
		}

		val controlIds = identitySet()

		nonWinnersByGroup.forEach { (groupKey, nonWinners) ->
			if (groupKey in winningGroups) return@forEach
			nonWinners.firstOrNull { it.id == baseExperienceId(it.id) }?.let { controlIds.add(it) }
		}

		return controlIds
	}

	// endregion

	companion object {
		private const val VARIANT_SEPARATOR = '~'
		private const val CONTROL_NAME = "control"
		private const val ORIGINAL_NAME = "Original"
		private const val UNSIGNED_INT_RANGE = 4_294_967_296.0

		/** JS string ordering (UTF-16 code units), used by the web SDK. */
		private val BY_ID = Comparator<Experience> { a, b -> a.id.compareTo(b.id) }

		fun baseExperienceId(id: String): String = id.substringBefore(VARIANT_SEPARATOR)

		/** Native experiences may not carry selectors, so the placement is used as the slot fallback. */
		private fun slot(experience: Experience): String? =
			experience.selectors?.firstOrNull()?.selector ?: experience.placement

		/** Must stay in lockstep with the web SDK `deterministic.ts` (hash version 1). */
		internal fun seededPercentage(userId: String, seed: String): Double {
			val crc = CRC32().apply { update("$seed:$userId".toByteArray(Charsets.UTF_8)) }
			return crc.value / UNSIGNED_INT_RANGE * 100
		}

		private fun identitySet(): MutableSet<Experience> =
			java.util.Collections.newSetFromMap(IdentityHashMap())
	}
}
