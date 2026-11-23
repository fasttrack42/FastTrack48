package com.legbehindneck.fasttrack48.lunar

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.WorkerThread
import com.legbehindneck.fasttrack48.data.activefast.ActiveFastRepository
import com.legbehindneck.fasttrack48.data.activefast.FastCheckIn
import com.legbehindneck.fasttrack48.utils.QuietHours
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.Executors

/**
 * Keeps exactly one alarm armed: the next reminder. Below Android 12 it is exact
 * ([AlarmManager.setExactAndAllowWhileIdle] needs no permission there); from Android 12 on,
 * where exact alarms need a permission this app does not ask for, it is
 * [AlarmManager.setAndAllowWhileIdle], which Android may deliver late, up to about an hour.
 * Both fire in Doze.
 *
 * Lateness costs nothing but time: every run posts each reminder owed by then
 * ([LunarReminderPlanner.due]) while its observance is still today or ahead, with its wording
 * counted from the actual day. What has been posted is remembered by reminder, not by
 * instant, so a time-zone change neither drops nor repeats one. Alarms do not survive a
 * reboot, an app update or a clock / time-zone change, so [LunarReminderReceiver] refreshes
 * on those too.
 *
 * Nothing is posted while a fast runs, nor in the quiet after one
 * ([LunarReminderPlanner.quietUntil]): every run reads the active fast and holds what came due
 * ([LunarReminderPlanner.settle]), and the alarm is armed for the end of the quiet so what was
 * held goes out then. Decided here, at the one place that posts, so no trigger can bypass it.
 * A fast whose "Still fasting?" is unanswered ([FastCheckIn]) does not hold reminders.
 */
object LunarReminderScheduler {

	internal const val ACTION_FIRE = "com.legbehindneck.fasttrack48.lunar.FIRE"

	/** How far back a run looks for reminders still owed: the longest lead plus a day. */
	private val LOOKBACK_DAYS = LunarReminderSettings.LEAD_OPTIONS.max() + 1L

	private val executor = Executors.newSingleThreadExecutor()

	// Resolved on first use: Koin is started in Application.onCreate, before any caller.
	private val activeFast: ActiveFastRepository by lazy { GlobalContext.get().get<ActiveFastRepository>() }

	/**
	 * Owns the active-fast watch for the life of the process, and must: SharedPreferences
	 * keeps change listeners only weakly, so a collector in GlobalScope, which holds no
	 * reference to its coroutines, can be garbage-collected along with its listener and stop
	 * without a trace. A failure is logged rather than left to crash the process.
	 */
	private val watchScope = CoroutineScope(
		SupervisorJob() + Dispatchers.Default +
			CoroutineExceptionHandler { _, e -> Napier.e("Active-fast watch failed", e) },
	)

	/**
	 * Process start: catch up and re-arm if anything is owed ([needsCatchUp]), then run again
	 * whenever a fast starts or ends, by whichever writer (the dial, a widget, a restored
	 * export). An end is passed on as such, so the quiet after it is counted from the moment the
	 * app saw it. Call once, from [android.app.Application.onCreate].
	 */
	fun start(context: Context) {
		val app = context.applicationContext
		watchScope.launch {
			var wasRunning: Boolean? = null
			activeFast.observe()
				.map { it.isRunning }
				.distinctUntilChanged()
				.collect { running ->
					val first = wasRunning == null
					val ended = wasRunning == true && !running
					wasRunning = running
					if (!first || needsCatchUp(app)) refresh(app, fastEnded = ended)
				}
		}
	}

	/**
	 * Whether this process start owes a run. Each run plans weeks of fast days from scratch,
	 * and an alarm or a job can start the process many times a day. None is owed while every
	 * reminder is off: turning one on runs a settings change, which owes nothing from before
	 * it. Otherwise a run is owed only when no alarm is armed (never yet, or a force-stop
	 * cleared it, which also cancels the alarm's PendingIntent) or the armed one is overdue
	 * (Doze held it back). Boot, update and clock or time-zone changes arrive as broadcasts of
	 * their own. A run with a stale floor is safe: [LunarReminderPlanner.due] owes nothing for a
	 * day already past.
	 */
	private fun needsCatchUp(context: Context): Boolean {
		if (!LunarSettingsStore.load(context).anyActive) return false
		val armed = LunarSettingsStore.schedulerState(context, ZoneId.systemDefault()).armedAtMillis ?: return true
		if (armed <= System.currentTimeMillis()) return true
		val existing = PendingIntent.getBroadcast(
			context,
			0,
			Intent(context, LunarReminderReceiver::class.java).setAction(ACTION_FIRE),
			PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
		)
		return existing == null
	}

	/**
	 * Settings changed: re-plan and arm the next reminder. A reminder whose alarm was due but
	 * not yet delivered (Doze holds it back) is still posted; one that only exists because of
	 * the change is not posted for a time already past.
	 */
	fun onSettingsChanged(context: Context) {
		val app = context.applicationContext
		executor.execute {
			try {
				reschedule(app, Run.SETTINGS_CHANGED)
			} catch (e: Exception) {
				Napier.e("Lunar reminder scheduling failed", e)
			}
		}
	}

	/**
	 * App start, fast started or ended, alarm, boot, update, clock or time-zone change: catch
	 * up, then re-arm. [zone] overrides the process default, which a time-zone broadcast can
	 * outrun. [fastEnded]: this run follows a fast's end, seen now.
	 */
	fun refresh(context: Context, zone: ZoneId? = null, fastEnded: Boolean = false, onDone: () -> Unit = {}) {
		val app = context.applicationContext
		executor.execute {
			try {
				reschedule(app, Run.CATCH_UP, zoneOverride = zone, fastEnded = fastEnded)
			} catch (e: Exception) {
				Napier.e("Lunar reminder refresh failed", e)
			} finally {
				onDone()
			}
		}
	}

	private enum class Run { CATCH_UP, SETTINGS_CHANGED }

	@WorkerThread
	private fun reschedule(context: Context, run: Run, zoneOverride: ZoneId? = null, fastEnded: Boolean = false) {
		val alarmManager = context.getSystemService(AlarmManager::class.java)
		val alarm = alarmIntent(context)
		val zone = zoneOverride ?: ZoneId.systemDefault()
		val now = ZonedDateTime.now(zone)
		val today = now.toLocalDate()
		val state = LunarSettingsStore.schedulerState(context, zone)
		val settings = LunarSettingsStore.load(context)

		// The fast, read once. After an end the slot keeps both halves until the next start.
		val start = activeFast.getFastStart()
		val end = activeFast.getFastEnd()
		val fasting = start != null && end == null && !FastCheckIn.isOpen(context, start)
		// The quiet after the last fast, kept as the latest seen: a new fast started inside it,
		// or an end backdated after the fact, cannot shorten it. Without a stored value (the
		// process died before this run) it is counted from the end itself.
		val ended = if (start != null && end != null) {
			val endAt = Instant.ofEpochMilli(end.toEpochMilliseconds()).atZone(zone)
			LunarReminderPlanner.quietUntil(
				endAt,
				Duration.ofMillis((end - start).inWholeMilliseconds),
				seenAt = if (fastEnded) now else endAt,
			)
		} else null
		val quiet = listOfNotNull(state.quietUntilMillis?.let { Instant.ofEpochMilli(it).atZone(zone) }, ended)
			.maxByOrNull { it.toInstant() }
			?.takeIf { it.isAfter(now) }
		val quietMillis = quiet?.toInstant()?.toEpochMilli()

		if (!settings.anyActive) {
			alarmManager.cancel(alarm)
			LunarSettingsStore.saveSchedulerState(
				context,
				state.copy(
					floor = now.toLocalDateTime(),
					posted = LunarReminderPlanner.prune(state.posted, today),
					held = emptySet(),
					armedAtMillis = null,
					quietUntilMillis = quietMillis,
				),
			)
			return
		}

		val location = LunarLocation.resolve(context, zone).point
		val plan = LunarReminderPlanner.plan(now.minusDays(LOOKBACK_DAYS), settings, location, zone)

		// Up to when reminders are owed. A catch-up owes everything due by now. A settings
		// change owes only what the armed alarm was for, and only once that alarm is overdue.
		val armed = state.armedAtMillis?.let { Instant.ofEpochMilli(it).atZone(zone) }
		val until = when (run) {
			Run.CATCH_UP -> now
			Run.SETTINGS_CHANGED -> armed?.takeIf { !it.isAfter(now) }
		}
		// No floor yet (the first run ever): nothing before now is owed.
		val floor = state.floor ?: now.toLocalDateTime()
		// What waits is released by day: posted at a time the user did not choose, it must not
		// wake them. The quiet after a fast already ends by day; this covers a release with no
		// quiet before it, such as a check-in that puts a fast in doubt at 03:00.
		val wait = quiet ?: QuietHours.after(now).takeIf {
			!fasting && state.held.isNotEmpty() && it.isAfter(now)
		}
		val due = LunarReminderPlanner.due(plan, now, until, floor, state.posted, state.held)
		val settled = LunarReminderPlanner.settle(due, fasting || wait != null, state.posted, state.held, today)
		settled.toPost.forEach { LunarNotifier.post(context, it) }

		// The next reminder, or the end of the wait when something is held for it. A fast's end
		// needs no alarm: the watch in [start] runs this again when it happens.
		val next = plan.firstOrNull { it.fireAt.isAfter(now) }?.fireAt
		val release = wait?.takeIf { !fasting && settled.held.isNotEmpty() }
		val armAt = listOfNotNull(next, release).minByOrNull { it.toInstant() }
		if (armAt == null) {
			alarmManager.cancel(alarm)
		} else {
			arm(alarmManager, armAt.toInstant().toEpochMilli(), alarm)
			Napier.d("Next lunar reminder run at $armAt")
		}
		LunarSettingsStore.saveSchedulerState(
			context,
			LunarSettingsStore.SchedulerState(
				floor = now.toLocalDateTime(),
				posted = settled.posted,
				held = settled.held,
				armedAtMillis = armAt?.toInstant()?.toEpochMilli(),
				quietUntilMillis = quietMillis,
			),
		)
	}

	/**
	 * Exact below Android 12, where an exact alarm needs no permission (lint does not follow
	 * the version check); inexact from 12 on, where it would.
	 */
	@SuppressLint("MissingPermission")
	private fun arm(alarmManager: AlarmManager, atMillis: Long, alarm: PendingIntent) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
			alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, alarm)
		} else {
			alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, alarm)
		}
	}

	private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
		context,
		0,
		Intent(context, LunarReminderReceiver::class.java).setAction(ACTION_FIRE),
		PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
	)
}

/** The alarm target, and the hook for the system events that clear or skew alarms. */
class LunarReminderReceiver : BroadcastReceiver() {
	private companion object {
		/** Intent.EXTRA_TIMEZONE (public only from API 30; the extra itself is far older). */
		const val EXTRA_TIME_ZONE = "time-zone"
	}

	override fun onReceive(context: Context, intent: Intent) {
		when (intent.action) {
			LunarReminderScheduler.ACTION_FIRE,
			Intent.ACTION_BOOT_COMPLETED,
			Intent.ACTION_MY_PACKAGE_REPLACED,
			Intent.ACTION_TIMEZONE_CHANGED,
			Intent.ACTION_TIME_CHANGED -> {
				// The broadcast names the new zone; the process default is updated by a
				// separate message from the system that this receiver can run ahead of.
				val zone = intent.getStringExtra(EXTRA_TIME_ZONE)
					?.let { runCatching { ZoneId.of(it) }.getOrNull() }
				val pending = goAsync()
				LunarReminderScheduler.refresh(context, zone) { pending.finish() }
			}
		}
	}
}
