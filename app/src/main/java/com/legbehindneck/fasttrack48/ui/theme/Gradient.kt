package com.legbehindneck.fasttrack48.ui.theme

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Colors from fast_background.xml (light mode)
val LightGradientStartColor = Color(0xFFE9DBFF) // #E9DBFF
val LightGradientEndColor = Color(0xFFC2D7FF) // #C2D7FF

// Dark mode gradient
val DarkGradientStartColor = Color(0xFF2A1F3D)
val DarkGradientEndColor = Color(0xFF1A1A1A)

/**
 * Extension function to apply the fast background gradient to a Modifier.
 * This gradient matches the ones defined in fast_background.xml:
 *
 * Light mode:
 * - startColor: #E9DBFF
 * - endColor: #C2D7FF
 * - angle: 90 degrees (bottom to top)
 *
 * Dark mode:
 * - startColor: #4E447A
 * - endColor: #8E8E8E
 * - angle: 90 degrees (bottom to top)
 */
fun Modifier.fastBackgroundGradient(show: Boolean, isDarkTheme: Boolean): Modifier =
	if (show) background(brush = if (isDarkTheme) DarkFastBrush else LightFastBrush) else this

// Built once: a Brush is immutable, and a plain modifier over a shared one allocates nothing per
// recomposition (the `composed` form it replaces built a new Brush and modifier every time).
private val LightFastBrush = Brush.verticalGradient(colors = listOf(LightGradientStartColor, LightGradientEndColor))
private val DarkFastBrush = Brush.verticalGradient(colors = listOf(DarkGradientStartColor, DarkGradientEndColor))
