package com.legbehindneck.fasttrack48.screens.log

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.legbehindneck.fasttrack48.data.log.FastingLogEntry
import com.legbehindneck.fasttrack48.utils.AppDateTime
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.CalendarMonth
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.daysOfWeek
import com.kizitonwose.calendar.core.firstDayOfWeekFromLocale
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toLocalDateTime
import java.time.YearMonth
import java.time.format.TextStyle
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.LocalDate as KxLocalDate

@ExperimentalTime
@Composable
fun LogCalendarContent(
    entries: List<FastingLogEntry>,
	activeFastStart: Instant?,
	/** Month to open on, set when the year strip drills into one. Null means "this month". */
	focusedMonth: KxLocalDate?,
	selectedDate: KxLocalDate?,
	onDateSelected: (KxLocalDate?) -> Unit,
	onAddForEmptyDay: (KxLocalDate) -> Unit,
    onEdit: (FastingLogEntry) -> Unit,
    onDelete: (FastingLogEntry) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
	val coverage = remember(entries) {
		coverageByDay(entries, TimeZone.currentSystemDefault())
	}

	val today = remember { java.time.LocalDate.now() }

	// The fast currently running, drawn as an open-ended span from its start day through
	// today. It gets no end cap: the capsule runs flush off today's cell because the fast
	// has not ended, and there is no logbook row to open, edit or delete for it yet.
	val ongoing = remember(activeFastStart, today) {
		activeFastStart?.let { start ->
			val tz = TimeZone.currentSystemDefault()
			OngoingSpan(
				startDate = start.toLocalDateTime(tz).date,
				endDate = today.toKotlinLocalDate(),
				color = stageColorForLength(Clock.System.now() - start),
			)
		}
	}
	val currentMonth = remember { YearMonth.now() }
	// The month to land on: the drill-down target from the year strip, else this month.
	// Switching view mode recreates this composable, so first composition is enough --
	// no scrollToMonth call is needed.
	val initialMonth = remember(focusedMonth) {
		focusedMonth?.let { YearMonth.of(it.year, it.monthNumber) } ?: currentMonth
	}
	val firstDayOfWeek = remember { firstDayOfWeekFromLocale() }
	val daysOfWeek = remember(firstDayOfWeek) { daysOfWeek(firstDayOfWeek = firstDayOfWeek) }

	val calendarState = rememberCalendarState(
		startMonth = currentMonth.minusMonths(24),
		endMonth = currentMonth,
		firstVisibleMonth = initialMonth,
		firstDayOfWeek = firstDayOfWeek,
	)

	LazyColumn(
		modifier = modifier.fillMaxSize(),
		contentPadding = contentPadding,
	) {
		item {
			DaysOfWeekRow(daysOfWeek)
		}
		item {
			HorizontalCalendar(
				state = calendarState,
				dayContent = { day ->
					val kxDate = day.date.toKotlinLocalDate()
					val covering = coverage.byDay[kxDate].orEmpty()
					// Pick the longest fast covering this day (handles the rare
					// overlap) and describe where the day sits in that fast's range.
					val band = covering.maxByOrNull { it.length }?.let { chosen ->
						val startDate = chosen.start.date
						val endDate = coverage.endDateById[chosen.id] ?: startDate
						DayBand(
							color = stageColorForLength(chosen.length),
							isStart = kxDate == startDate,
							isEnd = kxDate == endDate,
							isSingle = startDate == endDate,
						)
					} ?: ongoing
						?.takeIf { kxDate >= it.startDate && kxDate <= it.endDate }
						?.let { span ->
							DayBand(
								color = span.color,
								isStart = kxDate == span.startDate,
								isEnd = false,
								isSingle = false,
							)
						}
					DayCell(
						day = day,
						isToday = day.date == today,
						isFuture = day.date.isAfter(today),
						band = band,
						isSelected = selectedDate == kxDate,
						onClick = {
							when {
								// A day within a logged fast opens its detail dialog.
								covering.isNotEmpty() -> onDateSelected(kxDate)
								// A day inside the running fast: nothing to open, and it
								// must not offer to log a second fast over the top of it.
								band != null -> Unit
								// An empty past/today day offers to log a fast there
								// (future days are disabled, so this never fires for them).
								else -> onAddForEmptyDay(kxDate)
							}
						},
					)
				},
				monthHeader = { month -> MonthHeader(month) },
			)
		}
	}

	val selected = selectedDate
	val selectedEntries = if (selected != null) coverage.byDay[selected].orEmpty() else emptyList()
	if (selected != null && selectedEntries.isNotEmpty()) {
		FastDayDialog(
			entries = selectedEntries,
			onDismiss = { onDateSelected(null) },
			onEdit = onEdit,
			onDelete = onDelete,
		)
	}
}

@ExperimentalTime
@Composable
private fun FastDayDialog(
    entries: List<FastingLogEntry>,
    onDismiss: () -> Unit,
    onEdit: (FastingLogEntry) -> Unit,
    onDelete: (FastingLogEntry) -> Unit,
) {
	Dialog(
		onDismissRequest = onDismiss,
		properties = DialogProperties(
			dismissOnBackPress = true,
			dismissOnClickOutside = true,
		),
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 8.dp),
		) {
			entries.forEach { entry ->
				FastEntryItem(
					entry = entry,
					onEdit = {
						onEdit(entry)
						onDismiss()
					},
					onDelete = {
						onDelete(entry)
						onDismiss()
					},
				)
			}
		}
	}
}

@Composable
private fun DaysOfWeekRow(daysOfWeek: List<java.time.DayOfWeek>) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(bottom = 4.dp)
	) {
		daysOfWeek.forEach { dow ->
			Text(
				text = dow.getDisplayName(TextStyle.SHORT, LocalLocale.current.platformLocale),
				style = MaterialTheme.typography.labelSmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				textAlign = TextAlign.Center,
				modifier = Modifier.weight(1f)
			)
		}
	}
}

@Composable
private fun MonthHeader(month: CalendarMonth) {
	val title = remember(month.yearMonth) { AppDateTime.formatMonthYear(month.yearMonth) }
	Text(
		text = title,
		style = MaterialTheme.typography.titleMedium,
		color = MaterialTheme.colorScheme.onSurface,
		fontWeight = FontWeight.SemiBold,
		modifier = Modifier
			.fillMaxWidth()
			.padding(vertical = 8.dp),
		textAlign = TextAlign.Center
	)
}

@Composable
private fun DayCell(
	day: CalendarDay,
	isToday: Boolean,
	isFuture: Boolean,
	band: DayBand?,
	isSelected: Boolean,
	onClick: () -> Unit,
) {
	val inMonth = day.position == DayPosition.MonthDate
	val cover = band
	// Future days are greyed out and inert. Spill days from the adjacent month stay
	// dimmed, but a covered one (a fast's head/tail bleeding across the month edge)
	// remains tappable so the whole span is reachable — no dead-looking segments.
	val enabled = !isFuture && (inMonth || cover != null)

	val stageColor = cover?.color ?: Color.Transparent
	// The whole span is one soft capsule; the true start/end read a touch stronger.
	val bandColor = stageColor.copy(alpha = 0.20f)
	val isEndpoint = cover != null && (cover.isStart || cover.isEnd || cover.isSingle)
	val endpointFill = if (isEndpoint) stageColor.copy(alpha = 0.42f) else Color.Transparent

	val borderColor = when {
		isSelected -> MaterialTheme.colorScheme.primary
		isToday && inMonth -> MaterialTheme.colorScheme.outline
		else -> Color.Transparent
	}
	val borderWidth = if (isSelected) 2.dp else 1.dp

	val dayTextColor = when {
		isFuture -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
		// Spill days from another month are dimmed; a covered one sits a little
		// brighter so its part of the fast reads as present (and tappable).
		!inMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = if (cover != null) 0.55f else 0.3f)
		else -> MaterialTheme.colorScheme.onSurface
	}

	Box(
		modifier = Modifier
			.aspectRatio(1f)
			.clickable(enabled = enabled, onClick = onClick),
		contentAlignment = Alignment.Center,
	) {
		// Connecting band: one continuous capsule at token height, drawn edge-to-edge
		// so neighbouring cells fuse into a single stadium. It is rounded only at the
		// fast's true start/end; middle days (and week wraps) run flush to the edge.
		if (cover != null && !cover.isSingle) {
			val bandShape = when {
				cover.isStart -> RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50)
				cover.isEnd -> RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50)
				else -> RectangleShape
			}
			val bandAlign = when {
				cover.isStart -> Alignment.CenterEnd
				cover.isEnd -> Alignment.CenterStart
				else -> Alignment.Center
			}
			// Endpoint cells inset the outer side so the rounded cap sits under the
			// token circle; middle cells span the full width to bridge the gap.
			val bandWidth = if (cover.isStart || cover.isEnd) 0.90f else 1f
			Box(
				modifier = Modifier
					.align(bandAlign)
					.fillMaxWidth(bandWidth)
					.fillMaxHeight(DAY_TOKEN_FRACTION)
					.clip(bandShape)
					.background(bandColor),
			)
		}

		// The day token: a filled circle at range endpoints (and single-day fasts),
		// plus the today/selected ring, with the date number on top.
		Box(
			modifier = Modifier
				.fillMaxSize(DAY_TOKEN_FRACTION)
				.clip(CircleShape)
				.background(endpointFill, CircleShape)
				.border(borderWidth, borderColor, CircleShape),
			contentAlignment = Alignment.Center,
		) {
			Text(
				text = day.date.dayOfMonth.toString(),
				style = MaterialTheme.typography.bodyMedium,
				color = dayTextColor,
				fontWeight = if (isToday && inMonth) FontWeight.Bold else FontWeight.Normal,
			)
		}
	}
}

// The day circle / band height as a fraction of the square cell, so the capsule
// endpoints and connector share one diameter (a clean stadium).
private const val DAY_TOKEN_FRACTION = 0.80f

/** The fast currently running, as the range of days it covers. */
private data class OngoingSpan(
	val startDate: KxLocalDate,
	val endDate: KxLocalDate,
	val color: Color,
)

/** Where a given day sits within the fast that covers it. */
private data class DayBand(
	val color: Color,
	val isStart: Boolean,
	val isEnd: Boolean,
	val isSingle: Boolean,
)
