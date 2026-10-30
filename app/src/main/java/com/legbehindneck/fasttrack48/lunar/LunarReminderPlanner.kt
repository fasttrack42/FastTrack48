package com.legbehindneck.fasttrack48.lunar

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * What the user wants to be reminded of, and when. Lead days count back from the
 * observance day (0 = the same day); every reminder fires at [hourOfDay] local time.
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
	 * Several leads of one observance collapse into the latest. Its lead is counted again from
	 * today, so a reminder that comes late (an inexact alarm after midnight, a phone that was
	 * off) still says "today" or "tomorrow" truly. Every due reminder is in [Due.handled].
	 */
	fun due(
		plan: List<PlannedLunarReminder>,
		now: ZonedDateTime,
		until: ZonedDateTime,
		floor: LocalDateTime,
		posted: Set<String>,
	): Due {
		val today = now.toLocalDate()
		val due = plan.filter {
			!it.fireAt.isAfter(until) &&
				!it.fireAt.isAfter(now) &&
				!it.subject.day.isBefore(today) &&
				it.key !in posted &&
				it.fireAt.toLocalDateTime().isAfter(floor)
		}
		val toPost = due.groupBy { it.notificationId }.values.map { leads ->
			val latest = leads.maxBy { it.fireAt.toInstant() }
			latest.copy(leadDays = ChronoUnit.DAYS.between(today, latest.subject.day).toInt())
		}.sortedBy { it.subject.day }
		return Due(toPost, due.mapTo(HashSet()) { it.key })
	}

	/** What [due] found: the reminders to post, and the keys of every one it covered. */
	data class Due(val toPost: List<PlannedLunarReminder>, val handled: Set<String>)

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
	 * [hourOfDay] on the local date [leadDays] before [day]. Built from the local date and
	 * time, so it stays on the hour across daylight-saving changes; a time skipped by a
	 * spring-forward gap moves to the first valid instant after it.
	 */
	fun fireTime(day: LocalDate, leadDays: Int, hourOfDay: Int, zone: ZoneId): ZonedDateTime =
		day.minusDays(leadDays.toLong()).atTime(hourOfDay, 0).atZone(zone)
}
