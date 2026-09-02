package com.legbehindneck.fasttrack48.screens.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.legbehindneck.fasttrack48.FastingNotificationManager
import com.legbehindneck.fasttrack48.data.activefast.ActiveFastRepository
import com.legbehindneck.fasttrack48.data.log.FastingLogRepository
import com.legbehindneck.fasttrack48.data.log.ImportResult
import com.legbehindneck.fasttrack48.data.settings.SettingsDatasource
import com.legbehindneck.fasttrack48.data.settings.ThemeMode
import com.legbehindneck.fasttrack48.ui.theme.FastTrackTheme
import io.github.aakira.napier.Napier
import org.koin.android.ext.android.inject
import java.util.Locale

class SettingsActivity : AppCompatActivity() {
	private val settings by inject<SettingsDatasource>()
	private val activeFastRepository by inject<ActiveFastRepository>()
	private val logRepository by inject<FastingLogRepository>()
	private lateinit var requestNotificationPermission: ActivityResultLauncher<String>
	private lateinit var getContent: ActivityResultLauncher<String>
	private var pendingNotificationToggle = false
	private var notificationSettingState by mutableStateOf(false)
	private var stageAlertsSettingState by mutableStateOf(false)
	private var metricSystemSettingState by mutableStateOf(false)
	private var themeModeState by mutableStateOf(ThemeMode.SYSTEM)
	private var importResultState by mutableStateOf<ImportResult?>(null)

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		WindowCompat.getInsetsController(window, window.decorView)
			.isAppearanceLightStatusBars = false

		notificationSettingState = settings.getShowFastingNotification()
		stageAlertsSettingState = settings.getFastingAlerts()
		metricSystemSettingState = settings.getUseMetricSystem(default = isMetricSystemLocale())
		themeModeState = settings.getThemeMode()
		registerNotificationPermissionCallback()
		getContent = registerLogImport(logRepository) { result -> importResultState = result }

		setContent {
			FastTrackTheme(themeMode = themeModeState) {
				SettingsScreen(
					onBack = { finish() },
					settings = settings,
					notificationSettingState = notificationSettingState,
					onNotificationSettingChanged = { enabled -> handleNotificationSettingChange(enabled) },
					stageAlertsSettingState = stageAlertsSettingState,
					onStageAlertsSettingChanged = { enabled -> handleStageAlertsSettingChange(enabled) },
					metricSystemSettingState = metricSystemSettingState,
					onMetricSystemSettingChanged = { enabled -> handleMetricSystemSettingChange(enabled) },
					themeModeState = themeModeState,
					onThemeModeChanged = { mode -> handleThemeModeChange(mode) },
					onExportClick = { format -> exportFasts(logRepository, format) },
					// "*/*": the file's format is decided by sniffing its bytes, not by
					// whatever type the picker claims for it.
					onImportClick = { getContent.launch("*/*") }
				)

				importResultState?.let { result ->
					ImportResultDialog(
						result = result,
						onDismiss = { importResultState = null },
					)
				}
			}
		}
	}

	private fun registerNotificationPermissionCallback() {
		requestNotificationPermission = registerForActivityResult(
			ActivityResultContracts.RequestPermission()
		) { isGranted: Boolean ->
			if (isGranted) {
				Napier.d("Notification permission granted")
				if (pendingNotificationToggle) {
					settings.setShowFastingNotification(true)
					notificationSettingState = true
					pendingNotificationToggle = false

					// Show the notification if there's an active fast
					if (activeFastRepository.isFasting()) {
						val elapsedTime = activeFastRepository.getElapsedFastTime()
						FastingNotificationManager.postFastingNotification(this, elapsedTime)
					}
				}
			} else {
				Napier.w("Notification permission denied")
				// Reset the toggle since permission was denied
				settings.setShowFastingNotification(false)
				notificationSettingState = false
				pendingNotificationToggle = false
			}
		}
	}

	private fun handleNotificationSettingChange(enabled: Boolean) {
		if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			when {
				ContextCompat.checkSelfPermission(
					this,
					Manifest.permission.POST_NOTIFICATIONS
				) == PackageManager.PERMISSION_GRANTED -> {
					Napier.d("Notification permission already granted")
					settings.setShowFastingNotification(true)
					notificationSettingState = true

					// Show the notification if there's an active fast
					if (activeFastRepository.isFasting()) {
						val elapsedTime = activeFastRepository.getElapsedFastTime()
						FastingNotificationManager.postFastingNotification(this, elapsedTime)
					}
				}

				else -> {
					Napier.d("Requesting notification permission")
					pendingNotificationToggle = true
					requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
				}
			}
		} else {
			// Either disabled or Android < 13 (no permission needed)
			settings.setShowFastingNotification(enabled)
			notificationSettingState = enabled

			if (enabled) {
				// Show the notification if there's an active fast
				if (activeFastRepository.isFasting()) {
					val elapsedTime = activeFastRepository.getElapsedFastTime()
					FastingNotificationManager.postFastingNotification(this, elapsedTime)
				}
			} else {
				// Dismiss the notification if it's currently displayed
				FastingNotificationManager.cancelFastingNotification(this)
			}
		}
	}

	private fun handleStageAlertsSettingChange(enabled: Boolean) {
		settings.setFastingAlerts(enabled)
		stageAlertsSettingState = enabled
	}

	private fun handleMetricSystemSettingChange(enabled: Boolean) {
		settings.setUseMetricSystem(enabled)
		metricSystemSettingState = enabled
	}

	private fun handleThemeModeChange(mode: ThemeMode) {
		if (mode == themeModeState) return
		settings.setThemeMode(mode)
		themeModeState = mode
		recreate()
	}

	private fun isMetricSystemLocale(): Boolean {
		val locale: Locale = LocaleList.getDefault()[0]
		val imperialCountries = listOf("US", "LR", "MM")
		return !imperialCountries.contains(locale.country)
	}
}
