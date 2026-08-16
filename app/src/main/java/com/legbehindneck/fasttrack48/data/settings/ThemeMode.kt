package com.legbehindneck.fasttrack48.data.settings

enum class ThemeMode {
	SYSTEM,
	LIGHT,
	DARK;

	companion object {
		fun fromName(name: String?): ThemeMode =
			entries.firstOrNull { it.name == name } ?: SYSTEM
	}
}
