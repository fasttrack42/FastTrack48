package com.legbehindneck.fasttrack48.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.legbehindneck.fasttrack48.R
import com.legbehindneck.fasttrack48.data.Stages
import com.legbehindneck.fasttrack48.data.activefast.ActiveFastPreferencesDataSource
import com.legbehindneck.fasttrack48.data.activefast.ActiveFastRepositoryImpl
import com.legbehindneck.fasttrack48.screens.main.MainActivity
import com.legbehindneck.fasttrack48.screens.settings.withAppLanguage
import com.legbehindneck.fasttrack48.utils.formatDuration
import com.legbehindneck.fasttrack48.utils.getColorFor
import kotlin.time.Clock
import kotlin.time.Duration

/**
 * Draws the home-screen widget as plain RemoteViews in three sizes, 1x1, 2x1 and 3x2, picked by
 * the launcher for the space the widget has. Plain RemoteViews, not Glance: no WorkManager job
 * and no DataStore behind each update, and about 440 KB less code in the app.
 *
 * Every text is set here, from the app's own language (the launcher would resolve a text in
 * the layout in the device's), and every tap opens the app: to the dial, or straight into
 * starting or ending a fast.
 */
object WidgetUpdater {

	// The sizes the Glance widget offered, in dp.
	private val SMALL = SizeF(64f, 64f)
	private val MEDIUM = SizeF(128f, 64f)
	private val LARGE = SizeF(196f, 128f)

	// Own request codes: activity intents to MainActivity elsewhere use 0 or the check-in's, and
	// PendingIntent identity ignores extras, so a shared code would lose the start/stop request.
	private const val REQUEST_OPEN = 2001
	private const val REQUEST_START = 2002
	private const val REQUEST_STOP = 2003

	fun hasWidgets(context: Context): Boolean = widgetIds(context).isNotEmpty()

	/** Redraws every placed widget; nothing to do when none is placed. */
	fun updateWidgets(context: Context) {
		val ids = widgetIds(context)
		if (ids.isNotEmpty()) render(context, AppWidgetManager.getInstance(context), ids)
	}

	internal fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
		val fast = ActiveFastRepositoryImpl(ActiveFastPreferencesDataSource(context.applicationContext), Clock.System)
		val shown = shown(withAppLanguage(context), fast.getElapsedFastTime().takeIf { fast.isFasting() })
		val taps = Taps(
			open = activity(context, REQUEST_OPEN, null),
			start = activity(context, REQUEST_START, MainActivity.START_FAST_EXTRA),
			stop = activity(context, REQUEST_STOP, MainActivity.STOP_FAST_EXTRA),
		)
		for (id in ids) manager.updateAppWidget(id, views(context.packageName, shown, taps, manager.getAppWidgetOptions(id)))
	}

	private fun widgetIds(context: Context): IntArray =
		AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, FastingWidgetReceiver::class.java))

	private fun views(pkg: String, shown: Shown, taps: Taps, options: Bundle): RemoteViews {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			return RemoteViews(mapOf(SMALL to small(pkg, shown, taps), MEDIUM to medium(pkg, shown, taps), LARGE to large(pkg, shown, taps)))
		}
		// Before Android 12 the launcher takes one layout per orientation: portrait is the widget's
		// minimum width by its maximum height, landscape its maximum width by its minimum height.
		// Until the launcher reports a size, both read 0 and the smallest layout is drawn.
		fun forSize(width: Int, height: Int): RemoteViews {
			val size = listOf(LARGE, MEDIUM, SMALL).firstOrNull { it.width <= width && it.height <= height } ?: SMALL
			return when (size) {
				LARGE -> large(pkg, shown, taps)
				MEDIUM -> medium(pkg, shown, taps)
				else -> small(pkg, shown, taps)
			}
		}
		val portrait = forSize(
			options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH),
			options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT),
		)
		val landscape = forSize(
			options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH),
			options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT),
		)
		return RemoteViews(landscape, portrait)
	}

	private fun small(pkg: String, s: Shown, t: Taps) = RemoteViews(pkg, R.layout.widget_small).apply {
		showFasting(s.fasting)
		if (s.fasting) {
			setInt(R.id.widget_phase, "setColorFilter", s.phaseColor)
			setTextViewText(R.id.widget_hours, s.hours)
		} else {
			setTextViewText(R.id.widget_start_label, s.startCompact)
		}
		setOnClickPendingIntent(android.R.id.background, if (s.fasting) t.open else t.start)
	}

	private fun medium(pkg: String, s: Shown, t: Taps) = RemoteViews(pkg, R.layout.widget_medium).apply {
		showFasting(s.fasting)
		if (s.fasting) {
			setInt(R.id.widget_phase, "setColorFilter", s.phaseColor)
			setTextViewText(R.id.widget_hours, s.hours)
			setTextViewText(R.id.widget_phase_title, s.phaseTitle)
		} else {
			setTextViewText(R.id.widget_not_fasting, s.notFasting)
			setTextViewText(R.id.widget_start_label, s.tapToStart)
		}
		setOnClickPendingIntent(android.R.id.background, if (s.fasting) t.open else t.start)
	}

	private fun large(pkg: String, s: Shown, t: Taps) = RemoteViews(pkg, R.layout.widget_large).apply {
		showFasting(s.fasting)
		if (s.fasting) {
			setTextViewText(R.id.widget_in_progress, s.inProgress)
			setInt(R.id.widget_phase, "setColorFilter", s.phaseColor)
			setTextViewText(R.id.widget_hours, s.hours)
			setInt(R.id.widget_phase_dot, "setColorFilter", s.phaseColor)
			setTextViewText(R.id.widget_phase_title, s.phaseTitle)
			setTextViewText(R.id.widget_energy, s.energy)
			setTextViewText(R.id.widget_stop, s.stopButton)
			setOnClickPendingIntent(R.id.widget_stop, t.stop)
		} else {
			setTextViewText(R.id.widget_not_fasting, s.notFasting)
			setTextViewText(R.id.widget_start, s.startButton)
			setOnClickPendingIntent(R.id.widget_start, t.start)
		}
		setOnClickPendingIntent(android.R.id.background, t.open)
	}

	private fun RemoteViews.showFasting(fasting: Boolean) {
		setViewVisibility(R.id.widget_fasting, if (fasting) View.VISIBLE else View.GONE)
		setViewVisibility(R.id.widget_idle, if (fasting) View.GONE else View.VISIBLE)
	}

	/**
	 * Opens the app. If it is already open, its task comes forward and the request reaches the
	 * running activity (onNewIntent) instead of stacking a second one.
	 */
	private fun activity(context: Context, requestCode: Int, extra: String?): PendingIntent {
		val intent = Intent(context, MainActivity::class.java).apply {
			flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
			extra?.let { putExtra(it, true) }
		}
		return PendingIntent.getActivity(
			context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
		)
	}

	private class Taps(val open: PendingIntent, val start: PendingIntent, val stop: PendingIntent)

	/** Everything one render shows, worked out once for every widget and size. */
	private class Shown(
		val fasting: Boolean,
		val hours: String,
		val phaseTitle: String,
		val energy: String,
		val phaseColor: Int,
		val inProgress: String,
		val notFasting: String,
		val tapToStart: String,
		val startCompact: String,
		val startButton: String,
		val stopButton: String,
	)

	/** [texts] carries the app's language; [elapsed] is null while no fast runs. */
	private fun shown(texts: Context, elapsed: Duration?): Shown {
		val phase = elapsed?.let { Stages.getCurrentPhase(it) }
		val energyMode = phase?.let {
			texts.getString(if (it.fatBurning) R.string.fasting_energy_mode_fat else R.string.fasting_energy_mode_glucose)
		}
		return Shown(
			fasting = elapsed != null,
			hours = elapsed?.let { formatDuration(texts, it, withMinutes = false) }.orEmpty(),
			phaseTitle = phase?.let { texts.getString(it.title) }.orEmpty(),
			energy = energyMode?.let { texts.getString(R.string.fasting_energy_mode, it) }.orEmpty(),
			phaseColor = phase?.let { getColorFor(it).toArgb() } ?: 0,
			inProgress = texts.getString(R.string.app_widget_fast_in_progress),
			notFasting = texts.getString(R.string.app_widget_not_fasting),
			tapToStart = texts.getString(R.string.app_widget_start),
			startCompact = texts.getString(R.string.app_widget_start_compact),
			startButton = texts.getString(R.string.app_widget_start_fast_button),
			stopButton = texts.getString(R.string.app_widget_stop_fast_button),
		)
	}
}
