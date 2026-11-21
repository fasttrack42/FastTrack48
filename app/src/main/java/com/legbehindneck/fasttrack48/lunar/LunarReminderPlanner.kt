package com.legbehindneck.fasttrack48.lunar

import com.legbehindneck.fasttrack48.utils.QuietHours
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * What the user wants to be reminded of, and when. Lead days count back from the
 * observance day (0 = the same day); reminders fire at [hourOfDay] local time, except the
 * one on the day itself, which comes in the morning (see [LunarReminderPlanner.fireTime]).
 * Off by default: the feature is opt-in.
 */
data class LunarReminderSettings(
	val ekadashiEnabled: Boolean = false,
	/** Chosen once, when Ekadashi reminders are first turned on; null until then. */
	val tradition: EkadashiTradition? = null,
	val ekadashiLeadDays: Set<Int> = setOf(1),
	val fullMoonEnabled: Boolean = false,
	val fullMoonLeadDays: Set<Int> = setOf(1),
	val newMoonEnabled: Boolean = false,
	val newMoonLeadDays: Set<Int> = setOf(1),
	/** Shared by the full- and new-moon reminders: Purnima and Amavasya, or astronomical. */
	val moonDayStyle: MoonDayStyle = MoonDayStyle.VEDIC,
	val hourOfDay: Int = DEFAULT_HOUR,
) {
	val ekadashiActive: Boolean get() = ekadashiEnabled && tradition != null && ekadashiLeadDays.isNotEmpty()
	val fullMoonActive: Boolean get() = fullMoonEnabled && fullMoonLeadDays.isNotEmpty()
	val newMoonActive: Boolean get() = newMoonEnabled && newMoonLeadDays.isNotEmpty()
	val anyActive: Boolean get() = ekadashiActive || fullMoonActive || newMoonActive

	companion object {
		/** Before dinner on the day before a fast. */
		const val DEFAULT_HOUR = 18
		val LEAD_OPTIONS = listOf(0, 1, 2, 3)
	}
}

/** A concrete reminder: fire at [fireAt] about [subject], [leadDays] before it. */
data class PlannedLunarReminder(
	val subject: LunarDay,
	val leadDays: Int,
	val fireAt: ZonedDateTime,
) {
	/** One reminder's identity across re-plans and time zones: the observance and the lead. */
	val key: String get() = "$notificationId:$leadDays"

	/** One notification per observance, so a closer reminder replaces an earlier one. */
	val notificationId: Int
		get() {
			val base = when (subject) {
				is EkadashiDay -> EKADASHI_ID_BASE
				is MoonDay -> if (subject.phase == MoonPhase.FULL) FULL_MOON_ID_BASE else NEW_MOON_ID_BASE
			}
			return base + subject.day.toEpochDay().toInt()
		}

	private companion object {
		const val EKADASHI_ID_BASE = LunarReminderPlanner.OBSERVANCE_ID_SPAN
		const val FULL_MOON_ID_BASE = 2 * LunarReminderPlanner.OBSERVANCE_ID_SPAN
		const val NEW_MOON_ID_BASE = 3 * LunarReminderPlanner.OBSERVANCE_ID_SPAN
	}
}

/** Turns upcoming observances into concrete reminder firings. Android-free. */
object LunarReminderPlanner {

	const val HORIZON_DAYS = 45

	/** All reminders that fire in [from, from + horizonDays], sorted by fire time. */
	fun plan(
		from: ZonedDateTime,
		settings: LunarReminderSettings,
		location: GeoPoint,
		zone: ZoneId = from.zone,
		horizonDays: Int = HORIZON_DAYS,
	): List<PlannedLunarReminder> {
		val firstDay = from.withZoneSameInstant(zone).toLocalDate()
		val until = from.plusDays(horizonDays.toLong())
		// The latest lead (3 days) can reach an observance just past the horizon.
		val span = horizonDays + LunarReminderSettings.LEAD_OPTIONS.max() + 1
		val out = ArrayList<PlannedLunarReminder>()

		fun add(subject: LunarDay, leads: Set<Int>) {
			for (lead in leads) {
				val fire = fireTime(subject.day, lead, settings.hourOfDay, zone)
				if (!fire.isBefore(from) && !fire.isAfter(until)) {
					out.add(PlannedLunarReminder(subject, lead, fire))
				}
			}
		}

		val tradition = settings.tradition
		if (settings.ekadashiActive && tradition != null) {
			for (day in LunarCalendar.ekadashis(firstDay, span, location, zone, tradition)) {
				add(day, settings.ekadashiLeadDays)
			}
		}
		if (settings.fullMoonActive) {
			for (day in LunarCalendar.moonDays(firstDay, span, location, zone, MoonPhase.FULL, settings.moonDayStyle, tradition)) {
				add(day, settings.fullMoonLeadDays)
			}
		}
		if (settings.newMoonActive) {
			for (day in LunarCalendar.moonDays(firstDay, span, location, zone, MoonPhase.NEW, settings.moonDayStyle, tradition)) {
				add(day, settings.newMoonLeadDays)
			}
		}
		out.sortBy { it.fireAt.toInstant() }
		return out
	}

	/**
	 * The reminders owed at [now]: due by [until], not yet [posted], about an observance that
	 * is still today or ahead, and due after [floor], the local wall-clock time of the previous
	 * check, so a reminder that only came into being with a settings change is not posted for
	 * a time already past. Compared as local times, a time-zone change moves every reminder
	 * with the clock: one due at 18:00 is owed at 18:00 in the new zone, even if that instant
	 * was before the last check; one already shown is in [posted] and stays shown once.
	 *
	 * A [held] reminder came due while reminders were held (see [settle]): it was owed then, so
	 * it stays owed past [until] and [floor], until its observance is over. One for today is
	 * still worth posting: someone whose fast ran through the morning still keeps the rest of
	 * the day (an Ekadashi is often kept by leaving out grains), and the "that morning"
	 * reminder of an overnight faster would otherwise never arrive. A null [until] owes nothing
	 * new, only what is held.
	 *
	 * Several leads of one observance collapse into the latest. Its lead is counted again from
	 * today, so a reminder that comes late (an inexact alarm after midnight, a phone that was
	 * off, a fast that held it) still says "today" or "tomorrow" truly. Every due reminder is in
	 * [Due.handled].
	 */
	fun due(
		plan: List<PlannedLunarReminder>,
		now: ZonedDateTime,
		until: ZonedDateTime?,
		floor: LocalDateTime,
		posted: Set<String>,
		held: Set<String> = emptySet(),
	): Due {
		val today = now.toLocalDate()
		val due = plan.filter {
			!it.fireAt.isAfter(now) &&
				!it.subject.day.isBefore(today) &&
				it.key !in posted &&
				(
					it.key in held ||
						until != null && !it.fireAt.isAfter(until) && it.fireAt.toLocalDateTime().isAfter(floor)
				)
		}
		val toPost = due.groupBy { it.notificationId }.values.map { leads ->
			val latest = leads.maxBy { it.fireAt.toInstant() }
			latest.copy(leadDays = ChronoUnit.DAYS.between(today, latest.subject.day).toInt())
		}.sortedBy { it.subject.day }
		return Due(toPost, due.mapTo(HashSet()) { it.key })
	}

	/** What [due] found: the reminders to post, and the keys of every one it covered. */
	data class Due(val toPost: List<PlannedLunarReminder>, val handled: Set<String>)

	/**
	 * What one run does with [due]. A reminder to fast is noise to someone already fasting, and
	 * an intrusion on someone who has just stopped, so while reminders are on [hold] (a fast,
	 * the quiet after one ([quietUntil]), or the night while something waits) nothing is
	 * posted. What came due is held instead and posted by the first run after the hold, if
	 * its observance is not over ([due] re-offers it, its lead counted from that day). Without
	 * the hold, a reminder hour that falls inside a regular fast would never remind.
	 */
	fun settle(due: Due, hold: Boolean, posted: Set<String>, held: Set<String>, today: LocalDate): Settled =
		if (hold) {
			Settled(emptyList(), prune(posted, today), prune(held + due.handled, today))
		} else {
			// Every held reminder still owed is in due.handled; the rest is moot.
			Settled(due.toPost, prune(posted + due.handled, today), emptySet())
		}

	/** [settle]'s outcome: what to post now, and the posted and held keys to remember. */
	data class Settled(val toPost: List<PlannedLunarReminder>, val posted: Set<String>, val held: Set<String>)

	/** Shorter than this, a "fast" was a slip of the finger: it opens no quiet window. */
	val MIN_FAST: Duration = Duration.ofMinutes(1)

	/** After every fast, nothing for an hour: the meal, and no reminder that reads as a reply to ending. */
	val AFTER_FAST: Duration = Duration.ofMinutes(60)

	/** A fast of a day or more needs a day to refeed: the app's own end card turns to "break gently" here. */
	val REFEED_FROM: Duration = Duration.ofHours(24)
	val REFEED: Duration = Duration.ofHours(24)

	/** Five days or more carries refeeding risk for days after: the app's refeeding card's own threshold. */
	val LONG_REFEED_FROM: Duration = Duration.ofHours(120)
	val LONG_REFEED: Duration = Duration.ofHours(72)

	/**
	 * Until when fast-day reminders stay quiet after a fast of [length] that ended at [end], as
	 * the app learned at [seenAt]: an hour after the app saw it end, or the refeed time after
	 * the end itself for a fast of a day or more, whichever is later, and never in the night
	 * ([QuietHours]). Counting from [seenAt] matters because an end can be backdated ("I stopped
	 * earlier"): counted from the end alone, the hour could already be over at the tap.
	 *
	 * The refeed tiers start at a day, not at every fast: a daily 16:8 fast ends every noon,
	 * so a day-long block after each one would silence an 18:00 reminder for good.
	 * Null for a fast under a minute.
	 */
	fun quietUntil(end: ZonedDateTime, length: Duration, seenAt: ZonedDateTime): ZonedDateTime? {
		if (length < MIN_FAST) return null
		val refeed = when {
			length >= LONG_REFEED_FROM -> LONG_REFEED
			length >= REFEED_FROM -> REFEED
			else -> AFTER_FAST
		}
		val byEnd = end.plus(refeed)
		val bySeen = seenAt.withZoneSameInstant(end.zone).plus(AFTER_FAST)
		return QuietHours.after(if (byEnd.isAfter(bySeen)) byEnd else bySeen)
	}

	/** Posted keys still worth remembering: those of observances from yesterday on. */
	fun prune(posted: Set<String>, today: LocalDate): Set<String> {
		val keep = today.minusDays(1).toEpochDay()
		return posted.filterTo(HashSet()) { key ->
			key.substringBefore(':').toIntOrNull()?.let { it % OBSERVANCE_ID_SPAN >= keep } ?: false
		}
	}

	/** Notification ids are a kind base (multiples of this) plus the observance's epoch day. */
	internal const val OBSERVANCE_ID_SPAN = 1_000_000

	/**
	 * [hourOfDay] on the local date [leadDays] before [day]; on the day itself no later than
	 * 07:00, since a cue has to come before the act and a sunrise fast has begun by evening.
	 * Built from the local date and time, so it stays on the hour across daylight-saving
	 * changes; a time skipped by a spring-forward gap moves to the first valid instant after it.
	 */
	fun fireTime(day: LocalDate, leadDays: Int, hourOfDay: Int, zone: ZoneId): ZonedDateTime {
		val hour = if (leadDays == 0) minOf(hourOfDay, QuietHours.MORNING_HOUR) else hourOfDay
		return day.minusDays(leadDays.toLong()).atTime(hour, 0).atZone(zone)
	}
}
