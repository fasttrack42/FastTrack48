package com.legbehindneck.fasttrack48.screens.settings

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.legbehindneck.fasttrack48.R
import com.legbehindneck.fasttrack48.data.settings.DateStyle
import com.legbehindneck.fasttrack48.lunar.DeviceLocation
import com.legbehindneck.fasttrack48.lunar.EkadashiTradition
import com.legbehindneck.fasttrack48.lunar.FastDays
import com.legbehindneck.fasttrack48.lunar.LunarCalendar
import com.legbehindneck.fasttrack48.lunar.LunarDay
import com.legbehindneck.fasttrack48.lunar.LunarLocation
import com.legbehindneck.fasttrack48.lunar.LunarNotifier
import com.legbehindneck.fasttrack48.lunar.LunarReminderScheduler
import com.legbehindneck.fasttrack48.lunar.LunarReminderSettings
import com.legbehindneck.fasttrack48.lunar.LunarSettingsStore
import com.legbehindneck.fasttrack48.lunar.LunarTexts
import com.legbehindneck.fasttrack48.lunar.MoonDayStyle
import com.legbehindneck.fasttrack48.lunar.MoonPhase
import com.legbehindneck.fasttrack48.lunar.SavedLocation
import com.legbehindneck.fasttrack48.screens.fastdays.FastDaysSheet
import com.legbehindneck.fasttrack48.utils.AppDateTime
import com.legbehindneck.fasttrack48.utils.rememberToday
import com.legbehindneck.fasttrack48.utils.shouldUse24HourFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

private enum class LunarDialog { NONE, ENABLE_EKADASHI, TRADITION, MOON_DAY_STYLE, HOUR, LOCATION }

/** How far ahead the settings preview looks for the next observance. */
private const val PREVIEW_DAYS = 60

/**
 * Ekadashi, full-moon (Purnima) and new-moon (Amavasya) reminders: off until the user turns
 * them on. The first time Ekadashi is turned on, the user picks a calendar - the Smarta and
 * Vaishnava fast days differ on about 1 Ekadashi in 13, so there is no safe default. The
 * calendar also picks the Purnima and Amavasya fast days, so its row stays while either
 * follows the Hindu calendar. Choosing Ananda Marga turns on all four of its fasts, as
 * suggestions: each can be turned off again.
 */
@Composable
internal fun LunarSettingsSection(dateStyle: DateStyle) {
	val context = LocalContext.current
	// Location results can land after this screen is gone (a recreation mid-request), so
	// anything they touch outlives the activity.
	val appContext = context.applicationContext
	var zone by remember { mutableStateOf(ZoneId.systemDefault()) }
	var settings by remember { mutableStateOf(LunarSettingsStore.load(context)) }
	val locationVersion by LunarSettingsStore.locationVersion.collectAsState()
	val savedLocation = remember(locationVersion) { LunarSettingsStore.loadLocation(context) }
	var notificationsOn by remember { mutableStateOf(notificationsAllowed(context)) }
	val findingLocation by DeviceLocation.inProgress.collectAsState()
	var dialog by remember { mutableStateOf(LunarDialog.NONE) }
	var showFastDays by remember { mutableStateOf(false) }

	// Notifications can be turned on in system settings, and the time zone can change, while
	// this screen is in the background.
	val lifecycleOwner = context as? LifecycleOwner
	DisposableEffect(lifecycleOwner) {
		val observer = LifecycleEventObserver { _, event ->
			if (event == Lifecycle.Event.ON_RESUME) {
				notificationsOn = notificationsAllowed(context)
				zone = ZoneId.systemDefault()
			}
		}
		lifecycleOwner?.lifecycle?.addObserver(observer)
		onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
	}

	fun update(new: LunarReminderSettings) {
		settings = new
		LunarSettingsStore.save(context, new)
		LunarReminderScheduler.onSettingsChanged(context)
	}

	val notificationPermission = rememberLauncherForActivityResult(
		ActivityResultContracts.RequestPermission()
	) { notificationsOn = notificationsAllowed(context) }

	fun askForNotifications() {
		if (!notificationsOn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
		}
	}

	fun onLocationOutcome(outcome: DeviceLocation.Outcome) {
		val message = when (outcome) {
			is DeviceLocation.Outcome.Found -> {
				val saved = SavedLocation(outcome.point, ZoneId.systemDefault().id, System.currentTimeMillis())
				LunarSettingsStore.saveLocation(appContext, saved)
				LunarReminderScheduler.onSettingsChanged(appContext)
				R.string.lunar_location_saved
			}
			DeviceLocation.Outcome.NoPermission -> R.string.lunar_location_denied
			DeviceLocation.Outcome.LocationOff -> R.string.lunar_location_off
			DeviceLocation.Outcome.NotFound -> R.string.lunar_location_not_found
		}
		Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
	}

	fun findLocation() {
		DeviceLocation.requestOnce(appContext, ::onLocationOutcome)
	}

	val locationPermission = rememberLauncherForActivityResult(
		ActivityResultContracts.RequestPermission()
	) { granted ->
		if (granted) findLocation() else onLocationOutcome(DeviceLocation.Outcome.NoPermission)
	}

	val location = remember(savedLocation, zone) { LunarLocation.resolve(context, zone) }
	// A key for the previews below, so "next" moves on when the day does.
	val today = rememberToday(zone)
	val nextEkadashi by produceState<LunarDay?>(null, settings.ekadashiEnabled, settings.tradition, location, today) {
		val tradition = settings.tradition
		value = if (settings.ekadashiEnabled && tradition != null) {
			withContext(Dispatchers.Default) {
				LunarCalendar.ekadashis(today, PREVIEW_DAYS, location.point, zone, tradition).firstOrNull()
			}
		} else {
			null
		}
	}
	val nextFullMoon by produceState<LunarDay?>(
		null, settings.fullMoonEnabled, settings.moonDayStyle, settings.tradition, location, today,
	) {
		value = if (settings.fullMoonEnabled) {
			withContext(Dispatchers.Default) {
				LunarCalendar.moonDays(
					today, PREVIEW_DAYS, location.point, zone, MoonPhase.FULL, settings.moonDayStyle,
					settings.tradition,
				).firstOrNull()
			}
		} else {
			null
		}
	}
	val nextNewMoon by produceState<LunarDay?>(
		null, settings.newMoonEnabled, settings.moonDayStyle, settings.tradition, location, today,
	) {
		value = if (settings.newMoonEnabled) {
			withContext(Dispatchers.Default) {
				LunarCalendar.moonDays(
					today, PREVIEW_DAYS, location.point, zone, MoonPhase.NEW, settings.moonDayStyle,
					settings.tradition,
				).firstOrNull()
			}
		} else {
			null
		}
	}

	val anyEnabled = settings.ekadashiEnabled || settings.fullMoonEnabled || settings.newMoonEnabled

	Column {
		if (anyEnabled && !notificationsOn) {
			NotificationsOffRow(onTurnOn = { openNotificationSettings(context) })
		}

		LunarSwitchRow(
			title = stringResource(R.string.lunar_ekadashi_title),
			subtitle = when {
				!settings.ekadashiEnabled -> stringResource(R.string.lunar_ekadashi_subtitle_off)
				settings.ekadashiLeadDays.isEmpty() -> stringResource(R.string.lunar_no_days_selected)
				else -> nextEkadashi?.let { LunarTexts.details(context, it, dateStyle) }
					?: stringResource(R.string.lunar_calculating)
			},
			checked = settings.ekadashiEnabled,
			onChange = { checked ->
				if (checked && settings.tradition == null) {
					dialog = LunarDialog.ENABLE_EKADASHI
				} else {
					update(settings.copy(ekadashiEnabled = checked))
					if (checked) askForNotifications()
				}
			},
		)
		if (settings.ekadashiEnabled) {
			LeadDayChips(selected = settings.ekadashiLeadDays) { update(settings.copy(ekadashiLeadDays = it)) }
		}

		LunarSwitchRow(
			title = stringResource(R.string.lunar_fullmoon_title),
			subtitle = when {
				!settings.fullMoonEnabled -> stringResource(R.string.lunar_fullmoon_subtitle_off)
				settings.fullMoonLeadDays.isEmpty() -> stringResource(R.string.lunar_no_days_selected)
				else -> nextFullMoon?.let { LunarTexts.details(context, it, dateStyle) }
					?: stringResource(R.string.lunar_calculating)
			},
			checked = settings.fullMoonEnabled,
			onChange = { checked ->
				update(settings.copy(fullMoonEnabled = checked))
				if (checked) askForNotifications()
			},
		)
		if (settings.fullMoonEnabled) {
			LeadDayChips(selected = settings.fullMoonLeadDays) { update(settings.copy(fullMoonLeadDays = it)) }
		}

		LunarSwitchRow(
			title = stringResource(R.string.lunar_newmoon_title),
			subtitle = when {
				!settings.newMoonEnabled -> stringResource(R.string.lunar_newmoon_subtitle_off)
				settings.newMoonLeadDays.isEmpty() -> stringResource(R.string.lunar_no_days_selected)
				else -> nextNewMoon?.let { LunarTexts.details(context, it, dateStyle) }
					?: stringResource(R.string.lunar_calculating)
			},
			checked = settings.newMoonEnabled,
			onChange = { checked ->
				update(settings.copy(newMoonEnabled = checked))
				if (checked) askForNotifications()
			},
		)
		if (settings.newMoonEnabled) {
			LeadDayChips(selected = settings.newMoonLeadDays) { update(settings.copy(newMoonLeadDays = it)) }
		}

		// The calendar picks every Hindu-calendar fast day, so it heads the shared rows.
		val moonDaysFollowCalendar =
			(settings.fullMoonEnabled || settings.newMoonEnabled) && settings.moonDayStyle == MoonDayStyle.VEDIC
		if (settings.ekadashiEnabled || moonDaysFollowCalendar) {
			LunarValueRow(
				title = stringResource(R.string.lunar_tradition_title),
				value = stringResource(traditionLabel(settings.tradition ?: EkadashiTradition.SMARTA)),
				onClick = { dialog = LunarDialog.TRADITION },
			)
		}

		if (settings.fullMoonEnabled || settings.newMoonEnabled) {
			LunarValueRow(
				title = stringResource(R.string.lunar_moon_style_title),
				value = stringResource(moonDayStyleLabel(settings.moonDayStyle)),
				onClick = { dialog = LunarDialog.MOON_DAY_STYLE },
			)
		}

		if (anyEnabled) {
			LunarValueRow(
				title = stringResource(R.string.lunar_reminder_time_title),
				value = hourLabel(context, settings.hourOfDay, dateStyle),
				onClick = { dialog = LunarDialog.HOUR },
			)
			LunarValueRow(
				title = stringResource(R.string.lunar_location_title),
				value = when {
					findingLocation -> stringResource(R.string.lunar_location_finding)
					location.fromDevice -> stringResource(
						R.string.lunar_location_device,
						LunarTexts.date(
							context,
							Instant.ofEpochMilli(savedLocation?.savedAtMillis ?: 0L).atZone(zone).toLocalDate(),
							dateStyle,
						),
					)
					else -> stringResource(R.string.lunar_location_timezone, LunarLocation.zoneLabel(zone))
				},
				onClick = { if (!findingLocation) dialog = LunarDialog.LOCATION },
			)
		}

		if (FastDays.follows(settings)) {
			ListItem(
				headlineContent = {
					Text(
						text = stringResource(R.string.fast_days_see_all),
						style = MaterialTheme.typography.labelLarge,
						fontWeight = FontWeight.Bold,
						color = MaterialTheme.colorScheme.primary,
					)
				},
				modifier = Modifier.clickable { showFastDays = true },
			)
		}
	}

	if (showFastDays) {
		FastDaysSheet(dateStyle = dateStyle, onDismiss = { showFastDays = false })
	}

	when (dialog) {
		LunarDialog.NONE -> Unit
		LunarDialog.ENABLE_EKADASHI, LunarDialog.TRADITION -> {
			val enabling = dialog == LunarDialog.ENABLE_EKADASHI
			ChoiceDialog(
				title = stringResource(R.string.lunar_tradition_question),
				options = EkadashiTradition.entries.map { it to (traditionLabel(it) to traditionDetail(it)) },
				selected = settings.tradition,
				onPick = { tradition ->
					dialog = LunarDialog.NONE
					// Ananda Marga keeps four fasts, so joining it suggests all four; switching
					// between its two rules, or leaving it, keeps the switches as they are.
					val joining = tradition.anandaMarga && settings.tradition?.anandaMarga != true
					update(
						if (joining) {
							settings.copy(
								tradition = tradition,
								ekadashiEnabled = true,
								fullMoonEnabled = true,
								newMoonEnabled = true,
								moonDayStyle = MoonDayStyle.VEDIC,
							)
						} else {
							settings.copy(tradition = tradition, ekadashiEnabled = enabling || settings.ekadashiEnabled)
						}
					)
					if (enabling || joining) askForNotifications()
				},
				onDismiss = { dialog = LunarDialog.NONE },
			)
		}
		LunarDialog.MOON_DAY_STYLE -> ChoiceDialog(
			title = stringResource(R.string.lunar_moon_style_title),
			options = MoonDayStyle.entries.map { it to (moonDayStyleLabel(it) to moonDayStyleDetail(it)) },
			selected = settings.moonDayStyle,
			onPick = { style ->
				dialog = LunarDialog.NONE
				update(settings.copy(moonDayStyle = style))
			},
			onDismiss = { dialog = LunarDialog.NONE },
		)
		LunarDialog.HOUR -> HourDialog(
			initialHour = settings.hourOfDay,
			label = { hourLabel(context, it, dateStyle) },
			onSave = { hour ->
				dialog = LunarDialog.NONE
				update(settings.copy(hourOfDay = hour))
			},
			onDismiss = { dialog = LunarDialog.NONE },
		)
		LunarDialog.LOCATION -> LocationDialog(
			usingDeviceLocation = location.fromDevice,
			onUseLocation = {
				dialog = LunarDialog.NONE
				if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
					== PackageManager.PERMISSION_GRANTED
				) {
					findLocation()
				} else {
					locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
				}
			},
			onUseTimeZone = {
				dialog = LunarDialog.NONE
				LunarSettingsStore.saveLocation(context, null)
				LunarReminderScheduler.onSettingsChanged(context)
			},
			onDismiss = { dialog = LunarDialog.NONE },
		)
	}
}

@Composable
private fun LunarSwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
	ListItem(
		headlineContent = {
			Text(text = title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
		},
		supportingContent = { Text(text = subtitle, style = MaterialTheme.typography.bodySmall) },
		trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
	)
}

@Composable
private fun LunarValueRow(title: String, value: String, onClick: () -> Unit) {
	ListItem(
		headlineContent = {
			Text(text = title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
		},
		supportingContent = { Text(text = value, style = MaterialTheme.typography.bodySmall) },
		modifier = Modifier.clickable(onClick = onClick),
	)
}

@Composable
private fun NotificationsOffRow(onTurnOn: () -> Unit) {
	ListItem(
		headlineContent = {
			Text(
				text = stringResource(R.string.lunar_notifications_off),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.error,
			)
		},
		trailingContent = {
			TextButton(onClick = onTurnOn) { Text(stringResource(R.string.lunar_notifications_turn_on)) }
		},
	)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LeadDayChips(selected: Set<Int>, onChange: (Set<Int>) -> Unit) {
	Column(modifier = Modifier.padding(horizontal = 16.dp)) {
		Text(
			text = stringResource(R.string.lunar_remind_me),
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
			LunarReminderSettings.LEAD_OPTIONS.forEach { lead ->
				FilterChip(
					selected = lead in selected,
					onClick = { onChange(if (lead in selected) selected - lead else selected + lead) },
					label = { Text(stringResource(leadLabel(lead))) },
				)
			}
		}
	}
}

@Composable
private fun <T> ChoiceDialog(
	title: String,
	options: List<Pair<T, Pair<Int, Int>>>,
	selected: T?,
	onPick: (T) -> Unit,
	onDismiss: () -> Unit,
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(title) },
		text = {
			Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
				options.forEach { (value, labels) ->
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.clickable { onPick(value) }
							.padding(vertical = 8.dp),
						verticalAlignment = Alignment.CenterVertically,
					) {
						RadioButton(selected = value == selected, onClick = { onPick(value) })
						Column(modifier = Modifier.padding(start = 8.dp)) {
							Text(text = stringResource(labels.first), style = MaterialTheme.typography.bodyLarge)
							Text(
								text = stringResource(labels.second),
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
						}
					}
				}
			}
		},
		confirmButton = {
			TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_button)) }
		},
	)
}

@Composable
private fun HourDialog(initialHour: Int, label: (Int) -> String, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
	var hour by remember { mutableFloatStateOf(initialHour.toFloat()) }
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(stringResource(R.string.lunar_reminder_time_title)) },
		text = {
			Column {
				Text(text = label(hour.roundToInt()), style = MaterialTheme.typography.headlineSmall)
				Slider(value = hour, onValueChange = { hour = it }, valueRange = 0f..23f, steps = 22)
			}
		},
		confirmButton = {
			TextButton(onClick = { onSave(hour.roundToInt().coerceIn(0, 23)) }) {
				Text(stringResource(R.string.lunar_save))
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_button)) }
		},
	)
}

@Composable
private fun LocationDialog(
	usingDeviceLocation: Boolean,
	onUseLocation: () -> Unit,
	onUseTimeZone: () -> Unit,
	onDismiss: () -> Unit,
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(stringResource(R.string.lunar_location_dialog_title)) },
		text = {
			Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
				Text(stringResource(R.string.lunar_location_dialog_text))
				if (usingDeviceLocation) {
					TextButton(onClick = onUseTimeZone) { Text(stringResource(R.string.lunar_location_use_timezone)) }
				}
			}
		},
		confirmButton = {
			TextButton(onClick = onUseLocation) { Text(stringResource(R.string.lunar_location_use)) }
		},
		dismissButton = {
			TextButton(onClick = onDismiss) { Text(stringResource(R.string.lunar_location_not_now)) }
		},
	)
}

/**
 * Whether a reminder can appear: notifications are on for the app, and the reminders' own
 * channel, which the user can block on its own, is not blocked. The channel exists only once
 * a reminder has been posted; until then the app-wide setting decides.
 */
private fun notificationsAllowed(context: Context): Boolean {
	val manager = NotificationManagerCompat.from(context)
	if (!manager.areNotificationsEnabled()) return false
	val channel = manager.getNotificationChannel(LunarNotifier.CHANNEL_ID) ?: return true
	return channel.importance != NotificationManager.IMPORTANCE_NONE
}

/** The reminders' channel page when only that channel is blocked, else the app's page. */
private fun openNotificationSettings(context: Context) {
	val appOn = NotificationManagerCompat.from(context).areNotificationsEnabled()
	val intent = if (appOn) {
		Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
			.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
			.putExtra(Settings.EXTRA_CHANNEL_ID, LunarNotifier.CHANNEL_ID)
	} else {
		Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
			.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
	}
	context.startActivity(intent)
}

private fun hourLabel(context: Context, hour: Int, style: DateStyle): String =
	AppDateTime.formatTime(LocalDateTime(2000, 1, 1, hour, 0), style, shouldUse24HourFormat(context))

@StringRes
private fun leadLabel(lead: Int): Int = when (lead) {
	0 -> R.string.lunar_lead_same_day
	1 -> R.string.lunar_lead_1_day
	2 -> R.string.lunar_lead_2_days
	else -> R.string.lunar_lead_3_days
}

@StringRes
private fun traditionLabel(tradition: EkadashiTradition): Int = when (tradition) {
	EkadashiTradition.SMARTA -> R.string.lunar_tradition_smarta
	EkadashiTradition.VAISHNAVA -> R.string.lunar_tradition_vaishnava
	EkadashiTradition.ANANDA_MARGA -> R.string.lunar_tradition_ananda_marga
	EkadashiTradition.ANANDA_MARGA_INDIA -> R.string.lunar_tradition_ananda_marga_india
}

@StringRes
private fun traditionDetail(tradition: EkadashiTradition): Int = when (tradition) {
	EkadashiTradition.SMARTA -> R.string.lunar_tradition_smarta_detail
	EkadashiTradition.VAISHNAVA -> R.string.lunar_tradition_vaishnava_detail
	EkadashiTradition.ANANDA_MARGA -> R.string.lunar_tradition_ananda_marga_detail
	EkadashiTradition.ANANDA_MARGA_INDIA -> R.string.lunar_tradition_ananda_marga_india_detail
}

@StringRes
private fun moonDayStyleLabel(style: MoonDayStyle): Int = when (style) {
	MoonDayStyle.VEDIC -> R.string.lunar_moon_style_vedic
	MoonDayStyle.ASTRONOMICAL -> R.string.lunar_moon_style_astronomical
}

@StringRes
private fun moonDayStyleDetail(style: MoonDayStyle): Int = when (style) {
	MoonDayStyle.VEDIC -> R.string.lunar_moon_style_vedic_detail
	MoonDayStyle.ASTRONOMICAL -> R.string.lunar_moon_style_astronomical_detail
}
