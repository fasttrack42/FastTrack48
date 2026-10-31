#!/bin/sh
# Print the app version for now, or for the Unix time given as $1:  YY.MMDD.HH
#
# The time elapsed since 2012-01-23 00:00 Rome time, read as a date after
# 1970-01-01 00:00 UTC: whole years, then the month and day of that date (so
# elapsed months + 1 and days + 1), then its hour. 14.1003.10 is 14 years,
# 9 months, 2 days and 10 hours in. This is the original generateVersion():
#
#   const diffDate = new Date(new Date() - new Date(2012, 0, 23));
#   year  = diffDate.getUTCFullYear() - 1970, month = getUTCMonth() + 1,
#   day   = getUTCDate(),                     hour  = getUTCHours()
#
# with its reference pinned to the instant it meant (local midnight in Rome,
# 23:00 UTC the day before), so CI in UTC and a laptop anywhere agree. The
# fields only ever grow with time, so the code (the version without its dots)
# rises with every hour, and the year field turns over by itself (to 15 around
# 23 January 2027) instead of wrapping like a calendar month would.
set -eu

ref=1327273200   # 2012-01-23T00:00:00+01:00
now=${1:-$(date -u +%s)}
case $now in '' | *[!0-9]*) echo "version: $now: want Unix seconds" >&2; exit 2 ;; esac
[ "$now" -ge "$ref" ] || { echo "version: $now is before 2012-01-23" >&2; exit 2; }
elapsed=$((now - ref))

# GNU date (CI) takes -d @secs, BSD date (macOS) -r secs.
at() { date -u -d "@$elapsed" "+$1" 2>/dev/null || date -u -r "$elapsed" "+$1"; }

years=$(($(at %Y) - 1970))
# Month, day and hour stay strings: "08" and "09" are not octal numbers to sh.
printf '%02d.%s%s.%s\n' "$years" "$(at %m)" "$(at %d)" "$(at %H)"
