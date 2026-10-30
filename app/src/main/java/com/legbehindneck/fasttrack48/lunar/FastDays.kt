package com.legbehindneck.fasttrack48.lunar

import com.legbehindneck.fasttrack48.lunar.astro.Meeus
import com.legbehindneck.fasttrack48.lunar.astro.Sunrise
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** What a fast day is: its name, and the moon its icon shows. */
enum class FastKind(
	/** Moon-Sun elongation the icon draws, degrees: the Moon on such a day. */
	val elongation: Double,
) {
	SHUKLA_EKADASHI(126.0),
	KRISHNA_EKADASHI(306.0),
	PURNIMA(180.0),
	AMAVASYA(0.0),
	FULL_MOON(180.0),
	NEW_MOON(0.0),
}

/**
 * One fast day, as the Fasting screen and the Fast days sheet show it. [sunrise] is the local
 * sunrise on the day, when the fast starts (null at polar latitudes). [end] is when the day
 * stops being current: the end of the fast where it runs past midnight (the next sunrise, or
 * the end of the fast-breaking window), otherwise the end of the day.
 */
data class FastDay(
	val subject: LunarDay,
	val kind: FastKind,
	val sunrise: ZonedDateTime?,
	val end: ZonedDateTime,
) {
	val day: LocalDate get() = subject.day
}

/** The eight names of the Moon's phase. */
enum class MoonPhaseName {
	NEW, WAXING_CRESCENT, FIRST_QUARTER, WAXING_GIBBOUS, FULL, WANING_GIBBOUS, LAST_QUARTER, WANING_CRESCENT;

	companion object {
		/**
		 * A principal phase within [PRINCIPAL_DEGREES] of its exact moment (about a day either
		 * side), so the day of the full moon reads "Full moon"; the ones between otherwise.
		 */
		fun of(elongation: Double): MoonPhaseName {
			val e = Meeus.fixAngle(elongation)
			fun near(target: Double) = Math.abs(Meeus.wrapTo180(e - target)) < PRINCIPAL_DEGREES
			return when {
				near(0.0) -> NEW
				near(90.0) -> FIRST_QUARTER
				near(180.0) -> FULL
				near(270.0) -> LAST_QUARTER
				e < 90.0 -> WAXING_CRESCENT
				e < 180.0 -> WAXING_GIBBOUS
				e < 270.0 -> WANING_GIBBOUS
				else -> WANING_CRESCENT
			}
		}

		private const val PRINCIPAL_DEGREES = 12.0
	}
}

/** The Moon at an instant, and the full and new moons around it. */
data class MoonState(
	val elongation: Double,
	/** Lit fraction of the disc, 0..1. */
	val illumination: Double,
	val lastFullMoon: ZonedDateTime,
	val nextFullMoon: ZonedDateTime,
	val nextNewMoon: ZonedDateTime,
) {
	val phase: MoonPhaseName get() = MoonPhaseName.of(elongation)
}

/** The fast days the user follows, Android-free. */
object FastDays {

	/** Something to show: Ekadashi with a chosen calendar, or a full- or new-moon day. */
	fun follows(settings: LunarReminderSettings): Boolean =
		(settings.ekadashiEnabled && settings.tradition != null) || settings.fullMoonEnabled || settings.newMoonEnabled

	/** The followed fast days in [from, from + days], in date order. */
	fun list(
		from: LocalDate,
		days: Int,
		settings: LunarReminderSettings,
		location: GeoPoint,
		zone: ZoneId,
	): List<FastDay> {
		val out = ArrayList<LunarDay>()
		val tradition = settings.tradition
		if (settings.ekadashiEnabled && tradition != null) {
			out += LunarCalendar.ekadashis(from, days, location, zone, tradition)
		}
		if (settings.fullMoonEnabled) {
			out += LunarCalendar.moonDays(from, days, location, zone, MoonPhase.FULL, settings.moonDayStyle, tradition)
		}
		if (settings.newMoonEnabled) {
			out += LunarCalendar.moonDays(from, days, location, zone, MoonPhase.NEW, settings.moonDayStyle, tradition)
		}
		return out.map { of(it, location, zone) }
			.sortedWith(compareBy<FastDay> { it.day }.thenBy { it.kind.ordinal })
	}

	/** The fast still under way at [now], or else the next one. */
	fun current(days: List<FastDay>, now: ZonedDateTime): FastDay? =
		days.firstOrNull { now.isBefore(it.end) }

	fun moon(now: ZonedDateTime): MoonState {
		val zone = now.zone
		val jd = Meeus.julianDayFromMillis(now.toInstant().toEpochMilli())
		val newMoon = Meeus.previousNewMoon(jd)
		val nextNewMoon = Meeus.elongationCrossingInLunation(newMoon, 360.0)
		val fullMoon = Meeus.elongationCrossingInLunation(newMoon, 180.0)
		val lastFull: Double
		val nextFull: Double
		if (fullMoon <= jd) {
			lastFull = fullMoon
			nextFull = Meeus.elongationCrossingInLunation(nextNewMoon, 180.0)
		} else {
			lastFull = Meeus.elongationCrossingInLunation(Meeus.previousNewMoon(newMoon - 1.0), 180.0)
			nextFull = fullMoon
		}
		return MoonState(
			elongation = Meeus.elongationDegrees(jd),
			illumination = Meeus.illuminatedFraction(jd),
			lastFullMoon = at(lastFull, zone),
			nextFullMoon = at(nextFull, zone),
			nextNewMoon = at(nextNewMoon, zone),
		)
	}

	private fun of(subject: LunarDay, location: GeoPoint, zone: ZoneId): FastDay {
		val kind = when (subject) {
			is EkadashiDay -> if (subject.krishna) FastKind.KRISHNA_EKADASHI else FastKind.SHUKLA_EKADASHI
			is MoonDay -> when (subject.phase) {
				MoonPhase.FULL -> if (subject.style == MoonDayStyle.VEDIC) FastKind.PURNIMA else FastKind.FULL_MOON
				MoonPhase.NEW -> if (subject.style == MoonDayStyle.VEDIC) FastKind.AMAVASYA else FastKind.NEW_MOON
			}
		}
		val endOfDay = subject.day.plusDays(1).atStartOfDay(zone)
		val fastEnd = when (subject) {
			is EkadashiDay -> subject.parana?.let { it.end ?: it.start }
			is MoonDay -> subject.breakFast?.start
		}
		return FastDay(
			subject = subject,
			kind = kind,
			sunrise = Sunrise.sunrise(subject.day, zone, location.latitude, location.longitude),
			end = fastEnd?.takeIf { it.isAfter(endOfDay) } ?: endOfDay,
		)
	}

	private fun at(jd: Double, zone: ZoneId): ZonedDateTime =
		Instant.ofEpochMilli(Meeus.millisFromJulianDay(jd)).atZone(zone)
}
