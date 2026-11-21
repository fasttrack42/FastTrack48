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
	fun duringAFast_nothingIsPosted_andWhatCameDueIsHeld() {
		val plan = listOf(reminder(ekadashi, 1, 18, london))
		val now = at(london, 2026, 11, 3, 18, 0)
		val due = LunarReminderPlanner.due(plan, now, now, LocalDateTime.of(2026, 11, 3, 12, 0), emptySet())
		val settled = LunarReminderPlanner.settle(due, hold = true, posted = emptySet(), held = emptySet(), today = now.toLocalDate())
		assertTrue(settled.toPost.isEmpty())
		assertTrue(settled.posted.isEmpty())
		assertEquals(setOf(plan.single().key), settled.held)
	}

	@Test
	fun heldReminder_isPostedAfterTheHold_pastTheFloor_countedFromToday() {
		// "2 days before", held at 18:00 on the 2nd; runs during the hold moved the floor on.
		// Released the next morning: still owed, and now says "tomorrow".
		val plan = listOf(reminder(ekadashi, 2, 18, london))
		val held = setOf(plan.single().key)
		val now = at(london, 2026, 11, 3, 9, 0)
		val due = LunarReminderPlanner.due(plan, now, now, LocalDateTime.of(2026, 11, 3, 8, 0), emptySet(), held)
		val settled = LunarReminderPlanner.settle(due, hold = false, posted = emptySet(), held = held, today = now.toLocalDate())
		assertEquals(1, settled.toPost.single().leadDays)
		assertEquals(held, settled.posted)
		assertTrue(settled.held.isEmpty())
	}

	@Test
	fun heldThatMorningReminder_ofAnOvernightFaster_postsWhenTheFastEnds() {
		// 20:00 → 12:00 faster, "that morning" at 07:00 falls inside the fast and is held. Quiet
		// ends 13:00: the day is not over, so it still arrives, as "today".
		val plan = listOf(reminder(ekadashi, 0, 18, london))
		assertEquals(7, plan.single().fireAt.hour)
		val held = setOf(plan.single().key)
		val now = at(london, 2026, 11, 4, 13, 0)
		val due = LunarReminderPlanner.due(plan, now, now, LocalDateTime.of(2026, 11, 4, 12, 0), emptySet(), held)
		val settled = LunarReminderPlanner.settle(due, hold = false, posted = emptySet(), held = held, today = now.toLocalDate())
		assertEquals(0, settled.toPost.single().leadDays)
		assertEquals(held, settled.posted)
		assertTrue(settled.held.isEmpty())
	}

	@Test
	fun heldReminder_isOwedOnASettingsChangeWithNoOverdueAlarm() {
		val plan = listOf(reminder(ekadashi, 1, 18, london))
		val held = setOf(plan.single().key)
		val now = at(london, 2026, 11, 3, 21, 0)
		val due = LunarReminderPlanner.due(plan, now, null, LocalDateTime.of(2026, 11, 3, 20, 0), emptySet(), held)
		assertEquals(held, due.handled)
		// Without anything held, a settings change with no overdue alarm owes nothing.
		assertTrue(LunarReminderPlanner.due(plan, now, null, LocalDateTime.of(2026, 11, 3, 12, 0), emptySet()).handled.isEmpty())
	}

	@Test
	fun heldReminder_isDroppedWhenTheFastOutlastsItsObservance() {
		val plan = listOf(reminder(ekadashi, 1, 18, london))
		val held = setOf(plan.single().key)
		val now = at(london, 2026, 11, 5, 7, 0)
		val due = LunarReminderPlanner.due(plan, now, now, LocalDateTime.of(2026, 11, 5, 6, 0), emptySet(), held)
		val settled = LunarReminderPlanner.settle(due, hold = false, posted = emptySet(), held = held, today = now.toLocalDate())
		assertTrue(settled.toPost.isEmpty())
		assertTrue(settled.held.isEmpty())
	}

	@Test
	fun heldReminder_movedLaterBySettings_isNotPostedEarly() {
		// Held for 18:00, then the hour moved to 21:00; the fast ends at 19:00. The reminder
		// is not due yet: it leaves the held set and fires at 21:00 on its own.
		val plan = listOf(reminder(ekadashi, 1, 21, london))
		val held = setOf(plan.single().key)
		val now = at(london, 2026, 11, 3, 19, 0)
		val due = LunarReminderPlanner.due(plan, now, now, LocalDateTime.of(2026, 11, 3, 18, 30), emptySet(), held)
		val settled = LunarReminderPlanner.settle(due, hold = false, posted = emptySet(), held = held, today = now.toLocalDate())
		assertTrue(settled.toPost.isEmpty())
		assertTrue(settled.held.isEmpty())
		val at21 = at(london, 2026, 11, 3, 21, 0)
		assertEquals(1, LunarReminderPlanner.due(plan, at21, at21, now.toLocalDateTime(), settled.posted, settled.held).toPost.size)
	}

	@Test
	fun prune_keepsObservancesFromYesterdayOn() {
		val old = reminder(ekadashi.minusDays(2), 1, 18, london).key
		val yesterday = reminder(ekadashi.minusDays(1), 1, 18, london).key
		val ahead = reminder(ekadashi.plusDays(3), 1, 18, london).key
		assertEquals(setOf(yesterday, ahead), LunarReminderPlanner.prune(setOf(old, yesterday, ahead, "junk"), ekadashi))
	}
}
