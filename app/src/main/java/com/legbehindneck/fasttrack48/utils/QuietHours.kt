package com.legbehindneck.fasttrack48.utils

import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * The night, 22:00 to 07:00 local time, when nothing the app chooses the time of should make a
 * sound. One window for every part that speaks (stage alerts, the check-in, fast-day reminders
 * released after a fast), so the phone keeps the same manners whichever part it is. A time
 * the user picked themselves, such as their reminder hour, is never moved by it.
 */
object QuietHours {
	const val START_HOUR = 22
	const val MORNING_HOUR = 7

	fun isQuiet(time: LocalTime): Boolean = time.hour >= START_HOUR || time.hour < MORNING_HOUR

	/**
	 * [at], or the 07:00 that ends the night [at] falls in. Built from the local date and hour,
	 * so a daylight-saving change on that night does not shift it.
	 */
	fun after(at: ZonedDateTime): ZonedDateTime {
		if (!isQuiet(at.toLocalTime())) return at
		val day = if (at.hour >= START_HOUR) at.toLocalDate().plusDays(1) else at.toLocalDate()
		return day.atTime(MORNING_HOUR, 0).atZone(at.zone)
	}
}
