package com.marfeel.compass.experiences.model

enum class ExperienceContentType(val key: String) {
	TEXT_HTML("TextHTML"),
	JSON("Json"),
	AMP("AMP"),
	WIDGET_PROVIDER("WidgetProvider"),
	AD_SERVER("AdServer"),
	CONTAINER("Container"),
	UNKNOWN("Unknown");

	companion object {
		fun fromKey(key: String): ExperienceContentType =
			values().find { it.key == key } ?: UNKNOWN
	}
}
