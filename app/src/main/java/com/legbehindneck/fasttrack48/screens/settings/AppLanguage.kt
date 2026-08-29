package com.legbehindneck.fasttrack48.screens.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * The app's own language, independent of the device's.
 *
 * There is deliberately no setting of ours behind this. The selected language already has one
 * canonical home — the platform's per-app locale, which is the framework's `LocaleManager` on
 * API 33+ and AppCompat's own store below it — and [AppCompatDelegate] reads and writes both
 * through one API. Keeping a private copy would create a second source of truth that the
 * system Settings language picker could silently disagree with.
 */

/**
 * BCP-47 tags this app ships translations for, in autonym order.
 *
 * Must mirror `res/xml/locales_config.xml`: that file is what the *system* picker offers, this
 * list is what the in-app picker offers, and a language in one but not the other is either a
 * dead end or a missing option. Region qualifiers match the `res/values-*` folders exactly.
 */
val SupportedLanguageTags: List<String> = listOf(
	"de", "en", "es", "fr", "it", "nl", "pt-BR", "uk-UA", "zh-CN",
)

/**
 * A language's name written in that language — "Deutsch", "Português (Brasil)", "中文 (中国)".
 *
 * An autonym is the one label that is legible to the person who needs it: someone hunting for
 * their language in a list they cannot currently read recognises its own name, not its name
 * translated into the language they are stuck in. It also means nine languages cost zero
 * translated strings, and adding a tenth costs none either.
 */
fun languageAutonym(tag: String): String {
	val locale = Locale.forLanguageTag(tag)
	// ICU yields these lowercase in most languages; sentence case is what a list wants.
	return locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
}

/**
 * The currently selected app language, or `null` when the app follows the device.
 *
 * Resolved back to one of [SupportedLanguageTags] rather than returned verbatim: the platform
 * may hand back a locale it has canonicalised or a resource-resolved variant (`zh-Hans-CN` for
 * `zh-CN`, a bare `uk` for `uk-UA`), and the picker still has to recognise it as the row the
 * user chose. Matching on language alone is exact here because no language appears twice in
 * the list; an override outside the list (nothing in-app can set one) reads as "follow device",
 * which is what the user would see on screen anyway.
 */
fun selectedLanguageTag(): String? {
	val locale = AppCompatDelegate.getApplicationLocales()[0] ?: return null
	return SupportedLanguageTags.firstOrNull {
		Locale.forLanguageTag(it).language == locale.language
	}
}

/**
 * Switch the app to [tag], or back to the device's language when it is `null`.
 *
 * Nothing needs to be recreated by hand. AppCompat recreates its live activities below API 33
 * and the framework restarts the app above it, so the caller's own composition is torn down
 * and rebuilt against the new resources on the way out.
 */
fun applyAppLanguage(tag: String?) {
	AppCompatDelegate.setApplicationLocales(
		if (tag == null) LocaleListCompat.getEmptyLocaleList()
		else LocaleListCompat.forLanguageTags(tag)
	)
}
