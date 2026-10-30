package com.legbehindneck.fasttrack48.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.glance.GlanceTheme
import androidx.glance.color.ColorProvider
import androidx.glance.color.colorProviders

object FastingWidgetColorScheme {
	private val purple500 = Color(0xFF6200ee)
	private val purple200 = Color(0xFFA15BF8)
	private val white = Color(0xFFFFFFFF)
	private val black = Color(0xFF000000)
	private val black800 = Color(0xFF121212)
	private val gray = Color(0xFFE0E0E0)
	private val darkGray = Color(0xFF303030)
	private val subtleBackground = Color(0xFFE0E8FF)
	private val subtleBackgroundDark = Color(0xFF36195A)
	private val brightPurple = Color(0xFFC39DFF)
	private val brightishPurple = Color(0xFF8A5AC6)
	private val red600 = Color(0xFFb00020)
	private val red200 = Color(0xFFcf6679)

	// Day and night pairs for the roles the widget draws with (background, onBackground,
	// primary, secondary, surface, onSurface) and their partners.
	private val primary = ColorProvider(purple500, purple200)
	private val onPrimary = ColorProvider(gray, darkGray)
	private val secondary = ColorProvider(brightPurple, brightishPurple)
	private val onSecondary = ColorProvider(darkGray, gray)
	private val background = ColorProvider(subtleBackground, subtleBackgroundDark)
	private val onBackground = ColorProvider(black, white)
	private val surface = ColorProvider(white, black800)
	private val onSurface = ColorProvider(black, white)
	private val error = ColorProvider(red600, red200)
	private val onError = ColorProvider(white, white)

	// The Material 3 roles the widget never uses repeat their nearest Material 2 counterpart,
	// so a future use reads as part of the same scheme rather than a stray default.
	val colors = colorProviders(
		primary = primary,
		onPrimary = onPrimary,
		primaryContainer = primary,
		onPrimaryContainer = onPrimary,
		secondary = secondary,
		onSecondary = onSecondary,
		secondaryContainer = secondary,
		onSecondaryContainer = onSecondary,
		tertiary = secondary,
		onTertiary = onSecondary,
		tertiaryContainer = secondary,
		onTertiaryContainer = onSecondary,
		error = error,
		errorContainer = error,
		onError = onError,
		onErrorContainer = onError,
		background = background,
		onBackground = onBackground,
		surface = surface,
		onSurface = onSurface,
		surfaceVariant = background,
		onSurfaceVariant = onBackground,
		outline = onSurface,
		inverseOnSurface = surface,
		inverseSurface = onSurface,
		inversePrimary = primary,
		widgetBackground = background,
	)
}

@Composable
fun FastingWidgetTheme(content: @Composable () -> Unit) {
	GlanceTheme(
		colors = FastingWidgetColorScheme.colors,
		content = content
	)
}
