package com.marfeel.compass.experiences

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.marfeel.compass.experiences.model.Experience
import com.marfeel.compass.experiences.model.ExperienceContentType
import com.marfeel.compass.experiences.model.ExperienceFilter
import com.marfeel.compass.experiences.model.ExperienceType
import com.marfeel.compass.storage.MockSharedPreference
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
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
		filters: List<ExperienceFilter>? = null
	): Experience = Experience(
		id = id,
		name = id,
		type = ExperienceType.INLINE,
		typeRaw = "inline",
		placement = null,
		contentUrl = null,
		contentType = ExperienceContentType.UNKNOWN,
		features = null,
		strategy = null,
		selectors = null,
		filters = filters,
		rawJson = emptyMap()
	)

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
	fun `filterByExperiments keeps experience with matching filter`() {
		val groups = makeExperimentGroups()
		manager.handleExperimentGroups(groups)
		val assignedVariant = manager.getAssignments()["testGroup"]!!

		val experience = makeExperience(
			"exp1",
			filters = listOf(ExperienceFilter("mrf_exp_testGroup", "EQUALS", listOf(assignedVariant)))
		)
		val filtered = manager.filterByExperiments(listOf(experience))
		assertEquals(1, filtered.size)
	}

	@Test
	fun `filterByExperiments drops experience with non-matching filter`() {
		val groups = makeExperimentGroups()
		manager.handleExperimentGroups(groups)

		val experience = makeExperience(
			"exp1",
			filters = listOf(ExperienceFilter("mrf_exp_testGroup", "EQUALS", listOf("nonexistent_variant")))
		)
		val filtered = manager.filterByExperiments(listOf(experience))
		assertEquals(0, filtered.size)
	}

	@Test
	fun `filterByExperiments keeps experience with no filters`() {
		val experience = makeExperience("exp1")
		val filtered = manager.filterByExperiments(listOf(experience))
		assertEquals(1, filtered.size)
	}

	@Test
	fun `filterByExperiments keeps experience with non-experiment filters`() {
		val experience = makeExperience(
			"exp1",
			filters = listOf(ExperienceFilter("url", "EQUALS", listOf("something.com")))
		)
		val filtered = manager.filterByExperiments(listOf(experience))
		assertEquals(1, filtered.size)
	}

	@Test
	fun `filterByExperiments keeps experience with NOT_EQUALS filter when variant differs`() {
		val groups = makeExperimentGroups()
		manager.handleExperimentGroups(groups)
		val assignedVariant = manager.getAssignments()["testGroup"]!!
		val otherVariant = if (assignedVariant == "variant_a") "variant_b" else "variant_a"

		val experience = makeExperience(
			"exp1",
			filters = listOf(ExperienceFilter("mrf_exp_testGroup", "NOT_EQUALS", listOf(otherVariant)))
		)
		val filtered = manager.filterByExperiments(listOf(experience))
		assertEquals(1, filtered.size)
	}

	@Test
	fun `filterByExperiments drops experience with NOT_EQUALS filter when variant matches`() {
		val groups = makeExperimentGroups()
		manager.handleExperimentGroups(groups)
		val assignedVariant = manager.getAssignments()["testGroup"]!!

		val experience = makeExperience(
			"exp1",
			filters = listOf(ExperienceFilter("mrf_exp_testGroup", "NOT_EQUALS", listOf(assignedVariant)))
		)
		val filtered = manager.filterByExperiments(listOf(experience))
		assertEquals(0, filtered.size)
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
