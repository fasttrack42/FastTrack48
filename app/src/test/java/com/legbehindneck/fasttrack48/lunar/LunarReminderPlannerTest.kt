package com.legbehindneck.fasttrack48.lunar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class LunarReminderPlannerTest {

	private val delhi = ZoneId.of("Asia/Kolkata")
	private val newDelhi = GeoPoint(28.6139, 77.2090)

	@Test
	fun fireTime_staysOnTheHourAcrossDaylightSaving() {
		val ny = ZoneId.of("America/New_York")
		// 2026-03-08 springs forward, 2026-11-01 falls back.
		val spring = LunarReminderPlanner.fireTime(LocalDate.of(2026, 3, 9), 1, 18, ny)
		val fall = LunarReminderPlanner.fireTime(LocalDate.of(2026, 11, 2), 1, 18, ny)
		assertEquals(18, spring.hour)
		assertEquals(LocalDate.of(2026, 3, 8), spring.toLocalDate())
		assertEquals(18, fall.hour)
		assertEquals(LocalDate.of(2026, 11, 1), fall.toLocalDate())
	}

	@Test
	fun plan_isEmptyUntilTurnedOn() {
		val from = ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, delhi)
		assertFalse(LunarReminderSettings().anyActive)
		assertTrue(LunarReminderPlanner.plan(from, LunarReminderSettings(), newDelhi).isEmpty())
		// Ekadashi stays inactive until a calendar is chosen.
		val noTradition = LunarReminderSettings(ekadashiEnabled = true)
		assertTrue(LunarReminderPlanner.plan(from, noTradition, newDelhi).isEmpty())
	}

	@Test
	fun plan_ekadashiAndPurnima_matchDrikPanchang() {
		val from = ZonedDateTime.of(2026, 7, 1, 0, 0, 0, 0, delhi)
		val settings = LunarReminderSettings(
			ekadashiEnabled = true,
			tradition = EkadashiTradition.SMARTA,
			ekadashiLeadDays = setOf(1),
			fullMoonEnabled = true,
			fullMoonLeadDays = setOf(1),
			hourOfDay = 18,
		)
		val plan = LunarReminderPlanner.plan(from, settings, newDelhi, horizonDays = 60)
		val days = plan.map { it.subject.day }
		// Drik Panchang, New Delhi: Yogini 10 Jul (kshaya), Devshayani 25 Jul, Kamika 9 Aug,
		// Shravana Putrada 23 Aug; Purnima vrat 29 Jul and 27 Aug (Chaturdashi day).
		for (day in listOf("2026-07-10", "2026-07-25", "2026-08-09", "2026-08-23", "2026-07-29", "2026-08-27")) {
			assertTrue("missing $day in $days", LocalDate.parse(day) in days)
		}
		plan.forEach {
			assertEquals(18, it.fireAt.hour)
			assertEquals(it.subject.day.minusDays(1), it.fireAt.toLocalDate())
		}
		assertEquals(plan.sortedBy { it.fireAt.toInstant() }, plan)
		val ekadashi = plan.first { it.subject.day == LocalDate.of(2026, 7, 10) }.subject as EkadashiDay
		assertTrue("Smarta Parana present", ekadashi.parana != null)
	}

	@Test
	fun plan_keepsRemindersThatShareAFireTime() {
		// Drik, New Delhi: Jaya Ekadashi 29 Jan 2026, Purnima vrat 1 Feb 2026. "Same day" for
		// the Ekadashi and "3 days before" for the Purnima both fire on 29 Jan at 18:00.
		val from = ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, delhi)
		val settings = LunarReminderSettings(
			ekadashiEnabled = true,
			tradition = EkadashiTradition.SMARTA,
			ekadashiLeadDays = setOf(0),
			fullMoonEnabled = true,
			fullMoonLeadDays = setOf(3),
		)
		val plan = LunarReminderPlanner.plan(from, settings, newDelhi, horizonDays = 40)
		val shared = plan.filter { it.fireAt == ZonedDateTime.of(2026, 1, 29, 18, 0, 0, 0, delhi) }
		assertEquals(2, shared.size)
		assertTrue(shared.any { it.subject is EkadashiDay && it.leadDays == 0 })
		assertTrue(shared.any { it.subject is MoonDay && it.subject.day == LocalDate.of(2026, 2, 1) })
		assertEquals(2, shared.map { it.notificationId }.toSet().size)
	}

	@Test
	fun plan_newMoon_vedicIsDarshaAmavasya_astronomicalIsTheExactDate() {
		// Drik, New Delhi 2026: Darsha Amavasya 18 Mar, 14 Jun, 10 Sep and 8 Nov fall a day
		// before the named Amavasya; the exact new moons are on 19 Mar, 15 Jun, 11 Sep, 9 Nov.
		val from = ZonedDateTime.of(2026, 3, 1, 0, 0, 0, 0, delhi)
		fun days(style: MoonDayStyle) = LunarCalendar.moonDays(from.toLocalDate(), 270, newDelhi, delhi, MoonPhase.NEW, style)
			.map { it.day }
		val vedic = days(MoonDayStyle.VEDIC)
		val astronomical = days(MoonDayStyle.ASTRONOMICAL)
		for (day in listOf("2026-03-18", "2026-06-14", "2026-09-10", "2026-11-08")) {
			assertTrue("Darsha $day in $vedic", LocalDate.parse(day) in vedic)
			assertTrue("exact new moon after $day", LocalDate.parse(day).plusDays(1) in astronomical)
		}

		val settings = LunarReminderSettings(newMoonEnabled = true, newMoonLeadDays = setOf(1))
		val plan = LunarReminderPlanner.plan(from, settings, newDelhi, horizonDays = 60)
		assertTrue(plan.isNotEmpty())
		assertTrue(plan.all { val s = it.subject; s is MoonDay && s.phase == MoonPhase.NEW })
	}
}
