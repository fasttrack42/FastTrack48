package com.legbehindneck.fasttrack48.screens.main

import android.app.ComponentCaller
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import com.legbehindneck.fasttrack48.BuildConfig
import com.legbehindneck.fasttrack48.FastingNotificationManager
import com.legbehindneck.fasttrack48.R
import com.legbehindneck.fasttrack48.data.activefast.ActiveFastRepository
import com.legbehindneck.fasttrack48.data.log.FastingLogRepository
import com.legbehindneck.fasttrack48.data.settings.DateStyle
import com.legbehindneck.fasttrack48.data.settings.SettingsDatasource
import com.legbehindneck.fasttrack48.data.settings.ThemeMode
import com.legbehindneck.fasttrack48.utils.LocalDateStyle
import com.legbehindneck.fasttrack48.screens.fasting.ExternalRequests
import com.legbehindneck.fasttrack48.screens.fasting.StartFastRequest
import com.legbehindneck.fasttrack48.screens.info.InfoActivity
import com.legbehindneck.fasttrack48.screens.intro.IntroActivity
import com.legbehindneck.fasttrack48.screens.settings.ExportFormatDialog
import com.legbehindneck.fasttrack48.screens.settings.ImportFormatsDialog
import com.legbehindneck.fasttrack48.screens.settings.SettingsActivity
import com.legbehindneck.fasttrack48.screens.settings.exportFasts
import com.legbehindneck.fasttrack48.screens.settings.registerLogImport
import com.legbehindneck.fasttrack48.ui.theme.FastTrackTheme
import io.github.aakira.napier.Napier
import org.koin.android.ext.android.inject
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalFoundationApi::class)
@ExperimentalTime
class MainActivity : AppCompatActivity() {
	private var startFastRequestState by mutableStateOf<StartFastRequest?>(null)
	private var stopFastRequestState by mutableStateOf(false)
	private var shareRequestState by mutableStateOf(false)
	private var showAboutState by mutableStateOf(false)
	private var showExportFormatState by mutableStateOf(false)
	private var showImportNoticeState by mutableStateOf(false)
	private var themeModeState by mutableStateOf(ThemeMode.SYSTEM)
	private var dateStyleState by mutableStateOf(DateStyle.OPTIMIZED_COMPACT)
	private val settings by inject<SettingsDatasource>()
	private val fastingRepository by inject<ActiveFastRepository>()
	private val logRepository by inject<FastingLogRepository>()

	// Registered in onCreate, as registerForActivityResult demands: the activity must not
	// have reached STARTED yet. Launched with "*/*" — the file's format is decided by
	// sniffing its bytes, not by whatever type the picker claims for it.
	private lateinit var importLauncher: ActivityResultLauncher<String>

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		enableEdgeToEdge()
		WindowCompat.getInsetsController(window, window.decorView)
			.isAppearanceLightStatusBars = false

		themeModeState = settings.getThemeMode()
		dateStyleState = settings.getDateStyle()
		handleStartFastExtra(intent)
		importLauncher = registerLogImport(logRepository)

		if (!settings.getIntroSeen()) {
			startActivity(Intent(this, IntroActivity::class.java))
		}

		setContent {
			FastTrackTheme(themeMode = themeModeState) {
				CompositionLocalProvider(LocalDateStyle provides dateStyleState) {
				MainScreen(
					onShareClick = { shareRequestState = true },
					onInfoClick = { startActivity(Intent(this, InfoActivity::class.java)) },
					onAboutClick = { showAboutState = true },
					onSettingsClick = { startActivity(Intent(this, SettingsActivity::class.java)) },
					// The overflow menu's fasting actions ride the same channel the
					// widget and deep links use, so they land in FastingScreen's own
					// start selector / end confirmation with no duplicated logic.
					onStartFastClick = { startFastRequestState = StartFastRequest(startNow = false) },
					onEndFastClick = { stopFastRequestState = true },
					onExportClick = { showExportFormatState = true },
					// The Settings row spells out the accepted formats in its subtitle;
					// a menu item has no room for one, so the notice stands in for it —
					// once, then never again.
					onImportClick = {
						if (settings.getImportFormatsSeen()) importLauncher.launch("*/*")
						else showImportNoticeState = true
					},
					externalRequests = ExternalRequests(
						startFastRequest = startFastRequestState,
						stopFastRequested = stopFastRequestState,
						shareRequested = shareRequestState,
						consumeStartFastRequest = { startFastRequestState = null },
						consumeStopFastRequest = { stopFastRequestState = false },
						consumeShareRequest = { shareRequestState = false },
					),
				)

				if (showAboutState) {
					AboutDialog(
						versionName = BuildConfig.VERSION_NAME,
						onOpenUrl = { url -> openUrl(url) },
						onRateApp = { rateApp() },
						onShareApp = { shareApp() },
						onDismiss = { showAboutState = false },
					)
				}

				if (showExportFormatState) {
					ExportFormatDialog(
						onDismiss = { showExportFormatState = false },
						onSelect = { format -> exportFasts(logRepository, format) },
					)
				}

				if (showImportNoticeState) {
					ImportFormatsDialog(
						onDismiss = { showImportNoticeState = false },
						onContinue = {
							showImportNoticeState = false
							settings.setImportFormatsSeen(true)
							importLauncher.launch("*/*")
						},
					)
				}
				}
			}
		}
	}

	override fun onStart() {
		super.onStart()
		val currentMode = settings.getThemeMode()
		if (currentMode != themeModeState) {
			themeModeState = currentMode
		}
		val currentDateStyle = settings.getDateStyle()
		if (currentDateStyle != dateStyleState) {
			dateStyleState = currentDateStyle
		}
		setupFastingNotification()
	}

	private fun setupFastingNotification() {
		val shouldShowNotification = settings.getShowFastingNotification()

		if (fastingRepository.isFasting() && shouldShowNotification) {
			val elapsedTime = fastingRepository.getElapsedFastTime()
			FastingNotificationManager.postFastingNotification(this, elapsedTime)
		} else {
			FastingNotificationManager.cancelFastingNotification(this)
		}
	}

	// Only the single-arg override handles the intent: the platform's
	// (Intent, ComponentCaller) default delegates to it, so handling here too
	// would process every new intent twice.
	override fun onNewIntent(intent: Intent, caller: ComponentCaller) {
		super.onNewIntent(intent, caller)
	}

	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		handleStartFastExtra(intent)
	}

	private fun handleStartFastExtra(intent: Intent?) {
		if (intent?.getBooleanExtra(START_FAST_EXTRA, false) == true) {
			val startNow = intent.getBooleanExtra(START_FAST_NOW_EXTRA, false)
			startFastRequestState = StartFastRequest(startNow)
		} else if (intent?.getBooleanExtra(STOP_FAST_EXTRA, false) == true) {
			stopFastRequestState = true
		}
	}

	private fun openUrl(url: String) {
		try {
			startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
		} catch (e: ActivityNotFoundException) {
			Napier.w("No activity available to open url: $url", e)
		}
	}

	private fun rateApp() {
		try {
			startActivity(Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri()))
		} catch (_: ActivityNotFoundException) {
			// No Play Store on this device; fall back to the web listing
			openUrl("https://play.google.com/store/apps/details?id=$packageName")
		}
	}

	private fun shareApp() {
		val shareText =
			"${getString(R.string.app_name)} — https://play.google.com/store/apps/details?id=$packageName"
		val intent = Intent(Intent.ACTION_SEND).apply {
			type = "text/plain"
			putExtra(Intent.EXTRA_TEXT, shareText)
		}
		startActivity(Intent.createChooser(intent, null))
	}

	companion object {
		const val START_FAST_EXTRA = "START_FAST"
		const val START_FAST_NOW_EXTRA = "START_FAST_NOW"
		const val STOP_FAST_EXTRA = "STOP_FAST"
	}
}
