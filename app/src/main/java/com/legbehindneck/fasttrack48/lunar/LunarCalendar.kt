package com.legbehindneck.fasttrack48.lunar

import com.legbehindneck.fasttrack48.lunar.astro.EkadashiCalculator
import com.legbehindneck.fasttrack48.lunar.astro.Meeus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Observer location in decimal degrees (north/east positive). */
data class GeoPoint(val latitude: Double, val longitude: Double)

/** Which calendar picks the Ekadashi fast day. */
enum class EkadashiTradition {
	/** Drik Panchang and most Hindu calendars. */
	SMARTA,

	/** ISKCON / GCal. */
	VAISHNAVA,
}

/** What a full-moon reminder is about. */
enum class FullMoonStyle {
	/** The Purnima vrat (fast) day of the Hindu calendar. */
	PURNIMA,

	/** The local date of the exact astronomical full moon. */
	ASTRONOMICAL,
}

/** A fast-breaking window; a null [end] means "after [start]". */
data class TimeWindow(val start: ZonedDateTime, val end: ZonedDateTime?)

/** A day a reminder can be about. */
sealed interface LunarDay {
	val day: LocalDate
}

data class EkadashiDay(
	override val day: LocalDate,
	val krishna: Boolean,
	val tradition: EkadashiTradition,
	/** When to break the fast; null where the sun does not rise (polar latitudes). */
	val parana: TimeWindow?,
	/** Vaishnava only: the Mahadvadashi behind [day], if any. */
	val mahadvadashi: EkadashiCalculator.Mahadvadashi?,
) : LunarDay

data class FullMoonDay(
	override val day: LocalDate,
	val style: FullMoonStyle,
	/** [FullMoonStyle.PURNIMA]: when the Purnima tithi begins; otherwise the exact full moon. */
	val moment: ZonedDateTime,
) : LunarDay

/**
 * Upcoming Ekadashi and full-moon days for a place, Android-free. The observance rules
 * live in [EkadashiCalculator]; this only walks the lunations of a date window.
 */
object LunarCalendar {

	/** Ekadashi fast days in [from, from + days], sorted. */
	fun ekadashis(
		from: LocalDate,
		days: Int,
		location: GeoPoint,
		zone: ZoneId,
		tradition: EkadashiTradition,
	): List<EkadashiDay> {
		val until = from.plusDays(days.toLong())
		val out = ArrayList<EkadashiDay>()
		for (newMoon in newMoons(from, days, zone)) {
			for (krishna in booleanArrayOf(false, true)) {
				val r = when (tradition) {
					EkadashiTradition.SMARTA -> EkadashiCalculator.compute(
						newMoon, krishna, location.latitude, location.longitude, zone,
					)
					EkadashiTradition.VAISHNAVA -> EkadashiCalculator.computeVaishnava(
						newMoon, krishna, location.latitude, location.longitude, zone,
					)
				}
				if (r.fastDay in from..until) {
					out.add(
						EkadashiDay(
							day = r.fastDay,
							krishna = krishna,
							tradition = tradition,
							parana = r.parana?.let { TimeWindow(it.start, it.end) },
							mahadvadashi = r.mahadvadashi,
						)
					)
				}
			}
		}
		return out.sortedBy { it.day }
	}

	/** Full-moon days of the given [style] in [from, from + days], sorted. */
	fun fullMoons(
		from: LocalDate,
		days: Int,
		location: GeoPoint,
		zone: ZoneId,
		style: FullMoonStyle,
	): List<FullMoonDay> {
		val until = from.plusDays(days.toLong())
		val out = ArrayList<FullMoonDay>()
		for (newMoon in newMoons(from, days, zone)) {
			val day = when (style) {
				FullMoonStyle.PURNIMA -> {
					val r = EkadashiCalculator.computePurnimaVrat(
						newMoon, location.latitude, location.longitude, zone,
					)
					FullMoonDay(r.fastDay, style, r.tithiStart)
				}
				FullMoonStyle.ASTRONOMICAL -> {
					val full = Instant.ofEpochMilli(
						Meeus.millisFromJulianDay(Meeus.elongationCrossingInLunation(newMoon, 180.0))
					).atZone(zone)
					FullMoonDay(full.toLocalDate(), style, full)
				}
			}
			if (day.day in from..until) out.add(day)
		}
		return out.sortedBy { it.day }
	}

	/**
	 * New moons whose lunations can hold an observance in the window: from the last new
	 * moon before [from] (a lunation's Krishna Ekadashi falls ~25 days after it) onwards.
	 */
	private fun newMoons(from: LocalDate, days: Int, zone: ZoneId): List<Double> {
		val startMillis = from.atStartOfDay(zone).minusDays(3).toInstant().toEpochMilli()
		var newMoon = Meeus.previousNewMoon(Meeus.julianDayFromMillis(startMillis))
		return List(days / 29 + 3) {
			val current = newMoon
			newMoon = Meeus.elongationCrossingInLunation(newMoon, 360.0)
			current
		}
	}
}
