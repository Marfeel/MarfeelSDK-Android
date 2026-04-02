package com.marfeel.compass.experiences

import com.marfeel.compass.experiences.model.ExperienceContentType
import com.marfeel.compass.experiences.model.ExperienceType
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.junit.Test

class ExperiencesResponseParserTest {
	private val parser = ExperiencesResponseParser()

	private fun loadJson(name: String): String =
		javaClass.classLoader!!.getResourceAsStream(name)!!.bufferedReader().readText()

	@Test
	fun `parses elpais response into flat experience list`() {
		val json = loadJson("experiences_elpais_response.json")
		val result = parser.parse(json)
		assertTrue(result.experiences.isNotEmpty())
	}

	@Test
	fun `parses inline actions with correct type`() {
		val json = loadJson("experiences_elpais_response.json")
		val result = parser.parse(json)
		val inlines = result.experiences.filter { it.type == ExperienceType.INLINE }
		assertTrue(inlines.isNotEmpty())
		assertEquals("inline", inlines.first().typeRaw)
	}

	@Test
	fun `parses experience id and name from actions map`() {
		val json = loadJson("experiences_elpais_response.json")
		val result = parser.parse(json)
		val cta = result.experiences.find { it.name.contains("CTA cabecera") }
		assertNotNull(cta)
		assertEquals("IL_mLTwLgXbRS-MJzh1rJM6ng", cta!!.id)
	}

	@Test
	fun `parses TextHTML content type and url`() {
		val json = loadJson("experiences_elpais_response.json")
		val result = parser.parse(json)
		val cta = result.experiences.find { it.id == "IL_mLTwLgXbRS-MJzh1rJM6ng" }
		assertNotNull(cta)
		assertEquals(ExperienceContentType.TEXT_HTML, cta!!.contentType)
		assertTrue(cta.contentUrl!!.contains("flowcards.mrf.io/transformer"))
	}

	@Test
	fun `parses Json content type`() {
		val json = loadJson("experiences_elpais_response.json")
		val result = parser.parse(json)
		val recommender = result.experiences.find { it.id == "IL_r1-HQ0psRiiLuHZTpi9lDQ" }
		assertNotNull(recommender)
		assertEquals(ExperienceContentType.JSON, recommender!!.contentType)
	}

	@Test
	fun `parses compass actions without content url`() {
		val json = loadJson("experiences_elpais_response.json")
		val result = parser.parse(json)
		val compass = result.experiences.filter { it.type == ExperienceType.COMPASS }
		assertTrue(compass.isNotEmpty())
		assertNull(compass.first().contentUrl)
	}

	@Test
	fun `parses adManager actions`() {
		val json = loadJson("experiences_elpais_response.json")
		val result = parser.parse(json)
		val ads = result.experiences.filter { it.type == ExperienceType.AD_MANAGER }
		assertEquals(2, ads.size)
	}

	@Test
	fun `parses affiliationEnhancer actions`() {
		val json = loadJson("experiences_elpais_response.json")
		val result = parser.parse(json)
		val affiliation = result.experiences.filter { it.type == ExperienceType.AFFILIATION_ENHANCER }
		assertEquals(1, affiliation.size)
	}

	@Test
	fun `skips targeting and content metadata keys`() {
		val json = loadJson("experiences_elpais_response.json")
		val result = parser.parse(json)
		val targeting = result.experiences.filter { it.typeRaw == "targeting" }
		val content = result.experiences.filter { it.typeRaw == "content" }
		assertTrue(targeting.isEmpty())
		assertTrue(content.isEmpty())
	}

	@Test
	fun `extracts frequencyCap from targeting`() {
		val json = loadJson("experiences_elpais_response.json")
		val result = parser.parse(json)
		assertTrue(result.frequencyCapConfig.isNotEmpty())
		assertTrue(result.frequencyCapConfig.containsKey("IL_HMNmL7lWTOWBldjNWm1PgQ"))
	}

	@Test
	fun `parses selectors`() {
		val json = loadJson("experiences_elpais_response.json")
		val result = parser.parse(json)
		val cta = result.experiences.find { it.id == "IL_mLTwLgXbRS-MJzh1rJM6ng" }
		val selectors = cta!!.selectors
		assertNotNull(selectors)
		assertEquals("#s_b_df", selectors!!.first().selector)
		assertEquals("replace", selectors.first().strategy)
	}

	@Test
	fun `parses features map`() {
		val json = loadJson("experiences_elpais_response.json")
		val result = parser.parse(json)
		val cta = result.experiences.find { it.id == "IL_mLTwLgXbRS-MJzh1rJM6ng" }
		assertEquals("contextual", cta!!.features?.get("mode"))
		assertEquals(true, cta.features?.get("removable"))
	}

	@Test
	fun `parses strategy field`() {
		val json = loadJson("experiences_elpais_response.json")
		val result = parser.parse(json)
		val cta = result.experiences.find { it.id == "IL_mLTwLgXbRS-MJzh1rJM6ng" }
		assertEquals("replace", cta!!.strategy)
	}

	@Test
	fun `handles flowcards cards key as alias for actions`() {
		val json = loadJson("experiences_elpais_response.json")
		val result = parser.parse(json)
		val flowcards = result.experiences.filter { it.type == ExperienceType.FLOWCARDS }
		assertEquals(0, flowcards.size)
	}

	@Test
	fun `parses 20m response with multiple inline experiences`() {
		val json = loadJson("experiences_20m_response.json")
		val result = parser.parse(json)
		val inlines = result.experiences.filter { it.type == ExperienceType.INLINE }
		assertTrue(inlines.size >= 10)
	}

	@Test
	fun `preserves rawJson for each experience`() {
		val json = loadJson("experiences_elpais_response.json")
		val result = parser.parse(json)
		val compass = result.experiences.find { it.type == ExperienceType.COMPASS }
		assertNotNull(compass)
		assertTrue(compass!!.rawJson.containsKey("recirculationModules"))
	}
}
