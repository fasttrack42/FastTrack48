package com.legbehindneck.fasttrack48.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Dates and 12-hour times end in an abbreviation's period in several locales; a sentence
 * that ends on one must not gain a second period.
 */
class DoubledPeriodTest {

	@Test
	fun `abbreviated month before a full stop keeps one period`() {
		assertEquals(
			"Fastentag: Mi., 4. Nov. Fastenbrechen: Do., 5. Nov., 13:59–16:03.",
			AppDateTime.dropDoubledPeriods("Fastentag: Mi., 4. Nov.. Fastenbrechen: Do., 5. Nov., 13:59–16:03."),
		)
		assertEquals(
			"Jour de jeûne : mer. 4 nov.",
			AppDateTime.dropDoubledPeriods("Jour de jeûne : mer. 4 nov.."),
		)
	}

	@Test
	fun `twelve-hour time before a full stop keeps one period`() {
		assertEquals(
			"Romper el ayuno: jue, 5 nov, 1:59 p. m.–4:03 p. m.",
			AppDateTime.dropDoubledPeriods("Romper el ayuno: jue, 5 nov, 1:59 p. m.–4:03 p. m.."),
		)
	}

	@Test
	fun `text without a doubled period is unchanged`() {
		val samples = listOf(
			"Fast day: Wed, Nov 4. Break the fast: Thu, Nov 5, 1:59 PM–4:03 PM.",
			"Vollmond: Mi., 4. Nov., 13:59.",
			"Wait...",
			"满月：11月4日周三 13:59。",
			"",
		)
		samples.forEach { assertEquals(it, AppDateTime.dropDoubledPeriods(it)) }
	}
}
