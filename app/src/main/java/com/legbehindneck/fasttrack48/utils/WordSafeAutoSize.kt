package com.legbehindneck.fasttrack48.utils

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlin.math.floor

/**
 * [TextAutoSize.StepBased], with one additional thing it refuses to call a fit: a line
 * break that lands inside a word.
 *
 * The stock implementation asks only whether the laid-out text overflows its bounds. But
 * a greedy line breaker never overflows on width — when a word is wider than the line it
 * splits the word and carries on, and the result *fits*. So autosizing sees no reason to
 * step down, and the label settles at a size where Spanish "Terminar ayuno" renders as
 * "Termina / r ayuno". Neither a lower `minFontSize` nor a larger `maxLines` reaches this:
 * the acceptance test is wrong, not the range it searches.
 *
 * Here a candidate size is a fit only if, additionally, no line begins in the interior of
 * a word. That predicate is monotone in the same direction as the overflow one — a word
 * that fit at some size still fits at a smaller one — so the same binary search converges,
 * and the found size is the largest at which every word survives whole.
 *
 * Scripts that legitimately wrap between adjacent characters — Chinese, Japanese, Korean,
 * Thai and their neighbours — are exempt, or zh-CN would be forced to the floor on every
 * label. So is any break next to punctuation, whitespace or a hyphen, which are real break
 * opportunities in every script.
 *
 * When even [minFontSize] cannot hold the longest word — a container genuinely too narrow
 * for the string — the floor is returned and the break happens anyway. That is no worse
 * than the stock behaviour, and it is the point at which the layout, not the type, is what
 * needs fixing.
 *
 * @param minFontSize floor of the search; must be sp, as must the other two.
 * @param maxFontSize preferred size, tried first.
 * @param stepSize granularity; sizes returned are [minFontSize] plus a multiple of it.
 */
fun TextAutoSize.Companion.WordSafe(
	minFontSize: TextUnit,
	maxFontSize: TextUnit,
	stepSize: TextUnit = 0.5.sp,
): TextAutoSize = WordSafeAutoSize(minFontSize, maxFontSize, stepSize)

private class WordSafeAutoSize(
	private val minFontSize: TextUnit,
	private val maxFontSize: TextUnit,
	private val stepSize: TextUnit,
) : TextAutoSize {

	override fun TextAutoSizeLayoutScope.getFontSize(
		constraints: Constraints,
		text: AnnotatedString,
	): TextUnit {
		val step = stepSize.toPx()
		val smallest = minFontSize.toPx()
		val largest = maxFontSize.toPx()
		if (step <= 0f || largest <= smallest) return minFontSize
		if (fits(constraints, text, largest)) return maxFontSize

		// Invariant: `low` fits (or is the floor we fall back to), `high` does not.
		var low = smallest
		var high = largest
		while (high - low >= step) {
			val mid = (low + high) / 2f
			if (fits(constraints, text, mid)) low = mid else high = mid
		}
		// Snap onto the step grid, downwards -- a smaller size cannot stop fitting.
		return (floor((low - smallest) / step) * step + smallest).toSp()
	}

	private fun TextAutoSizeLayoutScope.fits(
		constraints: Constraints,
		text: AnnotatedString,
		fontSizePx: Float,
	): Boolean {
		val layout = performLayout(constraints, text, fontSizePx.toSp())
		return !layout.overflowed() && !layout.splitsAWord()
	}

	private fun TextLayoutResult.overflowed(): Boolean =
		when (layoutInput.overflow) {
			TextOverflow.Ellipsis,
			TextOverflow.StartEllipsis,
			TextOverflow.MiddleEllipsis ->
				lineCount > 0 && (isLineEllipsized(lineCount - 1) || didOverflowHeight)

			else -> didOverflowWidth || didOverflowHeight
		}

	/** True if any line after the first begins in the middle of a word. */
	private fun TextLayoutResult.splitsAWord(): Boolean {
		val source = layoutInput.text.text
		for (line in 1 until lineCount) {
			val start = getLineStart(line)
			if (start <= 0 || start >= source.length) continue
			if (isWordInterior(source[start - 1], source[start])) return true
		}
		return false
	}

	override fun equals(other: Any?): Boolean {
		if (other === this) return true
		if (other !is WordSafeAutoSize) return false
		return minFontSize == other.minFontSize &&
			maxFontSize == other.maxFontSize &&
			stepSize == other.stepSize
	}

	override fun hashCode(): Int {
		var result = minFontSize.hashCode()
		result = 31 * result + maxFontSize.hashCode()
		result = 31 * result + stepSize.hashCode()
		return result
	}
}

/**
 * Whether a break between [before] and [after] would cut a word in half.
 *
 * Only letters and digits can be a word's interior; anything else -- a space, a hyphen, a
 * slash, a dash, an emoji -- is a place a line is allowed to end. Surrogate halves report
 * as neither letter nor digit, which lands supplementary characters on the permissive side
 * of this test, where they belong: those are overwhelmingly emoji and CJK extensions.
 */
private fun isWordInterior(before: Char, after: Char): Boolean {
	if (!before.isLetterOrDigit() || !after.isLetterOrDigit()) return false
	return !before.wrapsBetweenCharacters() && !after.wrapsBetweenCharacters()
}

/**
 * Scripts written without spaces, where a break between two adjacent characters is the
 * normal way to wrap rather than a defect.
 */
private fun Char.wrapsBetweenCharacters(): Boolean = when (code) {
	in 0x0E00..0x0EFF, // Thai, Lao
	in 0x1000..0x109F, // Myanmar
	in 0x1780..0x17FF, // Khmer
	in 0x2E80..0x303F, // CJK radicals and punctuation
	in 0x3040..0x30FF, // Hiragana, Katakana
	in 0x3400..0x4DBF, // CJK unified ideographs extension A
	in 0x4E00..0x9FFF, // CJK unified ideographs
	in 0xA000..0xA4CF, // Yi
	in 0xAC00..0xD7AF, // Hangul syllables
	in 0xF900..0xFAFF, // CJK compatibility ideographs
	in 0xFF00..0xFF9F, // Halfwidth and fullwidth forms
	-> true

	else -> false
}
