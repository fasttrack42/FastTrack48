package com.legbehindneck.fasttrack48.lunar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class LunarReminderDueTest {

	private val london = ZoneId.of("Europe/London")
	private val moscow = ZoneId.of("Europe/Moscow")
	private val newYork = ZoneId.of("America/New_York")
	private val ekadashi = LocalDate.of(2026, 11, 4)

	private fun reminder(day: LocalDate, lead: Int, hour: Int, zone: ZoneId) = PlannedLunarReminder(
		subject = EkadashiDay(day, krishna = true, tradition = EkadashiTradition.SMARTA, parana = null, mahadvadashi = null),
		leadDays = lead,
		fireAt = LunarReminderPlanner.fireTime(day, lead, hour, zone),
	)

	private fun at(zone: ZoneId, y: Int, m: Int, d: Int, h: Int, min: Int = 0) =
		ZonedDateTime.of(y, m, d, h, min, 0, 0, zone)

	@Test
	fun lateAlarmAfterMidnight_stillPosts_countedFromToday() {
		// 23:00 the day before, delivered at 00:10: "Ekadashi today", not dropped.
		val plan = listOf(reminder(ekadashi, 1, 23, london))
		val now = at(london, 2026, 11, 4, 0, 10)
		val due = LunarReminderPlanner.due(plan, now, now, LocalDateTime.of(2026, 11, 3, 20, 0), emptySet())
		assertEquals(1, due.toPost.size)
		assertEquals(0, due.toPost.single().leadDays)
		assertEquals(setOf(plan.single().key), due.handled)
	}

	@Test
	fun eastwardZoneChange_postsTheReminderNowPastInTheNewZone() {
		// Last check 16:00 in London; at 17:00 London the phone moves to Moscow (20:00).
		// The reminder re-planned for 18:00 Moscow is owed now, though that instant is 15:00Z.
		val plan = listOf(reminder(ekadashi, 1, 18, moscow))
		val now = at(moscow, 2026, 11, 3, 20, 0)
		val due = LunarReminderPlanner.due(plan, now, now, LocalDateTime.of(2026, 11, 3, 16, 0), emptySet())
		assertEquals(1, due.toPost.size)
	}

	@Test
	fun westwardZoneChange_doesNotRepeatAPostedReminder() {
		val posted = reminder(ekadashi, 1, 18, london).key
		// Posted at 18:00 London; the phone moved to New York, where 18:00 comes again.
		val plan = listOf(reminder(ekadashi, 1, 18, newYork))
		val now = at(newYork, 2026, 11, 3, 18, 0)
		val due = LunarReminderPlanner.due(plan, now, now, LocalDateTime.of(2026, 11, 3, 14, 0), setOf(posted))
		assertTrue(due.toPost.isEmpty())
	}

	@Test
	fun settingsChange_postsOnlyWhatTheOverdueAlarmWasFor() {
		// The 18:00 alarm is held back by Doze; a setting changes at 18:20.
		val plan = listOf(reminder(ekadashi, 1, 18, london), reminder(ekadashi.plusDays(1), 2, 18, london))
		val now = at(london, 2026, 11, 3, 18, 20)
		val armed = at(london, 2026, 11, 3, 18, 0)
		val due = LunarReminderPlanner.due(plan, now, armed, LocalDateTime.of(2026, 11, 3, 10, 0), emptySet())
		assertEquals(listOf(ekadashi, ekadashi.plusDays(1)), due.toPost.map { it.subject.day })
		// A reminder for a time before the last check is not owed: it came with the change.
		val late = LunarReminderPlanner.due(plan, now, armed, LocalDateTime.of(2026, 11, 3, 18, 10), emptySet())
		assertTrue(late.toPost.isEmpty())
	}

	@Test
	fun severalDueLeads_collapseIntoOnePost() {
		// The phone was off for three days: the 3-day and 1-day reminders are both owed.
		val plan = listOf(reminder(ekadashi, 3, 18, london), reminder(ekadashi, 1, 18, london))
		val now = at(london, 2026, 11, 3, 19, 0)
		val due = LunarReminderPlanner.due(plan, now, now, LocalDateTime.of(2026, 10, 30, 12, 0), emptySet())
		assertEquals(1, due.toPost.size)
		assertEquals(1, due.toPost.single().leadDays)
		assertEquals(plan.map { it.key }.toSet(), due.handled)
	}

	@Test
	fun pastObservance_isNotPosted() {
		val plan = listOf(reminder(ekadashi, 1, 18, london))
		val now = at(london, 2026, 11, 5, 9, 0)
		val due = LunarReminderPlanner.due(plan, now, now, LocalDateTime.of(2026, 11, 1, 12, 0), emptySet())
		assertTrue(due.toPost.isEmpty())
	}

	@Test
	fun prune_keepsObservancesFromYesterdayOn() {
		val old = reminder(ekadashi.minusDays(2), 1, 18, london).key
		val yesterday = reminder(ekadashi.minusDays(1), 1, 18, london).key
		val ahead = reminder(ekadashi.plusDays(3), 1, 18, london).key
		assertEquals(setOf(yesterday, ahead), LunarReminderPlanner.prune(setOf(old, yesterday, ahead, "junk"), ekadashi))
	}
}
