package com.marfeel.compass.experiences

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
}
