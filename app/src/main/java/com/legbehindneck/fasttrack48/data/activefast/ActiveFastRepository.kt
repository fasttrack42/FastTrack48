package com.legbehindneck.fasttrack48.data.activefast

import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration
import kotlin.time.Instant

interface ActiveFastRepository {
	fun isFasting(): Boolean
	fun getElapsedFastTime(): Duration
	fun getFastStart(): Instant?
	fun getFastEnd(): Instant?

	/** The active fast as an observable value; see [ActiveFastDataSource.observe]. */
	fun observe(): Flow<ActiveFastWindow>

	fun startFast(timeStarted: Instant?)
	fun endFast(timeEnded: Instant? = null)
	fun setFastStart(newStart: Instant)
}