package com.legbehindneck.fasttrack48.lunar

import com.legbehindneck.fasttrack48.lunar.astro.Sunrise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test

class SunsetTest {

	@Test
	fun sunsetAfterMidnight_isTheEndOfThatDaysDaylight() {
		// Nuuk in early July: the sun sets after local midnight. The day's sunset is the one
		// after its sunrise, dated the next day, not the previous evening's.
		val nuuk = ZoneId.of("America/Nuuk")
		val day = LocalDate.of(2026, 7, 11)
		val rise = Sunrise.sunrise(day, nuuk, 64.18, -51.73)
		val set = Sunrise.sunset(day, nuuk, 64.18, -51.73)
		assertNotNull(rise)
		assertNotNull(set)
		assertTrue(set!!.isAfter(rise))
		assertEquals(day.plusDays(1), set.toLocalDate())
		val daylight = Duration.between(rise, set)
		assertTrue("daylight $daylight", daylight > Duration.ofHours(18) && daylight < Duration.ofHours(24))
	}

	@Test
	fun ordinaryLatitude_unchanged() {
		// Madrid: the sunset falls on its own date, as before.
		val madrid = ZoneId.of("Europe/Madrid")
		val day = LocalDate.of(2026, 11, 4)
		val set = Sunrise.sunset(day, madrid, 40.4168, -3.7038)!!
		assertEquals(day, set.toLocalDate())
		assertEquals(18, set.hour)
	}
}
