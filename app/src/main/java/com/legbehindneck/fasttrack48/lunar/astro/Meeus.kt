package com.legbehindneck.fasttrack48.lunar.astro

/**
 * Self-contained astronomical core based on Jean Meeus, "Astronomical
 * Algorithms", 2nd edition:
 *  - Solar position: chapter 25 (accuracy ~0.01 degree)
 *  - Lunar position: chapter 47, full table 47.A for longitude and distance
 *    (accuracy ~10 arcsec in longitude, ~4 km in distance)
 *  - Nutation and obliquity: chapter 22 (low-precision series)
 *  - Illuminated fraction: chapter 48
 *
 * All public entry points take a Julian Day expressed on the UT (civil,
 * Unix-time based) scale; the conversion to Terrestrial Time (delta-T) is
 * applied internally before evaluating the theories.
 *
 * Everything is a pure function of time: no state, no allocation beyond
 * locals, safe from any thread.
 *
 * Kotlin port of the original Java (v3 modernization); the numbers, series and
 * algorithm are unchanged and locked by the astro-tests golden suite.
 */
object Meeus {

    /** Mean length of the synodic month in days (new moon to new moon). */
    const val SYNODIC_MONTH: Double = 29.530588861

    /** Mean rate at which the Moon-Sun elongation grows, degrees per day. */
    const val MEAN_ELONGATION_RATE: Double = 360.0 / SYNODIC_MONTH

    /** Julian Day of the Unix epoch, 1970-01-01T00:00:00Z. */
    private const val JD_UNIX_EPOCH = 2440587.5

    private const val MILLIS_PER_DAY = 86400000.0
    private const val SECONDS_PER_DAY = 86400.0

    /** Mean radius of the Moon, km (for angular diameter). */
    private const val MOON_RADIUS_KM = 1737.4

    /** Radius of the Sun, km (for angular diameter). */
    private const val SUN_RADIUS_KM = 696000.0

    private const val KM_PER_AU = 149597870.7

    //------------------------------------------------------------------
    // Time scale conversions
    //------------------------------------------------------------------

    /** Unix epoch milliseconds to Julian Day (UT scale). */
    @JvmStatic
    fun julianDayFromMillis(millis: Long): Double = millis / MILLIS_PER_DAY + JD_UNIX_EPOCH

    /** Julian Day (UT scale) to Unix epoch milliseconds. */
    @JvmStatic
    fun millisFromJulianDay(jd: Double): Long = Math.round((jd - JD_UNIX_EPOCH) * MILLIS_PER_DAY)

    /**
     * Approximate delta-T (TT - UT) in seconds. Uses the Espenak/Meeus 2006
     * polynomial for 2005-2050 and the long-term parabola outside that range.
     */
    @JvmStatic
    fun deltaTSeconds(jdUt: Double): Double {
        val year = 2000.0 + (jdUt - 2451545.0) / 365.25
        if (year in 2005.0..2050.0) {
            val t = year - 2000.0
            return 62.92 + 0.32217 * t + 0.005589 * t * t
        }
        val u = (year - 1820.0) / 100.0
        return -20.0 + 32.0 * u * u
    }

    /** Julian centuries of Terrestrial Time since J2000.0, from a UT Julian Day. */
    private fun centuriesTT(jdUt: Double): Double {
        val jdTt = jdUt + deltaTSeconds(jdUt) / SECONDS_PER_DAY
        return (jdTt - 2451545.0) / 36525.0
    }

    //------------------------------------------------------------------
    // Small angle helpers
    //------------------------------------------------------------------

    /** Normalize an angle to [0, 360). */
    @JvmStatic
    fun fixAngle(degrees: Double): Double {
        val a = degrees % 360.0
        return if (a < 0) a + 360.0 else a
    }

    /** Normalize an angle to (-180, 180]. */
    @JvmStatic
    fun wrapTo180(degrees: Double): Double {
        val a = fixAngle(degrees)
        return if (a > 180.0) a - 360.0 else a
    }

    private fun dsin(degrees: Double): Double = Math.sin(Math.toRadians(degrees))

    private fun dcos(degrees: Double): Double = Math.cos(Math.toRadians(degrees))

    //------------------------------------------------------------------
    // Nutation and obliquity (Meeus ch. 22, low precision)
    //------------------------------------------------------------------

    /** Longitude of the Moon's ascending node, degrees. */
    private fun omega(t: Double): Double =
        125.04452 - 1934.136261 * t + 0.0020708 * t * t + t * t * t / 450000.0

    /** Nutation in longitude, degrees (accurate to ~0.5 arcsec). */
    private fun nutationInLongitude(t: Double): Double {
        val om = omega(t)
        val lSun = 280.4665 + 36000.7698 * t
        val lMoon = 218.3165 + 481267.8813 * t
        val arcsec = -17.20 * dsin(om) - 1.32 * dsin(2 * lSun) -
            0.23 * dsin(2 * lMoon) + 0.21 * dsin(2 * om)
        return arcsec / 3600.0
    }

    /** Mean obliquity of the ecliptic, degrees (Meeus 22.2). */
    private fun meanObliquity(t: Double): Double =
        23.0 + 26.0 / 60.0 + 21.448 / 3600.0 -
            (46.8150 * t + 0.00059 * t * t - 0.001813 * t * t * t) / 3600.0

    //------------------------------------------------------------------
    // Sun (Meeus ch. 25)
    //------------------------------------------------------------------

    /** Sun's geometric mean longitude L0, degrees. */
    private fun sunMeanLongitude(t: Double): Double =
        fixAngle(280.46646 + 36000.76983 * t + 0.0003032 * t * t)

    /** Sun's mean anomaly M, degrees. */
    private fun sunMeanAnomaly(t: Double): Double =
        fixAngle(357.52911 + 35999.05029 * t - 0.0001537 * t * t)

    /** Eccentricity of Earth's orbit. */
    private fun earthEccentricity(t: Double): Double =
        0.016708634 - 0.000042037 * t - 0.0000001267 * t * t

    /** Sun's equation of the center C, degrees. */
    private fun sunEquationOfCenter(t: Double): Double {
        val m = sunMeanAnomaly(t)
        return (1.914602 - 0.004817 * t - 0.000014 * t * t) * dsin(m) +
            (0.019993 - 0.000101 * t) * dsin(2 * m) +
            0.000289 * dsin(3 * m)
    }

    /**
     * Sun's apparent geocentric ecliptic longitude, degrees, corrected for
     * nutation and aberration (Meeus 25.10).
     */
    @JvmStatic
    fun sunApparentLongitude(jdUt: Double): Double {
        val t = centuriesTT(jdUt)
        val trueLongitude = sunMeanLongitude(t) + sunEquationOfCenter(t)
        return fixAngle(trueLongitude - 0.00569 - 0.00478 * dsin(omega(t)))
    }

    /** Earth-Sun distance, kilometres. */
    @JvmStatic
    fun sunDistanceKm(jdUt: Double): Double {
        val t = centuriesTT(jdUt)
        val e = earthEccentricity(t)
        val trueAnomaly = sunMeanAnomaly(t) + sunEquationOfCenter(t)
        val rAu = 1.000001018 * (1 - e * e) / (1 + e * dcos(trueAnomaly))
        return rAu * KM_PER_AU
    }

    /** Sun's apparent angular diameter, degrees. */
    @JvmStatic
    fun sunAngularDiameterDegrees(jdUt: Double): Double =
        Math.toDegrees(2.0 * Math.asin(SUN_RADIUS_KM / sunDistanceKm(jdUt)))

    /**
     * Sun's apparent declination, degrees. Uses the obliquity corrected the
     * NOAA way (+0.00256 cos(omega)) so it pairs with [equationOfTimeMinutes]
     * for sunrise computations.
     */
    @JvmStatic
    fun sunDeclinationDegrees(jdUt: Double): Double {
        val t = centuriesTT(jdUt)
        val lambda = sunApparentLongitude(jdUt)
        val eps = meanObliquity(t) + 0.00256 * dcos(omega(t))
        return Math.toDegrees(Math.asin(dsin(eps) * dsin(lambda)))
    }

    /**
     * Equation of time in minutes (apparent solar time minus mean solar time),
     * NOAA formulation (equivalent to Meeus 28.3).
     */
    @JvmStatic
    fun equationOfTimeMinutes(jdUt: Double): Double {
        val t = centuriesTT(jdUt)
        val eps = meanObliquity(t) + 0.00256 * dcos(omega(t))
        val l0 = sunMeanLongitude(t)
        val m = sunMeanAnomaly(t)
        val e = earthEccentricity(t)
        var y = Math.tan(Math.toRadians(eps) / 2.0)
        y *= y

        val radians = y * dsin(2 * l0) -
            2.0 * e * dsin(m) +
            4.0 * e * y * dsin(m) * dcos(2 * l0) -
            0.5 * y * y * dsin(4 * l0) -
            1.25 * e * e * dsin(2 * m)
        return 4.0 * Math.toDegrees(radians)
    }

    //------------------------------------------------------------------
    // Moon (Meeus ch. 47, table 47.A)
    //------------------------------------------------------------------

    /**
     * Periodic terms for the Moon's longitude (sigma-l) and distance
     * (sigma-r). Columns: multiple of D, M, M', F. Meeus table 47.A, 60 rows.
     */
    private val LR_ARGS: Array<IntArray> = arrayOf(
        intArrayOf(0, 0, 1, 0), intArrayOf(2, 0, -1, 0), intArrayOf(2, 0, 0, 0), intArrayOf(0, 0, 2, 0),
        intArrayOf(0, 1, 0, 0), intArrayOf(0, 0, 0, 2), intArrayOf(2, 0, -2, 0), intArrayOf(2, -1, -1, 0),
        intArrayOf(2, 0, 1, 0), intArrayOf(2, -1, 0, 0), intArrayOf(0, 1, -1, 0), intArrayOf(1, 0, 0, 0),
        intArrayOf(0, 1, 1, 0), intArrayOf(2, 0, 0, -2), intArrayOf(0, 0, 1, 2), intArrayOf(0, 0, 1, -2),
        intArrayOf(4, 0, -1, 0), intArrayOf(0, 0, 3, 0), intArrayOf(4, 0, -2, 0), intArrayOf(2, 1, -1, 0),
        intArrayOf(2, 1, 0, 0), intArrayOf(1, 0, -1, 0), intArrayOf(1, 1, 0, 0), intArrayOf(2, -1, 1, 0),
        intArrayOf(2, 0, 2, 0), intArrayOf(4, 0, 0, 0), intArrayOf(2, 0, -3, 0), intArrayOf(0, 1, -2, 0),
        intArrayOf(2, 0, -1, 2), intArrayOf(2, -1, -2, 0), intArrayOf(1, 0, 1, 0), intArrayOf(2, -2, 0, 0),
        intArrayOf(0, 1, 2, 0), intArrayOf(0, 2, 0, 0), intArrayOf(2, -2, -1, 0), intArrayOf(2, 0, 1, -2),
        intArrayOf(2, 0, 0, 2), intArrayOf(4, -1, -1, 0), intArrayOf(0, 0, 2, 2), intArrayOf(3, 0, -1, 0),
        intArrayOf(2, 1, 1, 0), intArrayOf(4, -1, -2, 0), intArrayOf(0, 2, -1, 0), intArrayOf(2, 2, -1, 0),
        intArrayOf(2, 1, -2, 0), intArrayOf(2, -1, 0, -2), intArrayOf(4, 0, 1, 0), intArrayOf(0, 0, 4, 0),
        intArrayOf(4, -1, 0, 0), intArrayOf(1, 0, -2, 0), intArrayOf(2, 1, 0, -2), intArrayOf(0, 0, 2, -2),
        intArrayOf(1, 1, 1, 0), intArrayOf(3, 0, -2, 0), intArrayOf(4, 0, -3, 0), intArrayOf(2, -1, 2, 0),
        intArrayOf(0, 2, 1, 0), intArrayOf(1, 1, -1, 0), intArrayOf(2, 0, 3, 0), intArrayOf(2, 0, -1, -2),
    )

    private val L_COEFF = intArrayOf(
        6288774, 1274027, 658314, 213618,
        -185116, -114332, 58793, 57066,
        53322, 45758, -40923, -34720,
        -30383, 15327, -12528, 10980,
        10675, 10034, 8548, -7888,
        -6766, -5163, 4987, 4036,
        3994, 3861, 3665, -2689,
        -2602, 2390, -2348, 2236,
        -2120, -2069, 2048, -1773,
        -1595, 1215, -1110, -892,
        -810, 759, -713, -700,
        691, 596, 549, 537,
        520, -487, -399, -381,
        351, -340, 330, 327,
        -323, 299, 294, 0,
    )

    private val R_COEFF = intArrayOf(
        -20905355, -3699111, -2955968, -569925,
        48888, -3149, 246158, -152138,
        -170733, -204586, -129620, 108743,
        104755, 10321, 0, 79661,
        -34782, -23210, -21636, 24208,
        30824, -8379, -16675, -12831,
        -10445, -11650, 14403, -7003,
        0, 10056, 6322, -9884,
        5751, 0, -4950, 4130,
        0, -3958, 0, 3258,
        2616, -1897, -2117, 2354,
        0, 0, -1423, -1117,
        -1571, -1739, 0, -4421,
        0, 0, 0, 0,
        1165, 0, 0, 8752,
    )

    /**
     * Lunar theory evaluation: apparent ecliptic longitude in degrees (index 0)
     * and geocentric distance in kilometres (index 1), packed to avoid two
     * separate series walks.
     */
    private fun moonLongitudeAndDistance(jdUt: Double): DoubleArray {
        val t = centuriesTT(jdUt)

        // Fundamental arguments, Meeus 47.1 - 47.5.
        val lp = fixAngle(
            218.3164477 + 481267.88123421 * t -
                0.0015786 * t * t + t * t * t / 538841.0 -
                t * t * t * t / 65194000.0,
        ) // mean longitude L'
        val d = fixAngle(
            297.8501921 + 445267.1114034 * t -
                0.0018819 * t * t + t * t * t / 545868.0 -
                t * t * t * t / 113065000.0,
        ) // mean elongation D
        val m = fixAngle(
            357.5291092 + 35999.0502909 * t -
                0.0001536 * t * t + t * t * t / 24490000.0,
        ) // sun mean anomaly M
        val mp = fixAngle(
            134.9633964 + 477198.8675055 * t +
                0.0087414 * t * t + t * t * t / 69699.0 -
                t * t * t * t / 14712000.0,
        ) // moon mean anomaly M'
        val f = fixAngle(
            93.2720950 + 483202.0175233 * t -
                0.0036539 * t * t - t * t * t / 3526000.0 +
                t * t * t * t / 863310000.0,
        ) // argument of latitude F

        // Eccentricity damping factor for terms involving M (Meeus 47.6).
        val e = 1.0 - 0.002516 * t - 0.0000074 * t * t
        val e2 = e * e

        var sumL = 0.0
        var sumR = 0.0
        for (i in LR_ARGS.indices) {
            val a = LR_ARGS[i]
            val arg = a[0] * d + a[1] * m + a[2] * mp + a[3] * f
            val damping = if (a[1] == 1 || a[1] == -1) {
                e
            } else if (a[1] == 2 || a[1] == -2) {
                e2
            } else {
                1.0
            }
            if (L_COEFF[i] != 0) {
                sumL += L_COEFF[i] * damping * dsin(arg)
            }
            if (R_COEFF[i] != 0) {
                sumR += R_COEFF[i] * damping * dcos(arg)
            }
        }

        // Additive terms: Venus (A1), Jupiter (A2), flattening of the Earth.
        val a1 = fixAngle(119.75 + 131.849 * t)
        val a2 = fixAngle(53.09 + 479264.290 * t)
        sumL += 3958.0 * dsin(a1) +
            1962.0 * dsin(lp - f) +
            318.0 * dsin(a2)

        val longitude = fixAngle(lp + sumL / 1000000.0 + nutationInLongitude(t))
        val distanceKm = 385000.56 + sumR / 1000.0

        return doubleArrayOf(longitude, distanceKm)
    }

    /** Moon's apparent geocentric ecliptic longitude, degrees. */
    @JvmStatic
    fun moonApparentLongitude(jdUt: Double): Double = moonLongitudeAndDistance(jdUt)[0]

    /** Earth-Moon distance (centre to centre), kilometres. */
    @JvmStatic
    fun moonDistanceKm(jdUt: Double): Double = moonLongitudeAndDistance(jdUt)[1]

    /** Moon's apparent angular diameter, degrees. */
    @JvmStatic
    fun moonAngularDiameterDegrees(jdUt: Double): Double =
        Math.toDegrees(2.0 * Math.asin(MOON_RADIUS_KM / moonDistanceKm(jdUt)))

    //------------------------------------------------------------------
    // Elongation, phase, tithi
    //------------------------------------------------------------------

    /**
     * Moon-Sun elongation in apparent geocentric ecliptic longitude, degrees
     * in [0, 360). 0 = new moon, 90 = first quarter, 180 = full moon,
     * 270 = last quarter. Tithi n spans [(n-1)*12, n*12) degrees.
     */
    @JvmStatic
    fun elongationDegrees(jdUt: Double): Double =
        fixAngle(moonApparentLongitude(jdUt) - sunApparentLongitude(jdUt))

    /**
     * Illuminated fraction of the Moon's disk, 0..1 (Meeus 48.2/48.3, with the
     * lunar ecliptic latitude neglected; the error is below 0.3%).
     */
    @JvmStatic
    fun illuminatedFraction(jdUt: Double): Double {
        val psi = Math.toRadians(elongationDegrees(jdUt))
        val sunKm = sunDistanceKm(jdUt)
        val moonKm = moonDistanceKm(jdUt)
        // Phase angle i: Sun-Moon-Earth angle.
        val i = Math.atan2(sunKm * Math.sin(psi), moonKm - sunKm * Math.cos(psi))
        return (1.0 + Math.cos(i)) / 2.0
    }

    /**
     * Find the instant at which the Moon-Sun elongation equals [targetDegrees]
     * (mod 360), searching from [jdUtGuess]. The elongation grows strictly
     * monotonically, so the fixed-point iteration is a contraction converging
     * to sub-second precision in fewer than 15 iterations for a ~1-day guess.
     */
    @JvmStatic
    fun searchElongationCrossing(targetDegrees: Double, jdUtGuess: Double): Double {
        var jd = jdUtGuess
        for (i in 0 until 50) {
            val diff = wrapTo180(targetDegrees - elongationDegrees(jd))
            if (Math.abs(diff) < 1e-6) { // ~0.007 s of time
                break
            }
            jd += diff / MEAN_ELONGATION_RATE
        }
        return jd
    }

    /** Instant of the last new moon at or before [jdUt] (elongation 0). */
    @JvmStatic
    fun previousNewMoon(jdUt: Double): Double {
        val elongation = elongationDegrees(jdUt)
        var newMoon = searchElongationCrossing(0.0, jdUt - elongation / MEAN_ELONGATION_RATE)
        // Numerically the solver can land on the *next* new moon when jdUt is
        // within moments of one; step back a lunation in that case.
        if (newMoon > jdUt + 1e-6) {
            newMoon = searchElongationCrossing(0.0, newMoon - SYNODIC_MONTH)
        }
        return newMoon
    }

    /**
     * Instant at which the elongation reaches [targetDegrees] within the
     * lunation starting at [newMoonJdUt]. [targetDegrees] must be in (0, 360];
     * 360 means the next new moon.
     */
    @JvmStatic
    fun elongationCrossingInLunation(newMoonJdUt: Double, targetDegrees: Double): Double {
        val guess = newMoonJdUt + targetDegrees / MEAN_ELONGATION_RATE
        return searchElongationCrossing(targetDegrees, guess)
    }

    //------------------------------------------------------------------
    // Latitude (Meeus ch. 47, table 47.B) and optical libration (ch. 53)
    //------------------------------------------------------------------

    /** Periodic terms for the Moon's latitude, columns D, M, M', F (table 47.B). */
    private val B_ARGS: Array<IntArray> = arrayOf(
        intArrayOf(0, 0, 0, 1), intArrayOf(0, 0, 1, 1), intArrayOf(0, 0, 1, -1), intArrayOf(2, 0, 0, -1),
        intArrayOf(2, 0, -1, 1), intArrayOf(2, 0, -1, -1), intArrayOf(2, 0, 0, 1), intArrayOf(0, 0, 2, 1),
        intArrayOf(2, 0, 1, -1), intArrayOf(0, 0, 2, -1), intArrayOf(2, -1, 0, -1), intArrayOf(2, 0, -2, -1),
        intArrayOf(2, 0, 1, 1), intArrayOf(2, 1, 0, -1), intArrayOf(2, -1, -1, 1), intArrayOf(2, -1, 0, 1),
        intArrayOf(2, -1, -1, -1), intArrayOf(0, 1, -1, -1), intArrayOf(4, 0, -1, -1), intArrayOf(0, 1, 0, 1),
        intArrayOf(0, 0, 0, 3), intArrayOf(0, 1, -1, 1), intArrayOf(1, 0, 0, 1), intArrayOf(0, 1, 1, 1),
        intArrayOf(0, 1, 1, -1), intArrayOf(0, 1, 0, -1), intArrayOf(1, 0, 0, -1), intArrayOf(0, 0, 3, 1),
        intArrayOf(4, 0, 0, -1), intArrayOf(4, 0, -1, 1), intArrayOf(0, 0, 1, -3), intArrayOf(4, 0, -2, 1),
        intArrayOf(2, 0, 0, -3), intArrayOf(2, 0, 2, -1), intArrayOf(2, -1, 1, -1), intArrayOf(2, 0, -2, 1),
        intArrayOf(0, 0, 3, -1), intArrayOf(2, 0, 2, 1), intArrayOf(2, 0, -3, -1), intArrayOf(2, 1, -1, 1),
        intArrayOf(2, 1, 0, 1), intArrayOf(4, 0, 0, 1), intArrayOf(2, -1, 1, 1), intArrayOf(2, -2, 0, -1),
        intArrayOf(0, 0, 1, 3), intArrayOf(2, 1, 1, -1), intArrayOf(1, 1, 0, -1), intArrayOf(1, 1, 0, 1),
        intArrayOf(0, 1, -2, -1), intArrayOf(2, 1, -1, -1), intArrayOf(1, 0, 1, 1), intArrayOf(2, -1, -2, -1),
        intArrayOf(0, 1, 2, 1), intArrayOf(4, 0, -2, -1), intArrayOf(4, -1, -1, -1), intArrayOf(1, 0, 1, -1),
        intArrayOf(4, 0, 1, -1), intArrayOf(1, 0, -1, -1), intArrayOf(4, -1, 0, -1), intArrayOf(2, -2, 0, 1),
    )

    private val B_COEFF = intArrayOf(
        5128122, 280602, 277693, 173237,
        55413, 46271, 32573, 17198,
        9266, 8822, 8216, 4324,
        4200, -3359, 2463, 2211,
        2065, -1870, 1828, -1794,
        -1749, -1565, -1491, -1475,
        -1410, -1344, -1335, 1107,
        1021, 833, 777, 671,
        607, 596, 491, -451,
        439, 422, 421, -366,
        -351, 331, 315, 302,
        -283, -229, 223, 223,
        -220, -220, -185, 181,
        -177, 176, 166, -164,
        132, -119, 115, 107,
    )

    /** Moon's apparent geocentric ecliptic latitude, degrees (Meeus 47, table 47.B). */
    @JvmStatic
    fun moonEclipticLatitudeDegrees(jdUt: Double): Double {
        val t = centuriesTT(jdUt)
        val lp = fixAngle(218.3164477 + 481267.88123421 * t - 0.0015786 * t * t
            + t * t * t / 538841.0 - t * t * t * t / 65194000.0)
        val d = fixAngle(297.8501921 + 445267.1114034 * t - 0.0018819 * t * t
            + t * t * t / 545868.0 - t * t * t * t / 113065000.0)
        val m = fixAngle(357.5291092 + 35999.0502909 * t - 0.0001536 * t * t
            + t * t * t / 24490000.0)
        val mp = fixAngle(134.9633964 + 477198.8675055 * t + 0.0087414 * t * t
            + t * t * t / 69699.0 - t * t * t * t / 14712000.0)
        val f = fixAngle(93.2720950 + 483202.0175233 * t - 0.0036539 * t * t
            - t * t * t / 3526000.0 + t * t * t * t / 863310000.0)
        val e = 1.0 - 0.002516 * t - 0.0000074 * t * t
        val e2 = e * e

        var sumB = 0.0
        for (i in B_ARGS.indices) {
            val a = B_ARGS[i]
            val arg = a[0] * d + a[1] * m + a[2] * mp + a[3] * f
            val damping = when (Math.abs(a[1])) {
                1 -> e
                2 -> e2
                else -> 1.0
            }
            sumB += B_COEFF[i] * damping * dsin(arg)
        }
        val a1 = fixAngle(119.75 + 131.849 * t)
        val a3 = fixAngle(313.45 + 481266.484 * t)
        sumB += -2235.0 * dsin(lp) + 382.0 * dsin(a3) +
            175.0 * dsin(a1 - f) + 175.0 * dsin(a1 + f) +
            127.0 * dsin(lp - mp) - 115.0 * dsin(lp + mp)

        return sumB / 1000000.0
    }

    /** Inclination of the mean lunar equator to the ecliptic, degrees. */
    private const val LUNAR_EQUATOR_INCLINATION = 1.54242

    /**
     * Optical libration of the Moon in selenographic longitude and latitude
     * (Meeus ch. 53), degrees. Index 0 = libration in longitude l' (the
     * sub-Earth point's selenographic longitude, east positive, roughly
     * +/-8 deg), index 1 = libration in latitude b' (+/-7 deg). Physical
     * libration (<0.04 deg) is neglected.
     */
    @JvmStatic
    fun opticalLibrationDegrees(jdUt: Double): DoubleArray {
        val t = centuriesTT(jdUt)
        val lambda = moonApparentLongitude(jdUt)
        val beta = moonEclipticLatitudeDegrees(jdUt)
        val dpsi = nutationInLongitude(t)
        val i = LUNAR_EQUATOR_INCLINATION
        val node = fixAngle(125.0445479 - 1934.1362891 * t + 0.0020754 * t * t
            + t * t * t / 467441.0 - t * t * t * t / 60616000.0)
        val f = fixAngle(93.2720950 + 483202.0175233 * t - 0.0036539 * t * t
            - t * t * t / 3526000.0 + t * t * t * t / 863310000.0)

        val w = fixAngle(lambda - dpsi - node)
        val sinW = dsin(w)
        val cosW = dcos(w)
        val cosB = dcos(beta)
        val sinB = dsin(beta)

        val a = Math.toDegrees(
            Math.atan2(sinW * cosB * dcos(i) - sinB * dsin(i), cosW * cosB),
        )
        var lPrime = a - f
        lPrime = wrapTo180(lPrime)
        val bPrime = Math.toDegrees(Math.asin(-sinW * cosB * dsin(i) - sinB * dcos(i)))
        return doubleArrayOf(lPrime, bPrime)
    }

    /**
     * Selenographic latitude of the sub-solar point, degrees (Meeus ch. 53 with
     * the Sun's ecliptic latitude taken as zero). Ranges about +/-1.6 deg and
     * gives the slight north/south tilt of the terminator.
     */
    @JvmStatic
    fun subSolarLatitudeDegrees(jdUt: Double): Double {
        val t = centuriesTT(jdUt)
        val lambdaSun = sunApparentLongitude(jdUt)
        val dpsi = nutationInLongitude(t)
        val i = LUNAR_EQUATOR_INCLINATION
        val node = fixAngle(125.0445479 - 1934.1362891 * t + 0.0020754 * t * t
            + t * t * t / 467441.0 - t * t * t * t / 60616000.0)
        val w0 = fixAngle(lambdaSun - dpsi - node)
        return Math.toDegrees(Math.asin(-dsin(w0) * dsin(i)))
    }
}
