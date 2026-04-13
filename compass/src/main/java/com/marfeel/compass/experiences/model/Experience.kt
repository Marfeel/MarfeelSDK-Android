package com.marfeel.compass.experiences.model

import com.marfeel.compass.experiences.ContentResolver

data class Experience(
	val id: String,
	val name: String,
	val type: ExperienceType,
	val typeRaw: String,
	val placement: String?,
	val contentUrl: String?,
	val contentType: ExperienceContentType,
	val features: Map<String, Any>?,
	val strategy: String?,
	val selectors: List<ExperienceSelector>?,
	val filters: List<ExperienceFilter>?,
	val rawJson: Map<String, Any>,
) {
	@Transient
	internal var contentResolver: ContentResolver? = null

	var resolvedContent: String? = null
		private set

	suspend fun resolve(): String? {
		if (resolvedContent != null) return resolvedContent
		if (contentUrl == null) return null
		resolvedContent = contentResolver?.fetch(contentUrl, id)
		return resolvedContent
	}
}

data class ExperienceSelector(
	val selector: String,
	val strategy: String,
)

data class ExperienceFilter(
	val key: String,
	val operator: String,
	val values: List<String>,
)
