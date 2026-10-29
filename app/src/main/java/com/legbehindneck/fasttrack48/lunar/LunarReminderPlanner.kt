package com.legbehindneck.fasttrack48.lunar

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

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
	val fullMoonStyle: FullMoonStyle = FullMoonStyle.PURNIMA,
	val fullMoonLeadDays: Set<Int> = setOf(1),
	val hourOfDay: Int = DEFAULT_HOUR,
) {
	val ekadashiActive: Boolean get() = ekadashiEnabled && tradition != null && ekadashiLeadDays.isNotEmpty()
	val fullMoonActive: Boolean get() = fullMoonEnabled && fullMoonLeadDays.isNotEmpty()
	val anyActive: Boolean get() = ekadashiActive || fullMoonActive

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
	/** One notification per observance, so a closer reminder replaces an earlier one. */
	val notificationId: Int
		get() = (if (subject is EkadashiDay) EKADASHI_ID_BASE else FULL_MOON_ID_BASE) +
			subject.day.toEpochDay().toInt()

	private companion object {
		const val EKADASHI_ID_BASE = 1_000_000
		const val FULL_MOON_ID_BASE = 2_000_000
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
			for (day in LunarCalendar.fullMoons(firstDay, span, location, zone, settings.fullMoonStyle)) {
				add(day, settings.fullMoonLeadDays)
			}
		}
		out.sortBy { it.fireAt.toInstant() }
		return out
	}

	/**
	 * [hourOfDay] on the local date [leadDays] before [day]. Built from the local date and
	 * time, so it stays on the hour across daylight-saving changes; a time skipped by a
	 * spring-forward gap moves to the first valid instant after it.
	 */
	fun fireTime(day: LocalDate, leadDays: Int, hourOfDay: Int, zone: ZoneId): ZonedDateTime =
		day.minusDays(leadDays.toLong()).atTime(hourOfDay, 0).atZone(zone)
}
