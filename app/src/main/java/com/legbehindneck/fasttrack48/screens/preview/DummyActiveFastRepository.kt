package com.legbehindneck.fasttrack48.screens.preview

import com.legbehindneck.fasttrack48.data.activefast.ActiveFastRepository
import com.legbehindneck.fasttrack48.data.activefast.ActiveFastWindow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Dummy implementation of ActiveFastRepository for preview purposes
 */
class DummyActiveFastRepository(
	private val isFastingValue: Boolean = false,
	private val elapsedHoursValue: Double = 0.0
) : ActiveFastRepository {
	override fun isFasting(): Boolean = isFastingValue

	override fun getElapsedFastTime(): Duration = (elapsedHoursValue.hours)

	override fun getFastStart(): Instant? =
		if (isFastingValue) Instant.Companion.fromEpochMilliseconds(System.currentTimeMillis() - (elapsedHoursValue * 3600000).toLong())
		else null

	override fun getFastEnd(): Instant? = null

	override fun observe(): Flow<ActiveFastWindow> =
		flowOf(ActiveFastWindow(getFastStart(), getFastEnd()))

	override fun startFast(timeStarted: Instant?) {}

	override fun endFast(timeEnded: Instant?) {}

	override fun setFastStart(newStart: Instant) {}
}