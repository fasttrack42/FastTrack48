package com.legbehindneck.fasttrack48.screens.fasting

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Spacing configuration for responsive layout
data class FastingSpacing(
	val small: Dp,
	val medium: Dp,
	val large: Dp,
	// The ladder's top rung, and the only one that separates *groups* rather than
	// elements: 21 is 2.6x (about phi squared) the 8dp between two lines of one group,
	// which reads as a boundary rather than as slightly loose spacing.
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
	/** The stage name, inside the ring above the timer. */
	val stageTitle: @Composable () -> TextStyle,
	val timerText: @Composable () -> TextStyle,
	/** The next milestone under the dial: "Ketosis in 2h 37m". */
	val milestone: @Composable () -> TextStyle,
	/** The milestone after it, and the end hour of the bar. */
	val milestoneDetail: @Composable () -> TextStyle,
	val stageDescription: @Composable () -> TextStyle,
)

val LocalFastingTypography = staticCompositionLocalOf<FastingTypography?> { null }

@Composable
fun fastingTypography(): FastingTypography =
	LocalFastingTypography.current ?: FastingTypography(
		stageTitle = { MaterialTheme.typography.titleMedium },
		timerText = { MaterialTheme.typography.displayLarge },
		milestone = { MaterialTheme.typography.titleMedium },
		milestoneDetail = { MaterialTheme.typography.bodyMedium },
		stageDescription = { MaterialTheme.typography.bodyMedium },
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
				stageTitle = { MaterialTheme.typography.titleSmall },
				timerText = { MaterialTheme.typography.displayMedium },
				milestone = { MaterialTheme.typography.titleSmall },
				milestoneDetail = { MaterialTheme.typography.bodySmall },
				stageDescription = { MaterialTheme.typography.bodySmall },
			)
		} else {
			FastingTypography(
				// Inside the ring now, as the timer's caption: one rung of title, not the
				// headline it was above the dial. The timer is the screen's one focal point.
				stageTitle = { MaterialTheme.typography.titleMedium },
				timerText = { MaterialTheme.typography.displayLarge },
				milestone = { MaterialTheme.typography.titleMedium },
				milestoneDetail = { MaterialTheme.typography.bodyMedium },
				stageDescription = { MaterialTheme.typography.bodyMedium },
			)
		}
	}
}
