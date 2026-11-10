package com.marfeel.compass.experiences

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.marfeel.compass.experiences.model.Experience
import com.marfeel.compass.experiences.model.ExperienceContentType
import com.marfeel.compass.experiences.model.ExperienceExperiment
import com.marfeel.compass.experiences.model.ExperienceSelector
import com.marfeel.compass.experiences.model.ExperienceType
import com.marfeel.compass.experiences.model.ExperimentType
import com.marfeel.compass.experiences.model.Experimentation
import com.marfeel.compass.storage.MockSharedPreference
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

class ExperimentManagerTest {
	private lateinit var prefs: MockSharedPreference
	private lateinit var manager: ExperimentManager

	@Before
	fun setUp() {
		prefs = MockSharedPreference()
		manager = ExperimentManager(prefs)
	}

	private fun makeExperimentGroups(): JsonObject {
		val groups = JsonObject()
		val group = JsonObject()
		group.addProperty("id", "testGroup")
		group.addProperty("name", "Test Group")
		val variants = JsonArray()
		val v1 = JsonObject()
		v1.addProperty("id", "variant_a")
		v1.addProperty("name", "Variant A")
		v1.addProperty("weight", 50)
		variants.add(v1)
		val v2 = JsonObject()
		v2.addProperty("id", "variant_b")
		v2.addProperty("name", "Variant B")
		v2.addProperty("weight", 50)
		variants.add(v2)
		group.add("variants", variants)
		groups.add("testGroup", group)
		return groups
	}

	private fun makeExperience(
		id: String,
		experiment: ExperienceExperiment? = null,
		selector: String? = null,
	): Experience = Experience(
		id = id,
		name = id,
		type = ExperienceType.INLINE,
		placement = null,
		contentUrl = null,
		contentType = ExperienceContentType.UNKNOWN,
		features = null,
		strategy = null,
		selectors = selector?.let { listOf(ExperienceSelector(it, "")) },
		filters = null,
		rawJson = emptyMap()
	).also { it.experiment = experiment }

	private fun testGroup(variantId: String) =
		ExperienceExperiment(ExperimentType.TEST_GROUP, null, "testGroup", listOf(variantId))

	private fun user(weight: Double) = ExperienceExperiment(ExperimentType.USER, weight, null, null)

	private object FailingRandom : Random() {
		override fun nextBits(bitCount: Int): Int = throw AssertionError("random must not be used")
		override fun nextDouble(): Double = throw AssertionError("random must not be used")
	}

	@Test
	fun `handleExperimentGroups assigns a variant`() {
		val groups = makeExperimentGroups()
		manager.handleExperimentGroups(groups)
		val assignments = manager.getAssignments()
		assertTrue(assignments.containsKey("testGroup"))
		assertTrue(assignments["testGroup"] in listOf("variant_a", "variant_b"))
	}

	@Test
	fun `handleExperimentGroups preserves existing assignment`() {
		val groups = makeExperimentGroups()
		manager.handleExperimentGroups(groups)
		val first = manager.getAssignments()["testGroup"]
		manager.handleExperimentGroups(groups)
		val second = manager.getAssignments()["testGroup"]
		assertEquals(first, second)
	}

	@Test
	fun `handleExperimentGroups with seeded random is deterministic`() {
		val groups = makeExperimentGroups()
		val manager1 = ExperimentManager(prefs, Random(42))
		manager1.handleExperimentGroups(groups)
		val assignment1 = manager1.getAssignments()["testGroup"]

		prefs.edit().clear().apply()
		val manager2 = ExperimentManager(prefs, Random(42))
		manager2.handleExperimentGroups(groups)
		val assignment2 = manager2.getAssignments()["testGroup"]

		assertEquals(assignment1, assignment2)
	}

	@Test
	fun `evaluate keeps assigned variant and drops loser on same slot`() {
		manager.setAssignment("testGroup", "variant_a")

		val result = manager.evaluate(listOf(
			makeExperience("exp_a", testGroup("variant_a"), "#slot"),
			makeExperience("exp_b", testGroup("variant_b"), "#slot"),
		))

		assertEquals(listOf("exp_a"), result.map { it.id })
		assertEquals(Experimentation(isControl = false, name = "variant_a"), result[0].experimentation)
	}

	@Test
	fun `evaluate marks loser on other slot as control`() {
		manager.setAssignment("testGroup", "variant_a")

		val result = manager.evaluate(listOf(
			makeExperience("exp_a", testGroup("variant_a"), "#slot-1"),
			makeExperience("exp_b", testGroup("variant_b"), "#slot-2"),
		))

		assertEquals(listOf("exp_a", "exp_b"), result.map { it.id })
		assertEquals(Experimentation(isControl = true, name = "variant_a"), result[1].experimentation)
	}

	@Test
	fun `evaluate uses assignments from handleExperimentGroups`() {
		manager.handleExperimentGroups(makeExperimentGroups())
		val assigned = manager.getAssignments()["testGroup"]

		val result = manager.evaluate(listOf(
			makeExperience("exp_a", testGroup("variant_a"), "#slot"),
			makeExperience("exp_b", testGroup("variant_b"), "#slot"),
		))

		assertEquals(1, result.size)
		assertEquals(assigned, result[0].experimentation?.name)
	}

	@Test
	fun `evaluate keeps test group unmarked without assignment`() {
		val result = manager.evaluate(listOf(
			makeExperience("exp_a", testGroup("variant_a"), "#slot"),
			makeExperience("exp_b", testGroup("variant_b"), "#slot"),
		))

		assertEquals(2, result.size)
		assertTrue(result.all { it.experimentation == null })
	}

	@Test
	fun `evaluate keeps experience without experiment`() {
		val result = manager.evaluate(listOf(makeExperience("exp1")))
		assertEquals(1, result.size)
		assertNull(result[0].experimentation)
	}

	@Test
	fun `evaluate hashes user experiments with user id`() {
		val manager = ExperimentManager(prefs, FailingRandom, { "user-1" }, { "site-user" })
		val percentage = ExperimentEvaluator.seededPercentage("user-1", "A")

		val result = manager.evaluate(listOf(
			makeExperience("A", user(50.0)),
			makeExperience("A~v1", user(50.0)),
		))

		assertEquals(listOf(if (percentage <= 50) "A" else "A~v1"), result.map { it.id })
	}

	@Test
	fun `evaluate falls back to site user id when user id is empty`() {
		val manager = ExperimentManager(prefs, FailingRandom, { "" }, { "site-user" })
		val percentage = ExperimentEvaluator.seededPercentage("site-user", "A")

		val result = manager.evaluate(listOf(
			makeExperience("A", user(50.0)),
			makeExperience("A~v1", user(50.0)),
		))

		assertEquals(listOf(if (percentage <= 50) "A" else "A~v1"), result.map { it.id })
	}

	@Test
	fun `setAssignment writes the given variant`() {
		manager.setAssignment("testGroup", "variant_b")
		assertEquals("variant_b", manager.getAssignments()["testGroup"])
	}

	@Test
	fun `setAssignment overrides an existing assignment`() {
		manager.handleExperimentGroups(makeExperimentGroups())
		manager.setAssignment("testGroup", "variant_a")
		assertEquals("variant_a", manager.getAssignments()["testGroup"])
	}

	@Test
	fun `setAssignment preserves other group assignments`() {
		manager.setAssignment("groupOne", "v1")
		manager.setAssignment("groupTwo", "v2")
		val assignments = manager.getAssignments()
		assertEquals("v1", assignments["groupOne"])
		assertEquals("v2", assignments["groupTwo"])
	}

	@Test
	fun `clear removes all assignments`() {
		manager.handleExperimentGroups(makeExperimentGroups())
		assertEquals(1, manager.getAssignments().size)
		manager.clear()
		assertEquals(0, manager.getAssignments().size)
	}

	@Test
	fun `handleExperimentGroups after clear re-assigns`() {
		manager.handleExperimentGroups(makeExperimentGroups())
		val first = manager.getAssignments()["testGroup"]
		manager.clear()
		manager.handleExperimentGroups(makeExperimentGroups())
		assertNotNull(manager.getAssignments()["testGroup"])
		assertTrue(first in listOf("variant_a", "variant_b"))
	}

	@Test
	fun `getTargetingEntries returns experiment assignments`() {
		val groups = makeExperimentGroups()
		manager.handleExperimentGroups(groups)
		val entries = manager.getTargetingEntries()
		assertNotNull(entries["experiment::testGroup"])
	}
}
