package com.legbehindneck.fasttrack48.screens.log

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.legbehindneck.fasttrack48.R
import com.legbehindneck.fasttrack48.data.Stages
import com.legbehindneck.fasttrack48.data.log.FastingLogEntry
import com.legbehindneck.fasttrack48.utils.AppDateTime
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toKotlinLocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import kotlin.math.ceil
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.LocalDate as KxLocalDate

/**
 * A year at a glance: one bar per day, its height the hours fasted that day, laid out as
 * twelve month columns across a single strip.
 *
 * Contiguous fasted days are drawn as one fused block, so a multi-day fast reads as a fat
 * plateau with lower partial days at each end while an isolated 18h day stays a thin tick.
 * Bars carry the same phase colours the month grid uses, so a 60h fast is visibly a
 * different animal from an 18h one.
 *
 * Swiping right pages back through earlier twelve-month windows, as far as the oldest
 * logged fast; tapping a month column drills into the month grid.
 */
@ExperimentalTime
@Composable
fun LogYearContent(
	entries: List<FastingLogEntry>,
	activeFastStart: Instant?,
	onMonthSelected: (KxLocalDate) -> Unit,
	contentPadding: PaddingValues,
	modifier: Modifier = Modifier,
) {
	val tz = remember { TimeZone.currentSystemDefault() }
	val thisMonth = remember { YearMonth.now() }
	val today = remember { java.time.LocalDate.now().toKotlinLocalDate() }

	// One page per twelve-month window, the last of them ending on the current month.
	// Anchoring to the current month rather than to January keeps the default page full of
	// recent history instead of a near-empty stub every January.
	val pageCount = remember(entries, thisMonth) {
		val oldest = entries.minByOrNull { it.start }?.start?.date ?: return@remember 1
		val monthsBack =
			(thisMonth.year - oldest.year) * 12 + (thisMonth.monthValue - oldest.monthNumber)
		ceil((monthsBack + 1) / 12.0).toInt().coerceAtLeast(1)
	}

	val pagerState = rememberPagerState(initialPage = pageCount - 1) { pageCount }

	// Entries arrive from the database after the first composition, so the page count the
	// state was created with is usually 1. Land on the newest window once real data is in,
	// but only once -- afterwards the page is the reader's to choose.
	var settled by remember { mutableStateOf(false) }
	LaunchedEffect(pageCount, entries.isNotEmpty()) {
		if (!settled && entries.isNotEmpty()) {
			pagerState.scrollToPage(pageCount - 1)
			settled = true
		}
	}

	Column(
		modifier = modifier
			.fillMaxSize()
			.verticalScroll(rememberScrollState())
			.padding(contentPadding),
	) {
		HorizontalPager(state = pagerState) { page ->
			// Page 0 is the oldest window; the last page ends on the current month.
			val endMonth = remember(page, thisMonth, pageCount) {
				thisMonth.minusMonths(((pageCount - 1 - page) * 12).toLong())
			}
			YearStrip(
				endMonth = endMonth,
				today = today,
				entries = entries,
				activeFastStart = activeFastStart,
				tz = tz,
				onMonthSelected = onMonthSelected,
			)
		}
		PhaseLegend()
	}
}

/** One twelve-month window ending on [endMonth]. */
@ExperimentalTime
@Composable
private fun YearStrip(
	endMonth: YearMonth,
	today: KxLocalDate,
	entries: List<FastingLogEntry>,
	activeFastStart: Instant?,
	tz: TimeZone,
	onMonthSelected: (KxLocalDate) -> Unit,
) {
	val locale = LocalLocale.current.platformLocale

	// The twelve months of this page and the day range they span.
	val months = remember(endMonth) {
		val first = endMonth.minusMonths(11)
		(0 until 12).map { first.plusMonths(it.toLong()) }
	}
	val windowStart = remember(months) {
		KxLocalDate(months.first().year, months.first().monthValue, 1)
	}
	val windowEnd = remember(months) {
		val last = months.last()
		KxLocalDate(last.year, last.monthValue, last.lengthOfMonth())
	}
	val dayCount = remember(months) { months.sumOf { it.lengthOfMonth() } }

	val loads = remember(entries, activeFastStart, windowStart, windowEnd, tz) {
		dailyFastedHours(
			entries = entries,
			activeFastStart = activeFastStart,
			now = Clock.System.now(),
			window = windowStart..windowEnd,
			tz = tz,
		)
	}

	// The window as one flat array indexed by day offset: the drawing loop walks it once,
	// and run detection is then just "is the neighbour also non-null".
	val days = remember(loads, windowStart, dayCount) {
		Array(dayCount) { i -> loads[windowStart.plus(i, DateTimeUnit.DAY)] }
	}

	val fastedDays = remember(days) { days.count { it != null } }
	val spanLabel = remember(months, locale) {
		"${AppDateTime.formatMonthYearShort(months.first(), locale)} \u2013 " +
			AppDateTime.formatMonthYearShort(months.last(), locale)
	}
	val chartDescription =
		stringResource(R.string.log_year_chart_description, spanLabel, fastedDays)

	val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
	val bandColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.045f)

	Column(
		modifier = Modifier
			.fillMaxWidth()
			.semantics { contentDescription = chartDescription },
	) {
		Text(
			text = spanLabel,
			style = MaterialTheme.typography.titleSmall,
			color = MaterialTheme.colorScheme.onSurface,
			fontWeight = FontWeight.SemiBold,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			textAlign = TextAlign.Center,
			modifier = Modifier
				.fillMaxWidth()
				.padding(bottom = 8.dp),
		)

		Box(
			modifier = Modifier
				.fillMaxWidth()
				.height(PlotHeight),
		) {
			Canvas(modifier = Modifier.fillMaxSize()) {
				val dayWidth = size.width / dayCount
				val minRun = MinRunWidth.toPx()
				val minBar = MinBarHeight.toPx()

				// Alternating month tint: what makes the months read as columns without
				// spending horizontal room on gutters.
				var offset = 0
				months.forEachIndexed { index, month ->
					val len = month.lengthOfMonth()
					if (index % 2 == 1) {
						drawRect(
							color = bandColor,
							topLeft = Offset(offset * dayWidth, 0f),
							size = Size(len * dayWidth, size.height),
						)
					}
					offset += len
				}

				// Gridlines every six hours, baseline included.
				for (hour in 0..24 step 6) {
					val y = size.height * (1f - hour / 24f)
					drawLine(
						color = gridColor,
						start = Offset(0f, y),
						end = Offset(size.width, y),
						strokeWidth = 1f,
					)
				}

				// Bars, walked as contiguous runs so one fast is one block. A run narrower
				// than MinRunWidth is widened about its centre; at ~1dp per day a lone
				// fasted day would otherwise antialias into nothing.
				var i = 0
				while (i < dayCount) {
					if (days[i] == null) {
						i++
						continue
					}
					var j = i
					while (j + 1 < dayCount && days[j + 1] != null) j++

					val span = j - i + 1
					val rawLeft = i * dayWidth
					val rawWidth = span * dayWidth
					val drawWidth = maxOf(rawWidth, minRun)
					val left = (rawLeft - (drawWidth - rawWidth) / 2f)
						.coerceIn(0f, (size.width - drawWidth).coerceAtLeast(0f))
					val perDay = drawWidth / span

					// One hue for the whole run: the longest fast touching it.
					val runColor = (i..j)
						.maxByOrNull { days[it]!!.longestFast }
						?.let { days[it]!!.color }
						?: Color.Transparent

					for (k in i..j) {
						val hours = days[k]!!.hours
						// A day holding only a sliver of a fast still gets a visible stub.
						val h = (size.height * (hours / 24.0).toFloat()).coerceAtLeast(minBar)
						drawRect(
							color = runColor,
							topLeft = Offset(left + (k - i) * perDay, size.height - h),
							size = Size(perDay, h),
						)
					}
					i = j + 1
				}
			}

			// Hour labels sit over the plot at its left edge rather than in a gutter: a
			// gutter would cost about 25 days of width on a phone. "12h" is centred, which
			// is exactly where its gridline runs.
			AxisLabel(hours = 24, modifier = Modifier.align(Alignment.TopStart))
			AxisLabel(hours = 12, modifier = Modifier.align(Alignment.CenterStart))

			// Touch targets: one transparent box per month, weighted by its length so the
			// hit areas line up exactly with the tinted columns. Real clickables rather
			// than pointerInput arithmetic buy ripple feedback and TalkBack labels.
			Row(modifier = Modifier.fillMaxSize()) {
				months.forEach { month ->
					val name = remember(month, locale) {
						"${month.month.getDisplayName(TextStyle.FULL, locale)} ${month.year}"
					}
					Box(
						modifier = Modifier
							.weight(month.lengthOfMonth().toFloat())
							.fillMaxSize()
							.clickable(onClickLabel = name) {
								onMonthSelected(KxLocalDate(month.year, month.monthValue, 1))
							},
					)
				}
			}
		}

		// Month labels, on the same weights as the columns above them.
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(top = 4.dp),
		) {
			months.forEach { month ->
				val isCurrent = month.year == today.year && month.monthValue == today.monthNumber
				Text(
					text = month.month.getDisplayName(TextStyle.SHORT, locale),
					style = MaterialTheme.typography.labelSmall,
					color = if (isCurrent) MaterialTheme.colorScheme.onSurface
					else MaterialTheme.colorScheme.onSurfaceVariant,
					fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
					textAlign = TextAlign.Center,
					maxLines = 1,
					overflow = TextOverflow.Clip,
					modifier = Modifier.weight(month.lengthOfMonth().toFloat()),
				)
			}
		}
	}
}

@Composable
private fun AxisLabel(hours: Int, modifier: Modifier = Modifier) {
	Text(
		text = stringResource(R.string.log_year_axis_hours, hours),
		style = MaterialTheme.typography.labelSmall,
		color = MaterialTheme.colorScheme.onSurfaceVariant,
		modifier = modifier.padding(start = 2.dp),
	)
}

/**
 * What the bar colours mean. The month grid has carried this palette unexplained; the
 * legend lives here because the year strip is where all five hues sit side by side.
 * It flows onto a second line rather than squeezing five columns, which no locale with
 * a word like "Optimale Autophagie" would survive.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhaseLegend() {
	FlowRow(
		modifier = Modifier
			.fillMaxWidth()
			.padding(top = 16.dp),
		horizontalArrangement = Arrangement.spacedBy(12.dp),
		verticalArrangement = Arrangement.spacedBy(6.dp),
	) {
		Stages.phases.forEachIndexed { index, phase ->
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(4.dp),
			) {
				Box(
					modifier = Modifier
						.size(8.dp)
						.clip(CircleShape)
						.background(
							calendarStageColors.getOrElse(index) { calendarStageColors.last() }
						),
				)
				Text(
					text = stringResource(phase.title),
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
			}
		}
	}
}

// Tall enough that a 6h partial day (36dp) is unmistakably shorter than a full 24h day.
private val PlotHeight = 144.dp

// The narrowest a fast may be drawn, so a single fasted day is still a visible tick.
private val MinRunWidth = 2.dp

// A fast of a few minutes deserves a mark rather than silently vanishing.
private val MinBarHeight = 1.dp
