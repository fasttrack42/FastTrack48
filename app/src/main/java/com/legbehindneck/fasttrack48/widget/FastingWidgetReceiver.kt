package com.legbehindneck.fasttrack48.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import com.legbehindneck.fasttrack48.AlertService

/**
 * The home-screen widget's provider. The launcher calls it when a widget is placed or resized
 * and after a reboot; nothing calls it on a timer (updatePeriodMillis is 0). While a fast runs,
 * AlertService's hourly tick redraws it, and the app redraws it when a fast starts or ends.
 */
class FastingWidgetReceiver : AppWidgetProvider() {

	override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
		WidgetUpdater.render(context, appWidgetManager, appWidgetIds)
		// A widget placed mid-fast needs the hourly tick, even with the notification off.
		AlertService.syncHourlyTick(context)
	}

	// Below Android 12 the layout follows the size reported here; from 12 the launcher picks it.
	override fun onAppWidgetOptionsChanged(
		context: Context,
		appWidgetManager: AppWidgetManager,
		appWidgetId: Int,
		newOptions: Bundle,
	) {
		WidgetUpdater.render(context, appWidgetManager, intArrayOf(appWidgetId))
	}

	// The last widget gone: the hourly tick may have run only for it.
	override fun onDisabled(context: Context) {
		AlertService.syncHourlyTick(context)
	}
}
