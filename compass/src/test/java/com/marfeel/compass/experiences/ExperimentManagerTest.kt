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
	private val userVars = mutableMapOf<String, String>()
	private val userVarWrites = mutableListOf<Pair<String, String>>()

	private val seedA = "eFmyf219Q3qh6OzYsZOdyw"
	private val mixedUid = "4a4ffef6-8d7e-4484-9fd4-7a3abd4c7bb0"

	@Before
	fun setUp() {
		prefs = MockSharedPreference()
		userVars.clear()
		userVarWrites.clear()
		manager = makeManager()
	}

	private fun makeManager(
		userId: String? = null,
		siteUserId: String? = null,
		random: Random = Random.Default,
	) = ExperimentManager(
		preferences = prefs,
		random = random,
		userIdProvider = { userId },
		siteUserIdProvider = { siteUserId },
		readUserVars = { userVars.toMap() },
		writeUserVar = { name, value ->
			userVars[name] = value
			userVarWrites.add(name to value)
		},
	)

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

	private fun makeGroups(
		key: String,
		id: String?,
		hashVersion: Int? = null,
		variants: List<Pair<String, Number>>,
	): JsonObject {
		val group = JsonObject()
		id?.let { group.addProperty("id", it) }
		group.addProperty("name", key)
		hashVersion?.let { group.addProperty("hashVersion", it) }
		val array = JsonArray()
		variants.forEach { (variantId, weight) ->
			array.add(JsonObject().apply {
				addProperty("id", variantId)
				addProperty("name", variantId)
				addProperty("weight", weight)
			})
		}
		group.add("variants", array)
		return JsonObject().apply { add(key, group) }
	}

	private fun hashedTestGroupVariant(userId: String): String =
		DeterministicHash.selectVariant(
			listOf(WeightedVariant("variant_a", 50.0), WeightedVariant("variant_b", 50.0)),
			userId, "testGroup", null
		)!!

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

	private class FixedRandom(private val value: Double) : Random() {
		override fun nextBits(bitCount: Int): Int = throw AssertionError("only nextDouble is expected")
		override fun nextDouble(): Double = value
	}

	// region Assignment

	@Test
	fun `handleExperimentGroups assigns a variant`() {
		manager.handleExperimentGroups(makeExperimentGroups())
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
	fun `handleExperimentGroups without user id uses the random draw`() {
		val groups = makeExperimentGroups()
		val manager1 = makeManager(random = FixedRandom(0.25))
		manager1.handleExperimentGroups(groups)
		val assignment1 = manager1.getAssignments()["testGroup"]

		manager1.clear()
		val manager2 = makeManager(random = FixedRandom(0.25))
		manager2.handleExperimentGroups(groups)
		val assignment2 = manager2.getAssignments()["testGroup"]

		assertEquals("variant_a", assignment1)
		assertEquals(assignment1, assignment2)
	}

	@Test
	fun `handleExperimentGroups hashes with the user id`() {
		val manager = makeManager(userId = "user-1", random = FailingRandom)

		manager.handleExperimentGroups(makeExperimentGroups())

		assertEquals(hashedTestGroupVariant("user-1"), manager.getAssignments()["testGroup"])
	}

	@Test
	fun `handleExperimentGroups falls back to site user id when user id is empty`() {
		val manager = makeManager(userId = "", siteUserId = "site-user", random = FailingRandom)

		manager.handleExperimentGroups(makeExperimentGroups())

		assertEquals(hashedTestGroupVariant("site-user"), manager.getAssignments()["testGroup"])
	}

	@Test
	fun `handleExperimentGroups honours hashVersion`() {
		val variants = listOf<Pair<String, Number>>("1" to 50, "2" to 50)
		val legacy = makeManager(userId = mixedUid, random = FailingRandom)
		legacy.handleExperimentGroups(makeGroups("group", seedA, variants = variants))
		assertEquals("2", legacy.getAssignments()["group"])

		legacy.clear()
		val mixed = makeManager(userId = mixedUid, random = FailingRandom)
		mixed.handleExperimentGroups(makeGroups("group", seedA, hashVersion = 2, variants = variants))
		assertEquals("1", mixed.getAssignments()["group"])
	}

	@Test
	fun `handleExperimentGroups uses the group key as seed without id`() {
		val manager = makeManager(userId = mixedUid, random = FailingRandom)

		manager.handleExperimentGroups(makeGroups(seedA, null, hashVersion = 2, variants = listOf("1" to 50, "2" to 50)))

		assertEquals("1", manager.getAssignments()[seedA])
	}

	@Test
	fun `handleExperimentGroups accepts decimal weights`() {
		val manager = makeManager(userId = "user-1", random = FailingRandom)

		manager.handleExperimentGroups(makeGroups("group", "group", variants = listOf("a" to 33.3, "b" to 66.7)))

		val expected = DeterministicHash.selectVariant(
			listOf(WeightedVariant("a", 33.3), WeightedVariant("b", 66.7)), "user-1", "group", null
		)
		assertEquals(expected, manager.getAssignments()["group"])
	}

	@Test
	fun `handleExperimentGroups keeps a stored assignment over the hash`() {
		val manager = makeManager(userId = "user-1", random = FailingRandom)
		val notHashed = if (hashedTestGroupVariant("user-1") == "variant_a") "variant_b" else "variant_a"
		manager.setAssignment("testGroup", notHashed)

		manager.handleExperimentGroups(makeExperimentGroups())

		assertEquals(notHashed, manager.getAssignments()["testGroup"])
	}

	@Test
	fun `handleExperimentGroups re-hashes when the stored variant is gone`() {
		val manager = makeManager(userId = "user-1", random = FailingRandom)
		manager.setAssignment("testGroup", "variant_removed")

		manager.handleExperimentGroups(makeExperimentGroups())

		assertEquals(hashedTestGroupVariant("user-1"), manager.getAssignments()["testGroup"])
	}

	@Test
	fun `handleExperimentGroups re-hashes when the stored variant has no weight`() {
		val manager = makeManager(userId = "user-1", random = FailingRandom)
		manager.setAssignment("group", "paused")

		manager.handleExperimentGroups(makeGroups("group", "group", variants = listOf("paused" to 0, "live" to 100)))

		assertEquals("live", manager.getAssignments()["group"])
	}

	@Test
	fun `handleExperimentGroups drops groups missing from the response`() {
		manager.setAssignment("finishedGroup", "v1")

		manager.handleExperimentGroups(makeExperimentGroups())

		val assignments = manager.getAssignments()
		assertNull(assignments["finishedGroup"])
		assertNotNull(assignments["testGroup"])
	}

	@Test
	fun `handleExperimentGroups without groups keeps assignments`() {
		manager.setAssignment("testGroup", "variant_a")

		manager.handleExperimentGroups(null)

		assertEquals(mapOf("testGroup" to "variant_a"), manager.getAssignments())
	}

	// endregion

	// region User var

	@Test
	fun `handleExperimentGroups writes the assignment to the user var`() {
		manager.handleExperimentGroups(makeExperimentGroups())

		assertEquals(manager.getAssignments()["testGroup"], userVars["mrfExperiment_testGroup"])
	}

	@Test
	fun `handleExperimentGroups writes a kept assignment to the user var`() {
		manager.setAssignment("testGroup", "variant_b")
		userVars.clear()

		manager.handleExperimentGroups(makeExperimentGroups())

		assertEquals("variant_b", userVars["mrfExperiment_testGroup"])
	}

	@Test
	fun `handleExperimentGroups does not rewrite an unchanged user var`() {
		manager.handleExperimentGroups(makeExperimentGroups())
		manager.handleExperimentGroups(makeExperimentGroups())

		assertEquals(1, userVarWrites.size)
	}

	@Test
	fun `handleExperimentGroups overwrites a stale user var`() {
		manager.setAssignment("testGroup", "variant_a")
		userVars["mrfExperiment_testGroup"] = "stale"

		manager.handleExperimentGroups(makeExperimentGroups())

		assertEquals("variant_a", userVars["mrfExperiment_testGroup"])
	}

	@Test
	fun `setAssignment writes the user var`() {
		manager.setAssignment("testGroup", "variant_b")

		assertEquals("variant_b", userVars["mrfExperiment_testGroup"])
	}

	// endregion

	// region Evaluation

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
		val manager = makeManager(userId = "user-1", siteUserId = "site-user", random = FailingRandom)
		val percentage = ExperimentEvaluator.seededPercentage("user-1", "A")

		val result = manager.evaluate(listOf(
			makeExperience("A", user(50.0)),
			makeExperience("A~v1", user(50.0)),
		))

		assertEquals(listOf(if (percentage <= 50) "A" else "A~v1"), result.map { it.id })
	}

	@Test
	fun `evaluate falls back to site user id when user id is empty`() {
		val manager = makeManager(userId = "", siteUserId = "site-user", random = FailingRandom)
		val percentage = ExperimentEvaluator.seededPercentage("site-user", "A")

		val result = manager.evaluate(listOf(
			makeExperience("A", user(50.0)),
			makeExperience("A~v1", user(50.0)),
		))

		assertEquals(listOf(if (percentage <= 50) "A" else "A~v1"), result.map { it.id })
	}

	// endregion

	// region Manual assignment and clear

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
	fun `handleExperimentGroups after clear re-assigns from the new user id`() {
		manager.setAssignment("testGroup", "variant_a")
		manager.clear()
		val manager = makeManager(userId = "user-2", random = FailingRandom)

		manager.handleExperimentGroups(makeExperimentGroups())

		assertEquals(hashedTestGroupVariant("user-2"), manager.getAssignments()["testGroup"])
	}

	@Test
	fun `getTargetingEntries returns experiment assignments`() {
		manager.handleExperimentGroups(makeExperimentGroups())
		val entries = manager.getTargetingEntries()
		assertNotNull(entries["experiment::testGroup"])
	}

	// endregion
}
