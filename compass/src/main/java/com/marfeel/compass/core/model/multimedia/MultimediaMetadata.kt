package com.marfeel.compass.core.model.multimedia

import com.google.gson.annotations.SerializedName
data class MultimediaMetadata(
    @SerializedName("m_il")
    val isLive: Boolean? = false,
    @SerializedName("m_ti")
    val title: String? = null,
    @SerializedName("m_d")
    val description: String? = null,
    @SerializedName("m_u")
    val url: String? = null,
    @SerializedName("m_th")
    val thumbnail: String? = null,
    @SerializedName("m_a")
    val authors: String? = null,
    @SerializedName("m_pt")
    val publishTime: Int? = null,
    @SerializedName("m_l")
    val duration: Int? = null
)
