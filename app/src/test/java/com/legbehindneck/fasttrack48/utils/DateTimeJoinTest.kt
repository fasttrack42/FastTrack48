package com.legbehindneck.fasttrack48.utils

import com.legbehindneck.fasttrack48.data.settings.DateStyle
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** A date and its time read as one phrase, punctuated as each language writes it. */
class DateTimeJoinTest {

	@Test
	fun `comma in languages that use one`() {
		listOf("en", "de", "es", "fr", "it", "nl", "pt-BR", "uk-UA").forEach { tag ->
			assertEquals(
				tag,
				"Thu, Nov 5, 13:59",
				AppDateTime.joinDateTime("Thu, Nov 5", "13:59", DateStyle.OPTIMIZED_WEEKDAY, Locale.forLanguageTag(tag)),
			)
		}
	}

	@Test
	fun `space in Chinese`() {
		assertEquals(
			"11月5日周四 13:59–16:03",
			AppDateTime.joinDateTime("11月5日周四", "13:59–16:03", DateStyle.OPTIMIZED_WEEKDAY, Locale.SIMPLIFIED_CHINESE),
		)
	}

	@Test
	fun `space for ISO in every language`() {
		assertEquals(
			"2026-11-05 13:59",
			AppDateTime.joinDateTime("2026-11-05", "13:59", DateStyle.ISO, Locale.GERMANY),
		)
	}
}
