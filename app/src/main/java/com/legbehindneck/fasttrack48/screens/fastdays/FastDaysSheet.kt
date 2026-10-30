package com.legbehindneck.fasttrack48.screens.fastdays

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.icu.text.NumberFormat
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.legbehindneck.fasttrack48.R
import com.legbehindneck.fasttrack48.data.settings.DateStyle
import com.legbehindneck.fasttrack48.lunar.EkadashiTradition
import com.legbehindneck.fasttrack48.lunar.FastDay
import com.legbehindneck.fasttrack48.lunar.FastDays
import com.legbehindneck.fasttrack48.lunar.LunarLocation
import com.legbehindneck.fasttrack48.lunar.MoonDayStyle
import com.legbehindneck.fasttrack48.lunar.MoonState
import com.legbehindneck.fasttrack48.lunar.LunarTexts
import com.legbehindneck.fasttrack48.screens.settings.ImportActivity
import com.legbehindneck.fasttrack48.utils.AppDateTime
import com.legbehindneck.fasttrack48.utils.ICalendar
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** A year of fast days: the list runs this far ahead. */
internal const val FAST_DAYS_SPAN = 366

/** Relative dates ("in 12 days") stop where a date alone reads better. */
private const val RELATIVE_DAYS = 31

/** The accent behind the next fast: a tint, so the text keeps the sheet's own ink. */
private const val TINT = 0.10f

/**
 * Every followed fast day for a year ahead, over whatever opened it. Half height shows the
 * next fast and the month ahead; dragged up, the whole year, month by month. A tap outside or
 * a swipe down closes it.
 */
@Composable
internal fun FastDaysSheet(dateStyle: DateStyle, onDismiss: () -> Unit) {
	// Shown once the days are in (a fraction of a second), so the sheet opens at half height
	// around them instead of growing from a placeholder to full height.
	val data = rememberFastDays(FAST_DAYS_SPAN) ?: return
	ModalBottomSheet(
		onDismissRequest = onDismiss,
		sheetState = rememberModalBottomSheetState(),
	) {
		FastDaysContent(data = data, dateStyle = dateStyle)
	}
}

@Composable
private fun FastDaysContent(data: FastDayData, dateStyle: DateStyle) {
	val context = LocalContext.current
	// Read so that a change of language redraws the texts built from the context.
	val configuration = LocalConfiguration.current
	val locale = configuration.locales[0]
	val scope = rememberCoroutineScope()
	val upcoming = remember(data.days, data.current) { upcoming(data) }
	val source = remember(data.settings, data.location, data.zone, configuration) { sourceLine(context, data) }
	val moon = remember(data.now.toLocalDate(), data.now.hour, data.zone) { FastDays.moon(data.now) }
	val months = remember(upcoming) { upcoming.groupBy { YearMonth.from(it.day) } }

	LazyColumn(contentPadding = PaddingValues(start = 21.dp, end = 21.dp, bottom = 34.dp)) {
		item {
			Row(verticalAlignment = Alignment.CenterVertically) {
				Text(
					text = stringResource(R.string.fast_days_title),
					style = MaterialTheme.typography.headlineSmall,
					fontWeight = FontWeight.Bold,
					modifier = Modifier.weight(1f),
				)
				if (upcoming.isNotEmpty()) {
					IconButton(onClick = { scope.launch { exportCalendar(context, upcoming, dateStyle) } }) {
						Icon(
							imageVector = Icons.Default.DateRange,
							contentDescription = stringResource(R.string.fast_days_add_to_calendar),
						)
					}
				}
			}
		}
		item { Note(source) }
		if (upcoming.isEmpty()) {
			item { Note(stringResource(R.string.fast_days_empty)) }
			return@LazyColumn
		}
		item { MoonLine(moon = moon, dateStyle = dateStyle) }
		item { LunarMonthStrip(moon = moon, days = data.days, now = data.now) }
		item { NextFastCard(fast = upcoming.first(), now = data.now, dateStyle = dateStyle) }
		months.forEach { (month, fasts) ->
			item(key = month.toString()) {
				Text(
					text = AppDateTime.formatMonthYear(month, locale).replaceFirstChar { it.titlecase(locale) },
					style = MaterialTheme.typography.labelLarge,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.padding(top = 21.dp, bottom = 5.dp),
				)
			}
			items(fasts, key = { "${it.kind}-${it.day}" }) { fast ->
				FastRow(fast = fast, now = data.now, highlighted = fast == upcoming.first())
			}
		}
	}
}

@Composable
private fun Note(text: String) {
	Text(
		text = text,
		style = MaterialTheme.typography.bodySmall,
		color = MaterialTheme.colorScheme.onSurfaceVariant,
		modifier = Modifier.padding(top = 3.dp, bottom = 13.dp),
	)
}

/** What the dates follow, and where the times are for. */
private fun sourceLine(context: Context, data: FastDayData): String {
	val place = if (data.location.fromDevice) {
		context.getString(R.string.fast_days_your_location)
	} else {
		LunarLocation.zoneLabel(data.zone)
	}
	val settings = data.settings
	val tradition = settings.tradition.takeIf { settings.ekadashiEnabled || settings.moonDayStyle == MoonDayStyle.VEDIC }
	return when (tradition) {
		EkadashiTradition.ANANDA_MARGA -> context.getString(R.string.fast_days_source_ananda_marga, place)
		EkadashiTradition.ANANDA_MARGA_INDIA -> context.getString(R.string.fast_days_source_ananda_marga_india, place)
		else -> context.getString(
			R.string.fast_days_source_local,
			context.getString(
				when {
					tradition == EkadashiTradition.VAISHNAVA -> R.string.lunar_tradition_vaishnava
					settings.ekadashiEnabled || settings.moonDayStyle == MoonDayStyle.VEDIC -> R.string.lunar_tradition_smarta
					else -> R.string.lunar_moon_style_astronomical
				}
			),
			place,
		)
	}.let(AppDateTime::dropDoubledPeriods)
}

/**
 * "Waning gibbous · 75%" with the next full or new moon, whichever comes first. That can be
 * two weeks off, so it carries its date, not only a weekday.
 */
@Composable
private fun MoonLine(moon: MoonState, dateStyle: DateStyle) {
	val context = LocalContext.current
	val configuration = LocalConfiguration.current
	val locale = configuration.locales[0]
	val percent = remember(moon.illumination, locale) {
		NumberFormat.getPercentInstance(locale).format(moon.illumination)
	}
	val fullFirst = moon.nextFullMoon.isBefore(moon.nextNewMoon)
	val nextAt = remember(moon, dateStyle, configuration) {
		dateTime(context, if (fullFirst) moon.nextFullMoon else moon.nextNewMoon, dateStyle)
	}
	val next = stringResource(
		if (fullFirst) R.string.fast_days_next_full_moon else R.string.fast_days_next_new_moon,
		nextAt,
	)
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(bottom = 8.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		MoonIcon(elongation = moon.elongation, modifier = Modifier.size(21.dp))
		Spacer(modifier = Modifier.width(8.dp))
		Text(
			text = stringResource(R.string.fast_days_joined, stringResource(moon.phase.nameRes()), percent),
			style = MaterialTheme.typography.bodyMedium,
			modifier = Modifier.weight(1f),
		)
		Spacer(modifier = Modifier.width(8.dp))
		Text(
			text = next,
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			textAlign = TextAlign.End,
		)
	}
}

/**
 * One lunar month, full moon to full moon, as a line: the fasts on it with their moons and
 * dates, the part already behind in the accent colour, and today marked. The fortnightly
 * rhythm at a glance.
 */
@Composable
private fun LunarMonthStrip(moon: MoonState, days: List<FastDay>, now: ZonedDateTime) {
	val measurer = rememberTextMeasurer()
	val colors = moonColors()
	val accent = MaterialTheme.colorScheme.primary
	val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
	val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
	val todayStyle = labelStyle.copy(color = accent, fontWeight = FontWeight.Bold)
	val todayLabel = stringResource(R.string.fast_days_today_marker)
	val start = moon.lastFullMoon
	val span = Duration.between(start, moon.nextFullMoon).toMillis().toFloat()
	// A Purnima fast can fall the day before its full moon, so the day before the start counts;
	// it sits at the left end.
	val marks = remember(days, start) {
		days.filter { !it.day.isBefore(start.toLocalDate().minusDays(1)) && !it.day.isAfter(moon.nextFullMoon.toLocalDate()) }
	}
	fun position(moment: ZonedDateTime): Float =
		(Duration.between(start, moment).toMillis() / span).coerceIn(0f, 1f)

	Canvas(
		modifier = Modifier
			.fillMaxWidth()
			.height(55.dp)
			.padding(horizontal = 8.dp),
	) {
		val y = 21.dp.toPx()
		val stroke = 2.dp.toPx()
		val moonRadius = 6.5.dp.toPx()
		val x = { f: Float -> f * size.width }
		drawLine(track, Offset(0f, y), Offset(size.width, y), stroke)
		val today = x(position(now))
		drawLine(accent.copy(alpha = 0.55f), Offset(0f, y), Offset(today, y), stroke)
		marks.forEach { fast ->
			// Noon marks the day: the date, not the hour, is what the strip shows.
			val at = x(position(fast.day.atTime(12, 0).atZone(now.zone)))
			drawMoon(Offset(at, y), moonRadius, fast.kind.elongation, colors)
			val label = measurer.measure(fast.day.dayOfMonth.toString(), labelStyle)
			drawText(label, topLeft = Offset(at - label.size.width / 2f, y + moonRadius + 3.dp.toPx()))
		}
		drawCircle(accent, 4.5.dp.toPx(), Offset(today, y))
		val marker = measurer.measure(todayLabel, todayStyle)
		val markerX = (today - marker.size.width / 2f).coerceIn(0f, (size.width - marker.size.width).coerceAtLeast(0f))
		drawText(marker, topLeft = Offset(markerX, y - moonRadius - 3.dp.toPx() - marker.size.height))
	}
}

/** The next fast: name, date, how far off, and its hours. */
@Composable
private fun NextFastCard(fast: FastDay, now: ZonedDateTime, dateStyle: DateStyle) {
	val context = LocalContext.current
	val configuration = LocalConfiguration.current
	val ahead = daysAhead(now, fast)
	val date = remember(fast, ahead, dateStyle, configuration) {
		Pair(LunarTexts.date(context, fast.day, dateStyle), relativeDay(context, ahead))
	}
	val window = remember(fast, dateStyle, configuration) { fastWindow(context, fast, dateStyle) }
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(top = 8.dp)
			.clip(RoundedCornerShape(21.dp))
			.background(MaterialTheme.colorScheme.primary.copy(alpha = TINT))
			.padding(13.dp),
		verticalAlignment = Alignment.Top,
	) {
		MoonIcon(elongation = fast.kind.elongation, modifier = Modifier.padding(top = 3.dp).size(34.dp))
		Spacer(modifier = Modifier.width(13.dp))
		Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
			val ink = MaterialTheme.colorScheme.onSurface
			Text(
				text = stringResource(fast.kind.nameRes()),
				style = MaterialTheme.typography.titleMedium,
				color = ink,
			)
			Text(
				text = stringResource(R.string.fast_days_joined, date.first, date.second),
				style = MaterialTheme.typography.bodyMedium,
				color = ink,
			)
			Text(
				text = window,
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
	}
}

/** "4  Wed  ◑  Krishna Ekadashi ........ in 5 days" */
@Composable
private fun FastRow(fast: FastDay, now: ZonedDateTime, highlighted: Boolean) {
	val context = LocalContext.current
	val configuration = LocalConfiguration.current
	val ahead = daysAhead(now, fast)
	val weekday = remember(fast.day, configuration) {
		fast.day.format(DateTimeFormatter.ofPattern("EEE", configuration.locales[0]))
	}
	val relative = remember(ahead, configuration) { relativeDay(context, ahead) }
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.heightIn(min = 44.dp)
			.clip(RoundedCornerShape(13.dp))
			.background(if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = TINT) else Color.Transparent)
			.padding(horizontal = 8.dp, vertical = 5.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(
			text = fast.day.dayOfMonth.toString(),
			style = MaterialTheme.typography.titleMedium.merge(TextStyle(fontFeatureSettings = "tnum")),
			fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium,
			textAlign = TextAlign.End,
			modifier = Modifier.widthIn(min = 24.dp),
		)
		Spacer(modifier = Modifier.width(8.dp))
		Text(
			text = weekday,
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.widthIn(min = 34.dp),
		)
		Spacer(modifier = Modifier.width(8.dp))
		MoonIcon(elongation = fast.kind.elongation, modifier = Modifier.size(16.dp))
		Spacer(modifier = Modifier.width(13.dp))
		Text(
			text = stringResource(fast.kind.nameRes()),
			style = MaterialTheme.typography.bodyLarge,
			fontWeight = if (highlighted) FontWeight.Medium else FontWeight.Normal,
			modifier = Modifier.weight(1f),
		)
		if (ahead <= RELATIVE_DAYS) {
			Spacer(modifier = Modifier.width(8.dp))
			Text(
				text = relative,
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
	}
}

/**
 * The listed fast days as an iCalendar file through the share sheet: one whole-day event
 * each, its hours in the description (the times are this phone's). FastTrack's own importer
 * is left out of the chooser; it would read the days as logged fasts.
 */
private suspend fun exportCalendar(context: Context, fasts: List<FastDay>, style: DateStyle) {
	try {
		val file = withContext(Dispatchers.Default) {
			val text = calendarText(context, fasts, style)
			File(context.cacheDir, "fastDays.ics").also { it.writeText(text) }
		}
		val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
		val send = Intent(Intent.ACTION_SEND).apply {
			type = "text/calendar"
			putExtra(Intent.EXTRA_STREAM, uri)
			addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
		}
		val chooser = Intent.createChooser(send, context.getString(R.string.fast_days_add_to_calendar))
			.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context, ImportActivity::class.java)))
		context.startActivity(chooser)
	} catch (e: CancellationException) {
		// The sheet closed while the file was being written: nothing failed.
		throw e
	} catch (e: Exception) {
		Napier.e("Fast-day calendar export failed", e)
		Toast.makeText(context, R.string.fast_days_export_failed, Toast.LENGTH_SHORT).show()
	}
}

private fun calendarText(context: Context, fasts: List<FastDay>, style: DateStyle): String {
	val date = DateTimeFormatter.BASIC_ISO_DATE
	val stamp = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))
	val lines = ArrayList<String>()
	lines += "BEGIN:VCALENDAR"
	lines += "VERSION:2.0"
	lines += "PRODID:-//legbehindneck.com//FastTrack48//EN"
	lines += "CALSCALE:GREGORIAN"
	lines += "METHOD:PUBLISH"
	lines += "X-WR-CALNAME:${ICalendar.escape(context.getString(R.string.fast_days_title))}"
	for (fast in fasts) {
		val day: LocalDate = fast.day
		lines += "BEGIN:VEVENT"
		lines += "UID:fastday-${fast.kind.name.lowercase()}-${day.format(date)}@legbehindneck.com"
		lines += "DTSTAMP:$stamp"
		lines += "DTSTART;VALUE=DATE:${day.format(date)}"
		lines += "DTEND;VALUE=DATE:${day.plusDays(1).format(date)}"
		lines += "SUMMARY:${ICalendar.escape(context.getString(fast.kind.nameRes()))}"
		lines += "DESCRIPTION:${ICalendar.escape(fastWindow(context, fast, style))}"
		lines += "TRANSP:TRANSPARENT"
		lines += "END:VEVENT"
	}
	lines += "END:VCALENDAR"
	return ICalendar.document(lines)
}
