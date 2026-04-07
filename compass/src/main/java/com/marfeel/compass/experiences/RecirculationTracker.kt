package com.marfeel.compass.experiences

import com.marfeel.compass.di.CompassComponent
import com.marfeel.compass.experiences.model.RecirculationModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

interface RecirculationTracking {
    fun trackElegible(modules: List<RecirculationModule>)
    fun trackImpression(module: RecirculationModule)
    fun trackClick(module: RecirculationModule)

    companion object {
        fun getInstance(): RecirculationTracking = RecirculationTracker
    }
}

internal object RecirculationTracker : RecirculationTracking {
    private val recirculationApiClient: RecirculationApiClient by lazy { CompassComponent.recirculationApiClient }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun trackElegible(modules: List<RecirculationModule>) {
        scope.launch { recirculationApiClient.send("elegible", modules) }
    }

    override fun trackImpression(module: RecirculationModule) {
        scope.launch { recirculationApiClient.send("impression", listOf(module)) }
    }

    override fun trackClick(module: RecirculationModule) {
        scope.launch { recirculationApiClient.send("click", listOf(module)) }
    }
}
