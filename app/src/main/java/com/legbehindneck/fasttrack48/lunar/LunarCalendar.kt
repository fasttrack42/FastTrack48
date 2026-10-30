package com.legbehindneck.fasttrack48.lunar

import com.legbehindneck.fasttrack48.lunar.astro.EkadashiCalculator
import com.legbehindneck.fasttrack48.lunar.astro.Meeus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Observer location in decimal degrees (north/east positive). */
data class GeoPoint(val latitude: Double, val longitude: Double)

/** Which calendar picks the fast days. */
enum class EkadashiTradition {
	/** Drik Panchang and most Hindu calendars. */
	SMARTA,

	/** ISKCON / GCal. */
	VAISHNAVA,

	/** Ananda Marga: the sunrise-to-sunrise day at Varanasi that holds most of the tithi. */
	ANANDA_MARGA,

	/** Ananda Marga as the India chart has it: the Indian calendar date that holds most of the tithi. */
	ANANDA_MARGA_INDIA;

	/** Ananda Marga picks Purnima and Amavasya by the same rule, and every fast runs sunrise to sunrise. */
	val anandaMarga: Boolean get() = this == ANANDA_MARGA || this == ANANDA_MARGA_INDIA
}

/** Full or new moon. */
enum class MoonPhase { FULL, NEW }

/** Which day a full- or new-moon reminder is about. */
enum class MoonDayStyle {
	/** The fast day of the Hindu calendar: Purnima vrat, Darsha Amavasya. */
	VEDIC,

	/** The local date of the exact astronomical full or new moon. */
	ASTRONOMICAL,
}

/** A fast-breaking window; a null [end] means "after [start]". */
data class TimeWindow(val start: ZonedDateTime, val end: ZonedDateTime?)

/** A day a reminder can be about. */
sealed interface LunarDay {
	val day: LocalDate

	/** Ananda Marga: the fast runs from sunrise on [day] to the next sunrise. */
	val sunriseToSunrise: Boolean
}

data class EkadashiDay(
	override val day: LocalDate,
	val krishna: Boolean,
	val tradition: EkadashiTradition,
	/** When to break the fast; null where the sun does not rise (polar latitudes). */
	val parana: TimeWindow?,
	/** Vaishnava only: the Mahadvadashi behind [day], if any. */
	val mahadvadashi: EkadashiCalculator.Mahadvadashi?,
) : LunarDay {
	override val sunriseToSunrise: Boolean get() = tradition.anandaMarga
}

data class MoonDay(
	override val day: LocalDate,
	val phase: MoonPhase,
	val style: MoonDayStyle,
	/** [MoonDayStyle.VEDIC]: when the Purnima / Amavasya tithi begins; otherwise the exact moment. */
	val moment: ZonedDateTime,
	override val sunriseToSunrise: Boolean = false,
	/** Ananda Marga: the next sunrise, when the fast ends; null at polar latitudes. */
	val breakFast: TimeWindow? = null,
) : LunarDay

/**
 * Upcoming Ekadashi, full-moon and new-moon days for a place, Android-free. The observance rules
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
					EkadashiTradition.ANANDA_MARGA, EkadashiTradition.ANANDA_MARGA_INDIA ->
						EkadashiCalculator.computeAnandaMarga(
							newMoon,
							if (krishna) EkadashiCalculator.KRISHNA_START_DEGREES else EkadashiCalculator.SHUKLA_START_DEGREES,
							tradition == EkadashiTradition.ANANDA_MARGA_INDIA,
							location.latitude, location.longitude, zone,
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

	/**
	 * Full- or new-moon days of the given [style] in [from, from + days], sorted. An Ananda
	 * Marga [tradition] picks the Purnima and Amavasya fast days by its own rule.
	 */
	fun moonDays(
		from: LocalDate,
		days: Int,
		location: GeoPoint,
		zone: ZoneId,
		phase: MoonPhase,
		style: MoonDayStyle,
		tradition: EkadashiTradition? = null,
	): List<MoonDay> {
		val until = from.plusDays(days.toLong())
		val out = ArrayList<MoonDay>()
		for (newMoon in newMoons(from, days, zone)) {
			val day = when (style) {
				MoonDayStyle.VEDIC -> if (tradition?.anandaMarga == true) {
					val r = EkadashiCalculator.computeAnandaMarga(
						newMoon,
						if (phase == MoonPhase.FULL) PURNIMA_START_DEGREES else AMAVASYA_START_DEGREES,
						tradition == EkadashiTradition.ANANDA_MARGA_INDIA,
						location.latitude, location.longitude, zone,
					)
					MoonDay(
						r.fastDay, phase, style, r.tithiStart,
						sunriseToSunrise = true,
						breakFast = r.parana?.let { TimeWindow(it.start, it.end) },
					)
				} else {
					val r = when (phase) {
						MoonPhase.FULL -> EkadashiCalculator.computePurnimaVrat(
							newMoon, location.latitude, location.longitude, zone,
						)
						MoonPhase.NEW -> EkadashiCalculator.computeDarshaAmavasya(
							newMoon, location.latitude, location.longitude, zone,
						)
					}
					MoonDay(r.fastDay, phase, style, r.tithiStart)
				}
				MoonDayStyle.ASTRONOMICAL -> {
					// The lunation's full moon, or the new moon that ends it.
					val elongation = if (phase == MoonPhase.FULL) 180.0 else 360.0
					val moment = Instant.ofEpochMilli(
						Meeus.millisFromJulianDay(Meeus.elongationCrossingInLunation(newMoon, elongation))
					).atZone(zone)
					MoonDay(moment.toLocalDate(), phase, style, moment)
				}
			}
			if (day.day in from..until) out.add(day)
		}
		return out.sortedBy { it.day }
	}

	private const val PURNIMA_START_DEGREES = 168.0
	private const val AMAVASYA_START_DEGREES = 348.0

	/**
	 * New moons whose lunations can hold an observance in the window: from the last new
	 * moon before [from] (a lunation's Krishna Ekadashi falls ~25 days after it, its
	 * Amavasya ~29) onwards.
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
