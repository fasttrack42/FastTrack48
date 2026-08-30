package com.legbehindneck.fasttrack48.screens.fasting

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legbehindneck.fasttrack48.ui.theme.LocalDarkTheme
import com.legbehindneck.fasttrack48.ui.theme.Purple100
import com.legbehindneck.fasttrack48.ui.theme.Purple700

val phaseTextColor_Light = Purple700
val phaseTextColor_Dark = Purple100

@Composable
fun phaseTextColor(): Color {
	val isDark: Boolean = LocalDarkTheme.current
	return if (isDark) {
		phaseTextColor_Dark
	} else {
		phaseTextColor_Light
	}
}

// Spacing configuration for responsive layout
data class FastingSpacing(
	val small: Dp,
	val medium: Dp,
	val large: Dp,
	// The ladder's top rung, and the only one that separates *groups* rather than
	// elements: 21 / 10 (the gap between two phase rows) is 2.1x, which is the smallest
	// ratio that reads as a group boundary rather than as slightly loose spacing.
	val xlarge: Dp,
	val iconSize: Dp,
	val buttonPaddingHorizontal: Dp,
	val buttonPaddingVertical: Dp,
)

// Spacing follows the Fibonacci ladder (3, 5, 8, 13, 21) — the integer
// expression of the golden ratio, so every gap relates to its neighbor by ~phi.
val LocalFastingSpacing = staticCompositionLocalOf {
	FastingSpacing(
		small = 5.dp,
		medium = 8.dp,
		large = 13.dp,
		xlarge = 21.dp,
		iconSize = 21.dp,
		buttonPaddingHorizontal = 13.dp,
		buttonPaddingVertical = 8.dp,
	)
}

// Typography configuration for responsive layout
data class FastingTypography(
	val stageTitle: @Composable () -> androidx.compose.ui.text.TextStyle,
	val energyMode: @Composable () -> androidx.compose.ui.text.TextStyle,
	val timerText: @Composable () -> androidx.compose.ui.text.TextStyle,
	val timerMilliseconds: @Composable () -> androidx.compose.ui.text.TextStyle,
	val phaseLabel: @Composable () -> androidx.compose.ui.text.TextStyle,
	val phaseTime: @Composable () -> androidx.compose.ui.text.TextStyle,
	val stageDescription: @Composable () -> androidx.compose.ui.text.TextStyle,
	val checkboxLabel: @Composable () -> androidx.compose.ui.text.TextStyle,
)

val LocalFastingTypography = staticCompositionLocalOf<FastingTypography?> { null }

@Composable
fun fastingTypography(): FastingTypography =
	LocalFastingTypography.current ?: FastingTypography(
		stageTitle = { MaterialTheme.typography.headlineMedium },
		energyMode = { MaterialTheme.typography.labelMedium },
		timerText = { MaterialTheme.typography.displayLarge },
		timerMilliseconds = { MaterialTheme.typography.headlineMedium },
		phaseLabel = { MaterialTheme.typography.titleMedium },
		phaseTime = { MaterialTheme.typography.titleMedium },
		stageDescription = { MaterialTheme.typography.bodyMedium },
		checkboxLabel = { MaterialTheme.typography.labelLarge },
	)

@Composable
fun fastingSpacing(): FastingSpacing = LocalFastingSpacing.current

@Composable
fun rememberFastingSpacing(isCompact: Boolean): FastingSpacing {
	return remember(isCompact) {
		if (isCompact) {
			FastingSpacing(
				small = 3.dp,
				medium = 5.dp,
				large = 8.dp,
				xlarge = 13.dp,
				iconSize = 13.dp,
				buttonPaddingHorizontal = 8.dp,
				buttonPaddingVertical = 5.dp,
			)
		} else {
			FastingSpacing(
				small = 5.dp,
				medium = 8.dp,
				large = 13.dp,
				xlarge = 21.dp,
				iconSize = 21.dp,
				buttonPaddingHorizontal = 13.dp,
				buttonPaddingVertical = 8.dp,
			)
		}
	}
}

@Composable
fun rememberFastingTypography(isCompact: Boolean): FastingTypography {
	return remember(isCompact) {
		if (isCompact) {
			FastingTypography(
				stageTitle = { MaterialTheme.typography.headlineSmall },
				energyMode = { MaterialTheme.typography.labelSmall },
				timerText = { MaterialTheme.typography.displayMedium },
				timerMilliseconds = { MaterialTheme.typography.headlineSmall },
				phaseLabel = { MaterialTheme.typography.bodySmall },
				phaseTime = { MaterialTheme.typography.bodySmall },
				stageDescription = { MaterialTheme.typography.bodySmall },
				checkboxLabel = { MaterialTheme.typography.labelMedium },
			)
		} else {
			FastingTypography(
				stageTitle = { MaterialTheme.typography.headlineMedium },
				energyMode = { MaterialTheme.typography.labelMedium },
				timerText = { MaterialTheme.typography.displayLarge },
				timerMilliseconds = { MaterialTheme.typography.headlineMedium },
				// One rung down from the title styles, and body weight rather than title
				// weight. These three rows are a readout, not an instruction: at
				// titleMedium's 16sp semibold they carried more voice than the timer they
				// elaborate on, and three of them stacked read as a demand.
				phaseLabel = { MaterialTheme.typography.bodyMedium },
				phaseTime = { MaterialTheme.typography.bodyMedium },
				stageDescription = { MaterialTheme.typography.bodyMedium },
				checkboxLabel = { MaterialTheme.typography.labelLarge },
			)
		}
	}
}

/**
 * The largest font size at which *every* string in [texts] fits [maxWidth] on one line.
 *
 * Compose's own `TextAutoSize` is per-composable, which is exactly wrong for a set of texts
 * that have to agree. Left to themselves the three phase labels would settle at three
 * different sizes — "Cetosis" at full size beside a shrunken "Quema de Grasa" — and the
 * rows are built as a mirrored block whose entire value is that every label ends on the
 * same x and every value begins on it. Ragged sizes would spend that alignment to save a
 * point of type.
 *
 * So the block is measured as a block: one size, chosen by the longest member, applied to
 * all of them. Stepping down by a point at a time from [max] is O(max - min) measurements
 * of a handful of short strings, done once per set and re-run only when the strings, the
 * style, the width, or the user's font scale change — none of which happen during a frame.
 *
 * Returns [min] when even that does not fit, leaving the caller's own overflow rule to
 * decide what happens; at that point no size would have helped.
 */
@Composable
internal fun rememberSharedAutoSize(
	texts: List<String>,
	style: TextStyle,
	maxWidth: Dp,
	min: TextUnit,
	max: TextUnit,
): TextUnit {
	val measurer = rememberTextMeasurer()
	val density = LocalDensity.current
	// fontScale is read explicitly: it is part of Density but not part of its equality for
	// our purposes, and a user changing the system font size must invalidate this.
	return remember(texts, style, maxWidth, min, max, density.density, density.fontScale) {
		val maxWidthPx = with(density) { maxWidth.roundToPx() }
		// A width of zero is the first composition, before constraints are known. Answering
		// `min` there would flash the smallest size for one frame; `max` measures nothing
		// and is corrected on the very next pass.
		if (maxWidthPx <= 0 || texts.isEmpty()) return@remember max

		var size = max.value
		while (size > min.value) {
			val candidate = style.copy(fontSize = size.sp)
			val fits = texts.all { text ->
				measurer.measure(
					text = text,
					style = candidate,
					maxLines = 1,
					softWrap = false,
					constraints = Constraints(),
				).size.width <= maxWidthPx
			}
			if (fits) return@remember size.sp
			size -= 1f
		}
		min
	}
}
