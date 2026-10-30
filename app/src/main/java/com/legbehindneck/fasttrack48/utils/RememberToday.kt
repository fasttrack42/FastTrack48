package com.legbehindneck.fasttrack48.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Today's date in [zone], turning over at midnight. Read again on every resume: a delay does
 * not run while the phone sleeps, and a screen left in the background for days must not keep
 * the day it was opened on. Use it as a key for anything that says "today" or "tomorrow".
 */
@Composable
fun rememberToday(zone: ZoneId = ZoneId.systemDefault()): LocalDate {
	val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
	val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
	var today by remember(zone) { mutableStateOf(LocalDate.now(zone)) }
	LaunchedEffect(zone, resumed) {
		while (resumed) {
			today = LocalDate.now(zone)
			val midnight = today.plusDays(1).atStartOfDay(zone)
			delay(Duration.between(ZonedDateTime.now(zone), midnight).toMillis().coerceAtLeast(MIN_DELAY_MILLIS))
		}
	}
	return today
}

private const val MIN_DELAY_MILLIS = 1_000L
