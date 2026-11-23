package com.legbehindneck.fasttrack48

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import com.legbehindneck.fasttrack48.data.Phase
import com.legbehindneck.fasttrack48.data.Stages
import com.legbehindneck.fasttrack48.data.activefast.ActiveFastRepository
import com.legbehindneck.fasttrack48.data.activefast.FastCheckIn
import com.legbehindneck.fasttrack48.data.settings.SettingsDatasource
import com.legbehindneck.fasttrack48.lunar.LunarReminderScheduler
import com.legbehindneck.fasttrack48.screens.main.MainActivity
import com.legbehindneck.fasttrack48.screens.settings.withAppLanguage
import com.legbehindneck.fasttrack48.utils.QuietHours
import com.legbehindneck.fasttrack48.utils.formatDuration
import com.legbehindneck.fasttrack48.widget.WidgetUpdater
import org.koin.android.ext.android.inject
import org.koin.core.context.GlobalContext
import java.time.LocalTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class AlertService : JobService() {
	private val repository: ActiveFastRepository by inject()
	private val settingsDatasource: SettingsDatasource by inject()

	override fun onStartJob(params: JobParameters): Boolean {
		// A phase job can outlive the reason it was scheduled: the fast may have ended, or
		// the user may have turned stage alerts off since. Neither should still post; nor
		// should a stage be announced while "Still fasting?" is open, to someone who may be eating.
		val start = repository.getFastStart()?.takeIf { repository.isFasting() }
		val phaseAlertWanted = start != null && settingsDatasource.getFastingAlerts() &&
			!FastCheckIn.isOpen(this, start)
		when (params.jobId) {
			JobId.FAT_BURN.id -> if (phaseAlertWanted) {
				postNotification(R.string.notification_fat_burn_title, R.string.notification_fat_burn_content)
				updateFastingNotification()
			}

			JobId.KETOSIS.id -> if (phaseAlertWanted) {
				postNotification(R.string.notification_ketosis_title, R.string.notification_ketosis_content)
				updateFastingNotification()
			}

			JobId.AUTOPHAGY.id -> if (phaseAlertWanted) {
				postNotification(R.string.notification_autophagy_title, R.string.notification_autophagy_content)
				updateFastingNotification()
			}

			JobId.OPTIMAL_AUTOPHAGY.id -> if (phaseAlertWanted) {
				postNotification(
					R.string.notification_optimal_autophagy_title,
					R.string.notification_optimal_autophagy_content,
				)
				updateFastingNotification()
			}

			// Asked once per fast. Marked before posting: without the permission nothing shows,
			// and the fast still counts as in doubt.
			JobId.CHECK_IN.id -> if (start != null && !FastCheckIn.wasAsked(this, start)) {
				FastCheckIn.markAsked(this, start)
				postCheckIn(repository.getElapsedFastTime())
				// The fast is in doubt now, so it stops holding fast-day reminders: let the
				// scheduler release what it held (by day only, never in the night).
				LunarReminderScheduler.refresh(this)
			}

			// The hourly tick: the ongoing notification's stage and the widget's hours, both on the
			// fast's own hour; re-armed while a fast runs and something shows it.
			JobId.HOURLY_UPDATE.id -> {
				updateFastingNotification()
				WidgetUpdater.updateWidgets(this)
				syncHourlyTick(this)
			}
		}

		jobFinished(params, false)

		return false
	}

	override fun onStopJob(params: JobParameters?): Boolean {
		return false
	}

	/**
	 * One notification for every stage, so a later stage replaces an earlier one instead of
	 * stacking; silent at night ([QuietHours]), so a fast started in the morning does not wake
	 * anyone at its 12 h mark. The shade still holds it in the morning.
	 */
	private fun postNotification(
		@StringRes title: Int,
		@StringRes content: Int,
	) {
		// This job's own context does not carry the app's language below API 33.
		val texts = withAppLanguage(this)
		createNotificationChannel(texts)

		val contentStr = texts.getString(content)

		val intent = Intent(this, MainActivity::class.java).apply {
			flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
		}
		val pendingIntent: PendingIntent =
			PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

		val builder = NotificationCompat.Builder(this, ALERTS_CHANNEL_ID)
			.setSmallIcon(R.drawable.ic_alert)
			.setContentTitle(texts.getString(title))
			.setContentText(contentStr)
			.setStyle(
				NotificationCompat.BigTextStyle()
					.bigText(contentStr)
			)
			.setPriority(NotificationCompat.PRIORITY_HIGH)
			.setSilent(QuietHours.isQuiet(LocalTime.now()))
			.setContentIntent(pendingIntent)
			.setAutoCancel(true)

		val notificationManager = getSystemService(NotificationManager::class.java)
		notificationManager.notify(NOTIFICATION_TAG, NOTIFICATION_ID_STAGE, builder.build())
	}

	/**
	 * "Still fasting?" for a fast that has run well past this person's usual ones. It asks and
	 * offers both answers; it never ends the fast. "End fast" opens the end sheet, where "I
	 * stopped earlier" sets the real end.
	 */
	private fun postCheckIn(elapsed: Duration) {
		val texts = withAppLanguage(this)
		createNotificationChannel(texts)

		val open = PendingIntent.getActivity(
			this,
			0,
			Intent(this, MainActivity::class.java).apply {
				flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
			},
			PendingIntent.FLAG_IMMUTABLE,
		)
		// Its own request code: PendingIntent identity ignores extras, so under 0 this would
		// resolve to the plain "open the app" intent above and lose STOP_FAST_EXTRA.
		val end = PendingIntent.getActivity(
			this,
			REQUEST_CHECK_IN_END_FAST,
			Intent(this, MainActivity::class.java).apply {
				flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
				putExtra(MainActivity.STOP_FAST_EXTRA, true)
			},
			PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
		)
		val stillFasting = PendingIntent.getBroadcast(
			this,
			REQUEST_CHECK_IN_STILL_FASTING,
			Intent(this, CheckInReceiver::class.java),
			PendingIntent.FLAG_IMMUTABLE,
		)
		val body = texts.getString(R.string.check_in_body, formatDuration(texts, elapsed, withMinutes = false))
		val notification = NotificationCompat.Builder(this, ALERTS_CHANNEL_ID)
			.setSmallIcon(R.drawable.ic_alert)
			.setContentTitle(texts.getString(R.string.check_in_title))
			.setContentText(body)
			.setStyle(NotificationCompat.BigTextStyle().bigText(body))
			.setSilent(QuietHours.isQuiet(LocalTime.now()))
			.setContentIntent(open)
			.setAutoCancel(true)
			.addAction(0, texts.getString(R.string.end_fast_button), end)
			.addAction(0, texts.getString(R.string.check_in_still_fasting), stillFasting)
			.build()
		getSystemService(NotificationManager::class.java).notify(NOTIFICATION_TAG, NOTIFICATION_ID_CHECK_IN, notification)
	}

	private fun createNotificationChannel(texts: Context) {
		val name = texts.getString(R.string.notification_channel_name)
		val descriptionText = texts.getString(R.string.notification_channel_description)
		val importance = NotificationManager.IMPORTANCE_DEFAULT
		val channel = NotificationChannel(ALERTS_CHANNEL_ID, name, importance).apply {
			description = descriptionText
		}
		// Register the channel with the system
		val notificationManager = getSystemService(NotificationManager::class.java)
		notificationManager.createNotificationChannel(channel)
	}

	private fun updateFastingNotification() {
		if (repository.isFasting() && settingsDatasource.getShowFastingNotification()) {
			val elapsedTime = repository.getElapsedFastTime()
			FastingNotificationManager.postFastingNotification(this, elapsedTime)
		}
	}

	companion object {
		const val ALERTS_CHANNEL_ID = "fast_alerts"
		const val NOTIFICATION_TAG = "alerts"

		const val NOTIFICATION_ID_STAGE = 1
		const val NOTIFICATION_ID_CHECK_IN = 6

		/**
		 * PendingIntent request codes for the check-in's two actions. Activity intents to
		 * MainActivity elsewhere use 0, and PendingIntent identity ignores extras, so any reuse
		 * of these would hand "End fast" someone else's intent, or theirs this one's extra.
		 */
		private const val REQUEST_CHECK_IN_END_FAST = 1001
		private const val REQUEST_CHECK_IN_STILL_FASTING = 1002

		enum class JobId(val id: Int) {
			FAT_BURN(1),
			KETOSIS(2),
			AUTOPHAGY(3),
			OPTIMAL_AUTOPHAGY(4),
			HOURLY_UPDATE(5),
			CHECK_IN(6),
		}

		private val LEEWAY = 30.minutes

		fun cancelAlerts(context: Context) {
			cancelPhaseAlerts(context)
			cancelHourlyUpdates(context)
		}

		/** Cancel the stage alerts only, leaving the ongoing notification's hourly refresh. */
		fun cancelPhaseAlerts(context: Context) {
			val jobScheduler = context.getSystemService(JobScheduler::class.java)
			jobScheduler.cancel(JobId.FAT_BURN.id)
			jobScheduler.cancel(JobId.KETOSIS.id)
			jobScheduler.cancel(JobId.AUTOPHAGY.id)
			jobScheduler.cancel(JobId.OPTIMAL_AUTOPHAGY.id)
		}

		fun cancelHourlyUpdates(context: Context) {
			val jobScheduler = context.getSystemService(JobScheduler::class.java)
			jobScheduler.cancel(JobId.HOURLY_UPDATE.id)
		}

		fun scheduleAlerts(elapsedTime: Duration, context: Context) {
			JobId.entries.forEach { jobId ->
				when (jobId) {
					JobId.FAT_BURN -> schedulePhase(
						elapsedTime,
						Stages.PHASE_FAT_BURN,
						jobId.id,
						context
					)

					JobId.KETOSIS -> schedulePhase(
						elapsedTime,
						Stages.PHASE_KETOSIS,
						jobId.id,
						context
					)

					JobId.AUTOPHAGY -> schedulePhase(
						elapsedTime,
						Stages.PHASE_AUTOPHAGY,
						jobId.id,
						context
					)

					JobId.OPTIMAL_AUTOPHAGY -> schedulePhase(
						elapsedTime,
						Stages.PHASE_OPTIMAL_AUTOPHAGY,
						jobId.id,
						context
					)

					// Not a stage alert: armed by syncHourlyTick for the notification and the widget.
					JobId.HOURLY_UPDATE -> Unit

					// Armed by the view model from the logbook, whatever the stage-alert setting.
					JobId.CHECK_IN -> Unit
				}
			}
		}

		/**
		 * Arms "Still fasting?" [delay] from now, replacing any armed before. Nothing when it is
		 * already due: whoever arms it is in the app, looking at the dial.
		 */
		fun scheduleCheckIn(context: Context, delay: Duration) {
			val jobScheduler = context.getSystemService(JobScheduler::class.java)
			jobScheduler.cancel(JobId.CHECK_IN.id)
			if (delay <= Duration.ZERO) return
			val millis = delay.inWholeMilliseconds
			val job = JobInfo.Builder(JobId.CHECK_IN.id, ComponentName(context, AlertService::class.java))
				.setPersisted(true)
				.setMinimumLatency(millis)
				.setOverrideDeadline(millis + LEEWAY.inWholeMilliseconds)
				.build()
			jobScheduler.schedule(job)
		}

		/** No fast running: the question, armed or shown, is moot. */
		fun cancelCheckIn(context: Context) {
			context.getSystemService(JobScheduler::class.java).cancel(JobId.CHECK_IN.id)
			context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_TAG, NOTIFICATION_ID_CHECK_IN)
		}

		/** The question answered, by its button or by opening the app: closed, its notification gone. */
		fun closeCheckIn(context: Context) {
			FastCheckIn.answer(context)
			context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_TAG, NOTIFICATION_ID_CHECK_IN)
		}

		/**
		 * Arm [phase]'s alert for the moment the fast reaches it. Computed from the exact
		 * elapsed time: whole hours would put a fast 15h50m in on a 1h delay, and its 16h
		 * alert at 16h50m.
		 *
		 * Always replaced, never kept: a job armed earlier can be wrong now (the start was
		 * corrected, or an update moved a stage, as fat burning moved from 16 h to 12 h), and
		 * the same moment computed again is the same job. A phase already passed has its job
		 * dropped rather than left to announce it late.
		 */
		private fun schedulePhase(elapsedTime: Duration, phase: Phase, jobId: Int, context: Context) {
			val until = phase.hours.hours - elapsedTime
			if (until > Duration.ZERO) {
				scheduleAlert(jobId, until.inWholeMilliseconds, context)
			} else {
				context.getSystemService(JobScheduler::class.java).cancel(jobId)
			}
		}

		/** Schedules [jobId], replacing any job pending under that id (JobScheduler.schedule does). */
		private fun scheduleAlert(jobId: Int, millsUntil: Long, context: Context) {
			val job = JobInfo.Builder(jobId, ComponentName(context, AlertService::class.java))
				.setPersisted(true)
				.setMinimumLatency(millsUntil)
				// Allow the device to decide with some leeway so that this can be batched with other jobs
				.setOverrideDeadline(millsUntil + LEEWAY.inWholeMilliseconds)
				.build()
			context.getSystemService(JobScheduler::class.java).schedule(job)
		}

		/**
		 * Arms the hourly tick while a fast runs and something shows its hours (the ongoing
		 * notification or a placed widget), and drops it otherwise. One job for both, so a widget
		 * needs no timer of its own.
		 */
		fun syncHourlyTick(context: Context) {
			val koin = GlobalContext.get()
			val fast = koin.get<ActiveFastRepository>()
			val showNotification = koin.get<SettingsDatasource>().getShowFastingNotification()
			if (fast.isFasting() && (showNotification || WidgetUpdater.hasWidgets(context))) {
				scheduleHourlyUpdate(context, fast.getElapsedFastTime())
			} else {
				cancelHourlyUpdates(context)
			}
		}

		/**
		 * The next tick at the fast's next whole hour, plus a second so that hour has turned when
		 * it runs: what the notification and the widget show changes on the hour, not up to an
		 * hour late.
		 */
		private fun scheduleHourlyUpdate(context: Context, elapsed: Duration) {
			val jobScheduler = context.getSystemService(JobScheduler::class.java)
			val jobId = JobId.HOURLY_UPDATE.id
			val hourMillis = 1.hours.inWholeMilliseconds
			val msUntil = hourMillis - elapsed.inWholeMilliseconds.mod(hourMillis) + 1_000L

			val job = JobInfo.Builder(jobId, ComponentName(context, AlertService::class.java))
				.setPersisted(true)
				.setMinimumLatency(msUntil)
				.setOverrideDeadline(msUntil + LEEWAY.inWholeMilliseconds)
				.build()
			// Replaces any pending tick (JobScheduler.schedule does).
			jobScheduler.schedule(job)
		}
	}
}

/** "Still fasting": the answer closes the question, so stage alerts and reminder holds resume. */
class CheckInReceiver : BroadcastReceiver() {
	override fun onReceive(context: Context, intent: Intent) {
		AlertService.closeCheckIn(context)
	}
}
