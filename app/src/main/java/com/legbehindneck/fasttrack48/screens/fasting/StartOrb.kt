package com.legbehindneck.fasttrack48.screens.fasting

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.legbehindneck.fasttrack48.R

/**
 * The entrance to a fast: a matte, dawn-coloured disc on the dial's own centre, carrying one
 * word.
 *
 * Dawn because the dial's first stage is the dawn of the journey, so the disc reads as "begin
 * here". It is warm against the violet ground, which keeps the pop-out of the gold coin it
 * replaces without the coin's message (a medal, before any effort). Matte because the rest
 * of the screen is flat; one metal object in it looked like another app. The colours are
 * fixed in both themes: a material, not a palette entry.
 *
 * Nesting, all fractions of the dial's side D:
 *
 *   disc     d = D / φ²                ~111dp on a 291dp dial
 *   bloom    D / φ                     radius 0.309 D; the innermost tick is at 0.347 D
 *   label    em = d / φ⁴ = D / φ⁶      the arc's own stroke width: word and ring, one weight
 *            ink width ≤ d / φ         the disc's golden minor
 *
 * The label is centred on the disc's centre. A word wider than the golden minor at that size
 * steps down until it fits ("STARTEN", "ПОЧАТИ"). If that would take it under a legible 13dp,
 * it may use the disc's inscribed square (d / √2) with tighter tracking instead
 * ("COMMENCER"), and nothing goes under 11dp. Sized in dp, not sp, so a 200% font setting
 * cannot push the word through the rim; at 16dp bold and 9:1 on this ground it is legible at
 * any setting.
 *
 * The one animation on an idle screen is the bloom's breath. It runs on a [graphicsLayer]
 * over a small composable rather than invalidating the dial's canvas, which is what lets
 * [TimeLine] keep its "nothing moves while idle" rule.
 *
 * [capturing] hides the disc for a shared image, by alpha rather than by removal, so the
 * bounds the capture was measured against do not shift underneath it.
 */
@Composable
fun StartOrb(
	dialSize: Dp,
	onClick: () -> Unit,
	capturing: Boolean,
	modifier: Modifier = Modifier,
) {
	val haptics = LocalHapticFeedback.current
	val description = stringResource(id = R.string.start_fast_button)

	val interactionSource = remember { MutableInteractionSource() }
	val pressed by interactionSource.collectIsPressedAsState()

	val breath = rememberInfiniteTransition(label = "orbBreath")
	val breathScale by breath.animateFloat(
		initialValue = 1f,
		targetValue = 1.06f,
		animationSpec = infiniteRepeatable(
			animation = tween(BreathMillis, easing = FastOutSlowInEasing),
			repeatMode = RepeatMode.Reverse,
		),
		label = "orbBreathScale",
	)
	val breathAlpha by breath.animateFloat(
		initialValue = 0.40f,
		targetValue = 0.62f,
		animationSpec = infiniteRepeatable(
			animation = tween(BreathMillis, easing = FastOutSlowInEasing),
			repeatMode = RepeatMode.Reverse,
		),
		label = "orbBreathAlpha",
	)

	// Press: the disc dips, the halo brightens, and a shade closes over the face. All through
	// graphicsLayer or the disc's own draw pass, so a touch never relayouts the dial.
	val pressScale by animateFloatAsState(
		targetValue = if (pressed) 0.965f else 1f,
		animationSpec = spring(
			dampingRatio = Spring.DampingRatioLowBouncy,
			stiffness = Spring.StiffnessMediumLow,
		),
		label = "orbPressScale",
	)
	val pressBloom by animateFloatAsState(
		targetValue = if (pressed) 1.35f else 1f,
		animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
		label = "orbPressBloom",
	)
	val pressSink by animateFloatAsState(
		targetValue = if (pressed) 1f else 0f,
		animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
		label = "orbPressSink",
	)

	val discSize = dialSize / (PHI * PHI)
	val bloomSize = dialSize / PHI

	val label = stringResource(id = R.string.start_orb_label).uppercase()
	val baseStyle = MaterialTheme.typography.titleSmall.merge(
		TextStyle(
			color = Ink,
			fontWeight = FontWeight.Bold,
			textAlign = TextAlign.Center,
			// Letterpress: a light lip under each stroke, the way a matte surface catches
			// light on the lower edge of a cut.
			shadow = Shadow(color = Color.White.copy(alpha = 0.45f), offset = Offset(0f, 1.5f), blurRadius = 0f),
			platformStyle = PlatformTextStyle(includeFontPadding = false),
		)
	)
	val (fontSize, tracking) = rememberLabelFit(label, discSize, baseStyle)

	Box(
		modifier = modifier
			// requiredSize, not size: the halo is wider than the ring's inscribed content
			// square, and that box neither clips nor needs to know.
			.requiredSize(bloomSize)
			.alpha(if (capturing) 0f else 1f),
		contentAlignment = Alignment.Center,
	) {
		Box(
			modifier = Modifier
				.matchParentSize()
				.graphicsLayer {
					scaleX = breathScale
					scaleY = breathScale
					alpha = (breathAlpha * pressBloom).coerceAtMost(1f)
				}
				.drawBehind {
					val radius = size.minDimension / 2f
					drawCircle(
						brush = Brush.radialGradient(
							// Fading to a transparent dawn rather than to Color.Transparent: the
							// latter is transparent black, and interpolating through it greys
							// the halo's outer half.
							colorStops = arrayOf(
								0f to Dawn,
								// 0.62 is where the disc's rim sits (0.382 / 0.618), so this stop
								// is the halo's visible starting alpha, not a point under the disc.
								0.62f to Dawn.copy(alpha = 0.26f),
								1f to Dawn.copy(alpha = 0f),
							),
							center = center,
							radius = radius,
						),
						radius = radius,
					)
				}
		)

		Box(
			modifier = Modifier
				.size(discSize)
				.graphicsLayer {
					scaleX = pressScale
					scaleY = pressScale
				}
				.clip(CircleShape)
				.drawBehind {
					val radius = size.minDimension / 2f

					// 1. The face: lit from the upper left, where the screen's own gradient
					//    is brightest. Radial, because a matte disc has no lathe marks.
					drawCircle(
						brush = Brush.radialGradient(
							colorStops = arrayOf(0f to DawnPale, 0.55f to Dawn, 1f to DawnDeep),
							center = Offset(size.width * 0.36f, size.height * 0.30f),
							radius = size.minDimension * 0.8f,
						),
						radius = radius,
					)

					// 2. A little falloff at the rim, where the surface turns away.
					drawCircle(
						brush = Brush.radialGradient(
							colorStops = arrayOf(
								0f to Color.Transparent,
								0.78f to Color.Transparent,
								1f to Color.Black.copy(alpha = 0.10f),
							),
							center = center,
							radius = radius,
						),
						radius = radius,
					)

					// 3. A hairline catch-light on the rim: one line separates "a flat circle"
					//    from "an object".
					val hairline = 1.dp.toPx()
					drawCircle(
						color = Color.White.copy(alpha = 0.35f),
						radius = radius - hairline / 2f,
						style = Stroke(width = hairline),
					)

					// 4. Pressed: the face sinks into shade from the rim inward.
					if (pressSink > 0f) {
						drawCircle(
							brush = Brush.radialGradient(
								colorStops = arrayOf(
									0f to Color.Black.copy(alpha = 0.06f * pressSink),
									1f to Color.Black.copy(alpha = 0.30f * pressSink),
								),
								center = center,
								radius = radius,
							),
							radius = radius,
						)
					}
				}
				.clickable(
					interactionSource = interactionSource,
					// No ripple: the sink, the scale and the halo already answer the touch.
					indication = null,
					role = Role.Button,
				) {
					haptics.performHapticFeedback(HapticFeedbackType.Confirm)
					onClick()
				}
				// The word is decoration over a control that already says what it does;
				// without this, TalkBack reads "Start fast, Start".
				.semantics { contentDescription = description },
			contentAlignment = Alignment.Center,
		) {
			BasicText(
				text = label,
				maxLines = 1,
				softWrap = false,
				style = baseStyle.copy(fontSize = fontSize, letterSpacing = tracking),
				modifier = Modifier.clearAndSetSemantics { },
			)
		}
	}
}

/**
 * Font size and tracking for the disc's word, by the rules in [StartOrb]'s documentation:
 * em = d / φ⁴, narrowed until the ink fits d / φ; past the legible floor, d / √2 with tighter
 * tracking; never under the hard floor. Width is linear in the font size, so one measurement
 * per rule gives the exact size, and the result is remembered per word and disc.
 */
@Composable
private fun rememberLabelFit(label: String, disc: Dp, style: TextStyle): Pair<TextUnit, TextUnit> {
	val measurer = rememberTextMeasurer()
	val density = LocalDensity.current
	return remember(label, disc, style, density) {
		with(density) {
			val emMax = (disc / (PHI * PHI * PHI * PHI)).toPx()
			fun inkWidth(em: Float, tracking: Float): Int = measurer.measure(
				text = label,
				style = style.copy(fontSize = em.toSp(), letterSpacing = tracking.em),
				maxLines = 1,
				softWrap = false,
			).size.width
			fun fit(limit: Float, tracking: Float): Float {
				val width = inkWidth(emMax, tracking)
				return if (width <= limit) emMax else emMax * limit / width
			}

			var tracking = Tracking
			var em = fit((disc / PHI).toPx(), tracking)
			if (em < LegibleEm.toPx()) {
				tracking = TightTracking
				em = fit((disc / Sqrt2).toPx(), tracking)
			}
			em.coerceAtLeast(FloorEm.toPx()).toSp() to tracking.em
		}
	}
}

/** The disc's own palette, the same in both themes. Dawn is the warm end of the dial. */
private val DawnPale = Color(0xFFFFE2B0)
private val Dawn = Color(0xFFF4B06A)
private val DawnDeep = Color(0xFFE5895E)

/** The word's colour: burnt umber, 9:1 at the disc's centre and 5.5:1 at its deepest edge. */
private val Ink = Color(0xFF4A1E00)

/** Tracking for one word in capitals: open enough to read as a stamp, tight enough to fit. */
private const val Tracking = 0.1f
private const val TightTracking = 0.04f

/** Below this a word on the golden minor is too small to read at a glance. */
private val LegibleEm = 13.dp

/** Nothing on the disc goes below this. */
private val FloorEm = 11.dp

/** Slow enough to read as breathing rather than as pulsing. */
private const val BreathMillis = 3400

private const val Sqrt2 = 1.41421f
