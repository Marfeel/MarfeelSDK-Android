package com.marfeel.compass.experiences

import com.marfeel.compass.experiences.model.Experience
import com.marfeel.compass.experiences.model.ExperienceContentType
import com.marfeel.compass.experiences.model.ExperienceExperiment
import com.marfeel.compass.experiences.model.ExperienceSelector
import com.marfeel.compass.experiences.model.ExperienceType
import com.marfeel.compass.experiences.model.ExperimentType
import com.marfeel.compass.experiences.model.Experimentation
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.junit.Test
import kotlin.random.Random

class ExperimentEvaluatorTest {

	private class FixedRandom(private val value: Double) : Random() {
		var calls = 0
		override fun nextBits(bitCount: Int): Int = 0
		override fun nextDouble(): Double {
			calls++
			return value
		}
	}

	private object FailingRandom : Random() {
		override fun nextBits(bitCount: Int): Int = throw AssertionError("random must not be used")
		override fun nextDouble(): Double = throw AssertionError("random must not be used")
	}

	private fun makeExperience(
		id: String,
		experiment: ExperienceExperiment?,
		name: String = id,
		placement: String? = null,
		selector: String? = null,
	): Experience = Experience(
		id = id,
		name = name,
		type = ExperienceType.INLINE,
		placement = placement,
		contentUrl = null,
		contentType = ExperienceContentType.UNKNOWN,
		features = null,
		strategy = null,
		selectors = selector?.let { listOf(ExperienceSelector(it, "")) },
		filters = null,
		rawJson = emptyMap(),
	).also { it.experiment = experiment }

	private fun request(weight: Double) = ExperienceExperiment(ExperimentType.REQUEST, weight, null, null)
	private fun user(weight: Double) = ExperienceExperiment(ExperimentType.USER, weight, null, null)
	private fun testGroup(groupId: String, vararg variantIds: String) =
		ExperienceExperiment(ExperimentType.TEST_GROUP, null, groupId, variantIds.toList())

	private fun evaluator(random: Double) = ExperimentEvaluator(FixedRandom(random))

	private fun List<Experience>.ids() = map { it.id }

	// region Experiences without experiment

	@Test
	fun `experience without experiment is kept untouched`() {
		val result = evaluator(0.5).evaluate(listOf(makeExperience("plain", null)), emptyMap(), null)
		assertEquals(listOf("plain"), result.ids())
		assertNull(result[0].experimentation)
	}

	@Test
	fun `test group without groupId is treated as plain experience`() {
		val experience = makeExperience("x", ExperienceExperiment(ExperimentType.TEST_GROUP, null, null, listOf("a")))
		val result = evaluator(0.5).evaluate(listOf(experience), emptyMap(), null)
		assertEquals(listOf("x"), result.ids())
		assertNull(result[0].experimentation)
	}

	// endregion

	// region Standalone REQUEST

	@Test
	fun `request base wins when draw falls in its weight`() {
		val base = makeExperience("A", request(50.0))
		val variant = makeExperience("A~v1", request(50.0), name = "Variant one")

		val result = evaluator(0.3).evaluate(listOf(variant, base), emptyMap(), null)

		assertEquals(listOf("A"), result.ids())
		assertEquals(Experimentation(isControl = false, name = "Original"), result[0].experimentation)
	}

	@Test
	fun `request variant wins and uses action name`() {
		val base = makeExperience("A", request(50.0))
		val variant = makeExperience("A~v1", request(50.0), name = "Variant one")

		val result = evaluator(0.7).evaluate(listOf(base, variant), emptyMap(), null)

		assertEquals(listOf("A~v1"), result.ids())
		assertEquals(Experimentation(isControl = false, name = "Variant one"), result[0].experimentation)
	}

	@Test
	fun `request draw equal to cumulative weight wins`() {
		val result = evaluator(0.5).evaluate(
			listOf(makeExperience("A", request(50.0)), makeExperience("A~v1", request(50.0))),
			emptyMap(),
			null,
		)
		assertEquals(listOf("A"), result.ids())
	}

	@Test
	fun `request without winner marks base as control`() {
		val result = evaluator(0.9).evaluate(
			listOf(makeExperience("A", request(20.0)), makeExperience("A~v1", request(20.0))),
			emptyMap(),
			null,
		)
		assertEquals(listOf("A"), result.ids())
		assertEquals(Experimentation(isControl = true, name = "control"), result[0].experimentation)
	}

	@Test
	fun `request without winner and without base drops every member`() {
		val result = evaluator(0.9).evaluate(
			listOf(makeExperience("A~v1", request(10.0)), makeExperience("A~v2", request(10.0))),
			emptyMap(),
			null,
		)
		assertTrue(result.isEmpty())
	}

	@Test
	fun `legacy single request winner is Original`() {
		val result = evaluator(0.1).evaluate(listOf(makeExperience("A", request(30.0))), emptyMap(), null)
		assertEquals(Experimentation(isControl = false, name = "Original"), result[0].experimentation)
	}

	@Test
	fun `legacy single request loser is kept as control`() {
		val result = evaluator(0.5).evaluate(listOf(makeExperience("A", request(30.0))), emptyMap(), null)
		assertEquals(listOf("A"), result.ids())
		assertEquals(Experimentation(isControl = true, name = "control"), result[0].experimentation)
	}

	@Test
	fun `request draw is shared across groups`() {
		val random = FixedRandom(0.3)
		val experiences = listOf(
			makeExperience("A", request(50.0)),
			makeExperience("A~v1", request(50.0)),
			makeExperience("B", request(50.0)),
			makeExperience("B~v1", request(50.0)),
		)

		val result = ExperimentEvaluator(random).evaluate(experiences, emptyMap(), null)

		assertEquals(1, random.calls)
		assertEquals(listOf("A", "B"), result.ids())
	}

	// endregion

	// region Standalone USER

	@Test
	fun `user experiment is deterministic for the same user`() {
		val percentage = ExperimentEvaluator.seededPercentage("user-1", "A")
		val expectedWinner = if (percentage <= 50) "A" else "A~v1"

		val result = ExperimentEvaluator(FailingRandom).evaluate(
			listOf(makeExperience("A", user(50.0)), makeExperience("A~v1", user(50.0))),
			emptyMap(),
			"user-1",
		)

		assertEquals(listOf(expectedWinner), result.ids())
		assertEquals(false, result[0].experimentation?.isControl)
	}

	@Test
	fun `user experiment falls back to random without user id`() {
		val result = evaluator(0.9).evaluate(
			listOf(makeExperience("A", user(50.0)), makeExperience("A~v1", user(50.0))),
			emptyMap(),
			null,
		)
		assertEquals(listOf("A~v1"), result.ids())
	}

	@Test
	fun `user experiment with empty user id falls back to random`() {
		val result = evaluator(0.1).evaluate(
			listOf(makeExperience("A", user(50.0)), makeExperience("A~v1", user(50.0))),
			emptyMap(),
			"",
		)
		assertEquals(listOf("A"), result.ids())
	}

	@Test
	fun `request and user groups with same base id are independent`() {
		val result = evaluator(0.5).evaluate(
			listOf(makeExperience("A", request(100.0)), makeExperience("A~v1", user(0.0))),
			emptyMap(),
			null,
		)

		val byId = result.associate { it.id to it.experimentation }
		assertEquals(Experimentation(isControl = false, name = "Original"), byId["A"])
		assertEquals(Experimentation(isControl = true, name = "control"), byId["A~v1"])
	}

	// endregion

	// region Test groups

	@Test
	fun `test group keeps assigned variant and drops loser on same slot`() {
		val result = evaluator(0.5).evaluate(
			listOf(
				makeExperience("X", testGroup("g", "va"), selector = "#s1"),
				makeExperience("Y", testGroup("g", "vb"), selector = "#s1"),
			),
			mapOf("g" to "va"),
			null,
		)

		assertEquals(listOf("X"), result.ids())
		assertEquals(Experimentation(isControl = false, name = "va"), result[0].experimentation)
	}

	@Test
	fun `test group marks loser on unclaimed slot as control`() {
		val result = evaluator(0.5).evaluate(
			listOf(
				makeExperience("X", testGroup("g", "va"), selector = "#s1"),
				makeExperience("Y", testGroup("g", "vb"), selector = "#s2"),
			),
			mapOf("g" to "va"),
			null,
		)

		assertEquals(listOf("X", "Y"), result.ids())
		assertEquals(Experimentation(isControl = true, name = "va"), result[1].experimentation)
	}

	@Test
	fun `test group keeps only one control per slot chosen by id`() {
		val result = evaluator(0.5).evaluate(
			listOf(
				makeExperience("X", testGroup("g", "va"), selector = "#s1"),
				makeExperience("Z", testGroup("g", "vc"), selector = "#s2"),
				makeExperience("Y", testGroup("g", "vb"), selector = "#s2"),
			),
			mapOf("g" to "va"),
			null,
		)

		assertEquals(listOf("X", "Y"), result.ids())
	}

	@Test
	fun `test group falls back to placement when no selectors`() {
		val result = evaluator(0.5).evaluate(
			listOf(
				makeExperience("X", testGroup("g", "va"), placement = "top"),
				makeExperience("Y", testGroup("g", "vb"), placement = "top"),
				makeExperience("Z", testGroup("g", "vb"), placement = "bottom"),
			),
			mapOf("g" to "va"),
			null,
		)

		assertEquals(listOf("X", "Z"), result.ids())
		assertEquals(true, result[1].experimentation?.isControl)
	}

	@Test
	fun `test group selector takes precedence over placement`() {
		val result = evaluator(0.5).evaluate(
			listOf(
				makeExperience("X", testGroup("g", "va"), placement = "top", selector = "#s1"),
				makeExperience("Y", testGroup("g", "vb"), placement = "top", selector = "#s2"),
			),
			mapOf("g" to "va"),
			null,
		)

		assertEquals(listOf("X", "Y"), result.ids())
	}

	@Test
	fun `test group loser without slot is dropped`() {
		val result = evaluator(0.5).evaluate(
			listOf(
				makeExperience("X", testGroup("g", "va"), selector = "#s1"),
				makeExperience("Y", testGroup("g", "vb")),
			),
			mapOf("g" to "va"),
			null,
		)

		assertEquals(listOf("X"), result.ids())
	}

	@Test
	fun `test group without assignment fails open`() {
		val result = evaluator(0.5).evaluate(
			listOf(
				makeExperience("X", testGroup("g", "va"), selector = "#s1"),
				makeExperience("Y", testGroup("g", "vb"), selector = "#s1"),
			),
			emptyMap(),
			"user-1",
		)

		assertEquals(listOf("X", "Y"), result.ids())
		assertTrue(result.all { it.experimentation == null })
	}

	@Test
	fun `test group assignment without members marks one control per slot`() {
		val result = evaluator(0.5).evaluate(
			listOf(
				makeExperience("X", testGroup("g", "va"), selector = "#s1"),
				makeExperience("Y", testGroup("g", "vb"), selector = "#s1"),
			),
			mapOf("g" to "control"),
			null,
		)

		assertEquals(listOf("X"), result.ids())
		assertEquals(Experimentation(isControl = true, name = "control"), result[0].experimentation)
	}

	@Test
	fun `test group member in several variants wins`() {
		val result = evaluator(0.5).evaluate(
			listOf(makeExperience("X", testGroup("g", "va", "vb"), selector = "#s1")),
			mapOf("g" to "vb"),
			null,
		)
		assertEquals(Experimentation(isControl = false, name = "vb"), result[0].experimentation)
	}

	// endregion

	@Test
	fun `keeps response order of kept experiences`() {
		val result = evaluator(0.9).evaluate(
			listOf(
				makeExperience("plain-1", null),
				makeExperience("A~v1", request(50.0)),
				makeExperience("plain-2", null),
				makeExperience("A", request(50.0)),
			),
			emptyMap(),
			null,
		)

		assertEquals(listOf("plain-1", "A~v1", "plain-2"), result.ids())
	}

	// region Helpers parity with web

	@Test
	fun `baseExperienceId strips the variant suffix`() {
		assertEquals("A", ExperimentEvaluator.baseExperienceId("A"))
		assertEquals("A", ExperimentEvaluator.baseExperienceId("A~v1"))
		assertEquals("A", ExperimentEvaluator.baseExperienceId("A~v1~x"))
		assertEquals("", ExperimentEvaluator.baseExperienceId("~v1"))
	}

	@Test
	fun `seededPercentage matches web sdk`() {
		// Value computed with croupier `seededPercentage('9f1c2e-ñ-user', 'IL_abc123')`.
		assertEquals(95.147161395289, ExperimentEvaluator.seededPercentage("9f1c2e-ñ-user", "IL_abc123"), 1e-9)
	}

	// endregion

	// region DeterministicHash (vectors shared with Croupier `DeterministicHashTest` and web `deterministic.test.ts`)

	@Test
	fun `hash matches croupier legacy vectors`() {
		assertEquals(3_129_066_013L, DeterministicHash.crc32("seed:user"))
		assertEquals(0.7285424538422376, DeterministicHash.fraction("seed:user"), 1e-15)
		assertEquals(72.85424538422376, DeterministicHash.seededPercentage("user", "seed"), 1e-12)
	}

	@Test
	fun `hash matches croupier mixed vectors`() {
		assertEquals(18_576_395L, DeterministicHash.fmix32(DeterministicHash.crc32("seed:user").toInt()).toLong() and 0xFFFFFFFFL)
		assertEquals(0.0043251540046185255, DeterministicHash.fraction("seed:user", 2), 1e-15)
		assertEquals(0.9152210694737732, DeterministicHash.fraction("grüpo:üser-🎯", 2), 1e-15)
	}

	@Test
	fun `hash version 1 and null keep the legacy hash`() {
		assertEquals(DeterministicHash.fraction("seed:user"), DeterministicHash.fraction("seed:user", 1))
	}

	@Test
	fun `selectVariant matches web legacy vectors`() {
		val variants = listOf(WeightedVariant("variant-a", 50.0), WeightedVariant("variant-b", 50.0))

		assertEquals("variant-a", DeterministicHash.selectVariant(variants, "user-1", "group-a", null))
		assertEquals("variant-b", DeterministicHash.selectVariant(variants, "user-2", "group-a", null))
	}

	@Test
	fun `selectVariant mixed hash decouples equal-length seeds`() {
		val uid = "4a4ffef6-8d7e-4484-9fd4-7a3abd4c7bb0"
		val variants = listOf(WeightedVariant("1", 50.0), WeightedVariant("2", 50.0))
		val seedA = "eFmyf219Q3qh6OzYsZOdyw"
		val seedB = "HfdplrMXRIKhGtvBUUZOoQ"

		assertEquals("2", DeterministicHash.selectVariant(variants, uid, seedA, null))
		assertEquals("2", DeterministicHash.selectVariant(variants, uid, seedB, null))
		assertEquals("1", DeterministicHash.selectVariant(variants, uid, seedA, 2))
		assertEquals("2", DeterministicHash.selectVariant(variants, uid, seedB, 2))
	}

	@Test
	fun `selectVariant returns null without weighted variants`() {
		assertNull(DeterministicHash.selectVariant(emptyList(), "user-1", "group-a", null))
		assertNull(DeterministicHash.selectVariant(listOf(WeightedVariant("variant-a", 0.0)), "user-1", "group-a", null))
	}

	@Test
	fun `selectVariant skips zero weight variants`() {
		val variants = listOf(WeightedVariant("off", 0.0), WeightedVariant("on", 33.3))

		assertEquals("on", DeterministicHash.selectVariant(variants, "user-1", "group-a", 2))
	}

	// endregion
}
