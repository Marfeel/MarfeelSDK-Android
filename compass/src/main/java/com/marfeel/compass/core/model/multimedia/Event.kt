package com.marfeel.compass.core.model.multimedia

enum class Event(val id: String) {
    PLAY("play"),
    PAUSE("pause"),
    END("end"),
    UPDATE_CURRENT_TIME("updateCurrentTime"),
    AD_PLAY("adPlay"),
    MUTE("mute"),
    UNMUTE("unmute"),
    FULL_SCREEN("fullscreen"),
    BACK_SCREEN("backscreen"),
    ENTER_VIEWPORT("enterViewport"),
    LEAVE_VIEWPORT("leaveViewport")
}