package com.legbehindneck.fasttrack48.lunar.astro;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * External anchors for the observance rules: every fast day, Gauna day,
 * Parana window, Purnima and Amavasya day below was read from Drik Panchang
 * (drikpanchang.com Smartha / ISKCON / Purnima / Amavasya lists and per-Ekadashi pages,
 * fetched 2026-10-29) for the stated city and year.
 */
public class DrikAnchorsTest {

    private static final class City {
        final String name;
        final double lat;
        final double lon;
        final ZoneId zone;

        City(String name, double lat, double lon, String zone) {
            this.name = name;
            this.lat = lat;
            this.lon = lon;
            this.zone = ZoneId.of(zone);
        }
    }

    private static final City DELHI = new City("New Delhi", 28.6139, 77.2090, "Asia/Kolkata");
    private static final City NEW_YORK = new City("New York", 40.7143, -74.0060, "America/New_York");
    private static final City LONDON = new City("London", 51.5085, -0.1257, "Europe/London");

    /**
     * Drik prints whole minutes. Against JPL Horizons this theory's tithi instants
     * run -25..+62 s and Drik's printed minute (+30 s) -70..-2 s, so a window edge
     * can sit up to ~2.2 min after Drik's minute midpoint; 150 s covers that band.
     */
    private static final long PARANA_TOL_MS = 150_000L;

    // ---- Smarta fast days and Gauna days (Drik "Ekadashi" lists) ----------------

    @Test
    public void smartaFastDays_newDelhi2026() {
        smarta(DELHI, 2026,
                "01-14 01-29 02-13 02-27 03-15 03-29 04-13 04-27 05-13 05-27 06-11 06-25 "
                        + "07-10 07-25 08-09 08-23 09-07 09-22 10-06 10-22 11-05 11-20 12-04 12-20",
                "07-11 11-21");
    }

    @Test
    public void smartaFastDays_newDelhi2027() {
        smarta(DELHI, 2027,
                "01-03 01-18 02-02 02-17 03-04 03-18 04-02 04-17 05-02 05-16 06-01 06-14 06-30 "
                        + "07-14 07-29 08-12 08-28 09-11 09-26 10-11 10-25 11-10 11-24 12-09 12-23",
                "01-19 07-30");
    }

    @Test
    public void smartaFastDays_newYork2026() {
        smarta(NEW_YORK, 2026,
                "01-14 01-28 02-12 02-27 03-14 03-28 04-13 04-27 05-12 05-26 06-11 06-25 "
                        + "07-10 07-24 08-08 08-23 09-06 09-22 10-06 10-21 11-04 11-20 12-04 12-19",
                "09-07 12-20");
    }

    @Test
    public void smartaFastDays_london2029() {
        smarta(LONDON, 2029,
                "01-10 01-26 02-08 02-24 03-10 03-26 04-09 04-24 05-09 05-24 06-07 06-22 "
                        + "07-07 07-21 08-06 08-19 09-04 09-18 10-03 10-17 11-02 11-16 12-01 12-16 12-31",
                "10-04");
    }

    // ---- Smarta Parana (Drik per-Ekadashi pages) ---------------------------------

    @Test
    public void smartaParana_matchesDrikPages() {
        // Morning window (Indira 2026), afternoon windows (Nirjala 2027 after a long
        // Hari Vasara; Prabodhini 2026 kshaya; Pausha Putrada 2027 and Aja 2026
        // Trisparsha) and the Gauna Paranas where Dvadashi ends before sunrise.
        smartaParana(DELHI, "2026-10-06", "06:17", "08:38", false);
        smartaParana(DELHI, "2027-06-14", "13:45", "16:33", false);
        smartaParana(DELHI, "2026-11-20", "13:11", "15:18", false);
        smartaParana(DELHI, "2026-11-21", "06:49", "08:56", true);
        smartaParana(DELHI, "2027-01-18", "13:35", "15:42", false);
        smartaParana(DELHI, "2027-01-19", "07:14", "09:21", true);
        smartaParana(NEW_YORK, "2026-09-06", "14:11", "16:45", false);
        smartaParana(NEW_YORK, "2026-09-07", "06:30", "09:03", true);
    }

    // ---- Vaishnava (ISKCON) fast days and Parana (Drik ISKCON lists) ---------------

    @Test
    public void vaishnava_newDelhi2026() {
        // 2026-06-25 (Nirjala) is left out on purpose: that Purnima ends 05:26 (Drik)
        // / 05:27 (this theory) against a 05:26 sunrise, a knife-edge Paksavardhini.
        vaishnava(DELHI, 2026, "01-14|07:15-10:45 01-29|07:10-10:47 02-13|07:00-10:44 "
                + "02-27|06:47-10:38 03-15|06:30-09:40 03-29|06:14-07:09 04-13|06:54-10:13 "
                + "04-27|05:43-10:07 05-13|05:31-10:02 05-27|05:25-07:56 06-11|05:23-10:02 "
                + "07-11|05:32-10:08 07-25|05:39-10:11 08-09|05:47-08:00 08-24|05:55-06:20 "
                + "09-07|06:02-10:13 09-22|06:10-10:12 10-06|06:17-10:12 10-22|06:27-10:12 "
                + "11-05|06:37-10:15 11-21|06:49-10:21 12-04|06:59-10:28 12-20|07:10-10:36");
    }

    @Test
    public void vaishnava_newDelhi2027() {
        vaishnava(DELHI, 2027, "01-03|07:15-10:42 01-19|07:14-10:46 02-02|07:08-10:46 "
                + "02-17|06:57-10:43 03-04|06:42-09:53 03-18|07:12-10:28 04-02|09:15-10:20 "
                + "04-17|05:53-07:42 05-02|05:39-10:05 05-16|05:29-10:02 06-01|05:24-08:28 "
                + "06-15|05:23-10:02 06-30|05:27-10:06 07-14|05:33-10:09 07-30|05:42-10:12 "
                + "08-12|09:34-10:14 08-28|05:57-09:35 09-11|06:04-10:13 09-26|06:12-10:12 "
                + "10-11|06:20-10:12 10-26|06:29-10:13 11-10|06:40-10:07 11-24|06:51-10:22 "
                + "12-09|09:13-10:30 12-23|07:11-10:37");
    }

    @Test
    public void vaishnava_newYork2026() {
        vaishnava(NEW_YORK, 2026, "01-14|07:18-09:46 01-29|07:08-10:29 02-12|10:19-10:24 "
                + "02-27|06:31-10:13 03-14|07:08-11:06 03-28|06:44-10:56 04-13|06:19-10:44 "
                + "04-27|05:59-09:21 05-13|05:40-10:28 05-26|05:30-10:25 06-11|05:24-10:06 "
                + "06-25|05:26-10:28 07-10|05:35-10:33 07-24|08:40-10:37 08-09|06:02-10:41 "
                + "08-23|06:15-10:44 09-07|06:30-10:46 09-22|06:45-10:47 10-06|06:59-10:49 "
                + "10-21|11:14-after 11-04|06:31-09:57 11-20|06:50-10:04 12-04|07:05-10:13 "
                + "12-20|07:16-10:22");
    }

    @Test
    public void vaishnava_london2029() {
        vaishnava(LONDON, 2029, "01-10|08:02-10:47 01-26|07:46-10:44 02-09|07:23-10:38 "
                + "02-24|09:15-10:27 03-10|06:23-10:15 03-26|06:47-11:00 04-09|06:15-10:47 "
                + "04-25|05:42-10:33 05-09|05:16-07:59 05-24|04:55-05:25 06-08|04:44-10:15 "
                + "06-22|04:44-10:16 07-07|04:54-10:21 07-21|05:10-10:28 08-06|05:34-06:18 "
                + "08-19|08:50-10:40 09-04|06:20-10:46 09-18|06:42-10:50 10-04|07:08-10:55 "
                + "10-17|08:33-11:00 11-02|06:58-10:08 11-16|07:22-10:18 12-02|07:47-10:29 "
                + "12-16|08:01-10:38 12-31|08:06-10:45");
    }

    @Test
    public void vaishnava_mahadvadashiLabels() {
        label(DELHI, "2026-08-24", EkadashiCalculator.Mahadvadashi.VYANJULI);
        label(DELHI, "2027-01-19", EkadashiCalculator.Mahadvadashi.TRISPRSA);
        label(DELHI, "2027-03-04", EkadashiCalculator.Mahadvadashi.UNMILANI);
        label(DELHI, "2027-06-15", EkadashiCalculator.Mahadvadashi.PAKSAVARDHINI);
        label(NEW_YORK, "2026-01-14", EkadashiCalculator.Mahadvadashi.UNMILANI);
        label(NEW_YORK, "2026-09-07", EkadashiCalculator.Mahadvadashi.TRISPRSA);
        label(NEW_YORK, "2026-12-20", EkadashiCalculator.Mahadvadashi.TRISPRSA);
        label(LONDON, "2029-05-09", EkadashiCalculator.Mahadvadashi.UNMILANI);
        label(LONDON, "2029-06-08", EkadashiCalculator.Mahadvadashi.PAKSAVARDHINI);
        label(LONDON, "2029-10-04", EkadashiCalculator.Mahadvadashi.TRISPRSA);
    }

    // ---- Purnima vrat and Purnima tithi days (Drik "Purnima" lists) -----------------

    @Test
    public void purnima_newDelhi2026() {
        purnima(DELHI, 2026,
                "01-03 02-01 03-03 04-01 05-01 05-30 06-29 07-29 08-27 09-26 10-25 11-24 12-23",
                "01-03 02-01 03-03 04-02 05-01 05-31 06-29 07-29 08-28 09-26 10-26 11-24 12-23");
    }

    @Test
    public void purnima_newYork2026() {
        purnima(NEW_YORK, 2026,
                "01-02 02-01 03-02 04-01 04-30 05-30 06-29 07-28 08-27 09-25 10-25 11-24 12-23",
                "01-02 02-01 03-03 04-01 05-01 05-30 06-29 07-29 08-27 09-26 10-25 11-24 12-23");
    }

    @Test
    public void purnima_london2029() {
        purnima(LONDON, 2029,
                "01-29 02-28 03-29 04-28 05-27 06-25 07-25 08-23 09-22 10-21 11-20 12-20",
                "01-29 02-28 03-29 04-28 05-27 06-25 07-25 08-23 09-22 10-22 11-20 12-20");
    }

    // ---- Darsha Amavasya and Amavasya days (Drik "Amavasya" lists) ----------------

    @Test
    public void amavasya_newDelhi2026() {
        amavasya(DELHI, 2026,
                "01-18 02-17 03-18 04-17 05-16 06-14 07-14 08-12 09-10 10-10 11-08 12-08",
                "01-18 02-17 03-19 04-17 05-16 06-15 07-14 08-12 09-11 10-10 11-09 12-08");
    }

    @Test
    public void amavasya_newDelhi2027() {
        amavasya(DELHI, 2027,
                "01-07 02-06 03-07 04-06 05-06 06-04 07-03 08-02 08-31 09-29 10-29 11-27 12-27",
                "01-07 02-06 03-08 04-06 05-06 06-04 07-04 08-02 08-31 09-30 10-29 11-28 12-27");
    }

    @Test
    public void amavasya_newYork2026() {
        // 17 Jan starts 13:33 but covers only half that day's Aparahna -> Darsha is 18 Jan;
        // 11 Aug starts 16:22 and 12 Aug has no Aparahna Amavasya -> Darsha is 11 Aug.
        amavasya(NEW_YORK, 2026,
                "01-18 02-16 03-18 04-16 05-16 06-14 07-13 08-11 09-10 10-09 11-08 12-08",
                "01-18 02-17 03-18 04-17 05-16 06-14 07-14 08-12 09-10 10-10 11-08 12-08");
    }

    @Test
    public void amavasya_london2026() {
        amavasya(LONDON, 2026,
                "01-18 02-16 03-18 04-16 05-16 06-14 07-13 08-12 09-10 10-10 11-08 12-08",
                "01-18 02-17 03-18 04-17 05-16 06-14 07-14 08-12 09-10 10-10 11-08 12-08");
    }

    // ---- helpers ------------------------------------------------------------------

    private static void smarta(City c, int year, String primary, String gauna) {
        Set<LocalDate> gotPrimary = new TreeSet<LocalDate>();
        Set<LocalDate> gotGauna = new TreeSet<LocalDate>();
        for (double nm : lunations(c, year)) {
            for (boolean krishna : new boolean[]{false, true}) {
                EkadashiCalculator.Result r =
                        EkadashiCalculator.compute(nm, krishna, c.lat, c.lon, c.zone);
                if (r.fastDay.getYear() != year) {
                    continue;
                }
                gotPrimary.add(r.fastDay);
                if (r.gaunaDay != null) {
                    gotGauna.add(r.gaunaDay);
                }
                assertNotNull(c.name + " Smarta Parana " + r.fastDay, r.parana);
            }
        }
        assertEquals(c.name + " " + year + " Smarta fast days", dates(year, primary), gotPrimary);
        assertEquals(c.name + " " + year + " Gauna days", dates(year, gauna), gotGauna);
    }

    private static void smartaParana(City c, String day, String from, String to, boolean gauna) {
        LocalDate want = LocalDate.parse(day);
        EkadashiCalculator.Parana p = null;
        for (double nm : lunations(c, want.getYear())) {
            for (boolean krishna : new boolean[]{false, true}) {
                EkadashiCalculator.Result r =
                        EkadashiCalculator.compute(nm, krishna, c.lat, c.lon, c.zone);
                if (!gauna && r.fastDay.equals(want)) {
                    p = r.parana;
                } else if (gauna && want.equals(r.gaunaDay)) {
                    p = r.gaunaParana;
                }
            }
        }
        assertNotNull(c.name + " " + day + " Parana", p);
        assertWindow(c.name + " " + day, want.plusDays(1), from, to, p, c.zone);
    }

    private static void vaishnava(City c, int year, String expected) {
        Map<LocalDate, String> want = new TreeMap<LocalDate, String>();
        for (String token : expected.split(" ")) {
            String[] kv = token.split("\\|");
            want.put(LocalDate.parse(year + "-" + kv[0]), kv[1]);
        }
        Map<LocalDate, EkadashiCalculator.Parana> got = new TreeMap<LocalDate, EkadashiCalculator.Parana>();
        for (double nm : lunations(c, year)) {
            for (boolean krishna : new boolean[]{false, true}) {
                EkadashiCalculator.Result r =
                        EkadashiCalculator.computeVaishnava(nm, krishna, c.lat, c.lon, c.zone);
                if (r.fastDay.getYear() == year) {
                    got.put(r.fastDay, r.parana);
                }
            }
        }
        for (Map.Entry<LocalDate, String> e : want.entrySet()) {
            String label = c.name + " ISKCON " + e.getKey();
            assertTrue(label + " fast day missing; got " + got.keySet(), got.containsKey(e.getKey()));
            String[] window = e.getValue().split("-");
            assertWindow(label, e.getKey().plusDays(1), window[0],
                    "after".equals(window[1]) ? null : window[1], got.get(e.getKey()), c.zone);
        }
    }

    private static void label(City c, String day, EkadashiCalculator.Mahadvadashi expected) {
        LocalDate want = LocalDate.parse(day);
        EkadashiCalculator.Mahadvadashi found = null;
        boolean seen = false;
        for (double nm : lunations(c, want.getYear())) {
            for (boolean krishna : new boolean[]{false, true}) {
                EkadashiCalculator.Result r =
                        EkadashiCalculator.computeVaishnava(nm, krishna, c.lat, c.lon, c.zone);
                if (r.fastDay.equals(want)) {
                    found = r.mahadvadashi;
                    seen = true;
                }
            }
        }
        assertTrue(c.name + " ISKCON fast on " + day, seen);
        assertEquals(c.name + " " + day + " Mahadvadashi", expected, found);
    }

    private static void purnima(City c, int year, String vrat, String tithiDay) {
        Set<LocalDate> gotVrat = new TreeSet<LocalDate>();
        Set<LocalDate> gotDay = new TreeSet<LocalDate>();
        for (double nm : lunations(c, year)) {
            EkadashiCalculator.Result v = EkadashiCalculator.computePurnimaVrat(nm, c.lat, c.lon, c.zone);
            EkadashiCalculator.Result d = EkadashiCalculator.computePurnima(nm, c.lat, c.lon, c.zone);
            if (v.fastDay.getYear() == year) {
                gotVrat.add(v.fastDay);
            }
            if (d.fastDay.getYear() == year) {
                gotDay.add(d.fastDay);
            }
        }
        assertEquals(c.name + " " + year + " Purnima vrat days", dates(year, vrat), gotVrat);
        assertEquals(c.name + " " + year + " Purnima days", dates(year, tithiDay), gotDay);
    }

    private static void amavasya(City c, int year, String darsha, String tithiDay) {
        Set<LocalDate> gotDarsha = new TreeSet<LocalDate>();
        Set<LocalDate> gotDay = new TreeSet<LocalDate>();
        for (double nm : lunations(c, year)) {
            EkadashiCalculator.Result v = EkadashiCalculator.computeDarshaAmavasya(nm, c.lat, c.lon, c.zone);
            EkadashiCalculator.Result d = EkadashiCalculator.computeAmavasya(nm, c.lat, c.lon, c.zone);
            if (v.fastDay.getYear() == year) {
                gotDarsha.add(v.fastDay);
            }
            if (d.fastDay.getYear() == year) {
                gotDay.add(d.fastDay);
            }
        }
        assertEquals(c.name + " " + year + " Darsha Amavasya days", dates(year, darsha), gotDarsha);
        assertEquals(c.name + " " + year + " Amavasya days", dates(year, tithiDay), gotDay);
    }

    private static void assertWindow(String label, LocalDate day, String from, String to,
                                     EkadashiCalculator.Parana p, ZoneId zone) {
        assertNotNull(label + " Parana", p);
        long wantFrom = minuteMidpoint(day, from, zone);
        long gotFrom = p.start.toInstant().toEpochMilli();
        assertTrue(label + " Parana from " + from + ", got " + p.start,
                Math.abs(gotFrom - wantFrom) <= PARANA_TOL_MS);
        if (to == null) {
            assertTrue(label + " Parana should be open-ended, got " + p.end, p.end == null);
        } else {
            assertNotNull(label + " Parana end", p.end);
            long wantTo = minuteMidpoint(day, to, zone);
            long gotTo = p.end.toInstant().toEpochMilli();
            assertTrue(label + " Parana to " + to + ", got " + p.end,
                    Math.abs(gotTo - wantTo) <= PARANA_TOL_MS);
        }
    }

    private static long minuteMidpoint(LocalDate day, String hhmm, ZoneId zone) {
        return ZonedDateTime.of(day, LocalTime.parse(hhmm), zone).toInstant().toEpochMilli() + 30_000L;
    }

    /** New moons from mid-November of the year before to February of the year after. */
    private static List<Double> lunations(City c, int year) {
        List<Double> out = new ArrayList<Double>();
        long from = LocalDate.of(year, 1, 1).atStartOfDay(c.zone).minusDays(45).toInstant().toEpochMilli();
        double nm = Meeus.previousNewMoon(Meeus.julianDayFromMillis(from));
        for (int i = 0; i < 16; i++) {
            out.add(nm);
            nm = Meeus.elongationCrossingInLunation(nm, 360.0);
        }
        return out;
    }

    private static Set<LocalDate> dates(int year, String monthDays) {
        Set<LocalDate> out = new TreeSet<LocalDate>();
        for (String md : monthDays.trim().split("\\s+")) {
            if (!md.isEmpty()) {
                out.add(LocalDate.parse(year + "-" + md));
            }
        }
        return out;
    }
}
