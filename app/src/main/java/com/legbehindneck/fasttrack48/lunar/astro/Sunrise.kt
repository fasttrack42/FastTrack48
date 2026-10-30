package com.legbehindneck.fasttrack48.lunar.astro

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Sunrise (and sunset) time for a civil date at a given location, using the NOAA solar
 * calculator formulation on top of the [Meeus] solar position (declination +
 * equation of time). Accuracy is about one minute, which is ample for the
 * panchang sunrise rule.
 *
 * Kotlin port of the original Java (v3 modernization); algorithm unchanged,
 * locked by the astro-tests golden suite.
 */
object Sunrise {

    /**
     * Standard rise/set zenith: 90 deg 50' = solar radius (16') plus mean
     * atmospheric refraction at the horizon (34').
     */
    private const val ZENITH_DEGREES = 90.833

    private const val MILLIS_PER_MINUTE = 60000L
    private const val MILLIS_PER_DAY = 86400000L

    /**
     * Compute the sunrise on the given civil date in the given time zone.
     *
     * @param date         the civil date (in [zone]) whose sunrise is wanted
     * @param zone         time zone defining the civil day
     * @param latitudeDeg  observer latitude, degrees, north positive
     * @param longitudeDeg observer longitude, degrees, east positive
     * @return the sunrise instant, or null if the sun does not rise on that
     *         date at that latitude (polar day/night)
     */
    @JvmStatic
    fun sunrise(
        date: LocalDate,
        zone: ZoneId,
        latitudeDeg: Double,
        longitudeDeg: Double,
    ): ZonedDateTime? {
        val (_, sunriseMillis) = sunriseAnchor(date, zone, latitudeDeg, longitudeDeg) ?: return null
        return Instant.ofEpochMilli(sunriseMillis).atZone(zone)
    }

    /**
     * Compute the sunset that follows the sunrise of the given civil date: the end of that
     * day's daylight, needed for the day divisions (Pratahkal, Madhyahna, 1/3 of daylight) of
     * the Parana and Purnima vrat rules. Solved for the same solar day as [sunrise], so it
     * may fall on the next civil date: near the Arctic Circle in summer the sun sets after
     * local midnight (Nuuk, 64 N, early July: about 00:37), and taking the sunset dated
     * [date] instead would return the previous evening's and a negative daylight.
     *
     * @return the sunset instant, or null if the sun does not rise or set on that date at
     *         that latitude (polar day/night)
     */
    @JvmStatic
    fun sunset(
        date: LocalDate,
        zone: ZoneId,
        latitudeDeg: Double,
        longitudeDeg: Double,
    ): ZonedDateTime? {
        val (anchor, _) = sunriseAnchor(date, zone, latitudeDeg, longitudeDeg) ?: return null
        val sunsetMillis = solveSunset(anchor, latitudeDeg, longitudeDeg) ?: return null
        return Instant.ofEpochMilli(sunsetMillis).atZone(zone)
    }

    /**
     * The UTC midnight the NOAA formula counts from for [date]'s sunrise, and that sunrise
     * (epoch millis). The anchor is the midnight of the UTC day
     * holding the local noon. Near the date line that day can be off by one from the one
     * whose sunrise falls on [date]; it is corrected once in the indicated direction. Null
     * when there is no sunrise, or none can be placed on [date] (a pathological zone and
     * longitude combination).
     */
    private fun sunriseAnchor(
        date: LocalDate,
        zone: ZoneId,
        latitudeDeg: Double,
        longitudeDeg: Double,
    ): Pair<Long, Long>? {
        // The formula yields minutes past the anchor, possibly negative or beyond 1440 for
        // longitudes far from the zone's nominal meridian; they are simply offsets from it.
        val localNoonMillis = date.atStartOfDay(zone).plusHours(12).toInstant().toEpochMilli()
        val anchor = floorToUtcMidnight(localNoonMillis)
        val first = solveSunrise(anchor, latitudeDeg, longitudeDeg) ?: return null
        val dayDelta = compareLocalDate(Instant.ofEpochMilli(first).atZone(zone).toLocalDate(), date)
        if (dayDelta == 0) return anchor to first
        val corrected = anchor - dayDelta * MILLIS_PER_DAY
        val second = solveSunrise(corrected, latitudeDeg, longitudeDeg) ?: return null
        return if (compareLocalDate(Instant.ofEpochMilli(second).atZone(zone).toLocalDate(), date) == 0) {
            corrected to second
        } else {
            null
        }
    }

    /**
     * Iteratively solve the NOAA sunrise equation for the UTC day starting at
     * [anchorUtcMidnight]. Returns the sunrise instant in epoch millis, or null
     * when the sun does not cross the rise/set zenith that day.
     */
    private fun solveSunrise(
        anchorUtcMidnight: Long,
        latitudeDeg: Double,
        longitudeDeg: Double,
    ): Long? {
        val latRad = Math.toRadians(latitudeDeg)

        // Initial estimate: 06:00 local mean solar time.
        var t = anchorUtcMidnight +
            Math.round((360.0 - 4.0 * longitudeDeg) * MILLIS_PER_MINUTE)

        for (i in 0 until 5) {
            val jd = Meeus.julianDayFromMillis(t)
            val declRad = Math.toRadians(Meeus.sunDeclinationDegrees(jd))
            val eqTimeMin = Meeus.equationOfTimeMinutes(jd)

            val cosHourAngle = (
                Math.cos(Math.toRadians(ZENITH_DEGREES)) -
                    Math.sin(latRad) * Math.sin(declRad)
                ) / (Math.cos(latRad) * Math.cos(declRad))
            if (cosHourAngle < -1.0 || cosHourAngle > 1.0) {
                return null // polar day or polar night
            }
            val hourAngleDeg = Math.toDegrees(Math.acos(cosHourAngle))

            val sunriseMinutesUtc = 720.0 - 4.0 * longitudeDeg - eqTimeMin -
                4.0 * hourAngleDeg
            val newT = anchorUtcMidnight + Math.round(sunriseMinutesUtc * MILLIS_PER_MINUTE)

            if (Math.abs(newT - t) < 500) {
                return newT
            }
            t = newT
        }
        return t
    }

    /** [solveSunrise] for the setting limb: hour angle added, 18:00 LMST guess. */
    private fun solveSunset(
        anchorUtcMidnight: Long,
        latitudeDeg: Double,
        longitudeDeg: Double,
    ): Long? {
        val latRad = Math.toRadians(latitudeDeg)

        var t = anchorUtcMidnight +
            Math.round((1080.0 - 4.0 * longitudeDeg) * MILLIS_PER_MINUTE)

        for (i in 0 until 5) {
            val jd = Meeus.julianDayFromMillis(t)
            val declRad = Math.toRadians(Meeus.sunDeclinationDegrees(jd))
            val eqTimeMin = Meeus.equationOfTimeMinutes(jd)

            val cosHourAngle = (
                Math.cos(Math.toRadians(ZENITH_DEGREES)) -
                    Math.sin(latRad) * Math.sin(declRad)
                ) / (Math.cos(latRad) * Math.cos(declRad))
            if (cosHourAngle < -1.0 || cosHourAngle > 1.0) {
                return null // polar day or polar night
            }
            val hourAngleDeg = Math.toDegrees(Math.acos(cosHourAngle))

            val sunsetMinutesUtc = 720.0 - 4.0 * longitudeDeg - eqTimeMin +
                4.0 * hourAngleDeg
            val newT = anchorUtcMidnight + Math.round(sunsetMinutesUtc * MILLIS_PER_MINUTE)

            if (Math.abs(newT - t) < 500) {
                return newT
            }
            t = newT
        }
        return t
    }

    private fun floorToUtcMidnight(millis: Long): Long {
        var day = millis / MILLIS_PER_DAY
        if (millis < 0 && millis % MILLIS_PER_DAY != 0L) {
            day -= 1
        }
        return day * MILLIS_PER_DAY
    }

    /** -1, 0 or +1 as [a] is before, equal to or after [b]. */
    private fun compareLocalDate(a: LocalDate, b: LocalDate): Int =
        Integer.signum(a.compareTo(b))
}
