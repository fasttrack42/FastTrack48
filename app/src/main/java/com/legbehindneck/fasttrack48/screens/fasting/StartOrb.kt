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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import com.legbehindneck.fasttrack48.utils.WordSafe
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Shadow
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legbehindneck.fasttrack48.R

/**
 * The entrance to a fast: a struck gold medallion seated on the dial's own centre.
 *
 * The idle dial is a hashed empty ring, and the action that fills it used to be the
 * smallest element on the screen, parked on the arc's left shoulder with nothing facing
 * it. Putting it in the centre lets the geometry state the intent — *you are entering the
 * ring* — and the composition finally has a subject.
 *
 * Nesting, all fractions of the dial's side D and all derived rather than chosen:
 *
 *   core disc   D / phi^2 = 0.382 D   ~130dp on the portrait dial
 *   bloom       D / phi   = 0.618 D   radius 0.309 D
 *   glyph       disc / phi^2          the same step, one level down
 *
 * The bloom's radius is the binding constraint: [TimeLine]'s innermost tick reaches
 * 0.3328 D, so the halo stops ~0.024 D short of the texture and never touches it. This is
 * the largest orb the existing dial permits, not a number picked to look big.
 *
 * ### Why the metal is hand-built and theme-independent
 *
 * The first cut of this used `journeyStageColor(0)` for the disc. In dark theme that is a
 * near-white ivory, so the "gold" orb rendered as a pale blob. Metal is not a palette
 * entry that inverts with the theme — gold is gold on either ground — so the ramp below is
 * its own, fixed, and used verbatim in both themes. Only the bloom borrows the stage-0
 * accent's role, and it does so in this same gold.
 *
 * The face is a [Brush.sweepGradient] rather than a linear or radial one, because that is
 * what lathe-turned metal actually is: brightness as a function of *angle*, two bright
 * lobes on the axis facing the light and two dark ones across from them. A radial gradient
 * can only ever produce a dome. Over it sit three cheap physical cues, in order — an
 * ambient-occlusion falloff toward the rim, one diffuse specular in the upper left, and a
 * hairline shade where the face meets the bevel. The bevel itself is a second sweep, phase
 * shifted so its bright quarter faces the same 315 degrees the specular does.
 *
 * The one animation on an idle screen is the bloom's breath, and it runs on a
 * [graphicsLayer] over a small composable rather than invalidating the dial's canvas —
 * which is what lets [TimeLine] keep its deliberate "nothing moves while idle" rule.
 *
 * [capturing] hides the orb for a shared image, by alpha rather than by removal, so the
 * bounds the capture was measured against do not shift underneath it.
 */
@Composable
fun StartOrb(
	dialSize: Dp,
	onClick: () -> Unit,
	capturing: Boolean,
	modifier: Modifier = Modifier,
) {
	val spacing = fastingSpacing()
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

	// Press: the disc dips, the halo brightens, and an inner shadow closes over the face —
	// the medallion pressing into its seat. All three through graphicsLayer or the existing
	// draw pass, so a touch never relayouts the dial underneath.
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

	val discSize = dialSize * DiscFraction
	val bloomSize = dialSize * BloomFraction
	// EmOverInk corrects the em box up to the ink it actually contains, so the flame's
	// visible height — not the invisible box around it — is the golden nesting step.
	val glyphEm = with(LocalDensity.current) { (dialSize * GlyphFraction * EmOverInk).toSp() }

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
							// Fading to a transparent *gold* rather than to
							// Color.Transparent: the latter is transparent black, and
							// interpolating through it greys the halo's outer half.
							colorStops = arrayOf(
								0f to Gold,
								// 0.62 is where the disc's rim sits (0.382 / 0.618), so
								// this stop is the halo's *visible* starting alpha rather
								// than a point hidden under the medallion.
								0.62f to Gold.copy(alpha = 0.26f),
								1f to Gold.copy(alpha = 0f),
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
					val bevel = radius * BevelFraction
					val faceRadius = radius - bevel

					// 1. The bevel: a full disc of angular metal, most of which the face
					//    then covers. Bright at 315 degrees (upper left), dark opposite.
					drawCircle(
						brush = Brush.sweepGradient(colorStops = BevelStops, center = center),
						radius = radius,
					)

					// 2. The face, turned on the same axis but with a shallower contrast
					//    range, so the bevel stays the brightest thing on the medallion.
					drawCircle(
						brush = Brush.sweepGradient(colorStops = FaceStops, center = center),
						radius = faceRadius,
					)

					// 3. Ambient occlusion: metal darkens toward a rim it cannot see past.
					drawCircle(
						brush = Brush.radialGradient(
							colorStops = arrayOf(
								0f to Color.Transparent,
								0.72f to Color.Black.copy(alpha = 0.06f),
								1f to Color.Black.copy(alpha = 0.30f),
							),
							center = center,
							radius = faceRadius,
						),
						radius = faceRadius,
					)

					// 4. One diffuse specular, upper left, where the screen's own gradient
					//    is brightest. Broad and soft — a hard spot would read as plastic.
					drawCircle(
						brush = Brush.radialGradient(
							colors = listOf(
								Color.White.copy(alpha = 0.34f),
								Color.White.copy(alpha = 0f),
							),
							center = Offset(size.width * 0.34f, size.height * 0.28f),
							radius = faceRadius * 0.92f,
						),
						radius = faceRadius,
					)

					// 5. The shade line where the face steps down from the bevel. One
					//    hairline is what separates "two circles" from "one machined part".
					val hairline = 1.dp.toPx()
					drawCircle(
						color = Color.Black.copy(alpha = 0.28f),
						radius = faceRadius - hairline / 2f,
						style = Stroke(width = hairline),
					)

					// 6. Pressed: the face sinks into shadow from the rim inward.
					if (pressSink > 0f) {
						drawCircle(
							brush = Brush.radialGradient(
								colorStops = arrayOf(
									0f to Color.Black.copy(alpha = 0.06f * pressSink),
									1f to Color.Black.copy(alpha = 0.34f * pressSink),
								),
								center = center,
								radius = faceRadius,
							),
							radius = faceRadius,
						)
					}
				}
				.clickable(
					interactionSource = interactionSource,
					// No ripple: the sink, the scale and the halo already answer the touch,
					// and a rectangular-origin ripple over turned metal reads as a smudge.
					indication = null,
					role = Role.Button,
				) {
					haptics.performHapticFeedback(HapticFeedbackType.Confirm)
					onClick()
				}
				// The glyph and the one-word label are decoration over a control that
				// already says what it does; without this, TalkBack reads "Start, Start".
				.semantics { contentDescription = description },
			contentAlignment = Alignment.Center,
		) {
			Column(
				horizontalAlignment = Alignment.CenterHorizontally,
				// The disc's inscribed square: everything inside stays off the bevel.
				modifier = Modifier
					.width(discSize / Sqrt2)
					.clearAndSetSemantics { },
			) {
				// The flame is type, not a vector: it is a colour emoji, and a colour emoji
				// cannot be tinted, so it is drawn at a size rather than at a colour. Drawn
				// as an engraved monochrome silhouette it read as a leaf — fire is carried by
				// its gradient, not by its outline — and as the emoji it also lands in the
				// same token family as the stage bubbles around the dial.
				//
				// Its slot is its em box, sized through Dp.toSp() so the glyph follows the
				// dial's geometry and not the user's font scale: a 200% accessibility setting
				// must not push a flame out through the rim.
				BasicText(
					text = FlameGlyph,
					style = TextStyle(
						fontSize = glyphEm,
						lineHeight = glyphEm,
						textAlign = TextAlign.Center,
						// The emoji is the one thing on the medallion that is not metal, so it
						// gets the one thing metal gives an object resting on it: a contact
						// shadow, short and soft, cast the way the face's own specular implies.
						shadow = Shadow(
							color = Color.Black.copy(alpha = 0.28f),
							offset = Offset(0f, 2f),
							blurRadius = 5f,
						),
						platformStyle = PlatformTextStyle(includeFontPadding = false),
					),
					modifier = Modifier.fillMaxWidth(),
				)
				Spacer(modifier = Modifier.height(spacing.small))
				// One word, tracked. Autosized rather than fixed because "Commencer" and
				// "ПОЧАТИ" have to live inside the same circle as "START".
				BasicText(
					text = stringResource(id = R.string.start_orb_label).uppercase(),
					maxLines = 1,
					autoSize = TextAutoSize.WordSafe(
						minFontSize = 10.sp,
						maxFontSize = 15.sp,
						stepSize = 1.sp,
					),
					style = MaterialTheme.typography.titleSmall.merge(
						TextStyle(
							color = Engraved,
							fontWeight = FontWeight.Bold,
							letterSpacing = 1.6.sp,
							textAlign = TextAlign.Center,
						)
					),
					modifier = Modifier.fillMaxWidth(),
				)
			}
		}
	}
}

/**
 * The medallion's own palette, fixed in both themes.
 *
 * Metal is described by where the light is, not by whether the page around it is dark, and
 * a gold that inverted with the theme would be a yellow shape rather than a material.
 */
private val Gold = Color(0xFFE3B24E)
private val GoldPale = Color(0xFFF7E4A8)
private val GoldDeep = Color(0xFFA8752A)
private val GoldShadow = Color(0xFF6E4A18)

/** Deep bronze rather than black: an engraved mark keeps the metal's hue in its shadow. */
private val Engraved = Color(0xFF3B2708)

/**
 * Angular brightness of the bevel. Compose's sweep starts at three o'clock and runs
 * clockwise, so upper left is 0.625 of a turn and lower right is 0.125 — the light sits at
 * the former, the cast shade at the latter. First and last stops match, or the seam shows.
 */
private val BevelStops = arrayOf(
	0.000f to GoldDeep,
	0.125f to GoldShadow,
	0.375f to Gold,
	0.625f to GoldPale,
	0.875f to Gold,
	1.000f to GoldDeep,
)

/** The same axis, compressed: the face must stay a step below its own rim. */
private val FaceStops = arrayOf(
	0.000f to GoldDeep,
	0.125f to GoldDeep,
	0.375f to Gold,
	0.625f to GoldPale,
	0.875f to Gold,
	1.000f to GoldDeep,
)

/** Core disc: the golden minor of the golden minor of the dial. */
private const val DiscFraction = 1f / (PHI * PHI)

/** Ambient halo, bounded by the dial's innermost tick ring at 0.3328 of the side. */
private const val BloomFraction = 1f / PHI

/** The same nesting step again, from the disc to the glyph inside it. */
private const val GlyphFraction = DiscFraction / (PHI * PHI)

/**
 * Noto Color Emoji draws the flame about 15% *taller* than its em square — the glyph
 * overshoots the ascent — so the em box has to be set below the target for the fire itself
 * to land on the nesting step. Measured off the rendered glyph, not assumed: the naive
 * assumption is that ink fits inside the em, and it does not.
 */
private const val EmOverInk = 0.87f

/** Stage four's mark, and the app's own symbol for what a fast is. */
private const val FlameGlyph = "\uD83D\uDD25"

/** Rim width as a fraction of the disc's radius — ~5dp on the portrait dial. */
private const val BevelFraction = 0.075f

/** Slow enough to read as breathing rather than as pulsing. */
private const val BreathMillis = 3400

private const val Sqrt2 = 1.41421f
