package com.legbehindneck.fasttrack48.screens.fastdays

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.legbehindneck.fasttrack48.R
import com.legbehindneck.fasttrack48.data.settings.DateStyle
import com.legbehindneck.fasttrack48.lunar.EkadashiDay
import com.legbehindneck.fasttrack48.lunar.FastDay
import com.legbehindneck.fasttrack48.lunar.FastDays
import com.legbehindneck.fasttrack48.lunar.FastKind
import com.legbehindneck.fasttrack48.lunar.LunarLocation
import com.legbehindneck.fasttrack48.lunar.LunarReminderSettings
import com.legbehindneck.fasttrack48.lunar.LunarSettingsStore
import com.legbehindneck.fasttrack48.lunar.LunarTexts
import com.legbehindneck.fasttrack48.lunar.MoonDay
import com.legbehindneck.fasttrack48.lunar.MoonDayStyle
import com.legbehindneck.fasttrack48.lunar.MoonPhaseName
import com.legbehindneck.fasttrack48.lunar.ResolvedLocation
import com.legbehindneck.fasttrack48.lunar.astro.Meeus
import com.legbehindneck.fasttrack48.utils.AppDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos

/** The fast days the user follows, where they are, and the minute the views were drawn for. */
internal data class FastDayData(
	val settings: LunarReminderSettings,
	val zone: ZoneId,
	val location: ResolvedLocation,
	/**
	 * From a lunar month back, so the strip shows the fasts already behind and a fast still
	 * running past midnight stays current; empty when none is followed.
	 */
	val days: List<FastDay>,
	val now: ZonedDateTime,
) {
	/** The fast under way, or else the next one. */
	val current: FastDay? get() = FastDays.current(days, now)
}

private data class FastDayInputs(
	val settings: LunarReminderSettings,
	val zone: ZoneId,
	val location: ResolvedLocation,
)

private data class FastDayList(val inputs: FastDayInputs, val days: List<FastDay>)

/**
 * The followed fast days for [span] days ahead, or null while they are calculated (and in
 * previews). Settings live in another activity and the time zone can change in the
 * background, so both are read again on every resume; the list is recalculated when they
 * change or the date turns, the clock re-read every minute.
 */
@Composable
internal fun rememberFastDays(span: Int): FastDayData? {
	if (LocalInspectionMode.current) return null
	val context = LocalContext.current
	val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
	val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
	val locationVersion by LunarSettingsStore.locationVersion.collectAsState()

	var inputs by remember { mutableStateOf(loadInputs(context)) }
	LaunchedEffect(resumed, locationVersion) {
		if (resumed) inputs = loadInputs(context)
	}

	var now by remember { mutableStateOf(ZonedDateTime.now(inputs.zone)) }
	LaunchedEffect(resumed, inputs.zone) {
		// Restarted on resume: a delay does not run while the phone sleeps.
		while (resumed) {
			now = ZonedDateTime.now(inputs.zone)
			delay(MINUTE_MILLIS - System.currentTimeMillis() % MINUTE_MILLIS)
		}
	}
	val today = now.toLocalDate()

	val list by produceState<FastDayList?>(null, inputs, today) {
		val current = inputs
		value = FastDayList(
			current,
			if (!FastDays.follows(current.settings)) {
				emptyList()
			} else {
				withContext(Dispatchers.Default) {
					FastDays.list(
						today.minusDays(LOOKBACK_DAYS.toLong()), span + LOOKBACK_DAYS, current.settings, current.location.point, current.zone,
					)
				}
			},
		)
	}
	return list?.let { FastDayData(it.inputs.settings, it.inputs.zone, it.inputs.location, it.days, now) }
}

private fun loadInputs(context: Context): FastDayInputs {
	val zone = ZoneId.systemDefault()
	return FastDayInputs(LunarSettingsStore.load(context), zone, LunarLocation.resolve(context, zone))
}

private const val MINUTE_MILLIS = 60_000L
private const val LOOKBACK_DAYS = 31

/**
 * The next fast in one tappable line, under the idle dial:
 *
 *   ( ◐  Ekadashi in 5 days           > )
 *   (    Wed, Nov 4                     )
 *
 * The second line says what matters next: the date while the fast is days away, when it
 * starts the day before, when it ends once it runs. Nothing when no fast day is followed.
 */
@Composable
internal fun FastDayEntry(
	data: FastDayData?,
	dateStyle: DateStyle,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val fast = data?.current ?: return
	val context = LocalContext.current
	// Read so that a change of language redraws the texts built from the context.
	val configuration = LocalConfiguration.current
	val ink = MaterialTheme.colorScheme.onBackground
	val ahead = daysAhead(data.now, fast)
	val title = remember(fast, ahead, configuration) { LunarTexts.title(context, fast.subject, ahead) }
	val detail = remember(fast, data.now, dateStyle, configuration) {
		entryDetail(context, fast, data.now, ahead, dateStyle)
	}
	Row(
		modifier = modifier
			.fillMaxWidth()
			.heightIn(min = 55.dp)
			.clip(CircleShape)
			.background(ink.copy(alpha = 0.06f))
			.clickable(
				onClickLabel = stringResource(R.string.fast_days_see_all),
				role = Role.Button,
				onClick = onClick,
			)
			.padding(start = 13.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		MoonIcon(elongation = fast.kind.elongation, modifier = Modifier.size(21.dp))
		Spacer(modifier = Modifier.width(13.dp))
		Column(modifier = Modifier.weight(1f)) {
			Text(
				text = title,
				style = MaterialTheme.typography.titleSmall,
				color = ink,
			)
			Text(
				text = detail,
				style = MaterialTheme.typography.bodySmall,
				color = ink.copy(alpha = 0.74f),
			)
		}
		Icon(
			imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
			contentDescription = null,
			tint = ink.copy(alpha = 0.6f),
		)
	}
}

/** Whole days from [now] to the fast; 0 while it runs, also after midnight until it ends. */
internal fun daysAhead(now: ZonedDateTime, fast: FastDay): Int =
	ChronoUnit.DAYS.between(now.toLocalDate(), fast.day).toInt().coerceAtLeast(0)

private fun entryDetail(context: Context, fast: FastDay, now: ZonedDateTime, ahead: Int, style: DateStyle): String {
	val subject = fast.subject
	val started = fast.sunrise?.let { !now.isBefore(it) } ?: (ahead == 0)
	return when {
		ahead >= 2 -> LunarTexts.date(context, fast.day, style)
		subject is MoonDay && !subject.sunriseToSunrise -> moonMoment(context, fast, style)
		!started -> fast.sunrise?.let { context.getString(R.string.fast_day_from_sunrise, LunarTexts.time(context, it, style)) }
			?: LunarTexts.date(context, fast.day, style)
		else -> fastEnd(context, fast, style) ?: LunarTexts.date(context, fast.day, style)
	}
}

/**
 * The fast's hours in full, for the sheet: "Fast from sunrise 07:48 to sunrise Thu, 07:49";
 * for a Smarta or Vaishnava Ekadashi the start and the fast-breaking window on two lines.
 */
internal fun fastWindow(context: Context, fast: FastDay, style: DateStyle): String {
	val subject = fast.subject
	if (subject is MoonDay && !subject.sunriseToSunrise) return moonMoment(context, fast, style)
	val sunrise = fast.sunrise ?: return fastEnd(context, fast, style) ?: LunarTexts.date(context, fast.day, style)
	val start = LunarTexts.time(context, sunrise, style)
	if (subject.sunriseToSunrise) {
		val next = (subject as? MoonDay)?.breakFast?.start ?: (subject as? EkadashiDay)?.parana?.start
		return next?.let { context.getString(R.string.fast_day_window, start, weekdayTime(context, it, style)) }
			?: context.getString(R.string.fast_day_from_sunrise, start)
	}
	val from = context.getString(R.string.fast_day_from_sunrise, start)
	return fastEnd(context, fast, style)?.let { "$from\n$it" } ?: from
}

/** When the fast ends: the next sunrise (Ananda Marga), or the fast-breaking window. */
private fun fastEnd(context: Context, fast: FastDay, style: DateStyle): String? = when (val subject = fast.subject) {
	is EkadashiDay -> subject.parana?.let {
		if (subject.sunriseToSunrise) {
			context.getString(R.string.fast_day_until_sunrise, weekdayTime(context, it.start, style))
		} else {
			AppDateTime.dropDoubledPeriods(context.getString(R.string.fast_day_break_fast, LunarTexts.parana(context, it, style)))
		}
	}
	is MoonDay -> subject.breakFast?.let {
		context.getString(R.string.fast_day_until_sunrise, weekdayTime(context, it.start, style))
	}
}

/** A Drik Purnima or Amavasya: when the tithi begins; an astronomical one: the exact moment. */
private fun moonMoment(context: Context, fast: FastDay, style: DateStyle): String {
	val subject = fast.subject as MoonDay
	return context.getString(
		if (subject.style == MoonDayStyle.VEDIC) R.string.fast_day_begins else R.string.fast_day_exact,
		context.getString(fast.kind.nameRes()),
		dateTime(context, subject.moment, style),
	)
}

/** "Thu, Nov 12, 07:49": a moment that may be days away. */
internal fun dateTime(context: Context, moment: ZonedDateTime, style: DateStyle): String =
	AppDateTime.joinDateTime(
		LunarTexts.date(context, moment.toLocalDate(), style),
		LunarTexts.time(context, moment, style),
		style,
		locale(context),
	)

/** "Thu, 07:49": a time on a nearby day. ISO keeps its date. */
internal fun weekdayTime(context: Context, moment: ZonedDateTime, style: DateStyle): String {
	val locale = locale(context)
	val day = if (style == DateStyle.ISO) {
		LunarTexts.date(context, moment.toLocalDate(), style)
	} else {
		moment.format(DateTimeFormatter.ofPattern("EEE", locale))
	}
	return AppDateTime.joinDateTime(day, LunarTexts.time(context, moment, style), style, locale)
}

/** "today", "tomorrow", "in 5 days". */
internal fun relativeDay(context: Context, ahead: Int): String = when (ahead) {
	0 -> context.getString(R.string.fast_day_today)
	1 -> context.getString(R.string.fast_day_tomorrow)
	else -> context.resources.getQuantityString(R.plurals.fast_day_in_days, ahead, ahead)
}

private fun locale(context: Context): Locale = context.resources.configuration.locales[0] ?: Locale.getDefault()

@StringRes
internal fun FastKind.nameRes(): Int = when (this) {
	FastKind.SHUKLA_EKADASHI -> R.string.fast_kind_shukla_ekadashi
	FastKind.KRISHNA_EKADASHI -> R.string.fast_kind_krishna_ekadashi
	FastKind.PURNIMA -> R.string.fast_kind_purnima
	FastKind.AMAVASYA -> R.string.fast_kind_amavasya
	FastKind.FULL_MOON -> R.string.moon_phase_full
	FastKind.NEW_MOON -> R.string.moon_phase_new
}

@StringRes
internal fun MoonPhaseName.nameRes(): Int = when (this) {
	MoonPhaseName.NEW -> R.string.moon_phase_new
	MoonPhaseName.WAXING_CRESCENT -> R.string.moon_phase_waxing_crescent
	MoonPhaseName.FIRST_QUARTER -> R.string.moon_phase_first_quarter
	MoonPhaseName.WAXING_GIBBOUS -> R.string.moon_phase_waxing_gibbous
	MoonPhaseName.FULL -> R.string.moon_phase_full
	MoonPhaseName.WANING_GIBBOUS -> R.string.moon_phase_waning_gibbous
	MoonPhaseName.LAST_QUARTER -> R.string.moon_phase_last_quarter
	MoonPhaseName.WANING_CRESCENT -> R.string.moon_phase_waning_crescent
}

/** The Moon as seen from the north at [elongation] degrees: 0 new, 180 full; lit on the right while waxing. */
@Composable
internal fun MoonIcon(elongation: Double, modifier: Modifier = Modifier) {
	val colors = moonColors()
	Canvas(modifier = modifier) {
		drawMoon(center, size.minDimension / 2, elongation, colors)
	}
}

/** Lit side, shadow and rim; the shadow is a little lighter on a dark theme so the disc still reads. */
internal data class MoonColors(val lit: Color, val shadow: Color, val rim: Color)

@Composable
internal fun moonColors(): MoonColors {
	val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
	return remember(dark) {
		if (dark) {
			MoonColors(MoonLit, Color(0xFF4B4659), Color.White.copy(alpha = 0.18f))
		} else {
			MoonColors(MoonLit, Color(0xFF3B3748), Color.Black.copy(alpha = 0.22f))
		}
	}
}

private val MoonLit = Color(0xFFF6DFA0)

/**
 * The lit part is the semicircle on the lit side plus or minus half an ellipse: the
 * terminator, whose half-width is r·|cos e|. Past the quarter (gibbous) it bulges into the
 * dark half; before it (crescent) it cuts into the lit half.
 */
internal fun DrawScope.drawMoon(center: Offset, radius: Float, elongation: Double, colors: MoonColors) {
	drawCircle(colors.shadow, radius, center)
	val e = Math.toRadians(elongation)
	val lit = (1 - cos(e)) / 2
	if (lit > FULL_DISC) {
		drawCircle(colors.lit, radius, center)
	} else if (lit > NO_DISC) {
		val waxing = Meeus.fixAngle(elongation) < 180.0
		val gibbous = lit > 0.5
		val half = (abs(cos(e)) * radius).toFloat()
		val path = Path().apply {
			moveTo(center.x, center.y - radius)
			arcTo(Rect(center, radius), -90f, if (waxing) 180f else -180f, false)
			if (half < 0.5f) {
				lineTo(center.x, center.y - radius)
			} else {
				arcTo(
					Rect(center.x - half, center.y - radius, center.x + half, center.y + radius),
					90f,
					if (waxing == gibbous) 180f else -180f,
					false,
				)
			}
			close()
		}
		drawPath(path, colors.lit)
	}
	val rimWidth = radius * 0.08f
	drawCircle(colors.rim, radius - rimWidth / 2, center, style = Stroke(width = rimWidth))
}

private const val FULL_DISC = 0.985
private const val NO_DISC = 0.015

/** The current fast and every one after it. */
internal fun upcoming(data: FastDayData): List<FastDay> {
	val current = data.current ?: return emptyList()
	val from: LocalDate = current.day
	return data.days.filter { !it.day.isBefore(from) }
}
