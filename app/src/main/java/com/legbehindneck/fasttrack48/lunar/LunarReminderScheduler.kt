package com.legbehindneck.fasttrack48.lunar

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.WorkerThread
import io.github.aakira.napier.Napier
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
 */
object LunarReminderScheduler {

	internal const val ACTION_FIRE = "com.legbehindneck.fasttrack48.lunar.FIRE"

	/** How far back a run looks for reminders still owed: the longest lead plus a day. */
	private val LOOKBACK_DAYS = LunarReminderSettings.LEAD_OPTIONS.max() + 1L

	private val executor = Executors.newSingleThreadExecutor()

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
	 * App start, alarm, boot, update, clock or time-zone change: catch up, then re-arm.
	 * [zone] overrides the process default, which a time-zone broadcast can outrun.
	 */
	fun refresh(context: Context, zone: ZoneId? = null, onDone: () -> Unit = {}) {
		val app = context.applicationContext
		executor.execute {
			try {
				reschedule(app, Run.CATCH_UP, zoneOverride = zone)
			} catch (e: Exception) {
				Napier.e("Lunar reminder refresh failed", e)
			} finally {
				onDone()
			}
		}
	}

	private enum class Run { CATCH_UP, SETTINGS_CHANGED }

	@WorkerThread
	private fun reschedule(context: Context, run: Run, zoneOverride: ZoneId? = null) {
		val alarmManager = context.getSystemService(AlarmManager::class.java)
		val alarm = alarmIntent(context)
		val zone = zoneOverride ?: ZoneId.systemDefault()
		val now = ZonedDateTime.now(zone)
		val state = LunarSettingsStore.schedulerState(context, zone)
		val settings = LunarSettingsStore.load(context)
		if (!settings.anyActive) {
			alarmManager.cancel(alarm)
			LunarSettingsStore.saveSchedulerState(
				context,
				state.copy(
					floor = now.toLocalDateTime(),
					posted = LunarReminderPlanner.prune(state.posted, now.toLocalDate()),
					armedAtMillis = null,
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
		val due = until?.let { LunarReminderPlanner.due(plan, now, it, floor, state.posted) }
		due?.toPost?.forEach { LunarNotifier.post(context, it) }

		val next = plan.firstOrNull { it.fireAt.isAfter(now) }
		if (next == null) {
			alarmManager.cancel(alarm)
		} else {
			arm(alarmManager, next.fireAt.toInstant().toEpochMilli(), alarm)
			Napier.d("Next lunar reminder at ${next.fireAt} for ${next.subject.day}")
		}
		LunarSettingsStore.saveSchedulerState(
			context,
			LunarSettingsStore.SchedulerState(
				floor = now.toLocalDateTime(),
				posted = LunarReminderPlanner.prune(state.posted + due?.handled.orEmpty(), now.toLocalDate()),
				armedAtMillis = next?.fireAt?.toInstant()?.toEpochMilli(),
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
