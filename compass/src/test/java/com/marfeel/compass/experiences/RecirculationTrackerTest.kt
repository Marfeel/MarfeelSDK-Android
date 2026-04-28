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

    internal class RecirculationTrackerTestable(private val apiClient: RecirculationApiClient) : Recirculation {
        override fun trackEligible(name: String, links: List<RecirculationLink>) {
            apiClient.send("elegible", listOf(RecirculationModule(name, links)))
        }

        override fun trackImpression(name: String, links: List<RecirculationLink>) {
            apiClient.send("impression", listOf(RecirculationModule(name, links)))
        }

        override fun trackImpression(name: String, link: RecirculationLink) {
            trackImpression(name, listOf(link))
        }

        override fun trackClick(name: String, link: RecirculationLink) {
            apiClient.send("click", listOf(RecirculationModule(name, listOf(link))))
        }
    }

    @Before
    fun setUp() {
        tracker = RecirculationTrackerTestable(apiClient)
    }

    @Test
    fun `trackEligible delegates to apiClient with elegible event type`() {
        val links = listOf(RecirculationLink("https://a.com", 0))
        tracker.trackEligible("mod-1", links)
        verify { apiClient.send("elegible", listOf(RecirculationModule("mod-1", links))) }
    }

    @Test
    fun `trackImpression delegates to apiClient with impression event type`() {
        val links = listOf(RecirculationLink("https://a.com", 0))
        tracker.trackImpression("mod-1", links)
        verify { apiClient.send("impression", listOf(RecirculationModule("mod-1", links))) }
    }

    @Test
    fun `trackClick delegates to apiClient with click event type`() {
        val link = RecirculationLink("https://a.com", 5)
        tracker.trackClick("mod-1", link)
        verify { apiClient.send("click", listOf(RecirculationModule("mod-1", listOf(link)))) }
    }

    @Test
    fun `trackEligible with empty list delegates empty list`() {
        tracker.trackEligible("mod-1", emptyList())
        verify { apiClient.send("elegible", listOf(RecirculationModule("mod-1", emptyList()))) }
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

    private fun trackEligibleViaExperiences(experience: Experience, links: List<RecirculationLink>) {
        tracker.trackEligible(experience.id, links)
    }

    private fun trackImpressionViaExperiences(experience: Experience, links: List<RecirculationLink>) {
        tracker.trackImpression(experience.id, links)
    }

    private fun trackClickViaExperiences(experience: Experience, link: RecirculationLink) {
        tracker.trackClick(experience.id, link)
    }

    @Test
    fun `trackEligible maps experience id to module name`() {
        val links = listOf(RecirculationLink("https://a.com", 0))
        val exp = makeExperience("exp-1")

        trackEligibleViaExperiences(exp, links)

        verify {
            apiClient.send("elegible", listOf(RecirculationModule("exp-1", links)))
        }
    }

    @Test
    fun `trackRecirculationImpression maps experience id to module name with links`() {
        val links = listOf(RecirculationLink("https://a.com", 0), RecirculationLink("https://b.com", 1))
        val experience = makeExperience("exp-42")

        trackImpressionViaExperiences(experience, links)

        verify { apiClient.send("impression", listOf(RecirculationModule("exp-42", links))) }
    }

    @Test
    fun `trackClick maps experience id to module name with single link`() {
        val link = RecirculationLink("https://clicked.com", 3)
        val experience = makeExperience("exp-99")

        trackClickViaExperiences(experience, link)

        verify { apiClient.send("click", listOf(RecirculationModule("exp-99", listOf(link)))) }
    }

    @Test
    fun `trackEligible passes client-provided links not contentUrl`() {
        val experience = makeExperience("exp-1", contentUrl = "https://original.com")
        val clientLinks = listOf(RecirculationLink("https://override.com", 0))

        trackEligibleViaExperiences(experience, clientLinks)

        verify {
            apiClient.send(
                "elegible",
                listOf(RecirculationModule("exp-1", clientLinks))
            )
        }
    }
}
