package com.marfeel.compass.experiences

import com.marfeel.compass.experiences.model.ExperienceContentType
import com.marfeel.compass.experiences.model.ExperienceFilterOperator
import com.marfeel.compass.experiences.model.ExperienceType
import com.marfeel.compass.experiences.model.ExperienceFamily
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
	fun `parses small response into flat experience list`() {
		val json = loadJson("experiences_response_small.json")
		val result = parser.parse(json)
		assertTrue(result.experiences.isNotEmpty())
	}

	@Test
	fun `parses inline actions with correct type`() {
		val json = loadJson("experiences_response_small.json")
		val result = parser.parse(json)
		val inlines = result.experiences.filter { it.type == ExperienceType.INLINE }
		assertTrue(inlines.isNotEmpty())
		assertEquals(ExperienceType.INLINE, inlines.first().type)
	}

	@Test
	fun `parses experience id and name from actions map`() {
		val json = loadJson("experiences_response_small.json")
		val result = parser.parse(json)
		val cta = result.experiences.find { it.name.contains("home-cta-widget") }
		assertNotNull(cta)
		assertEquals("IL_mLTwLgXbRS-MJzh1rJM6ng", cta!!.id)
	}

	@Test
	fun `parses TextHTML content type and url`() {
		val json = loadJson("experiences_response_small.json")
		val result = parser.parse(json)
		val cta = result.experiences.find { it.id == "IL_mLTwLgXbRS-MJzh1rJM6ng" }
		assertNotNull(cta)
		assertEquals(ExperienceContentType.TEXT_HTML, cta!!.contentType)
		assertTrue(cta.contentUrl!!.contains("example.com"))
	}

	@Test
	fun `parses Json content type`() {
		val json = loadJson("experiences_response_small.json")
		val result = parser.parse(json)
		val recommender = result.experiences.find { it.id == "IL_r1-HQ0psRiiLuHZTpi9lDQ" }
		assertNotNull(recommender)
		assertEquals(ExperienceContentType.JSON, recommender!!.contentType)
	}

	@Test
	fun `parses compass actions without content url`() {
		val json = loadJson("experiences_response_small.json")
		val result = parser.parse(json)
		val compass = result.experiences.filter { it.type == ExperienceType.COMPASS }
		assertTrue(compass.isNotEmpty())
		assertNull(compass.first().contentUrl)
	}

	@Test
	fun `parses adManager actions`() {
		val json = loadJson("experiences_response_small.json")
		val result = parser.parse(json)
		val ads = result.experiences.filter { it.type == ExperienceType.AD_MANAGER }
		assertEquals(2, ads.size)
	}

	@Test
	fun `parses affiliationEnhancer actions`() {
		val json = loadJson("experiences_response_small.json")
		val result = parser.parse(json)
		val affiliation = result.experiences.filter { it.type == ExperienceType.AFFILIATION_ENHANCER }
		assertEquals(1, affiliation.size)
	}

	@Test
	fun `skips targeting and content metadata keys`() {
		val json = loadJson("experiences_response_small.json")
		val result = parser.parse(json)
		val targeting = result.experiences.filter { it.type.key == "targeting" }
		val content = result.experiences.filter { it.type.key == "content" }
		assertTrue(targeting.isEmpty())
		assertTrue(content.isEmpty())
	}

	@Test
	fun `extracts frequencyCap from targeting`() {
		val json = loadJson("experiences_response_small.json")
		val result = parser.parse(json)
		assertTrue(result.frequencyCapConfig.isNotEmpty())
		assertTrue(result.frequencyCapConfig.containsKey("IL_HMNmL7lWTOWBldjNWm1PgQ"))
	}

	@Test
	fun `parses selectors`() {
		val json = loadJson("experiences_response_small.json")
		val result = parser.parse(json)
		val cta = result.experiences.find { it.id == "IL_mLTwLgXbRS-MJzh1rJM6ng" }
		val selectors = cta!!.selectors
		assertNotNull(selectors)
		assertEquals("#slot-a", selectors!!.first().selector)
		assertEquals("replace", selectors.first().strategy)
	}

	@Test
	fun `parses features map`() {
		val json = loadJson("experiences_response_small.json")
		val result = parser.parse(json)
		val cta = result.experiences.find { it.id == "IL_mLTwLgXbRS-MJzh1rJM6ng" }
		assertEquals("contextual", cta!!.features?.get("mode"))
		assertEquals(true, cta.features?.get("removable"))
	}

	@Test
	fun `parses strategy field`() {
		val json = loadJson("experiences_response_small.json")
		val result = parser.parse(json)
		val cta = result.experiences.find { it.id == "IL_mLTwLgXbRS-MJzh1rJM6ng" }
		assertEquals("replace", cta!!.strategy)
	}

	@Test
	fun `handles flowcards cards key as alias for actions`() {
		val json = loadJson("experiences_response_small.json")
		val result = parser.parse(json)
		val flowcards = result.experiences.filter { it.type == ExperienceType.FLOWCARDS }
		assertEquals(0, flowcards.size)
	}

	@Test
	fun `parses large response with multiple inline experiences`() {
		val json = loadJson("experiences_response_large.json")
		val result = parser.parse(json)
		val inlines = result.experiences.filter { it.type == ExperienceType.INLINE }
		assertTrue(inlines.size >= 10)
	}

	@Test
	fun `parses family when present`() {
		val json = loadJson("experiences_response_small.json")
		val result = parser.parse(json)
		val recommender = result.experiences.find { it.id == "IL_r1-HQ0psRiiLuHZTpi9lDQ" }
		assertEquals(ExperienceFamily.RECOMMENDER, recommender!!.family)
	}

	@Test
	fun `family is null when absent from json`() {
		val json = loadJson("experiences_response_small.json")
		val result = parser.parse(json)
		val cta = result.experiences.find { it.id == "IL_mLTwLgXbRS-MJzh1rJM6ng" }
		assertNull(cta!!.family)
	}

	@Test
	fun `unknown family string maps to UNKNOWN`() {
		val json = """
		{
			"inline": {
				"actions": {
					"test": {
						"id": "test1",
						"family": "somefuturefamily"
					}
				}
			}
		}
		""".trimIndent()
		val result = parser.parse(json)
		assertEquals(ExperienceFamily.UNKNOWN, result.experiences.first().family)
	}

	@Test
	fun `preserves rawJson for each experience`() {
		val json = loadJson("experiences_response_small.json")
		val result = parser.parse(json)
		val compass = result.experiences.find { it.type == ExperienceType.COMPASS }
		assertNotNull(compass)
		assertTrue(compass!!.rawJson.containsKey("recirculationModules"))
	}

	@Test
	fun `tree filter group with empty children produces no filters`() {
		val json = """
		{
			"compass": {
				"actions": {
					"a": {
						"id": "AC_1",
						"filters": { "type": "group", "children": [], "logic": "AND" }
					}
				}
			}
		}
		""".trimIndent()
		val result = parser.parse(json)
		assertNull(result.experiences.first().filters)
	}

	@Test
	fun `tree filter top-level condition is parsed and comparator mapped to operator`() {
		val json = """
		{
			"experimentation": {
				"actions": {
					"a": {
						"id": "AC_1",
						"filters": {
							"type": "condition",
							"field": "url",
							"comparator": "eq",
							"values": ["https://dev.marfeel.co/"]
						}
					}
				}
			}
		}
		""".trimIndent()
		val result = parser.parse(json)
		val filters = result.experiences.first().filters!!
		assertEquals(1, filters.size)
		assertEquals("url", filters[0].key)
		assertEquals(ExperienceFilterOperator.EQUALS, filters[0].operator)
		assertEquals(listOf("https://dev.marfeel.co/"), filters[0].values)
	}

	@Test
	fun `tree filter group flattens nested condition children`() {
		val json = """
		{
			"compass": {
				"actions": {
					"a": {
						"id": "AC_1",
						"filters": {
							"type": "group",
							"logic": "AND",
							"children": [
								{ "type": "condition", "field": "url", "comparator": "contains", "values": ["news"] },
								{ "type": "condition", "field": "lang", "comparator": "neq", "values": ["es"] }
							]
						}
					}
				}
			}
		}
		""".trimIndent()
		val result = parser.parse(json)
		val filters = result.experiences.first().filters!!
		assertEquals(2, filters.size)
		assertEquals(ExperienceFilterOperator.LIKE, filters[0].operator)
		assertEquals(ExperienceFilterOperator.NOT_EQUALS, filters[1].operator)
	}

	@Test
	fun `legacy array filter form still parses`() {
		val json = """
		{
			"inline": {
				"actions": {
					"a": {
						"id": "IL_1",
						"filters": [
							{ "key": "url", "operator": "EQUALS", "values": ["https://example.com"] }
						]
					}
				}
			}
		}
		""".trimIndent()
		val result = parser.parse(json)
		val filters = result.experiences.first().filters!!
		assertEquals(1, filters.size)
		assertEquals("url", filters[0].key)
		assertEquals(ExperienceFilterOperator.EQUALS, filters[0].operator)
	}
}
