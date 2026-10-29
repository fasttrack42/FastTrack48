#!/usr/bin/env python3
"""Generate app/src/main/java/.../lunar/ZoneLocations.kt from tzdata's zone.tab.

Each IANA time zone maps to its principal city (zone.tab column 2, ISO 6709).
FastTrack uses that point for sunrise when the user has not shared a location.

Usage: scripts/gen_zone_locations.py [path/to/zone.tab]   (default: /usr/share/zoneinfo/zone.tab)
"""
import os
import re
import sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(
    os.path.dirname(SCRIPT_DIR),
    "app/src/main/java/com/legbehindneck/fasttrack48/lunar/ZoneLocations.kt",
)

# India spans ~30 degrees of longitude in one zone. Kolkata (the zone.tab city) sits at
# its eastern edge; central India (Nagpur) halves the fast-day errors across the country
# (simulated 2026-2035 over ten Indian cities: 2.4% -> 1.2% of Ekadashis).
OVERRIDES = {
    "Asia/Kolkata": (21.1458, 79.0882),
}

# Legacy names (tzdata "backward" links) that devices still report.
ALIASES = {
    "Asia/Calcutta": "Asia/Kolkata",
    "Asia/Katmandu": "Asia/Kathmandu",
    "Asia/Saigon": "Asia/Ho_Chi_Minh",
    "Asia/Rangoon": "Asia/Yangon",
    "Asia/Dacca": "Asia/Dhaka",
    "Asia/Thimbu": "Asia/Thimphu",
    "Asia/Ulan_Bator": "Asia/Ulaanbaatar",
    "Asia/Macao": "Asia/Macau",
    "Asia/Ujung_Pandang": "Asia/Makassar",
    "Asia/Chongqing": "Asia/Shanghai",
    "Asia/Chungking": "Asia/Shanghai",
    "Asia/Harbin": "Asia/Shanghai",
    "Asia/Kashgar": "Asia/Urumqi",
    "Asia/Tel_Aviv": "Asia/Jerusalem",
    "Asia/Istanbul": "Europe/Istanbul",
    "Asia/Ashkhabad": "Asia/Ashgabat",
    "Europe/Kiev": "Europe/Kyiv",
    "Europe/Uzhgorod": "Europe/Kyiv",
    "Europe/Zaporozhye": "Europe/Kyiv",
    "Europe/Belfast": "Europe/London",
    "Atlantic/Faeroe": "Atlantic/Faroe",
    "Africa/Asmera": "Africa/Asmara",
    "Africa/Timbuktu": "Africa/Bamako",
    "America/Buenos_Aires": "America/Argentina/Buenos_Aires",
    "America/Indianapolis": "America/Indiana/Indianapolis",
    "America/Louisville": "America/Kentucky/Louisville",
    "America/Godthab": "America/Nuuk",
    "America/Montreal": "America/Toronto",
    "Pacific/Truk": "Pacific/Chuuk",
    "Pacific/Ponape": "Pacific/Pohnpei",
    "Pacific/Enderbury": "Pacific/Kanton",
    "US/Eastern": "America/New_York",
    "US/Central": "America/Chicago",
    "US/Mountain": "America/Denver",
    "US/Pacific": "America/Los_Angeles",
    "US/Alaska": "America/Anchorage",
    "US/Hawaii": "Pacific/Honolulu",
    "US/Arizona": "America/Phoenix",
    "Navajo": "America/Denver",
    "Canada/Eastern": "America/Toronto",
    "Canada/Central": "America/Winnipeg",
    "Canada/Mountain": "America/Edmonton",
    "Canada/Pacific": "America/Vancouver",
    "Australia/ACT": "Australia/Sydney",
    "Australia/NSW": "Australia/Sydney",
    "Australia/Victoria": "Australia/Melbourne",
    "Australia/Queensland": "Australia/Brisbane",
    "Australia/West": "Australia/Perth",
    "Australia/South": "Australia/Adelaide",
    "Australia/North": "Australia/Darwin",
    "Australia/Tasmania": "Australia/Hobart",
    "Brazil/East": "America/Sao_Paulo",
    "Mexico/General": "America/Mexico_City",
    "Chile/Continental": "America/Santiago",
    "GB": "Europe/London",
    "Eire": "Europe/Dublin",
    "Portugal": "Europe/Lisbon",
    "Poland": "Europe/Warsaw",
    "Turkey": "Europe/Istanbul",
    "Iceland": "Atlantic/Reykjavik",
    "Egypt": "Africa/Cairo",
    "Libya": "Africa/Tripoli",
    "Iran": "Asia/Tehran",
    "Israel": "Asia/Jerusalem",
    "Singapore": "Asia/Singapore",
    "Hongkong": "Asia/Hong_Kong",
    "Japan": "Asia/Tokyo",
    "ROK": "Asia/Seoul",
    "PRC": "Asia/Shanghai",
    "ROC": "Asia/Taipei",
    "NZ": "Pacific/Auckland",
    "Cuba": "America/Havana",
    "Jamaica": "America/Jamaica",
    "Kwajalein": "Pacific/Kwajalein",
}

ISO6709 = re.compile(r"^([+-])(\d{2})(\d{2})(\d{2})?([+-])(\d{3})(\d{2})(\d{2})?$")


def parse_iso6709(s):
    m = ISO6709.match(s)
    if not m:
        raise ValueError("bad ISO 6709 coordinate: " + s)
    lat = int(m.group(2)) + int(m.group(3)) / 60 + int(m.group(4) or 0) / 3600
    lon = int(m.group(6)) + int(m.group(7)) / 60 + int(m.group(8) or 0) / 3600
    return (-lat if m.group(1) == "-" else lat, -lon if m.group(5) == "-" else lon)


def main():
    src = sys.argv[1] if len(sys.argv) > 1 else "/usr/share/zoneinfo/zone.tab"
    table = {}
    with open(src, encoding="utf-8") as f:
        for line in f:
            if line.startswith("#") or not line.strip():
                continue
            cols = line.rstrip("\n").split("\t")
            table[cols[2]] = parse_iso6709(cols[1])
    for zone, point in OVERRIDES.items():
        if zone not in table:
            sys.exit("override for unknown zone: " + zone)
        table[zone] = point
    for alias, target in ALIASES.items():
        if target not in table:
            sys.exit("alias %s -> unknown zone %s" % (alias, target))

    rows = "\n".join("%s %.4f %.4f" % (z, p[0], p[1]) for z, p in sorted(table.items()))
    aliases = "\n".join('\t\t"%s" to "%s",' % (a, t) for a, t in sorted(ALIASES.items()))
    kotlin = f'''package com.legbehindneck.fasttrack48.lunar

// GENERATED by scripts/gen_zone_locations.py from tzdata zone.tab - do not edit by hand.

/**
 * A representative point for each IANA time zone: the zone's principal city from
 * tzdata, except Asia/Kolkata, which uses central India (Nagpur) because Kolkata sits
 * at the eastern edge of a zone ~30 degrees wide. Sunrise is computed for this point
 * when the user has not shared a location.
 */
internal object ZoneLocations {{

	fun lookup(zoneId: String): GeoPoint? = table[zoneId] ?: ALIASES[zoneId]?.let {{ table[it] }}

	private val table: Map<String, GeoPoint> by lazy {{
		DATA.lineSequence()
			.filter {{ it.isNotBlank() }}
			.associate {{ line ->
				val (zone, lat, lon) = line.split(' ')
				zone to GeoPoint(lat.toDouble(), lon.toDouble())
			}}
	}}

	private val ALIASES = mapOf(
{aliases}
	)

	private const val DATA = """
{rows}
"""
}}
'''
    with open(OUT, "w", encoding="utf-8") as f:
        f.write(kotlin)
    print("wrote %d zones, %d aliases -> %s" % (len(table), len(ALIASES), OUT))


if __name__ == "__main__":
    main()
