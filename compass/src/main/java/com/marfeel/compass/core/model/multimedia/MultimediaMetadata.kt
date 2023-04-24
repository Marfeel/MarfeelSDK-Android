package com.marfeel.compass.core.model.multimedia

import kotlinx.serialization.SerialName

data class MultimediaMetadata(
    @SerialName("m_il")
    val isLive: Boolean? = false,
    @SerialName("m_ti")
    val title: String? = null,
    @SerialName("m_d")
    val description: String? = null,
    @SerialName("m_u")
    val url: String? = null,
    @SerialName("m_th")
    val thumbnail: String? = null,
    @SerialName("m_a")
    val authors: String? = null,
    @SerialName("m_pt")
    val publishTime: Int? = null,
    @SerialName("m_l")
    val duration: Int? = null
)
