package com.legbehindneck.fasttrack48.data.settings

enum class LogViewMode {
	LIST,
	CALENDAR;

	companion object {
		fun fromName(name: String?): LogViewMode =
			entries.firstOrNull { it.name == name } ?: LIST
	}
}
