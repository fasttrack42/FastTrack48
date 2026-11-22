package com.legbehindneck.fasttrack48.utils

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.repeatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState

/** Breaths taken each time the screen comes into view: enough to be seen, then rest. */
private const val BREATHS = 3

/**
 * A breath from 0 to 1 and back, [BREATHS] times whenever the screen comes into view or [key]
 * changes, then at rest at 0. A pulse that never stops redraws every frame for as long as the
 * screen is on (60 or 120 times a second); a few breaths on arrival carry the same cue.
 *
 * Read [Animatable.value] only inside a draw or graphicsLayer block, so the breathing costs a
 * redraw and never a recomposition. While [active] is false the breath rests at 0.
 */
@Composable
fun rememberBoundedBreath(
	active: Boolean,
	halfBreathMillis: Int,
	key: Any? = null,
): Animatable<Float, AnimationVector1D> {
	val breath = remember { Animatable(0f) }
	val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
	val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
	LaunchedEffect(active, resumed, key) {
		if (active && resumed) {
			val half = tween<Float>(halfBreathMillis, easing = FastOutSlowInEasing)
			// An odd count ends on the target; Reverse with an even count would jump at the end.
			breath.animateTo(1f, repeatable(iterations = BREATHS * 2 - 1, animation = half, repeatMode = RepeatMode.Reverse))
			breath.animateTo(0f, half)
		} else {
			breath.snapTo(0f)
		}
	}
	return breath
}
