package com.marfeel.compass.experiences

import com.marfeel.compass.experiences.model.Experience
import com.marfeel.compass.experiences.model.ExperienceContentType
import com.marfeel.compass.experiences.model.ExperienceType
import com.marfeel.compass.experiences.model.RecirculationLink
import com.marfeel.compass.experiences.model.RecirculationModule
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class RecirculationTrackerTest {
    private val apiClient = mockk<RecirculationApiClient>(relaxed = true)
    private lateinit var tracker: RecirculationTrackerTestable

    internal class RecirculationTrackerTestable(private val apiClient: RecirculationApiClient) : RecirculationTracking {
        override fun trackElegible(modules: List<RecirculationModule>) {
            apiClient.send("elegible", modules)
        }

        override fun trackImpression(module: RecirculationModule) {
            apiClient.send("impression", listOf(module))
        }

        override fun trackClick(module: RecirculationModule) {
            apiClient.send("click", listOf(module))
        }
    }

    @Before
    fun setUp() {
        tracker = RecirculationTrackerTestable(apiClient)
    }

    @Test
    fun `trackElegible delegates to apiClient with elegible event type`() {
        val modules = listOf(
            RecirculationModule("mod-1", listOf(RecirculationLink("https://a.com", "0"))),
            RecirculationModule("mod-2", listOf(RecirculationLink("https://b.com", "1")))
        )
        tracker.trackElegible(modules)
        verify { apiClient.send("elegible", modules) }
    }

    @Test
    fun `trackImpression delegates to apiClient with impression event type`() {
        val module = RecirculationModule("mod-1", listOf(RecirculationLink("https://a.com", "0")))
        tracker.trackImpression(module)
        verify { apiClient.send("impression", listOf(module)) }
    }

    @Test
    fun `trackClick delegates to apiClient with click event type`() {
        val module = RecirculationModule("mod-1", listOf(RecirculationLink("https://a.com", "5")))
        tracker.trackClick(module)
        verify { apiClient.send("click", listOf(module)) }
    }

    @Test
    fun `trackElegible with empty list delegates empty list`() {
        tracker.trackElegible(emptyList())
        verify { apiClient.send("elegible", emptyList()) }
    }

    private fun makeExperience(id: String = "exp-1", contentUrl: String? = "https://example.com"): Experience =
        Experience(
            id = id,
            name = "Test",
            type = ExperienceType.INLINE,
            placement = null,
            contentUrl = contentUrl,
            contentType = ExperienceContentType.TEXT_HTML,
            features = null,
            strategy = null,
            selectors = null,
            filters = null,
            rawJson = emptyMap()
        )

    private fun trackElegibleViaExperiences(experiences: Map<Experience, List<RecirculationLink>>) {
        val modules = experiences.map { (exp, links) -> RecirculationModule(exp.id, links) }
        tracker.trackElegible(modules)
    }

    private fun trackImpressionViaExperiences(experience: Experience, links: List<RecirculationLink>) {
        tracker.trackImpression(RecirculationModule(experience.id, links))
    }

    private fun trackClickViaExperiences(experience: Experience, link: RecirculationLink) {
        tracker.trackClick(RecirculationModule(experience.id, listOf(link)))
    }

    @Test
    fun `trackElegible maps experience id to module name`() {
        val links1 = listOf(RecirculationLink("https://a.com", "0"))
        val links2 = listOf(RecirculationLink("https://b.com", "1"))
        val exp1 = makeExperience("exp-1")
        val exp2 = makeExperience("exp-2")
        val experiences = mapOf(exp1 to links1, exp2 to links2)

        trackElegibleViaExperiences(experiences)

        verify {
            apiClient.send(
                "elegible",
                listOf(RecirculationModule("exp-1", links1), RecirculationModule("exp-2", links2))
            )
        }
    }

    @Test
    fun `trackRecirculationImpression maps experience id to module name with links`() {
        val links = listOf(RecirculationLink("https://a.com", "0"), RecirculationLink("https://b.com", "1"))
        val experience = makeExperience("exp-42")

        trackImpressionViaExperiences(experience, links)

        verify { apiClient.send("impression", listOf(RecirculationModule("exp-42", links))) }
    }

    @Test
    fun `trackClick maps experience id to module name with single link`() {
        val link = RecirculationLink("https://clicked.com", "3")
        val experience = makeExperience("exp-99")

        trackClickViaExperiences(experience, link)

        verify { apiClient.send("click", listOf(RecirculationModule("exp-99", listOf(link)))) }
    }

    @Test
    fun `trackElegible passes client-provided links not contentUrl`() {
        val experience = makeExperience("exp-1", contentUrl = "https://original.com")
        val clientLinks = listOf(RecirculationLink("https://override.com", "0"))
        val experiences = mapOf(experience to clientLinks)

        trackElegibleViaExperiences(experiences)

        verify {
            apiClient.send(
                "elegible",
                listOf(RecirculationModule("exp-1", clientLinks))
            )
        }
    }
}
