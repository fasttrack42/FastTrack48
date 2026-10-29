package com.legbehindneck.fasttrack48.lunar.astro

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Ekadashi fast days and Parana (fast-breaking) windows, plus the Vedic
 * Purnima/Amavasya civil days and their fast days (Purnima vrat, Darsha Amavasya).
 *
 * A tithi is the period during which the Moon-Sun elongation traverses a
 * 12-degree slice. Ekadashi is the 11th tithi of each fortnight:
 *  - Shukla (waxing) Ekadashi: elongation 120 - 132 degrees
 *  - Krishna (waning) Ekadashi: elongation 300 - 312 degrees
 * Dvadashi follows (132-144 / 312-324), then Trayodashi (144-156 / 324-336).
 * The true tithi boundaries come from root-finding on the [Meeus] elongation;
 * the observance rules below then pick civil days from the local sunrises.
 *
 * Smarta rules ([compute]) - as published by Drik Panchang:
 *  - Ekadashi at exactly one sunrise (day D): fast on D, except when Dvadashi
 *    touches no sunrise (Trisparsha on D) and Ekadashi began on D-1: then fast
 *    on D-1 so that the Parana falls within Dvadashi; D is the Gauna day.
 *  - Kshaya (Ekadashi touches no sunrise): fast on the day the tithi begins;
 *    the next day is the Gauna day.
 *  - Vriddhi (Ekadashi at two sunrises): fast on the second day.
 *  - Parana on the next day: after Hari Vasara (the first quarter of Dvadashi)
 *    and within Pratahkal (the first fifth of daylight), closing early if
 *    Dvadashi ends; if Hari Vasara outlasts Pratahkal, after Madhyahna (the
 *    third fifth) until the end of Aparahna (the fourth fifth); if Dvadashi
 *    ended before sunrise, from sunrise to the end of Pratahkal.
 *
 * Vaishnava rules ([computeVaishnava]) - GCal (docs/lunar/GCalEventsDocumentation.pdf)
 * as applied by Drik Panchang's ISKCON lists:
 *  - Arunodaya (96 min before sunrise) must be free of Dashami, else the fast
 *    moves to the next day; kshaya -> the next day; vriddhi -> the second day
 *    (Unmilani).
 *  - A pure (Suddha) Ekadashi yields to Dvadashi when Dvadashi spans two
 *    sunrises (Vyanjuli) or when the fortnight's Purnima/Amavasya spans two
 *    sunrises (Paksavardhini). The nakshatra Mahadvadashis do not move the
 *    fast in the published ISKCON lists, so they are not modelled.
 *  - Parana: from sunrise (after Hari Vasara) until 1/3 of daylight, closing
 *    early at the end of the governing tithi; "after X" when no window is left.
 *
 * Validation (scratch harness, 2026-10): against Drik Panchang for New Delhi
 * 2026-2027, New York 2026 and London 2029 - Smarta 98/98 fast days and 7/7
 * Gauna days, ISKCON 97/98 fast days with every Parana within 2 minutes,
 * Purnima vrat and Purnima tithi days 38/38, Darsha Amavasya and Amavasya days 49/49
 * (New Delhi 2026-2027, New York 2026, London 2026). The single miss is a Purnima end
 * within one minute of sunrise: this theory's tithi instants agree with JPL
 * Horizons to -25..+62 s (Sun series), while Drik's run a near-constant
 * 36-66 s earlier than JPL, so only such knife-edge cases can differ.
 */
object EkadashiCalculator {

    /** Elongation at which the Shukla Ekadashi tithi begins, degrees. */
    const val SHUKLA_START_DEGREES: Double = 120.0

    /** Elongation at which the Krishna Ekadashi tithi begins, degrees. */
    const val KRISHNA_START_DEGREES: Double = 300.0

    /** Width of one tithi in elongation, degrees. */
    const val TITHI_WIDTH_DEGREES: Double = 12.0

    /** The Mahadvadashi that defines (or names) a Vaishnava fast day. */
    enum class Mahadvadashi { UNMILANI, VYANJULI, TRISPRSA, PAKSAVARDHINI }

    /** Fast-breaking window. A null [end] means "break the fast after [start]". */
    class Parana internal constructor(
        @JvmField val start: ZonedDateTime,
        @JvmField val end: ZonedDateTime?,
    )

    /** Immutable result: the tithi interval and the observed day. */
    class Result internal constructor(
        /** Instant the tithi begins. */
        @JvmField val tithiStart: ZonedDateTime,
        /** Instant the tithi ends. */
        @JvmField val tithiEnd: ZonedDateTime,
        /** The civil day on which the fast (or the observance) falls. */
        @JvmField val fastDay: LocalDate,
        /**
         * The sunrise of [fastDay], or the tithi midpoint when no sunrise
         * exists at the location (polar latitudes).
         */
        @JvmField val displayInstant: ZonedDateTime,
        /** Ekadashi only: when to break the fast (null at polar latitudes). */
        @JvmField val parana: Parana? = null,
        /** Smarta Ekadashi only: the alternate (Gauna) fast day, if any. */
        @JvmField val gaunaDay: LocalDate? = null,
        /** The Parana that follows a fast on [gaunaDay]. */
        @JvmField val gaunaParana: Parana? = null,
        /** Vaishnava Ekadashi only: the Mahadvadashi behind [fastDay], if any. */
        @JvmField val mahadvadashi: Mahadvadashi? = null,
    )

    /**
     * Smarta Ekadashi of the lunation beginning at the given new moon.
     *
     * @param newMoonJdUt  instant of the lunation's new moon, Julian Day (UT)
     * @param krishna      false for Shukla (waxing), true for Krishna (waning)
     * @param latitudeDeg  observer latitude, degrees, north positive
     * @param longitudeDeg observer longitude, degrees, east positive
     * @param zone         time zone defining the civil day
     * @return never null; falls back to the tithi midpoint (no Parana) when a
     *         needed sunrise does not exist (polar day/night)
     */
    @JvmStatic
    fun compute(
        newMoonJdUt: Double,
        krishna: Boolean,
        latitudeDeg: Double,
        longitudeDeg: Double,
        zone: ZoneId,
    ): Result {
        val t = tithis(newMoonJdUt, krishna, zone)
        val ek = sunriseDays(t.ekStart, t.ekEnd, latitudeDeg, longitudeDeg, zone)
            ?: return midpointFallback(t.ekStart, t.ekEnd, zone)
        val begin = t.ekStart.toLocalDate()
        val fastDay: LocalDate
        val gauna: LocalDate?
        when {
            ek.isEmpty() -> {
                fastDay = begin
                gauna = begin.plusDays(1)
            }
            ek.size >= 2 -> {
                fastDay = ek[1]
                gauna = null
            }
            else -> {
                val d = ek[0]
                val dv = sunriseDays(t.ekEnd, t.dvEnd, latitudeDeg, longitudeDeg, zone)
                    ?: return midpointFallback(t.ekStart, t.ekEnd, zone)
                if (dv.isEmpty() && begin.isBefore(d)) {
                    fastDay = d.minusDays(1)
                    gauna = d
                } else {
                    fastDay = d
                    gauna = null
                }
            }
        }
        val sunrise = Sunrise.sunrise(fastDay, zone, latitudeDeg, longitudeDeg)
            ?: return midpointFallback(t.ekStart, t.ekEnd, zone)
        return Result(
            t.ekStart, t.ekEnd, fastDay, sunrise,
            parana = smartaParana(t, fastDay, latitudeDeg, longitudeDeg, zone),
            gaunaDay = gauna,
            gaunaParana = gauna?.let { smartaParana(t, it, latitudeDeg, longitudeDeg, zone) },
        )
    }

    /**
     * Vaishnava (GCal / ISKCON) Ekadashi of the lunation beginning at the given
     * new moon; same parameters and fallback as [compute].
     */
    @JvmStatic
    fun computeVaishnava(
        newMoonJdUt: Double,
        krishna: Boolean,
        latitudeDeg: Double,
        longitudeDeg: Double,
        zone: ZoneId,
    ): Result {
        val t = tithis(newMoonJdUt, krishna, zone)
        val ek = sunriseDays(t.ekStart, t.ekEnd, latitudeDeg, longitudeDeg, zone)
            ?: return midpointFallback(t.ekStart, t.ekEnd, zone)
        val dv = sunriseDays(t.ekEnd, t.dvEnd, latitudeDeg, longitudeDeg, zone)
            ?: return midpointFallback(t.ekStart, t.ekEnd, zone)
        var label: Mahadvadashi? = null
        val fastDay: LocalDate = when {
            // Kshaya: the first sunrise after the tithi already belongs to Dvadashi.
            ek.isEmpty() -> t.ekStart.toLocalDate().plusDays(1)
            ek.size >= 2 -> {
                label = Mahadvadashi.UNMILANI
                ek[1]
            }
            else -> {
                val d = ek[0]
                val sunrise = Sunrise.sunrise(d, zone, latitudeDeg, longitudeDeg)
                    ?: return midpointFallback(t.ekStart, t.ekEnd, zone)
                if (sunrise.minusMinutes(ARUNODAYA_MINUTES).isBefore(t.ekStart)) {
                    // Dashami current at arunodaya -> viddha; fast the next day.
                    d.plusDays(1)
                } else {
                    val fifteenth = sunriseDays(
                        t.fifteenthStart, t.fifteenthEnd, latitudeDeg, longitudeDeg, zone,
                    ) ?: return midpointFallback(t.ekStart, t.ekEnd, zone)
                    when {
                        dv.size >= 2 -> {
                            label = Mahadvadashi.VYANJULI
                            dv[0]
                        }
                        dv.size == 1 && fifteenth.size >= 2 -> {
                            label = Mahadvadashi.PAKSAVARDHINI
                            dv[0]
                        }
                        dv.isEmpty() -> {
                            label = Mahadvadashi.TRISPRSA
                            d
                        }
                        else -> d
                    }
                }
            }
        }
        val sunrise = Sunrise.sunrise(fastDay, zone, latitudeDeg, longitudeDeg)
            ?: return midpointFallback(t.ekStart, t.ekEnd, zone)
        return Result(
            t.ekStart, t.ekEnd, fastDay, sunrise,
            parana = gcalParana(t, fastDay, sunrise, latitudeDeg, longitudeDeg, zone),
            mahadvadashi = label,
        )
    }

    /**
     * Purnima (full-moon) civil day: the day whose sunrise falls within the
     * Gaura-15 tithi (elongation 168-180 deg), which ENDS at the astronomical
     * full moon. The Purnima civil day may therefore differ from the
     * astronomical full-moon day.
     */
    @JvmStatic
    fun computePurnima(
        newMoonJdUt: Double,
        latitudeDeg: Double,
        longitudeDeg: Double,
        zone: ZoneId,
    ): Result = computeTithiDay(newMoonJdUt, 168.0, 180.0, latitudeDeg, longitudeDeg, zone)

    /**
     * Purnima vrat (Upavasa) day, Drik Panchang rule: the fast moves to the
     * previous day (Chaturdashi) when Purnima has begun by the end of that day's
     * Madhyahna (the third fifth of daylight); otherwise it is the Purnima day
     * of [computePurnima]. The tithi fields are those of the Purnima tithi.
     */
    @JvmStatic
    fun computePurnimaVrat(
        newMoonJdUt: Double,
        latitudeDeg: Double,
        longitudeDeg: Double,
        zone: ZoneId,
    ): Result {
        val label = computePurnima(newMoonJdUt, latitudeDeg, longitudeDeg, zone)
        val d0 = label.tithiStart.toLocalDate()
        if (d0 == label.fastDay) return label
        val rise = Sunrise.sunrise(d0, zone, latitudeDeg, longitudeDeg) ?: return label
        val set = Sunrise.sunset(d0, zone, latitudeDeg, longitudeDeg) ?: return label
        val riseMs = millis(rise)
        val start = millis(label.tithiStart)
        val madhyahnaEnd = riseMs + (millis(set) - riseMs) * 3 / 5
        return if (start > riseMs && start < madhyahnaEnd) {
            Result(label.tithiStart, label.tithiEnd, d0, rise)
        } else {
            label
        }
    }

    /**
     * Darsha Amavasya - the Amavasya fast and Pitru (ancestor) day - Drik Panchang rule:
     * the civil day whose Aparahna (the fourth fifth of daylight) the Amavasya tithi
     * covers longest, the earlier day on a tie; when it covers no Aparahna at all, the
     * Amavasya day of [computeAmavasya]. It falls on the previous day (Chaturdashi) when
     * Amavasya begins during the day and ends the next morning. The tithi fields are
     * those of the Amavasya tithi.
     */
    @JvmStatic
    fun computeDarshaAmavasya(
        newMoonJdUt: Double,
        latitudeDeg: Double,
        longitudeDeg: Double,
        zone: ZoneId,
    ): Result {
        val label = computeAmavasya(newMoonJdUt, latitudeDeg, longitudeDeg, zone)
        val start = millis(label.tithiStart)
        val end = millis(label.tithiEnd)
        var bestDay: LocalDate? = null
        var bestSunrise: ZonedDateTime? = null
        var bestCover = 0L
        var day = label.tithiStart.toLocalDate()
        while (!day.isAfter(label.tithiEnd.toLocalDate())) {
            val rise = Sunrise.sunrise(day, zone, latitudeDeg, longitudeDeg) ?: return label
            val set = Sunrise.sunset(day, zone, latitudeDeg, longitudeDeg) ?: return label
            val r = millis(rise)
            val daylight = millis(set) - r
            val cover = minOf(r + daylight * 4 / 5, end) - maxOf(r + daylight * 3 / 5, start)
            if (cover > bestCover) {
                bestCover = cover
                bestDay = day
                bestSunrise = rise
            }
            day = day.plusDays(1)
        }
        return if (bestDay == null || bestSunrise == null || bestDay == label.fastDay) {
            label
        } else {
            Result(label.tithiStart, label.tithiEnd, bestDay, bestSunrise)
        }
    }

    /**
     * Amavasya (new-moon) civil day: the day whose sunrise falls within the
     * Krsna-15 tithi (elongation 348-360 deg), which ENDS at the astronomical
     * new moon (the next conjunction).
     */
    @JvmStatic
    fun computeAmavasya(
        newMoonJdUt: Double,
        latitudeDeg: Double,
        longitudeDeg: Double,
        zone: ZoneId,
    ): Result = computeTithiDay(newMoonJdUt, 348.0, 360.0, latitudeDeg, longitudeDeg, zone)

    private fun computeTithiDay(
        newMoonJdUt: Double,
        startDegrees: Double,
        endDegrees: Double,
        latitudeDeg: Double,
        longitudeDeg: Double,
        zone: ZoneId,
    ): Result {
        val startJd = Meeus.elongationCrossingInLunation(newMoonJdUt, startDegrees)
        val endJd = Meeus.elongationCrossingInLunation(newMoonJdUt, endDegrees)
        val tithiStart = at(startJd, zone)
        val tithiEnd = at(endJd, zone)
        return tithiDayForObservance(tithiStart, tithiEnd, latitudeDeg, longitudeDeg, zone)
    }

    // ---- tithi boundaries ------------------------------------------------------

    /** Ekadashi, Dvadashi and Trayodashi boundaries plus the fortnight's 15th tithi. */
    private class Tithis(
        val ekStart: ZonedDateTime,
        val ekEnd: ZonedDateTime,
        val dvEnd: ZonedDateTime,
        val trEnd: ZonedDateTime,
        val fifteenthStart: ZonedDateTime,
        val fifteenthEnd: ZonedDateTime,
    )

    private fun tithis(newMoonJdUt: Double, krishna: Boolean, zone: ZoneId): Tithis {
        val start = if (krishna) KRISHNA_START_DEGREES else SHUKLA_START_DEGREES
        val step = TITHI_WIDTH_DEGREES / Meeus.MEAN_ELONGATION_RATE
        val ekStartJd = Meeus.elongationCrossingInLunation(newMoonJdUt, start)
        val ekEndJd = Meeus.searchElongationCrossing(start + TITHI_WIDTH_DEGREES, ekStartJd + step)
        val dvEndJd = Meeus.searchElongationCrossing(start + 2 * TITHI_WIDTH_DEGREES, ekEndJd + step)
        val trEndJd = Meeus.searchElongationCrossing(start + 3 * TITHI_WIDTH_DEGREES, dvEndJd + step)
        return Tithis(
            ekStart = at(ekStartJd, zone),
            ekEnd = at(ekEndJd, zone),
            dvEnd = at(dvEndJd, zone),
            trEnd = at(trEndJd, zone),
            // Purnima (168-180) or Amavasya (348-360), solved as computeTithiDay does.
            fifteenthStart = at(Meeus.elongationCrossingInLunation(newMoonJdUt, start + 4 * TITHI_WIDTH_DEGREES), zone),
            fifteenthEnd = at(Meeus.elongationCrossingInLunation(newMoonJdUt, start + 5 * TITHI_WIDTH_DEGREES), zone),
        )
    }

    /**
     * Civil days whose sunrise falls within [from, until), in order; null when a
     * day in the span has no sunrise (polar day/night), so callers can fall back.
     */
    private fun sunriseDays(
        from: ZonedDateTime,
        until: ZonedDateTime,
        latitudeDeg: Double,
        longitudeDeg: Double,
        zone: ZoneId,
    ): List<LocalDate>? {
        val out = ArrayList<LocalDate>(3)
        var day = from.toLocalDate()
        val last = until.toLocalDate()
        while (!day.isAfter(last)) {
            val sunrise = Sunrise.sunrise(day, zone, latitudeDeg, longitudeDeg) ?: return null
            if (!sunrise.isBefore(from) && sunrise.isBefore(until)) out.add(day)
            day = day.plusDays(1)
        }
        return out
    }

    // ---- Parana ----------------------------------------------------------------

    /** Hari Vasara ends after the first quarter of Dvadashi. */
    private fun hariVasaraEnd(t: Tithis): Long =
        millis(t.ekEnd) + (millis(t.dvEnd) - millis(t.ekEnd)) / 4

    /** Drik Panchang (Smarta) Parana for a fast on [fastDay]. */
    private fun smartaParana(
        t: Tithis,
        fastDay: LocalDate,
        latitudeDeg: Double,
        longitudeDeg: Double,
        zone: ZoneId,
    ): Parana? {
        val day = fastDay.plusDays(1)
        val rise = Sunrise.sunrise(day, zone, latitudeDeg, longitudeDeg) ?: return null
        val set = Sunrise.sunset(day, zone, latitudeDeg, longitudeDeg) ?: return null
        val r = millis(rise)
        val daylight = millis(set) - r
        val pratahkalEnd = r + daylight / 5
        val madhyahnaEnd = r + daylight * 3 / 5
        val aparahnaEnd = r + daylight * 4 / 5
        val dvEnd = millis(t.dvEnd)
        val hv = hariVasaraEnd(t)
        val begin: Long
        val end: Long
        when {
            dvEnd <= r -> {
                begin = r
                end = pratahkalEnd
            }
            hv <= pratahkalEnd -> {
                begin = maxOf(r, hv)
                end = minOf(dvEnd, pratahkalEnd)
            }
            else -> {
                begin = maxOf(madhyahnaEnd, hv)
                end = minOf(dvEnd, aparahnaEnd)
            }
        }
        return parana(begin, end, zone)
    }

    /** GCal (Vaishnava) Parana for a fast on [fastDay] with sunrise [fastSunrise]. */
    private fun gcalParana(
        t: Tithis,
        fastDay: LocalDate,
        fastSunrise: ZonedDateTime,
        latitudeDeg: Double,
        longitudeDeg: Double,
        zone: ZoneId,
    ): Parana? {
        val day = fastDay.plusDays(1)
        val rise = Sunrise.sunrise(day, zone, latitudeDeg, longitudeDeg) ?: return null
        val set = Sunrise.sunset(day, zone, latitudeDeg, longitudeDeg) ?: return null
        val r = millis(rise)
        val third = r + (millis(set) - r) / 3
        val ekEnd = millis(t.ekEnd)
        val dvEnd = millis(t.dvEnd)
        val fs = millis(fastSunrise)
        val fastOnDvadashi = fs >= ekEnd && fs < dvEnd
        val begin: Long
        val limit: Long
        if (!fastOnDvadashi) {
            if (r >= ekEnd && r < dvEnd) {
                // Normal / Unmilani: Dvadashi at the Parana sunrise.
                begin = maxOf(r, hariVasaraEnd(t))
                limit = dvEnd
            } else {
                // Trisprsa: Dvadashi already over; Trayodashi at the Parana sunrise.
                begin = r
                limit = Long.MAX_VALUE
            }
        } else {
            // Fast kept on Dvadashi (viddha, kshaya, Vyanjuli, Paksavardhini).
            begin = r
            limit = if (r < dvEnd) dvEnd else millis(t.trEnd)
        }
        return parana(begin, minOf(limit, third), zone)
    }

    private fun parana(begin: Long, end: Long, zone: ZoneId): Parana =
        Parana(at(begin, zone), if (end > begin) at(end, zone) else null)

    // ---- selection rules (Purnima / Amavasya) -----------------------------------

    /**
     * Non-fast tithi-label rule (Purnima / Amavasya): the day whose sunrise the
     * tithi prevails at; but for a KSHAYA tithi (it begins after one sunrise and
     * ends before the next, touching no sunrise) it is assigned to the day it
     * BEGINS.
     */
    private fun tithiDayForObservance(
        tithiStart: ZonedDateTime,
        tithiEnd: ZonedDateTime,
        latitudeDeg: Double,
        longitudeDeg: Double,
        zone: ZoneId,
    ): Result {
        val d0 = tithiStart.toLocalDate()
        val s0 = Sunrise.sunrise(d0, zone, latitudeDeg, longitudeDeg)
            ?: return midpointFallback(tithiStart, tithiEnd, zone)
        // Tithi already current at this sunrise -> this day.
        if (!s0.isBefore(tithiStart)) {
            return Result(tithiStart, tithiEnd, d0, s0)
        }
        // Tithi begins after this sunrise. If it still runs at the next sunrise,
        // the tithi prevails there (normal); otherwise it is kshaya.
        val d1 = d0.plusDays(1)
        val s1 = Sunrise.sunrise(d1, zone, latitudeDeg, longitudeDeg)
        if (s1 != null && s1.isBefore(tithiEnd)) {
            return Result(tithiStart, tithiEnd, d1, s1)
        }
        // Kshaya: no sunrise inside the tithi -> the day it begins.
        return Result(tithiStart, tithiEnd, d0, s0)
    }

    private fun midpointFallback(
        tithiStart: ZonedDateTime,
        tithiEnd: ZonedDateTime,
        zone: ZoneId,
    ): Result {
        val midpointMillis = (millis(tithiStart) + millis(tithiEnd)) / 2
        val midpoint = Instant.ofEpochMilli(midpointMillis).atZone(zone)
        return Result(tithiStart, tithiEnd, midpoint.toLocalDate(), midpoint)
    }

    private fun at(jdUt: Double, zone: ZoneId): ZonedDateTime =
        Instant.ofEpochMilli(Meeus.millisFromJulianDay(jdUt)).atZone(zone)

    private fun at(millis: Long, zone: ZoneId): ZonedDateTime =
        Instant.ofEpochMilli(millis).atZone(zone)

    private fun millis(z: ZonedDateTime): Long = z.toInstant().toEpochMilli()

    /**
     * Arunodaya (Gaudiya dawn) offset before sunrise = 4 ghatikas = 96 minutes,
     * the standard Gaudiya value used by GCal.
     */
    private const val ARUNODAYA_MINUTES: Long = 96L
}
