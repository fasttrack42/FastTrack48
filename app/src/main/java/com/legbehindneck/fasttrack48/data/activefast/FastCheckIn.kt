package com.legbehindneck.fasttrack48.data.activefast

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * "Still fasting?": asked once per fast, when it runs well past this person's usual fasts, so
 * a fast left running by mistake is noticed. Keyed by the fast's start, so a new fast starts
 * clean with nothing to reset.
 *
 * While the question is open the fast is in doubt: stage alerts pause (they would report
 * stages to someone who may be eating) and fast-day reminders are not held back for it (a
 * forgotten fast must not silence them). The question asks; it never ends a fast.
 */
object FastCheckIn {
	private const val PREFS = "fast_check_in"
	private const val KEY_START = "start"
	private const val KEY_OPEN = "open"

	/** Past any daily fast: a day and a half. */
	val MIN_AFTER: Duration = 36.hours

	/**
	 * When to ask: past [MIN_AFTER] and past half again the longest of [recent] fasts. Recent
	 * rather than all-time, so one long fast years ago does not push the question out to days.
	 */
	fun askAfter(recent: List<Duration>): Duration = maxOf(MIN_AFTER, (recent.maxOrNull() ?: Duration.ZERO) * 1.5)

	fun wasAsked(context: Context, start: Instant): Boolean =
		prefs(context).getLong(KEY_START, -1L) == start.toEpochMilliseconds()

	/** Asked for the fast that began at [start], and not answered yet. */
	fun isOpen(context: Context, start: Instant): Boolean =
		wasAsked(context, start) && prefs(context).getBoolean(KEY_OPEN, false)

	fun markAsked(context: Context, start: Instant) = prefs(context).edit {
		putLong(KEY_START, start.toEpochMilliseconds())
		putBoolean(KEY_OPEN, true)
	}

	fun answer(context: Context) = prefs(context).edit { putBoolean(KEY_OPEN, false) }

	private fun prefs(context: Context): SharedPreferences =
		context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
