package com.legbehindneck.fasttrack48.screens.fasting

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.legbehindneck.fasttrack48.R
import com.legbehindneck.fasttrack48.data.settings.DateStyle
import com.legbehindneck.fasttrack48.screens.preview.getContext
import com.legbehindneck.fasttrack48.utils.AppDateTime
import com.legbehindneck.fasttrack48.utils.LocalDateStyle
import com.legbehindneck.fasttrack48.utils.shouldUse24HourFormat
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * When the running fast began — the caption on the arc's origin.
 *
 * The screen shows "fasting for 55m" without ever saying what those 55 minutes are
 * measured from, which is the one fact needed to notice the start is wrong. This block
 * states it and makes it correctable in place.
 *
 * Placement is not arbitrary: [TimeLine] opens its arc at 135 degrees, which in Canvas
 * coordinates is the lower left, so this sits directly beneath where the journey begins,
 * in the slot the Start Fast action itself occupied before the fast started. The pencil
 * wears the arc's own first-stage colour, so the correspondence reads as a relationship
 * rather than as an indent.
 *
 * Label above, value below: two short lines make one object of the width of a pill, which
 * is what lets it balance the action seated at the arc's other end.
 */
@Composable
fun FastStartBlock(
	startTime: Instant?,
	onEditStart: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val start = startTime ?: return

	val spacing = fastingSpacing()
	val style = LocalDateStyle.current
	val is24Hour = shouldUse24HourFormat(getContext())
	val label = rememberStartLabel(start, style, is24Hour)

	// The pencil is a 13dp whisper; the whole block is the target. clickable() merges
	// descendants, so TalkBack reads one node ending in the onClickLabel.
	Column(
		modifier = modifier
			.clip(RoundedCornerShape(spacing.large))
			.clickable(onClickLabel = stringResource(R.string.edit_start_time), onClick = onEditStart)
			.heightIn(min = BandHeight)
			.padding(horizontal = spacing.small, vertical = spacing.small),
		verticalArrangement = Arrangement.Center,
	) {
		Row(verticalAlignment = Alignment.CenterVertically) {
			Text(
				text = stringResource(R.string.fast_start_label),
				style = MaterialTheme.typography.labelMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
			Spacer(modifier = Modifier.width(spacing.small))
			Icon(
				imageVector = Icons.Default.Edit,
				contentDescription = null,
				modifier = Modifier.size(spacing.large),
				tint = journeyStageColor(0),
			)
		}
		Text(
			text = label,
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onBackground,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
		)
	}
}

/**
 * "Today, 14:15" for an instant. "Today" / "Yesterday" beat a date the reader has to
 * decode, but they are suppressed under [DateStyle.ISO] — a user who asked for ISO asked
 * for an unambiguous machine-shaped date, and relative words would override that.
 */
@Composable
private fun rememberStartLabel(
	instant: Instant,
	style: DateStyle,
	is24Hour: Boolean,
): String {
	val todayLabel = stringResource(R.string.relative_today)
	val yesterdayLabel = stringResource(R.string.relative_yesterday)

	return remember(instant, style, is24Hour, todayLabel, yesterdayLabel) {
		val tz = TimeZone.currentSystemDefault()
		val dateTime = instant.toLocalDateTime(tz)
		val today = Clock.System.now().toLocalDateTime(tz).date
		// Negative for a future date, which the clamp forbids; it falls through to the
		// absolute format either way rather than claiming a relative day it isn't.
		val daysAgo = dateTime.date.daysUntil(today)

		val day = when {
			style == DateStyle.ISO -> AppDateTime.formatDate(dateTime.date, style)
			daysAgo == 0 -> todayLabel
			daysAgo == 1 -> yesterdayLabel
			else -> AppDateTime.formatDate(dateTime.date, style)
		}
		"$day, ${AppDateTime.formatTime(dateTime, style, is24Hour)}"
	}
}
