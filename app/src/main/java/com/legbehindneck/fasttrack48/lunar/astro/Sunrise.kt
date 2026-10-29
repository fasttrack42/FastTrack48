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
        // Anchor: UTC midnight of the UTC day containing the local noon of the
        // requested date. The NOAA formula yields minutes past UTC midnight of
        // that day (possibly negative or beyond 1440 for longitudes far from the
        // zone's nominal meridian - that is fine, they are simply offsets from
        // the anchor).
        val localNoonMillis = date.atStartOfDay(zone).plusHours(12).toInstant().toEpochMilli()
        val anchorUtcMidnight = floorToUtcMidnight(localNoonMillis)

        var sunriseMillis = solveSunrise(anchorUtcMidnight, latitudeDeg, longitudeDeg)
            ?: return null

        var result = Instant.ofEpochMilli(sunriseMillis).atZone(zone)

        // Near the date line the anchor UTC day can be off by one from the day
        // that actually contains the requested local date's sunrise; correct
        // once in the indicated direction.
        val dayDelta = compareLocalDate(result.toLocalDate(), date)
        if (dayDelta != 0) {
            sunriseMillis = solveSunrise(
                anchorUtcMidnight - dayDelta * MILLIS_PER_DAY,
                latitudeDeg,
                longitudeDeg,
            ) ?: return null
            result = Instant.ofEpochMilli(sunriseMillis).atZone(zone)
            if (compareLocalDate(result.toLocalDate(), date) != 0) {
                // Cannot place a sunrise on this civil date (pathological
                // zone/longitude combination) - report as "no sunrise".
                return null
            }
        }
        return result
    }

    /**
     * Compute the sunset on the given civil date in the given time zone - the
     * mirror of [sunrise] (same zenith, same date-line handling), needed for
     * the day divisions (Pratahkal, Madhyahna, 1/3 of daylight) of the Parana
     * and Purnima vrat rules. The sunrise path is deliberately left untouched.
     *
     * @return the sunset instant, or null if the sun does not set on that date
     *         at that latitude (polar day/night)
     */
    @JvmStatic
    fun sunset(
        date: LocalDate,
        zone: ZoneId,
        latitudeDeg: Double,
        longitudeDeg: Double,
    ): ZonedDateTime? {
        val localNoonMillis = date.atStartOfDay(zone).plusHours(12).toInstant().toEpochMilli()
        val anchorUtcMidnight = floorToUtcMidnight(localNoonMillis)

        var sunsetMillis = solveSunset(anchorUtcMidnight, latitudeDeg, longitudeDeg)
            ?: return null

        var result = Instant.ofEpochMilli(sunsetMillis).atZone(zone)

        val dayDelta = compareLocalDate(result.toLocalDate(), date)
        if (dayDelta != 0) {
            sunsetMillis = solveSunset(
                anchorUtcMidnight - dayDelta * MILLIS_PER_DAY,
                latitudeDeg,
                longitudeDeg,
            ) ?: return null
            result = Instant.ofEpochMilli(sunsetMillis).atZone(zone)
            if (compareLocalDate(result.toLocalDate(), date) != 0) {
                return null
            }
        }
        return result
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
