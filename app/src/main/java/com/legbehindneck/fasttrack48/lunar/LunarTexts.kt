package com.legbehindneck.fasttrack48.lunar

import android.content.Context
import com.legbehindneck.fasttrack48.R
import com.legbehindneck.fasttrack48.data.settings.DateStyle
import com.legbehindneck.fasttrack48.lunar.astro.EkadashiCalculator.Mahadvadashi
import com.legbehindneck.fasttrack48.utils.AppDateTime
import com.legbehindneck.fasttrack48.utils.shouldUse24HourFormat
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toKotlinLocalDateTime
import java.time.LocalDate
import java.time.ZonedDateTime

/** User-facing text for observance days, shared by the notifications and Settings. */
internal object LunarTexts {

	/** Notification title, e.g. "Ekadashi tomorrow". */
	fun title(context: Context, reminder: PlannedLunarReminder): String {
		val lead = reminder.leadDays
		val (today, tomorrow, inDays) = when (val subject = reminder.subject) {
			is EkadashiDay -> Triple(
				R.string.lunar_ekadashi_today, R.string.lunar_ekadashi_tomorrow, R.plurals.lunar_ekadashi_in_days,
			)
			is MoonDay -> when (subject.phase) {
				MoonPhase.FULL -> when (subject.style) {
					MoonDayStyle.VEDIC -> Triple(
						R.string.lunar_purnima_today, R.string.lunar_purnima_tomorrow, R.plurals.lunar_purnima_in_days,
					)
					MoonDayStyle.ASTRONOMICAL -> Triple(
						R.string.lunar_fullmoon_today, R.string.lunar_fullmoon_tomorrow, R.plurals.lunar_fullmoon_in_days,
					)
				}
				MoonPhase.NEW -> when (subject.style) {
					MoonDayStyle.VEDIC -> Triple(
						R.string.lunar_amavasya_today, R.string.lunar_amavasya_tomorrow, R.plurals.lunar_amavasya_in_days,
					)
					MoonDayStyle.ASTRONOMICAL -> Triple(
						R.string.lunar_newmoon_today, R.string.lunar_newmoon_tomorrow, R.plurals.lunar_newmoon_in_days,
					)
				}
			}
		}
		return when (lead) {
			0 -> context.getString(today)
			1 -> context.getString(tomorrow)
			else -> context.resources.getQuantityString(inDays, lead, lead)
		}
	}

	/** The facts of the day: the date and, for Ekadashi, when to break the fast. */
	fun details(context: Context, subject: LunarDay, style: DateStyle): String = when (subject) {
		is EkadashiDay -> {
			val day = subject.mahadvadashi?.let {
				context.getString(R.string.lunar_day_with_name, date(subject.day, style), mahadvadashiName(context, it))
			} ?: date(subject.day, style)
			subject.parana?.let {
				context.getString(R.string.lunar_ekadashi_details, day, parana(context, it, style))
			} ?: context.getString(R.string.lunar_ekadashi_details_no_parana, day)
		}
		is MoonDay -> {
			val full = subject.phase == MoonPhase.FULL
			when (subject.style) {
				MoonDayStyle.VEDIC -> context.getString(
					if (full) R.string.lunar_purnima_details else R.string.lunar_amavasya_details,
					date(subject.day, style),
					dateTime(context, subject.moment, style),
				)
				MoonDayStyle.ASTRONOMICAL -> context.getString(
					if (full) R.string.lunar_fullmoon_details else R.string.lunar_newmoon_details,
					date(subject.day, style),
					time(context, subject.moment, style),
				)
			}
		}
	}

	fun parana(context: Context, window: TimeWindow, style: DateStyle): String {
		val day = date(window.start.toLocalDate(), style)
		val from = time(context, window.start, style)
		return window.end?.let { context.getString(R.string.lunar_parana_window, day, from, time(context, it, style)) }
			?: context.getString(R.string.lunar_parana_after, day, from)
	}

	/** A reminder is about a day, so the weekday helps: the compact style gains it. */
	fun date(day: LocalDate, style: DateStyle): String =
		AppDateTime.formatDate(day.toKotlinLocalDate(), withWeekday(style))

	fun time(context: Context, moment: ZonedDateTime, style: DateStyle): String =
		AppDateTime.formatTime(
			moment.toLocalDateTime().toKotlinLocalDateTime(), style, shouldUse24HourFormat(context),
		)

	private fun dateTime(context: Context, moment: ZonedDateTime, style: DateStyle): String =
		context.getString(
			R.string.lunar_date_time, date(moment.toLocalDate(), style), time(context, moment, style),
		)

	private fun withWeekday(style: DateStyle): DateStyle =
		if (style == DateStyle.OPTIMIZED_COMPACT) DateStyle.OPTIMIZED_WEEKDAY else style

	private fun mahadvadashiName(context: Context, m: Mahadvadashi): String = context.getString(
		when (m) {
			Mahadvadashi.UNMILANI -> R.string.lunar_mahadvadashi_unmilani
			Mahadvadashi.VYANJULI -> R.string.lunar_mahadvadashi_vyanjuli
			Mahadvadashi.TRISPRSA -> R.string.lunar_mahadvadashi_trisparsha
			Mahadvadashi.PAKSAVARDHINI -> R.string.lunar_mahadvadashi_pakshavardhini
		}
	)
}
