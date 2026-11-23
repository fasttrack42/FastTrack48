package com.legbehindneck.fasttrack48

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.legbehindneck.fasttrack48.data.Stages
import com.legbehindneck.fasttrack48.data.descriptionFor
import com.legbehindneck.fasttrack48.screens.main.MainActivity
import com.legbehindneck.fasttrack48.screens.settings.withAppLanguage
import com.legbehindneck.fasttrack48.utils.getColorFor
import io.github.aakira.napier.Napier
import kotlin.time.Duration

object FastingNotificationManager {
	const val FASTING_CHANNEL_ID = "fasting_status"
	const val NOTIFICATION_ID = 1000

	fun postFastingNotification(
		context: Context,
		elapsedTime: Duration
	) {
		// Check if we have notification permission (Android 13+)
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			if (ActivityCompat.checkSelfPermission(
					context,
					Manifest.permission.POST_NOTIFICATIONS
				) != PackageManager.PERMISSION_GRANTED
			) {
				Napier.w("Notification permission not granted, cannot post fasting notification")
				return
			}
		}

		post(withAppLanguage(context), elapsedTime)
	}

	/**
	 * [context] carries the app's language, so every text below is in it.
	 *
	 * The time is a chronometer counting from the fast's start, so the shade always agrees with
	 * the dial; an hourly refresh would lag it by up to an hour and a half. The text is the same
	 * stage readout the screen shows under the dial ([Stages.stage]), refreshed hourly.
	 */
	private fun post(context: Context, elapsedTime: Duration) {
		createNotificationChannel(context)

		val elapsedHours = elapsedTime.inWholeHours
		val startedAt = System.currentTimeMillis() - elapsedTime.inWholeMilliseconds
		val stage = Stages.stage[Stages.stage.indexOfLast { it.hours <= elapsedHours }.coerceAtLeast(0)]
		val title = context.getString(stage.title)
		val description = context.getString(descriptionFor(stage, elapsedHours))

		val intent = Intent(context, MainActivity::class.java).apply {
			flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
		}
		val pendingIntent = PendingIntent.getActivity(
			context,
			0,
			intent,
			PendingIntent.FLAG_IMMUTABLE
		)

		// Check if we're on API 36+ for progress-centric notification
		val notification = if (Build.VERSION.SDK_INT >= 36) {
			// Scale progress to match equal-sized segments
			// Find which phase we're in and calculate progress within that phase
			val currentPhaseIndex = Stages.phases.indexOfLast { elapsedHours >= it.hours }.coerceAtLeast(0)
			val currentPhase = Stages.phases[currentPhaseIndex]
			val nextPhase = Stages.phases.getOrNull(currentPhaseIndex + 1)

			val phaseStartHours = currentPhase.hours.toFloat()
			val phaseEndHours = nextPhase?.hours?.toFloat() ?: Stages.END_TIME
			val progressInPhase =
				((elapsedHours.toFloat() - phaseStartHours) / (phaseEndHours - phaseStartHours)).coerceIn(0f, 1f)

			// Each segment is equal size (1000 / number of phases)
			val segmentLength = 1000 / Stages.phases.size
			val normalizedProgress =
				((currentPhaseIndex * segmentLength) + (progressInPhase * segmentLength)).toInt().coerceIn(0, 1000)

			// Create progress segments for each fasting stage with equal sizes
			val progressStyle = Notification.ProgressStyle().apply {
				setProgress(normalizedProgress)

				setProgressTrackerIcon(Icon.createWithResource(context, R.drawable.ic_notification_progress))
				setStyledByProgress(false)

				// Progress segments are equal size in pixels when displayed
				val segmentLength = 1000 / Stages.phases.size
				Stages.phases.forEach { phase ->
					addProgressSegment(Notification.ProgressStyle.Segment(segmentLength).apply {
						setColor(getColorFor(phase).toArgb())
					})
				}
			}

			Notification.Builder(context, FASTING_CHANNEL_ID)
				.setSmallIcon(R.drawable.ic_alert)
				.setContentTitle(title)
				.setContentText(description)
				.setWhen(startedAt)
				.setShowWhen(true)
				.setUsesChronometer(true)
				.setStyle(progressStyle)
				.setOngoing(true)
				.setContentIntent(pendingIntent)
				.setCategory(Notification.CATEGORY_STATUS)
				.setAutoCancel(false)
				.build()
		} else {
			NotificationCompat.Builder(context, FASTING_CHANNEL_ID)
				.setSmallIcon(R.drawable.ic_alert)
				.setContentTitle(title)
				.setContentText(description)
				.setWhen(startedAt)
				.setShowWhen(true)
				.setUsesChronometer(true)
				.setStyle(
					NotificationCompat.BigTextStyle()
						.bigText(description)
						.setBigContentTitle(title)
				)
				.setOngoing(true)
				.setPriority(NotificationCompat.PRIORITY_DEFAULT)
				.setContentIntent(pendingIntent)
				.setAutoCancel(false)
				.build()
		}

		// No debug logging here: its arguments (two calls into the notification service) were
		// evaluated in release too, on every post, though nothing was ever logged there.
		context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
	}

	fun cancelFastingNotification(context: Context) {
		val notificationManager = context.getSystemService(NotificationManager::class.java)
		notificationManager.cancel(NOTIFICATION_ID)
	}

	private fun createNotificationChannel(context: Context) {
		val name = context.getString(R.string.notification_fasting_channel_name)
		val descriptionText = context.getString(R.string.notification_fasting_channel_description)
		val importance = NotificationManager.IMPORTANCE_LOW
		val channel = NotificationChannel(FASTING_CHANNEL_ID, name, importance).apply {
			description = descriptionText
			setSound(null, null)
			enableVibration(false)
		}

		val notificationManager = context.getSystemService(NotificationManager::class.java)
		notificationManager.createNotificationChannel(channel)
	}
}
