package com.marfeel.compass.tracker.multimedia

import com.google.gson.annotations.SerializedName
import com.marfeel.compass.core.model.multimedia.Event
import com.marfeel.compass.core.model.multimedia.MultimediaMetadata
import com.marfeel.compass.core.model.multimedia.PlaybackInfo
import com.marfeel.compass.core.model.multimedia.Type
import java.util.*

private val TIMELESS_EVENTS = arrayOf(Event.LEAVE_VIEWPORT, Event.LEAVE_VIEWPORT)

internal class MultimediaItem(
    @Transient
    val id: String,
    @SerializedName("m_p")
    val provider: String,
    @SerializedName("m_pi")
    val providerId: String,
    @SerializedName("m_t")
    val type: Type,
    @Transient
    val metadata: MultimediaMetadata,
) {
    @SerializedName("imp")
    val imp = UUID.randomUUID().toString()
    @SerializedName("m")
    val playbackInfo = PlaybackInfo()

    fun addEvent(event: Event, eventTime: Int) {
        if (metadata.duration == null || eventTime <= (metadata.duration + 1) || TIMELESS_EVENTS.contains(event)) {
            playbackInfo.addEvent(event, eventTime)
        }
    }
}