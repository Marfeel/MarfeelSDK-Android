package com.marfeel.compass.experiences

import com.marfeel.compass.di.CompassComponent
import com.marfeel.compass.experiences.model.RecirculationLink
import com.marfeel.compass.experiences.model.RecirculationModule
import com.marfeel.compass.storage.SessionStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

interface Recirculation {
    fun trackEligible(name: String, links: List<RecirculationLink>)
    fun trackImpression(name: String, links: List<RecirculationLink>)
    fun trackImpression(name: String, link: RecirculationLink)
    fun trackClick(name: String, link: RecirculationLink)

    companion object {
        fun getInstance(): Recirculation = RecirculationTracker
    }
}

internal class WholeModuleAugmenter(private val pageUrlProvider: () -> String?) {
    private val lock = Any()
    private var currentPageUrl: String? = null
    private val moduleStates = mutableMapOf<String, Boolean>()

    fun onEligible(modules: List<RecirculationModule>): List<RecirculationModule> =
        synchronized(lock) {
            resetIfPageChanged()
            modules.map { module ->
                if (!moduleStates.containsKey(module.name)) {
                    moduleStates[module.name] = false
                    module.copy(links = module.links + wholeModuleLink())
                } else {
                    module
                }
            }
        }

    fun onImpression(module: RecirculationModule): RecirculationModule =
        synchronized(lock) {
            resetIfPageChanged()
            if (moduleStates[module.name] == false) {
                moduleStates[module.name] = true
                module.copy(links = module.links + wholeModuleLink())
            } else {
                module
            }
        }

    private fun resetIfPageChanged() {
        val pageUrl = pageUrlProvider()
        if (pageUrl != currentPageUrl) {
            currentPageUrl = pageUrl
            moduleStates.clear()
        }
    }

    companion object {
        const val WHOLE_MODULE_POSITION = 255
        const val WHOLE_MODULE_URL = " "

        fun wholeModuleLink(): RecirculationLink =
            RecirculationLink(url = WHOLE_MODULE_URL, position = WHOLE_MODULE_POSITION)
    }
}

internal object RecirculationTracker : Recirculation {
    private val recirculationApiClient: RecirculationApiClient by lazy { CompassComponent.recirculationApiClient }
    private val sessionStorage: SessionStorage by lazy { CompassComponent.sessionStorage }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val augmenter by lazy { WholeModuleAugmenter { sessionStorage.readPage()?.url } }

    override fun trackEligible(name: String, links: List<RecirculationLink>) {
        val module = RecirculationModule(name, links)
        val augmented = augmenter.onEligible(listOf(module))
        scope.launch { recirculationApiClient.send("elegible", augmented) }
    }

    override fun trackImpression(name: String, links: List<RecirculationLink>) {
        val module = RecirculationModule(name, links)
        val augmented = augmenter.onImpression(module)
        scope.launch { recirculationApiClient.send("impression", listOf(augmented)) }
    }

    override fun trackImpression(name: String, link: RecirculationLink) {
        trackImpression(name, listOf(link))
    }

    override fun trackClick(name: String, link: RecirculationLink) {
        val module = RecirculationModule(name, listOf(link))
        scope.launch { recirculationApiClient.send("click", listOf(module)) }
    }
}
