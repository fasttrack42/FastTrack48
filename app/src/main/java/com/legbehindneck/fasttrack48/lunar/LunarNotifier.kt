package com.legbehindneck.fasttrack48.lunar

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.legbehindneck.fasttrack48.R
import com.legbehindneck.fasttrack48.data.settings.SettingsDatasource
import com.legbehindneck.fasttrack48.screens.main.MainActivity
import io.github.aakira.napier.Napier
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Posts Ekadashi / full-moon reminders on their own channel, so they can be tuned apart. */
object LunarNotifier : KoinComponent {
	const val CHANNEL_ID = "lunar_days"
	private const val NOTIFICATION_TAG = "lunar"

	private val settings: SettingsDatasource by inject()

	fun post(context: Context, reminder: PlannedLunarReminder) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
			ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
			!= PackageManager.PERMISSION_GRANTED
		) {
			Napier.w("Notification permission not granted, cannot post lunar reminder")
			return
		}
		ensureChannel(context)

		val title = LunarTexts.title(context, reminder)
		val details = LunarTexts.details(context, reminder.subject, settings.getDateStyle())
		val intent = Intent(context, MainActivity::class.java).apply {
			flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
		}
		val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

		val notification = NotificationCompat.Builder(context, CHANNEL_ID)
			.setSmallIcon(R.drawable.ic_alert)
			.setContentTitle(title)
			.setContentText(details)
			.setStyle(NotificationCompat.BigTextStyle().bigText(details))
			.setCategory(NotificationCompat.CATEGORY_REMINDER)
			.setPriority(NotificationCompat.PRIORITY_DEFAULT)
			.setContentIntent(pendingIntent)
			.setAutoCancel(true)
			.build()

		context.getSystemService(NotificationManager::class.java)
			.notify(NOTIFICATION_TAG, reminder.notificationId, notification)
	}

	private fun ensureChannel(context: Context) {
		val channel = NotificationChannel(
			CHANNEL_ID,
			context.getString(R.string.lunar_channel_name),
			NotificationManager.IMPORTANCE_DEFAULT,
		).apply {
			description = context.getString(R.string.lunar_channel_description)
		}
		context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
	}
}
