package com.legbehindneck.fasttrack48.lunar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class AnandaMargaCalendarTest {

	private val madrid = ZoneId.of("Europe/Madrid")
	private val madridPoint = GeoPoint(40.4168, -3.7038)

	private fun year2026(tradition: EkadashiTradition, zone: ZoneId = madrid, point: GeoPoint = madridPoint): List<FastDay> =
		FastDays.list(LocalDate.of(2026, 1, 1), 364, followAll(tradition), point, zone)

	private fun followAll(tradition: EkadashiTradition) = LunarReminderSettings(
		ekadashiEnabled = true,
		tradition = tradition,
		fullMoonEnabled = true,
		newMoonEnabled = true,
	)

	private fun byKind(days: List<FastDay>): Map<FastKind, List<String>> =
		days.groupBy({ it.kind }, { it.day.toString().substring(5) })

	@Test
	fun writtenRule_isTheVaranasiSunriseDayHoldingTheMidpoint() {
		// Recomputed independently (Astronomy Engine, Varanasi sunrise); the closest case sits
		// 14 minutes from a sunrise.
		val expected = mapOf(
			FastKind.SHUKLA_EKADASHI to "01-28 02-27 03-28 04-27 05-26 06-25 07-24 08-23 09-22 10-21 11-20 12-20",
			FastKind.PURNIMA to "01-02 02-01 03-02 04-01 05-01 05-30 06-29 07-29 08-27 09-26 10-25 11-24 12-23",
			FastKind.KRISHNA_EKADASHI to "01-13 02-12 03-14 04-13 05-12 06-11 07-10 08-08 09-07 10-06 11-04 12-04",
			FastKind.AMAVASYA to "01-18 02-16 03-18 04-17 05-16 06-14 07-13 08-12 09-10 10-10 11-08 12-08",
		).mapValues { it.value.split(' ') }
		assertEquals(expected, byKind(year2026(EkadashiTradition.ANANDA_MARGA)))
	}

	@Test
	fun indiaChartRule_reproducesTheIndiaChart2026() {
		// india.anandamarga.org, 2026: every date but the second Purnima (chart 30 May; its
		// midpoint is 01:07 IST on the 31st). Amavasya 9 Nov is a knife edge: its midpoint is
		// 20 s after midnight IST in this engine, and the chart agrees.
		val expected = mapOf(
			FastKind.SHUKLA_EKADASHI to "01-29 02-27 03-28 04-27 05-26 06-25 07-24 08-23 09-22 10-22 11-20 12-20",
			FastKind.PURNIMA to "01-03 02-01 03-03 04-01 05-01 05-31 06-29 07-29 08-27 09-26 10-25 11-24 12-23",
			FastKind.KRISHNA_EKADASHI to "01-14 02-13 03-14 04-13 05-13 06-11 07-10 08-09 09-07 10-06 11-04 12-04",
			FastKind.AMAVASYA to "01-18 02-17 03-18 04-17 05-16 06-14 07-14 08-12 09-10 10-10 11-09 12-08",
		).mapValues { it.value.split(' ') }
		assertEquals(expected, byKind(year2026(EkadashiTradition.ANANDA_MARGA_INDIA)))
	}

	@Test
	fun datesAreTheSameEverywhere() {
		val madridDays = year2026(EkadashiTradition.ANANDA_MARGA).map { it.kind to it.day }
		val laDays = year2026(
			EkadashiTradition.ANANDA_MARGA, ZoneId.of("America/Los_Angeles"), GeoPoint(34.0522, -118.2437),
		).map { it.kind to it.day }
		val tokyoDays = year2026(
			EkadashiTradition.ANANDA_MARGA, ZoneId.of("Asia/Tokyo"), GeoPoint(35.6762, 139.6503),
		).map { it.kind to it.day }
		assertEquals(madridDays, laDays)
		assertEquals(madridDays, tokyoDays)
	}

	@Test
	fun everyFastRunsFromLocalSunriseToTheNextSunrise() {
		for (fast in year2026(EkadashiTradition.ANANDA_MARGA)) {
			assertTrue(fast.subject.sunriseToSunrise)
			val sunrise = fast.sunrise
			assertNotNull(sunrise)
			assertEquals(fast.day, sunrise!!.toLocalDate())
			val next = when (val s = fast.subject) {
				is EkadashiDay -> s.parana?.start
				is MoonDay -> s.breakFast?.start
			}
			assertEquals(fast.day.plusDays(1), next!!.toLocalDate())
			assertEquals(next, fast.end)
		}
	}

	@Test
	fun aFastStaysCurrentUntilTheNextSunrise() {
		val days = FastDays.list(LocalDate.of(2026, 11, 1), 40, followAll(EkadashiTradition.ANANDA_MARGA), madridPoint, madrid)
		val ekadashi = days.first { it.day == LocalDate.of(2026, 11, 4) }
		// 03:00 the next morning, before sunrise: still the fast day.
		assertEquals(ekadashi, FastDays.current(days, ZonedDateTime.of(2026, 11, 5, 3, 0, 0, 0, madrid)))
		// After that sunrise the next fast (Amavasya, 8 Nov) takes over.
		val after = FastDays.current(days, ekadashi.end.plusMinutes(1))
		assertEquals(LocalDate.of(2026, 11, 8), after?.day)
		assertEquals(FastKind.AMAVASYA, after?.kind)
	}

	@Test
	fun otherCalendarsKeepTheirOwnMoonDays() {
		// Without an Ananda Marga calendar, Purnima stays the Drik vrat day and the fast is not
		// a sunrise-to-sunrise one.
		val smarta = FastDays.list(LocalDate.of(2026, 5, 25), 10, followAll(EkadashiTradition.SMARTA), madridPoint, madrid)
		assertTrue(smarta.none { it.subject.sunriseToSunrise })
	}

	@Test
	fun moonPhaseNames() {
		assertEquals(MoonPhaseName.NEW, MoonPhaseName.of(0.0))
		assertEquals(MoonPhaseName.NEW, MoonPhaseName.of(355.0))
		assertEquals(MoonPhaseName.WAXING_CRESCENT, MoonPhaseName.of(40.0))
		assertEquals(MoonPhaseName.FIRST_QUARTER, MoonPhaseName.of(95.0))
		assertEquals(MoonPhaseName.WAXING_GIBBOUS, MoonPhaseName.of(126.0))
		assertEquals(MoonPhaseName.FULL, MoonPhaseName.of(178.0))
		assertEquals(MoonPhaseName.WANING_GIBBOUS, MoonPhaseName.of(241.0))
		assertEquals(MoonPhaseName.LAST_QUARTER, MoonPhaseName.of(270.0))
		assertEquals(MoonPhaseName.WANING_CRESCENT, MoonPhaseName.of(306.0))
	}

	@Test
	fun moonState_bracketsNowWithFullMoons() {
		val now = ZonedDateTime.of(2026, 10, 30, 12, 0, 0, 0, madrid)
		val moon = FastDays.moon(now)
		assertTrue(moon.lastFullMoon.isBefore(now) && moon.nextFullMoon.isAfter(now))
		assertTrue(moon.nextNewMoon.isAfter(now))
		assertEquals(LocalDate.of(2026, 10, 26), moon.lastFullMoon.toLocalDate())
		assertEquals(LocalDate.of(2026, 11, 9), moon.nextNewMoon.toLocalDate())
		assertEquals(MoonPhaseName.WANING_GIBBOUS, moon.phase)
	}
}
