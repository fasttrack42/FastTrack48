package com.legbehindneck.fasttrack48.utils

import android.text.format.DateFormat
import androidx.compose.runtime.staticCompositionLocalOf
import com.legbehindneck.fasttrack48.data.settings.DateStyle
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * The active [DateStyle] for the current composition. Provided once near the app
 * root and refreshed when the user changes it in Settings — no OS-locale listeners,
 * and every UI date/time reads it here.
 */
val LocalDateStyle = staticCompositionLocalOf { DateStyle.OPTIMIZED_COMPACT }

/**
 * One place that turns instants into user-facing text. Everything is locale-aware:
 * field order comes from the locale (via CLDR skeletons / localized formats), not a
 * hardcoded pattern, so "Jun 1" (en-US), "1 Jun" (en-GB) and "6月1日" (zh) all fall
 * out correctly. The [is24Hour] flag comes from the user's system setting.
 *
 * Formatters are immutable and expensive to build, so they are cached and reused.
 * Machine surfaces (CSV / iCal / JSON export) do NOT use this — they stay ISO.
 */
object AppDateTime {

	private val formatterCache = ConcurrentHashMap<String, DateTimeFormatter>()

	private fun skeletonFormatter(locale: Locale, skeleton: String): DateTimeFormatter =
		formatterCache.getOrPut("s|${locale.toLanguageTag()}|$skeleton") {
			DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
		}

	private fun patternFormatter(locale: Locale, pattern: String): DateTimeFormatter =
		formatterCache.getOrPut("p|${locale.toLanguageTag()}|$pattern") {
			DateTimeFormatter.ofPattern(pattern, locale)
		}

	private fun localizedDateFormatter(locale: Locale, style: FormatStyle): DateTimeFormatter =
		formatterCache.getOrPut("d|${locale.toLanguageTag()}|$style") {
			DateTimeFormatter.ofLocalizedDate(style).withLocale(locale)
		}

	private fun currentYear(): Int = YearMonth.now().year

	private fun dateFormatter(style: DateStyle, year: Int, locale: Locale): DateTimeFormatter =
		when (style) {
			DateStyle.OPTIMIZED_COMPACT ->
				skeletonFormatter(locale, if (year == currentYear()) "MMMd" else "yMMMd")

			DateStyle.OPTIMIZED_WEEKDAY ->
				skeletonFormatter(locale, if (year == currentYear()) "MMMEd" else "yMMMEd")

			DateStyle.SYSTEM_SHORT -> localizedDateFormatter(locale, FormatStyle.SHORT)
			DateStyle.SYSTEM_MEDIUM -> localizedDateFormatter(locale, FormatStyle.MEDIUM)
			DateStyle.SYSTEM_LONG -> localizedDateFormatter(locale, FormatStyle.LONG)
			DateStyle.ISO -> patternFormatter(locale, "uuuu-MM-dd")
		}

	private fun timeFormatter(style: DateStyle, minute: Int, is24Hour: Boolean, locale: Locale): DateTimeFormatter {
		val skeleton = when {
			style == DateStyle.ISO -> return patternFormatter(locale, "HH:mm")
			is24Hour -> "Hm"
			// Optimized styles drop ":00" (12h only; "20" alone would be ambiguous).
			(style == DateStyle.OPTIMIZED_COMPACT || style == DateStyle.OPTIMIZED_WEEKDAY) && minute == 0 -> "h"
			else -> "hm"
		}
		return skeletonFormatter(locale, skeleton)
	}

	private fun datePart(dt: LocalDateTime, style: DateStyle, locale: Locale): String =
		dt.toJavaLocalDateTime().format(dateFormatter(style, dt.year, locale))

	private fun timePart(dt: LocalDateTime, style: DateStyle, is24Hour: Boolean, locale: Locale): String =
		dt.toJavaLocalDateTime().format(timeFormatter(style, dt.minute, is24Hour, locale))

	/** Date only (respects the style; Optimized omits the current year). */
	fun formatDate(dt: LocalDateTime, style: DateStyle, locale: Locale = Locale.getDefault()): String =
		datePart(dt, style, locale)

	/** Date only, from a calendar date. */
	fun formatDate(date: LocalDate, style: DateStyle, locale: Locale = Locale.getDefault()): String =
		date.toJavaLocalDate().format(dateFormatter(style, date.year, locale))

	/** Time only. */
	fun formatTime(dt: LocalDateTime, style: DateStyle, is24Hour: Boolean, locale: Locale = Locale.getDefault()): String =
		timePart(dt, style, is24Hour, locale)

	/** A single date + time, e.g. for a settings sample. */
	fun formatDateTime(
		dt: LocalDateTime,
		style: DateStyle,
		is24Hour: Boolean,
		locale: Locale = Locale.getDefault(),
	): String {
		val date = datePart(dt, style, locale)
		val time = timePart(dt, style, is24Hour, locale)
		return joinDateTime(date, time, style, locale)
	}

	/**
	 * A formatted date and time (or time range) as one phrase, joined as [locale] writes it:
	 * "Thu, Nov 5, 13:59", but "11月5日周四 13:59" — Chinese, Japanese and Korean put a space
	 * there, not a comma (CLDR). ISO keeps its plain space in every language.
	 */
	fun joinDateTime(date: String, time: String, style: DateStyle, locale: Locale): String =
		if (style == DateStyle.ISO || locale.language in SPACE_JOINED_LANGUAGES) "$date $time"
		else "$date, $time"

	private val SPACE_JOINED_LANGUAGES = setOf("zh", "ja", "ko")

	/** Localized month + year for a calendar header, e.g. "June 2025" / "2025年6月". */
	fun formatMonthYear(yearMonth: YearMonth, locale: Locale = Locale.getDefault()): String =
		yearMonth.format(skeletonFormatter(locale, "yMMMM"))

	/** Month + year, abbreviated ("Sep 2025") — for axis headings where width is scarce. */
	fun formatMonthYearShort(yearMonth: YearMonth, locale: Locale = Locale.getDefault()): String =
		yearMonth.format(skeletonFormatter(locale, "yMMM"))

	/**
	 * A fast's window as one heading. Same-day fasts show the date once and the times
	 * as a range; multi-day fasts date both ends. ISO stays fully explicit on each end.
	 */
	fun formatFastRange(
		start: LocalDateTime,
		end: LocalDateTime,
		style: DateStyle,
		is24Hour: Boolean,
		locale: Locale = Locale.getDefault(),
	): String {
		if (style == DateStyle.ISO) {
			return "${formatDateTime(start, style, is24Hour, locale)} → ${formatDateTime(end, style, is24Hour, locale)}"
		}
		val startDate = datePart(start, style, locale)
		val startTime = timePart(start, style, is24Hour, locale)
		val endTime = timePart(end, style, is24Hour, locale)
		return if (start.date == end.date) {
			"$startDate · $startTime – $endTime"
		} else {
			"${joinDateTime(startDate, startTime, style, locale)} → " +
				joinDateTime(datePart(end, style, locale), endTime, style, locale)
		}
	}

	/**
	 * [text] with each doubled full stop reduced to one. A formatted date or time can end in an
	 * abbreviation's period ("Mi., 4. Nov.", "1:59 p. m."), and a sentence that ends on one
	 * takes no second period: "Fastentag: Mi., 4. Nov. Fastenbrechen: …". For text built from
	 * a template that puts a date or time before a full stop. Exactly two dots only, so an
	 * ellipsis written as three is left alone.
	 */
	fun dropDoubledPeriods(text: String): String = text.replace(DOUBLED_PERIOD, ".")

	private val DOUBLED_PERIOD = Regex("""(?<!\.)\.\.(?!\.)""")
}
