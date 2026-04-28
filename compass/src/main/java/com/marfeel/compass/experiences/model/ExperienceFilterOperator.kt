package com.marfeel.compass.experiences.model

enum class ExperienceFilterOperator(val key: String) {
	EQUALS("eq"),
	NOT_EQUALS("neq"),
	LIKE("contains"),
	NLIKE("ncontains"),
	GT("gt"),
	GTE("gte"),
	LT("lt"),
	LTE("lte"),
	UNKNOWN("unknown");

	companion object {
		fun fromKey(key: String): ExperienceFilterOperator =
			values().find { it.key == key }
				?: values().find { it.name == key }
				?: UNKNOWN
	}
}
