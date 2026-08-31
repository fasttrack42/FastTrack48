package com.legbehindneck.fasttrack48.screens.log

import androidx.compose.ui.graphics.Color
import com.legbehindneck.fasttrack48.data.Stages
import com.legbehindneck.fasttrack48.data.log.FastingLogEntry
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * How the logbook is projected onto calendar days, shared by the month grid and the year
 * strip. Both views have to agree on which days a fast touches; computing that twice would
 * be two chances to disagree.
 */

/** Per-day coverage precomputed for a set of entries. */
class CalendarCoverage(
	val byDay: Map<LocalDate, List<FastingLogEntry>>,
	val endDateById: Map<Int, LocalDate>,
)

/**
 * Which fasts were active on each calendar day. A fast spans every day from its start day
 * through the last day it was still running, so a multi-day fast highlights the whole range
 * rather than only the day it began on.
 */
@ExperimentalTime
fun coverageByDay(entries: List<FastingLogEntry>, tz: TimeZone): CalendarCoverage {
	val byDay = HashMap<LocalDate, MutableList<FastingLogEntry>>()
	val endDateById = HashMap<Int, LocalDate>()
	for (e in entries) {
		val startDate = e.start.date
		val endInstant = e.start.toInstant(tz).plus(e.length)
		// Last day the fast was actually active (a fast ending at 00:00 does not claim
		// that day), never earlier than the start day.
		val endDate = maxOf(startDate, (endInstant - 1.milliseconds).toLocalDateTime(tz).date)
		endDateById[e.id] = endDate
		var d = startDate
		while (d <= endDate) {
			byDay.getOrPut(d) { mutableListOf() }.add(e)
			d = d.plus(1, DateTimeUnit.DAY)
		}
	}
	return CalendarCoverage(byDay, endDateById)
}

/** Hours fasted on one calendar day, and the longest fast that touched it. */
data class DayLoad(
	val hours: Double,
	val color: Color,
	val longestFast: Duration,
)

/**
 * Hours fasted per calendar day inside [window] — the year strip's bar heights.
 *
 * Day boundaries come from [atStartOfDayIn] rather than a flat 24h, so a clock change is
 * handled honestly: a 23-hour spring-forward day cannot report 24 hours fasted. Overlapping
 * fasts on one day are summed and capped at 24; the colour is that of the longest fast
 * touching the day, matching how the month grid picks a day's hue.
 *
 * [activeFastStart], when set, enters as a synthetic fast running to [now], so a fast still
 * in progress appears here exactly as it does in the month grid.
 */
@ExperimentalTime
fun dailyFastedHours(
	entries: List<FastingLogEntry>,
	activeFastStart: Instant?,
	now: Instant,
	window: ClosedRange<LocalDate>,
	tz: TimeZone,
): Map<LocalDate, DayLoad> {
	if (window.start > window.endInclusive) return emptyMap()

	class Acc(var hours: Double = 0.0, var longest: Duration = Duration.ZERO)

	val acc = HashMap<LocalDate, Acc>()

	fun add(from: Instant, until: Instant, length: Duration) {
		if (until <= from) return
		// Clamp the walk to the window before iterating: a corrupt entry claiming years
		// of coverage must not turn into an unbounded loop.
		val firstDay = maxOf(from.toLocalDateTime(tz).date, window.start)
		val lastDay = minOf((until - 1.milliseconds).toLocalDateTime(tz).date, window.endInclusive)
		var day = firstDay
		while (day <= lastDay) {
			val dayStart = day.atStartOfDayIn(tz)
			val dayEnd = day.plus(1, DateTimeUnit.DAY).atStartOfDayIn(tz)
			val overlap = minOf(until, dayEnd) - maxOf(from, dayStart)
			if (overlap > Duration.ZERO) {
				val a = acc.getOrPut(day) { Acc() }
				a.hours += overlap.toDouble(DurationUnit.HOURS)
				if (length > a.longest) a.longest = length
			}
			day = day.plus(1, DateTimeUnit.DAY)
		}
	}

	for (e in entries) {
		val start = e.start.toInstant(tz)
		add(start, start + e.length, e.length)
	}
	activeFastStart?.let { start -> add(start, now, now - start) }

	return acc.mapValues { (_, a) ->
		DayLoad(
			hours = a.hours.coerceIn(0.0, 24.0),
			color = stageColorForLength(a.longest),
			longestFast = a.longest,
		)
	}
}

// Calm, desaturated calendar tones per phase - warmth/renewal rather than alarm
// (glucose -> fat burn -> ketosis -> autophagy -> optimal). Applied at low opacity in the
// month grid, at full strength in the year strip where the bars are only a pixel wide.
// One hue per fast.
val calendarStageColors = listOf(
	Color(0xFF9AA7B3), // Glucose - quiet slate
	Color(0xFF6FBF8B), // Fat burn - soft green
	Color(0xFFE0A94E), // Ketosis - warm amber
	Color(0xFFE08A6B), // Autophagy - soft coral
	Color(0xFFB98AD8), // Optimal autophagy - soft violet
)

/** The wash for a fast of this length - the same scale whether it has ended or not. */
fun stageColorForLength(length: Duration): Color {
	val lenHours = length.toDouble(DurationUnit.HOURS)
	val stage = Stages.phases.lastOrNull { lenHours >= it.hours } ?: Stages.phases.first()
	val stageIndex = Stages.phases.indexOf(stage).coerceAtLeast(0)
	return calendarStageColors.getOrElse(stageIndex) { calendarStageColors.last() }
}
