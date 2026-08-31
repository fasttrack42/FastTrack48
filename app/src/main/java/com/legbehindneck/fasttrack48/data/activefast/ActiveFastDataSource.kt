package com.legbehindneck.fasttrack48.data.activefast

import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/**
 * The active-fast slot, whole.
 *
 * Read as one value because the two halves only mean anything together — a start with no
 * end is a fast in progress, a start with an end is one that finished — and observing them
 * separately would publish the instant between two writes as though it were a state the
 * app had actually been in.
 */
data class ActiveFastWindow(
	val start: Instant? = null,
	val end: Instant? = null,
) {
	val isRunning: Boolean get() = start != null && end == null
}

interface ActiveFastDataSource {
	fun getFastStart(): Instant?
	fun getFastEnd(): Instant?

	/**
	 * The current window, then the window again after every write to either half — by
	 * whoever wrote it. This view model is not the only writer: the importer restores an
	 * in-progress fast, and the widget and notification actions act on the same slot. A
	 * value cached at the moment one writer happened to write it is wrong as soon as any
	 * other one does, and nothing but a process restart would correct it.
	 */
	fun observe(): Flow<ActiveFastWindow>

	fun setFastStart(time: Instant)
	fun setFastEnd(time: Instant)

	fun clearFastStart()
	fun clearFastEnd()
}
