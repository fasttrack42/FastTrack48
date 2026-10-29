package com.legbehindneck.fasttrack48.lunar.astro;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Golden / characterization test for the {@code lunar.astro} package (Kotlin
 * Meeus/Sunrise/EkadashiCalculator), ported from the standalone JVM harness
 * used before the module extraction; shared with the NextMoon app.
 *
 * <p>Three independent layers guard the crown-jewel math (DrikAnchorsTest adds the observance rules):
 * <ol>
 *   <li><b>Invariants</b> - physical truths that must hold if the math is right
 *       (illumination ~0 at new / ~1 at full, elongation ~90/180/270 at the
 *       quarters, phase ordering, distances in range, Ekadashi display instant
 *       is a real sunrise inside the tithi, polar null cases).</li>
 *   <li><b>Anchors</b> - known syzygies to +/-10 min, proving correctness (not
 *       merely self-consistency).</li>
 *   <li><b>Regression goldens</b> - exact current outputs in the committed
 *       {@code golden-values.txt} resource, re-verified within a tight
 *       tolerance so any future change that shifts a number is caught.</li>
 * </ol>
 *
 * To intentionally regenerate the baseline after a reviewed math change, run
 * {@link #main(String[])} with a target path (writes the captured values).
 */
public class GoldenAstroTest {

    private static final ZoneId UTC = ZoneOffset.UTC;
    private static final ZoneId DELHI = ZoneId.of("Asia/Kolkata");
    private static final ZoneId LONDON = ZoneId.of("Europe/London");
    private static final ZoneId NY = ZoneId.of("America/New_York");
    private static final ZoneId OSLO = ZoneId.of("Europe/Oslo");

    private static final double DELHI_LAT = 28.6139;
    private static final double DELHI_LON = 77.2090;

    private static final double TOL_ANGLE_DEG = 1e-6;
    private static final double TOL_DIST_KM = 1e-3;
    private static final double TOL_FRAC = 1e-9;
    private static final long TOL_MILLIS = 2L;

    private final StringBuilder problems = new StringBuilder();
    private int checks = 0;

    @Test
    public void astroInvariantsAnchorsAndGoldens() throws Exception {
        invariants();
        anchors();
        Map<String, String> golden = readGolden();
        verifyGoldens(golden, computeGoldens());

        if (problems.length() > 0) {
            fail("astro golden suite: failures after " + checks + " checks:\n" + problems);
        }
    }

    @Test
    public void opticalLibration_matchesMeeusExample() {
        // Meeus "Astronomical Algorithms" Example 53.a, 1992 April 12, 0h TD:
        // total libration l ~ -1.23 deg, b ~ +4.20 deg (optical dominates).
        double[] lb = Meeus.opticalLibrationDegrees(jdUtc(1992, 4, 12, 0, 0));
        assertTrue("libration in longitude ~ -1.2, got " + lb[0], lb[0] > -1.6 && lb[0] < -0.8);
        assertTrue("libration in latitude ~ +4.2, got " + lb[1], lb[1] > 3.8 && lb[1] < 4.6);

        // Optical libration stays within its physical envelope all year.
        double base = jdUtc(2024, 1, 1, 0, 0);
        for (int d = 0; d < 366; d += 5) {
            double[] x = Meeus.opticalLibrationDegrees(base + d);
            assertTrue("|lon| < 8.5, got " + x[0], Math.abs(x[0]) < 8.5);
            assertTrue("|lat| < 7.2, got " + x[1], Math.abs(x[1]) < 7.2);
        }
    }

    //------------------------------------------------------------------
    // Helpers
    //------------------------------------------------------------------

    private static double jdUtc(int y, int mo, int d, int h, int mi) {
        long millis = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, UTC).toInstant().toEpochMilli();
        return Meeus.julianDayFromMillis(millis);
    }

    private static double jdOf(ZonedDateTime z) {
        return Meeus.julianDayFromMillis(z.toInstant().toEpochMilli());
    }

    private static String iso(double jdUt) {
        long millis = Meeus.millisFromJulianDay(jdUt);
        return Instant.ofEpochMilli(millis).toString();
    }

    private void ok(boolean cond, String msg) {
        checks++;
        if (!cond) {
            problems.append("  FAIL: ").append(msg).append('\n');
        }
    }

    private void near(double actual, double expected, double tol, String msg) {
        ok(Math.abs(actual - expected) <= tol,
                msg + " (got " + actual + ", want " + expected + " +/-" + tol + ")");
    }

    //------------------------------------------------------------------
    // Layer 1 - physical invariants
    //------------------------------------------------------------------

    private void invariants() {
        double nm = Meeus.previousNewMoon(jdUtc(2024, 1, 15, 0, 0));
        double prev = -1;
        for (int k = 0; k < 13; k++) {
            double fq = Meeus.elongationCrossingInLunation(nm, 90);
            double full = Meeus.elongationCrossingInLunation(nm, 180);
            double lq = Meeus.elongationCrossingInLunation(nm, 270);
            double next = Meeus.elongationCrossingInLunation(nm, 360);

            near(Meeus.wrapTo180(Meeus.elongationDegrees(nm)), 0, 1e-3, "elong@new k=" + k);
            near(Meeus.elongationDegrees(fq), 90, 1e-3, "elong@FQ k=" + k);
            near(Meeus.elongationDegrees(full), 180, 1e-3, "elong@full k=" + k);
            near(Meeus.elongationDegrees(lq), 270, 1e-3, "elong@LQ k=" + k);

            ok(nm < fq && fq < full && full < lq && lq < next, "phase order k=" + k);

            ok(Meeus.illuminatedFraction(nm) < 0.02, "illum@new small k=" + k);
            ok(Meeus.illuminatedFraction(full) > 0.98, "illum@full big k=" + k);
            near(Meeus.illuminatedFraction(fq), 0.5, 0.03, "illum@FQ half k=" + k);
            near(Meeus.illuminatedFraction(lq), 0.5, 0.03, "illum@LQ half k=" + k);

            double dist = Meeus.moonDistanceKm(full);
            ok(dist > 356000 && dist < 407000, "moon dist band k=" + k + " =" + dist);

            if (prev > 0) {
                double gap = nm - prev;
                ok(Math.abs(gap - Meeus.SYNODIC_MONTH) < 0.9, "synodic gap k=" + k + " =" + gap);
            }
            prev = nm;
            nm = next;
        }

        double q = jdUtc(2024, 7, 4, 12, 0);
        double pn = Meeus.previousNewMoon(q);
        ok(pn <= q + 1e-6, "previousNewMoon <= query");
        near(Meeus.wrapTo180(Meeus.elongationDegrees(pn)), 0, 1e-3, "previousNewMoon elong ~0");

        double sd = Meeus.sunDistanceKm(jdUtc(2024, 1, 3, 0, 0));
        ok(sd > 147.0e6 && sd < 152.1e6, "sun distance ~1AU =" + sd);

        ZonedDateTime sr = Sunrise.sunrise(LocalDate.of(2024, 3, 20), DELHI, DELHI_LAT, DELHI_LON);
        ok(sr != null, "Delhi has a sunrise on equinox");
        if (sr != null) {
            int h = sr.getHour();
            ok(h >= 5 && h <= 7, "Delhi sunrise in 05-07 local, got " + sr);
            ok(sr.toLocalDate().equals(LocalDate.of(2024, 3, 20)), "sunrise on requested date");
        }

        ZonedDateTime polarJune = Sunrise.sunrise(LocalDate.of(2024, 6, 21), OSLO, 69.65, 18.96);
        ZonedDateTime polarDec = Sunrise.sunrise(LocalDate.of(2024, 12, 21), OSLO, 69.65, 18.96);
        ok(polarJune == null, "polar day -> null sunrise (June)");
        ok(polarDec == null, "polar night -> null sunrise (Dec)");

        double lun = Meeus.previousNewMoon(jdUtc(2024, 1, 5, 0, 0));
        for (int k = 0; k < 6; k++) {
            for (boolean krishna : new boolean[]{false, true}) {
                EkadashiCalculator.Result r =
                        EkadashiCalculator.compute(lun, krishna, DELHI_LAT, DELHI_LON, DELHI);
                ok(r != null, "ekadashi non-null k=" + k + " krishna=" + krishna);
                ok(r.tithiStart.isBefore(r.tithiEnd), "tithi start<end k=" + k);
                double days = (r.tithiEnd.toInstant().toEpochMilli()
                        - r.tithiStart.toInstant().toEpochMilli()) / 86400000.0;
                ok(days > 0.7 && days < 1.3, "tithi ~1 day k=" + k + " =" + days);
                ok(r.displayInstant.toLocalDate().equals(r.fastDay), "display on fastDay k=" + k);
                int hh = r.displayInstant.getHour();
                ok(hh >= 5 && hh <= 8, "ekadashi sunrise 05-08 local k=" + k
                        + " krishna=" + krishna + " got " + r.displayInstant);
            }
            lun = Meeus.elongationCrossingInLunation(lun, 360);
        }

        // Vedic Purnima/Amavasya civil days + Vaishnava Ekadashi fast day.
        double lunV = Meeus.previousNewMoon(jdUtc(2024, 1, 5, 0, 0));
        for (int k = 0; k < 6; k++) {
            EkadashiCalculator.Result pur =
                    EkadashiCalculator.computePurnima(lunV, DELHI_LAT, DELHI_LON, DELHI);
            ok(Meeus.illuminatedFraction(jdOf(pur.displayInstant)) > 0.90,
                    "purnima moon near-full k=" + k);
            ok(pur.displayInstant.toLocalDate().equals(pur.fastDay), "purnima display on day k=" + k);

            EkadashiCalculator.Result ama =
                    EkadashiCalculator.computeAmavasya(lunV, DELHI_LAT, DELHI_LON, DELHI);
            ok(Meeus.illuminatedFraction(jdOf(ama.displayInstant)) < 0.10,
                    "amavasya moon near-new k=" + k);

            for (boolean krishna : new boolean[]{false, true}) {
                EkadashiCalculator.Result sm =
                        EkadashiCalculator.compute(lunV, krishna, DELHI_LAT, DELHI_LON, DELHI);
                EkadashiCalculator.Result vai =
                        EkadashiCalculator.computeVaishnava(lunV, krishna, DELHI_LAT, DELHI_LON, DELHI);
                double base = krishna ? 300.0 : 120.0;
                double rel = Meeus.elongationDegrees(jdOf(vai.displayInstant)) - base;
                ok(rel > -1.0 && rel < 25.0,
                        "vaishnava fast sunrise is ekadasi/dvadasi k=" + k + " krishna=" + krishna
                                + " rel=" + rel);
                long diff = Math.abs(vai.fastDay.toEpochDay() - sm.fastDay.toEpochDay());
                ok(diff <= 1, "vaishnava within 1 day of smarta k=" + k + " krishna=" + krishna
                        + " diff=" + diff);
            }
            lunV = Meeus.elongationCrossingInLunation(lunV, 360);
        }
    }

    //------------------------------------------------------------------
    // Layer 2 - external anchors
    //------------------------------------------------------------------

    private void anchors() {
        long tol = 10 * 60 * 1000L;

        double nm = Meeus.previousNewMoon(jdUtc(2017, 8, 22, 0, 0));
        long nmMs = Meeus.millisFromJulianDay(nm);
        long wantNm = ZonedDateTime.of(2017, 8, 21, 18, 30, 0, 0, UTC).toInstant().toEpochMilli();
        ok(Math.abs(nmMs - wantNm) < tol, "2017-08-21 new moon ~18:30Z, got " + iso(nm));

        double nm2 = Meeus.previousNewMoon(jdUtc(2000, 1, 21, 12, 0));
        double full = Meeus.elongationCrossingInLunation(nm2, 180);
        long fullMs = Meeus.millisFromJulianDay(full);
        long wantFull = ZonedDateTime.of(2000, 1, 21, 4, 40, 0, 0, UTC).toInstant().toEpochMilli();
        ok(Math.abs(fullMs - wantFull) < tol, "2000-01-21 full moon ~04:40Z, got " + iso(full));

        // Vedic civil days vs drikpanchang (New Delhi), lunation of the 2024-01-11
        // new moon: Pausha Purnima 2024-01-25, Magha (Mauni) Amavasya 2024-02-09.
        double nmJan = Meeus.previousNewMoon(jdUtc(2024, 1, 15, 0, 0));
        EkadashiCalculator.Result pur =
                EkadashiCalculator.computePurnima(nmJan, DELHI_LAT, DELHI_LON, DELHI);
        ok(pur.fastDay.equals(LocalDate.of(2024, 1, 25)),
                "Pausha Purnima 2024-01-25, got " + pur.fastDay);
        EkadashiCalculator.Result ama =
                EkadashiCalculator.computeAmavasya(nmJan, DELHI_LAT, DELHI_LON, DELHI);
        ok(ama.fastDay.equals(LocalDate.of(2024, 2, 9)),
                "Magha Amavasya 2024-02-09, got " + ama.fastDay);
    }

    //------------------------------------------------------------------
    // Layer 3 - regression goldens
    //------------------------------------------------------------------

    private static Map<String, String> computeGoldens() {
        Map<String, String> g = new LinkedHashMap<String, String>();

        double jd = jdUtc(2024, 1, 1, 0, 0);
        g.put("deltaT.2024-01-01", d(Meeus.deltaTSeconds(jd)));
        g.put("deltaT.1900", d(Meeus.deltaTSeconds(jdUtc(1900, 1, 1, 0, 0))));
        g.put("deltaT.2100", d(Meeus.deltaTSeconds(jdUtc(2100, 1, 1, 0, 0))));
        g.put("sun.lon.2024-01-01", d(Meeus.sunApparentLongitude(jd)));
        g.put("sun.dist.2024-01-01", d(Meeus.sunDistanceKm(jd)));
        g.put("sun.decl.2024-01-01", d(Meeus.sunDeclinationDegrees(jd)));
        g.put("sun.eot.2024-01-01", d(Meeus.equationOfTimeMinutes(jd)));
        g.put("moon.lon.2024-01-01", d(Meeus.moonApparentLongitude(jd)));
        g.put("moon.dist.2024-01-01", d(Meeus.moonDistanceKm(jd)));
        g.put("moon.illum.2024-01-01", d(Meeus.illuminatedFraction(jd)));
        g.put("moon.elong.2024-01-01", d(Meeus.elongationDegrees(jd)));

        double nm = Meeus.previousNewMoon(jdUtc(2024, 5, 10, 0, 0));
        g.put("lun2024-05.new", ms(nm));
        g.put("lun2024-05.fq", ms(Meeus.elongationCrossingInLunation(nm, 90)));
        g.put("lun2024-05.full", ms(Meeus.elongationCrossingInLunation(nm, 180)));
        g.put("lun2024-05.lq", ms(Meeus.elongationCrossingInLunation(nm, 270)));
        g.put("lun2024-05.next", ms(Meeus.elongationCrossingInLunation(nm, 360)));

        double n = Meeus.previousNewMoon(jdUtc(2025, 1, 1, 0, 0));
        for (int k = 0; k < 13; k++) {
            g.put("newmoon.chain." + k, ms(n));
            n = Meeus.elongationCrossingInLunation(n, 360);
        }

        putSunrise(g, "sunrise.delhi.2024-03-20", LocalDate.of(2024, 3, 20), DELHI, DELHI_LAT, DELHI_LON);
        putSunrise(g, "sunrise.delhi.2024-06-21", LocalDate.of(2024, 6, 21), DELHI, DELHI_LAT, DELHI_LON);
        putSunrise(g, "sunrise.london.2024-03-20", LocalDate.of(2024, 3, 20), LONDON, 51.5074, -0.1278);
        putSunrise(g, "sunrise.london.2024-12-21", LocalDate.of(2024, 12, 21), LONDON, 51.5074, -0.1278);
        putSunrise(g, "sunrise.ny.2024-06-21", LocalDate.of(2024, 6, 21), NY, 40.7128, -74.0060);

        double lun = Meeus.previousNewMoon(jdUtc(2024, 1, 5, 0, 0));
        for (int k = 0; k < 6; k++) {
            for (boolean krishna : new boolean[]{false, true}) {
                EkadashiCalculator.Result r =
                        EkadashiCalculator.compute(lun, krishna, DELHI_LAT, DELHI_LON, DELHI);
                String key = "ekadashi." + k + "." + (krishna ? "krishna" : "shukla");
                g.put(key + ".fastDay", r.fastDay.toString());
                g.put(key + ".display", String.valueOf(r.displayInstant.toInstant().toEpochMilli()));
                g.put(key + ".tithiStart", String.valueOf(r.tithiStart.toInstant().toEpochMilli()));
                g.put(key + ".tithiEnd", String.valueOf(r.tithiEnd.toInstant().toEpochMilli()));
            }
            lun = Meeus.elongationCrossingInLunation(lun, 360);
        }
        return g;
    }

    private static void putSunrise(Map<String, String> g, String key, LocalDate date,
                                   ZoneId zone, double lat, double lon) {
        ZonedDateTime sr = Sunrise.sunrise(date, zone, lat, lon);
        g.put(key, sr == null ? "null" : String.valueOf(sr.toInstant().toEpochMilli()));
    }

    private void verifyGoldens(Map<String, String> golden, Map<String, String> now) {
        for (Map.Entry<String, String> e : new TreeMap<String, String>(now).entrySet()) {
            String key = e.getKey();
            String actual = e.getValue();
            String want = golden.get(key);
            checks++;
            if (want == null) {
                problems.append("  FAIL: no golden for key ").append(key)
                        .append(" (new=").append(actual).append(")\n");
                continue;
            }
            if (!goldenEquals(key, want, actual)) {
                problems.append("  FAIL: ").append(key)
                        .append(" golden=").append(want).append(" now=").append(actual).append('\n');
            }
        }
        for (String key : golden.keySet()) {
            if (!now.containsKey(key)) {
                checks++;
                problems.append("  FAIL: golden key missing from current run: ").append(key).append('\n');
            }
        }
    }

    private static boolean goldenEquals(String key, String want, String actual) {
        if (want.equals(actual)) {
            return true;
        }
        if (isLongKey(key)) {
            try {
                return Math.abs(Long.parseLong(want) - Long.parseLong(actual)) <= TOL_MILLIS;
            } catch (NumberFormatException nfe) {
                return false;
            }
        }
        double tol;
        if (key.startsWith("moon.dist") || key.startsWith("sun.dist")) {
            tol = TOL_DIST_KM;
        } else if (key.contains("illum")) {
            tol = TOL_FRAC;
        } else if (key.startsWith("deltaT") || key.contains("eot")) {
            tol = 1e-7;
        } else {
            tol = TOL_ANGLE_DEG;
        }
        try {
            return Math.abs(Double.parseDouble(want) - Double.parseDouble(actual)) <= tol;
        } catch (NumberFormatException nfe) {
            return false;
        }
    }

    private static boolean isLongKey(String key) {
        return key.startsWith("lun") || key.startsWith("newmoon.chain")
                || key.startsWith("sunrise.")
                || key.endsWith(".display") || key.endsWith(".tithiStart")
                || key.endsWith(".tithiEnd");
    }

    private static String d(double v) {
        return Double.toString(v);
    }

    private static String ms(double jdUt) {
        return String.valueOf(Meeus.millisFromJulianDay(jdUt));
    }

    private static Map<String, String> readGolden() throws Exception {
        Map<String, String> g = new LinkedHashMap<String, String>();
        InputStream in = GoldenAstroTest.class.getResourceAsStream("/golden-values.txt");
        if (in == null) {
            throw new IllegalStateException("golden-values.txt not found on the test classpath");
        }
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        try {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq > 0) {
                    g.put(line.substring(0, eq), line.substring(eq + 1));
                }
            }
        } finally {
            r.close();
        }
        return g;
    }

    /**
     * Regenerate the golden baseline (only after a reviewed, intentional math
     * change). Usage: {@code java ...GoldenAstroTest <path>} - defaults to
     * {@code app/src/test/resources/golden-values.txt}.
     */
    public static void main(String[] args) throws Exception {
        String path = args.length > 0 ? args[0] : "app/src/test/resources/golden-values.txt";
        Map<String, String> g = computeGoldens();
        PrintWriter w = new PrintWriter(new FileWriter(path));
        try {
            w.println("# MoonShaman astro golden values - regenerate with GoldenAstroTest.main");
            w.println("# Do not hand-edit; a diff here means the astronomy output changed.");
            for (Map.Entry<String, String> e : g.entrySet()) {
                w.println(e.getKey() + "=" + e.getValue());
            }
        } finally {
            w.close();
        }
        System.out.println("Captured " + g.size() + " golden values -> " + path);
    }
}
