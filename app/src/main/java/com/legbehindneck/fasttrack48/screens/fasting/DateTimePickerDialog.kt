package com.legbehindneck.fasttrack48.screens.fasting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.getSelectedDate
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.legbehindneck.fasttrack48.R
import com.legbehindneck.fasttrack48.screens.preview.getContext
import com.legbehindneck.fasttrack48.utils.DateRangeSelectableDates
import com.legbehindneck.fasttrack48.utils.shouldUse24HourFormat
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toInstant
import kotlinx.datetime.toKotlinMonth
import kotlinx.datetime.toLocalDateTime
import java.util.Calendar
import kotlin.time.ExperimentalTime
import kotlin.time.Instant


/** Whole days, in milliseconds — the unit DatePicker and SelectableDates both work in. */
private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

/**
 * State for the DateTimePickerDialog
 */
class DateTimePickerDialogState {
	var currentStep by mutableIntStateOf(0)
}

/**
 * Create and remember a DateTimePickerDialogState
 */
@Composable
fun rememberDateTimePickerDialogState(): DateTimePickerDialogState {
	return remember { DateTimePickerDialogState() }
}

@ExperimentalTime
@Composable
fun DateTimePickerDialog(
	onDismiss: () -> Unit,
	onDateTimeSelected: (Instant) -> Unit,
	title: String,
	finishButton: String,
	state: DateTimePickerDialogState = rememberDateTimePickerDialogState(),
	initialInstant: Instant? = null,
	minInstant: Instant? = null,
	maxInstant: Instant? = null,
) {
	val initialDateTime = remember(initialInstant) {
		initialInstant?.let { instant ->
			val kotlinxInstant = Instant.fromEpochMilliseconds(instant.toEpochMilliseconds())
			kotlinxInstant.toLocalDateTime(TimeZone.currentSystemDefault())
		}
	}

	val minDateTime = remember(minInstant) {
		minInstant?.let { instant ->
			val kotlinxInstant = Instant.fromEpochMilliseconds(instant.toEpochMilliseconds())
			kotlinxInstant.toLocalDateTime(TimeZone.currentSystemDefault())
		}
	}

	val maxDateTime = remember(maxInstant) {
		maxInstant?.let { instant ->
			val kotlinxInstant = Instant.fromEpochMilliseconds(instant.toEpochMilliseconds())
			kotlinxInstant.toLocalDateTime(TimeZone.currentSystemDefault())
		}
	}

	// DatePicker speaks in UTC-midnight milliseconds: it recovers the day with
	// `epochMillis / 86_400_000`, and DateRangeSelectableDates does the same. Handing it the
	// *local* midnight of the same calendar day lands in the previous day for every zone east
	// of UTC, which preselected the day before the fast actually started.
	val minDateMillis = remember(minDateTime) {
		minDateTime?.date?.toEpochDays()?.times(MILLIS_PER_DAY)
	}

	val initialDateMillis = remember(initialDateTime) {
		initialDateTime?.date?.toEpochDays()?.times(MILLIS_PER_DAY)
	}

	// rememberDatePickerState / rememberTimePickerState read their initial values exactly once
	// and never again — they are rememberSaveable under the hood, with no keys. Reopening the
	// editor with a start time that has since been corrected would therefore restore the value
	// from the first open. key() puts each state in its own composition group, so a changed
	// initial value builds a new state instead of resurrecting the stale one.
	val datePickerState = key(initialDateMillis, minDateMillis) {
		rememberDatePickerState(
			initialSelectedDateMillis = initialDateMillis,
			selectableDates = DateRangeSelectableDates(minDateMillis)
		)
	}

	// Use initial time if provided, otherwise the time the dialog opened. Frozen in a remember:
	// read live it would advance every recomposition and, being a key below, reset the time
	// picker under the user's finger once a minute.
	val openedAt = remember { Calendar.getInstance() }
	val initialHour = initialDateTime?.hour ?: openedAt[Calendar.HOUR_OF_DAY]
	val initialMinute = initialDateTime?.minute ?: openedAt[Calendar.MINUTE]

	val timePickerState = key(initialHour, initialMinute) {
		rememberTimePickerState(
			initialHour = initialHour,
			initialMinute = initialMinute,
			is24Hour = shouldUse24HourFormat(getContext()),
		)
	}

	val isNextButtonEnabled = remember(
		datePickerState.selectedDateMillis,
		timePickerState.hour,
		timePickerState.minute,
		state.currentStep,
		minDateTime,
		maxDateTime
	) {
		when (state.currentStep) {
			0 -> datePickerState.selectedDateMillis != null
			1 -> {
				val selectedDate = datePickerState.getSelectedDate()
				if (selectedDate == null) {
					false
				} else {
					// The date picker enforces both bounds, but only at day granularity.
					// The one case it cannot catch is a time-of-day out of range on a day
					// that is itself in range — i.e. the day a bound falls on.
					val isSameDayAs: (LocalDateTime) -> Boolean = { bound ->
						selectedDate.year == bound.year &&
								selectedDate.monthValue == bound.month.number &&
								selectedDate.dayOfMonth == bound.day
					}
					val selectedMinutes = timePickerState.hour * 60 + timePickerState.minute

					val notBeforeMin = minDateTime?.let { min ->
						!isSameDayAs(min) || selectedMinutes >= min.hour * 60 + min.minute
					} ?: true

					val notAfterMax = maxDateTime?.let { max ->
						!isSameDayAs(max) || selectedMinutes <= max.hour * 60 + max.minute
					} ?: true

					notBeforeMin && notAfterMax
				}
			}
			else -> false
		}
	}

	Dialog(
		onDismissRequest = onDismiss,
		properties = DialogProperties(usePlatformDefaultWidth = false),
	) {
		Card(
			modifier = Modifier
				.widthIn(max = 600.dp)
				.heightIn(max = 800.dp)
				.verticalScroll(rememberScrollState())
		) {
			Column(
				verticalArrangement = Arrangement.Center,
				horizontalAlignment = Alignment.CenterHorizontally
			) {
				// Header with title and close button
				Row(
					modifier = Modifier
						.padding(start = 16.dp)
						.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically
				) {
					Text(
						text = title,
						style = MaterialTheme.typography.headlineSmall
					)
					IconButton(onClick = onDismiss) {
						Icon(
							imageVector = Icons.Default.Close,
							contentDescription = stringResource(id = R.string.close_button_content_description)
						)
					}
				}

				Spacer(modifier = Modifier.height(8.dp))

				when (state.currentStep) {
					0 -> {
						DatePicker(
							state = datePickerState
						)
					}

					1 -> {
						TimePicker(
							state = timePickerState,
						)
					}
				}

				Row(
					modifier = Modifier
						.padding(8.dp)
						.fillMaxWidth(),
					horizontalArrangement = Arrangement.End
				) {
					TextButton(onClick = onDismiss) {
						Text(stringResource(id = R.string.cancel_button))
					}

					Spacer(modifier = Modifier.width(8.dp))

					Button(
						onClick = {
							if (state.currentStep < 1) {
								state.currentStep++
							} else {
								val selectedDate = datePickerState.getSelectedDate()
								selectedDate?.let { date ->
                                    val dateTime = LocalDateTime(
                                        year = date.year,
                                        month = date.month.toKotlinMonth(),
                                        day = date.dayOfMonth,
                                        hour = timePickerState.hour,
                                        minute = timePickerState.minute,
                                        second = 0,
                                        nanosecond = 0
                                    )
									val instant = dateTime.toInstant(TimeZone.currentSystemDefault())
									onDateTimeSelected(instant)
									onDismiss()
								}
							}
						},
						enabled = isNextButtonEnabled
					) {
						Text(
							text = if (state.currentStep < 1) {
								stringResource(id = R.string.next_button)
							} else {
								finishButton
							}
						)
					}
				}
			}
		}
	}
}
