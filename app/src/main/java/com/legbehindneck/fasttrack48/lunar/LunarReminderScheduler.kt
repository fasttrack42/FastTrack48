package com.legbehindneck.fasttrack48.lunar

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.WorkerThread
import io.github.aakira.napier.Napier
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.Executors

/**
 * Keeps exactly one alarm armed: the next reminder. [AlarmManager.setAndAllowWhileIdle]
 * fires even in Doze and needs no exact-alarm permission; Android may deliver it a few
 * minutes late, which suits a reminder at a set hour.
 *
 * Every refresh posts all reminders that came due since the watermark (several can share
 * a fire time, and a late alarm or a reboot must not drop one), moves the watermark to
 * now, and arms the next. Alarms do not survive a reboot, an app update or a clock /
 * time-zone change, so [LunarReminderReceiver] refreshes on those too.
 */
object LunarReminderScheduler {

	internal const val ACTION_FIRE = "com.legbehindneck.fasttrack48.lunar.FIRE"

	/** A missed reminder is still posted this long after its time, but only on its own day. */
	private const val CATCH_UP_HOURS = 6L

	private val executor = Executors.newSingleThreadExecutor()

	/** Settings changed: skip anything already due and arm the next reminder. */
	fun onSettingsChanged(context: Context) {
		val app = context.applicationContext
		executor.execute {
			try {
				LunarSettingsStore.setWatermark(app, System.currentTimeMillis())
				reschedule(app, postDue = false)
			} catch (e: Exception) {
				Napier.e("Lunar reminder scheduling failed", e)
			}
		}
	}

	/** App start, alarm, boot, update, clock or time-zone change: catch up, then re-arm. */
	fun refresh(context: Context, onDone: () -> Unit = {}) {
		val app = context.applicationContext
		executor.execute {
			try {
				reschedule(app, postDue = true)
			} catch (e: Exception) {
				Napier.e("Lunar reminder refresh failed", e)
			} finally {
				onDone()
			}
		}
	}

	@WorkerThread
	private fun reschedule(context: Context, postDue: Boolean) {
		val alarmManager = context.getSystemService(AlarmManager::class.java)
		val alarm = alarmIntent(context)
		val settings = LunarSettingsStore.load(context)
		if (!settings.anyActive) {
			alarmManager.cancel(alarm)
			return
		}

		val zone = ZoneId.systemDefault()
		val now = ZonedDateTime.now(zone)
		val nowMillis = now.toInstant().toEpochMilli()
		val location = LunarLocation.resolve(context, zone).point
		val plan = LunarReminderPlanner.plan(now.minusHours(CATCH_UP_HOURS), settings, location, zone)

		if (postDue) {
			val watermark = LunarSettingsStore.watermark(context) ?: nowMillis
			val today = now.toLocalDate()
			plan.filter {
				val fire = it.fireAt.toInstant().toEpochMilli()
				fire > watermark && fire <= nowMillis && it.fireAt.toLocalDate() == today
			}.forEach { LunarNotifier.post(context, it) }
		}
		LunarSettingsStore.setWatermark(context, nowMillis)

		val next = plan.firstOrNull { it.fireAt.toInstant().toEpochMilli() > nowMillis }
		if (next == null) {
			alarmManager.cancel(alarm)
		} else {
			alarmManager.setAndAllowWhileIdle(
				AlarmManager.RTC_WAKEUP, next.fireAt.toInstant().toEpochMilli(), alarm,
			)
			Napier.d("Next lunar reminder at ${next.fireAt} for ${next.subject.day}")
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
	override fun onReceive(context: Context, intent: Intent) {
		when (intent.action) {
			LunarReminderScheduler.ACTION_FIRE,
			Intent.ACTION_BOOT_COMPLETED,
			Intent.ACTION_MY_PACKAGE_REPLACED,
			Intent.ACTION_TIMEZONE_CHANGED,
			Intent.ACTION_TIME_CHANGED -> {
				val pending = goAsync()
				LunarReminderScheduler.refresh(context) { pending.finish() }
			}
		}
	}
}
