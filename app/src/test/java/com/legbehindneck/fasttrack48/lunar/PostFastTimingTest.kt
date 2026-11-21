package com.legbehindneck.fasttrack48.lunar

import com.legbehindneck.fasttrack48.utils.QuietHours
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** When fast-day reminders may speak again after a fast, and when the day-of reminder fires. */
class PostFastTimingTest {

	private val madrid = ZoneId.of("Europe/Madrid")

	private fun at(d: Int, h: Int, min: Int = 0) = ZonedDateTime.of(2026, 11, d, h, min, 0, 0, madrid)

	@Test
	fun shortFast_quietForAnHourFromTheTap() {
		val end = at(3, 12)
		assertEquals(at(3, 13), LunarReminderPlanner.quietUntil(end, Duration.ofHours(16), seenAt = end))
	}

	@Test
	fun backdatedEnd_countsTheHourFromTheTap_notFromTheEnd() {
		// "I stopped earlier": ended 08:00, set at 12:00. Counted from 08:00 the hour would be over.
		val quiet = LunarReminderPlanner.quietUntil(at(3, 8), Duration.ofHours(16), seenAt = at(3, 12))
		assertEquals(at(3, 13), quiet)
	}

	@Test
	fun dayLongFast_quietForADay() {
		val end = at(3, 19)
		assertEquals(at(4, 19), LunarReminderPlanner.quietUntil(end, Duration.ofHours(24), seenAt = end))
	}

	@Test
	fun fiveDayFast_quietForThreeDays() {
		val end = at(7, 12)
		assertEquals(at(10, 12), LunarReminderPlanner.quietUntil(end, Duration.ofHours(120), seenAt = end))
	}

	@Test
	fun slipOfTheFinger_opensNoWindow() {
		val end = at(3, 12)
		assertNull(LunarReminderPlanner.quietUntil(end, Duration.ofSeconds(30), seenAt = end))
	}

	@Test
	fun quietEndingAtNight_movesToTheMorning() {
		val shortEnd = at(3, 21, 30)
		assertEquals(at(4, 7), LunarReminderPlanner.quietUntil(shortEnd, Duration.ofHours(16), seenAt = shortEnd))
		val longEnd = at(3, 23)
		assertEquals(at(5, 7), LunarReminderPlanner.quietUntil(longEnd, Duration.ofHours(30), seenAt = longEnd))
	}

	@Test
	fun dailySixteenEight_stillGetsItsEveningReminder() {
		// The case a day-long block after every fast would break: 20:00 → 12:00, reminder 18:00.
		val end = at(3, 12)
		val quiet = LunarReminderPlanner.quietUntil(end, Duration.ofHours(16), seenAt = end)!!
		val reminder = LunarReminderPlanner.fireTime(LocalDate.of(2026, 11, 4), 1, 18, madrid)
		assertTrue(quiet.isBefore(reminder))
	}

	@Test
	fun sameDayReminder_comesInTheMorning_otherLeadsKeepTheHour() {
		val day = LocalDate.of(2026, 11, 4)
		assertEquals(at(4, 7), LunarReminderPlanner.fireTime(day, 0, 18, madrid))
		assertEquals(at(4, 6), LunarReminderPlanner.fireTime(day, 0, 6, madrid))
		assertEquals(at(3, 18), LunarReminderPlanner.fireTime(day, 1, 18, madrid))
	}

	@Test
	fun quietHours_coverTenAtNightToSevenInTheMorning() {
		assertTrue(QuietHours.isQuiet(LocalTime.of(22, 0)))
		assertTrue(QuietHours.isQuiet(LocalTime.of(6, 59)))
		assertFalse(QuietHours.isQuiet(LocalTime.of(7, 0)))
		assertFalse(QuietHours.isQuiet(LocalTime.of(21, 59)))
		assertEquals(at(4, 7), QuietHours.after(at(3, 22)))
		assertEquals(at(4, 7), QuietHours.after(at(4, 6, 59)))
		assertEquals(at(3, 21, 59), QuietHours.after(at(3, 21, 59)))
	}
}
