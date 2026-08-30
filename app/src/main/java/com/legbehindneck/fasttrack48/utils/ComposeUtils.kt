package com.legbehindneck.fasttrack48.utils

import android.os.Vibrator
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.material3.SelectableDates
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import java.time.LocalDate as JavaLocalDate

@Composable
fun combinePadding(padding1: PaddingValues, padding2: PaddingValues): PaddingValues {
	val layoutDirection = LocalLayoutDirection.current

	return PaddingValues(
		start = padding1.calculateStartPadding(layoutDirection) + padding2.calculateStartPadding(layoutDirection),
		top = padding1.calculateTopPadding() + padding2.calculateTopPadding(),
		end = padding1.calculateEndPadding(layoutDirection) + padding2.calculateEndPadding(layoutDirection),
		bottom = padding1.calculateBottomPadding() + padding2.calculateBottomPadding()
	)
}

/**
 * Custom implementation of SelectableDates that only allows dates before now (including today)
 */
class PastAndTodaySelectableDates : SelectableDates {
	override fun isSelectableDate(utcTimeMillis: Long): Boolean {
		val today = JavaLocalDate.now()
		val date = JavaLocalDate.ofEpochDay(utcTimeMillis / (24 * 60 * 60 * 1000))
		return !date.isAfter(today)
	}
}

/**
 * Custom implementation of SelectableDates that only allows dates between a minimum date and today
 */
class DateRangeSelectableDates(private val minDateMillis: Long?) : SelectableDates {
	override fun isSelectableDate(utcTimeMillis: Long): Boolean {
		val today = JavaLocalDate.now()
		val date = JavaLocalDate.ofEpochDay(utcTimeMillis / (24 * 60 * 60 * 1000))

		// Date must not be after today
		if (date.isAfter(today)) return false

		// If minDateMillis is specified, date must not be before it
		if (minDateMillis != null) {
			val minDate = JavaLocalDate.ofEpochDay(minDateMillis / (24 * 60 * 60 * 1000))
			if (date.isBefore(minDate)) return false
		}

		return true
	}
}

val MAX_COLUMN_WIDTH = 600.dp

@Composable
fun rememberVibrator(): Vibrator? {
	val context = LocalContext.current
	return remember { context.getSystemService<Vibrator>() }
}


/**
 * The floor the type may be compressed to, expressed as a font scale rather than an sp
 * value so it applies to a whole block at once and keeps its internal hierarchy: a title
 * stays proportionally larger than the body it heads, whatever the block gives back.
 *
 * 0.8 rather than 1.0 because the pressure is not only accessibility. At the system
 * default scale a long locale already overruns a layout that fits in English — that is
 * the same defect and deserves the same remedy, and a floor of 1.0 would leave it with
 * nothing to give.
 */
private const val MinFitFontScale = 0.8f

/** Guarantees termination: every step gives back at least this much, so the search ends. */
private const val MinFitStep = 0.02f

/**
 * A [Density] whose font scale has been reduced by exactly as much as it takes for
 * [scrollState]'s content to stop overflowing its viewport — shrink first, scroll only
 * when even [minFontScale] does not fit.
 *
 * Autosizing cannot do this job. `TextAutoSize` resolves a size against the constraints
 * its own text node is given, and under a `verticalScroll` the incoming maxHeight is
 * Infinity: the fit test can only ever fail on width or on maxLines, never on height. No
 * minimum, however low, changes that — a height-bound block inside a scroll will never
 * shrink itself, because from where the text stands there is always room. And per-node
 * autosizing could not keep the block coherent even if it could see the height; each text
 * would settle at its own size and the hierarchy would go ragged.
 *
 * So the block is fitted as a block, against the one number that actually reports the
 * overflow: [ScrollState.maxValue], which the scroll modifier writes during measurement
 * and which is the exact pixel amount by which the content exceeds the viewport. Each
 * observation of an overflow gives back part of the font scale; the resulting relayout
 * publishes a new overflow; the loop settles in two or three frames because the first
 * step is proportional to the deficit rather than a fixed decrement.
 *
 * It cannot oscillate: the scale is monotonically decreasing for a given
 * [resetKey]/viewport/system-scale, so a shrink can never re-expand into the overflow
 * that caused it. It cannot run away: [minFontScale] bounds it, and past that the scroll
 * is the escape hatch, which is the correct answer at that point — beyond this floor the
 * honest response to a reader who asked for large type is a scrollbar, not smaller words.
 *
 * Only sp is affected. `density` is passed through untouched, so every dp in the block —
 * padding, the dial, the arc band — measures exactly as it did before.
 *
 * One caveat: [Density] built this way converts sp linearly, where the platform's own
 * density is non-linear above scale 1.0 on API 34+. Inside a block that has already been
 * asked to give type back, the linear curve is the more predictable of the two, and the
 * difference at these scales is under a point.
 *
 * @param resetKey anything whose change makes the previous fit stale — typically the text
 *   being laid out. Changing it restores the reader's own scale and re-fits from there,
 *   which is what lets the block grow back when its content gets shorter.
 */
@Composable
fun rememberFitToViewportDensity(
	scrollState: ScrollState,
	resetKey: Any? = null,
	minFontScale: Float = MinFitFontScale,
): Density {
	val base = LocalDensity.current
	// viewportSize is a key, not an input: it changes on rotation or a window resize, and
	// a fit computed for the old viewport says nothing about the new one.
	var scale by remember(base.density, base.fontScale, minFontScale, resetKey, scrollState.viewportSize) {
		mutableFloatStateOf(base.fontScale)
	}

	LaunchedEffect(scrollState, base.density, base.fontScale, minFontScale, resetKey) {
		snapshotFlow { scrollState.maxValue }.collect { overflow ->
			// MAX_VALUE is "not measured yet"; 0 is "it fits".
			if (overflow <= 0 || overflow == Int.MAX_VALUE) return@collect
			val viewport = scrollState.viewportSize
			if (viewport <= 0 || scale <= minFontScale) return@collect

			// viewport / content is the factor the whole block would have to shrink by if
			// all of it were type. Some of it is dp and will not move, so that factor
			// undershoots; halving the distance to it approaches the true fit from above
			// and never overshoots into type smaller than the overflow warranted.
			val allType = scale * viewport / (viewport + overflow).toFloat()
			scale = minOf(scale - MinFitStep, (scale + allType) / 2f).coerceAtLeast(minFontScale)
		}
	}

	return remember(base.density, scale) { Density(base.density, scale) }
}
