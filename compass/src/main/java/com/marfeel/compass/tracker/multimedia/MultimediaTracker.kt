package com.marfeel.compass.tracker.multimedia

import com.marfeel.compass.core.model.multimedia.Event
import com.marfeel.compass.core.model.multimedia.Type
import com.marfeel.compass.core.model.multimedia.MultimediaMetadata
import com.marfeel.compass.core.ping.MultimediaPingEmitter
import com.marfeel.compass.di.CompassComponent
import com.marfeel.compass.tracker.CompassTracker
import com.marfeel.compass.tracker.compassNotInitializedErrorMessage

private fun itemNotInitializedErrorMessage(id: String): String {
    return String.format(
        "Multimedia item %s has not been initialized. MultimediaTracker.initializeItem must be called before tracking the item.",
        id
    )
}

interface MultimediaTracking {
    /**
     * Registers multimedia item for tracking
     * @param id The item identifier
     * @param provider The multimedia provider
     * @param providerId The multimedia provider identifier
     * @param type The multimedia type
     * @param metadata The multimedia metadata
     */
    fun initializeItem(id: String, provider: String, providerId: String, type: Type, metadata: MultimediaMetadata)

    /**
     * Tracks an event for the item matching the provided id.
     * @param id The item identifier
     * @param event The event to track
     * @param eventTime The time when the event has occurred
     */
    fun registerEvent(id: String, event: Event, eventTime: Int)

    companion object {
        /**
         *
         * @return The singleton instance of the MultimediaTracking interface
         */
        fun getInstance(): MultimediaTracking = MultimediaTracker

        internal fun reset() {
            (getInstance() as MultimediaTracker).reset()
        }
    }
}

internal object MultimediaTracker : MultimediaTracking {

    private val pingEmitter: MultimediaPingEmitter by lazy { CompassComponent.multimediaPingEmitter }
    private val items:HashMap<String, MultimediaItem> = hashMapOf();

    override fun initializeItem(id: String, provider: String, providerId: String, type: Type, metadata: MultimediaMetadata) {
        items[id] = MultimediaItem(id, provider, providerId, type, metadata)

        track(id)
    }

    override fun registerEvent(id: String, event: Event, eventTime: Int) {
        val item = checkNotNull(items[id]) {  itemNotInitializedErrorMessage(id) }

        item.addEvent(event, eventTime);

        track(id)
    }

    private fun track(id: String) {
        check(CompassTracker.initialized) { compassNotInitializedErrorMessage }

        val item = checkNotNull(items[id]) {  itemNotInitializedErrorMessage(id) }

        pingEmitter.ping(item)
    }

    internal fun reset() {
        pingEmitter.reset()
    }
}
