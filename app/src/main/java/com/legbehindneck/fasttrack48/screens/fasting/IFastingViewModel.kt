package com.legbehindneck.fasttrack48.screens.fasting

import com.legbehindneck.fasttrack48.data.log.FastingLogEntry
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration
import kotlin.time.Instant

interface IFastingViewModel {
	enum class StageState {
		NotStarted, StartedInactive, StartedActive
	}

	data class FastingUiState(
		val isFasting: Boolean = false,
		val elapsedTime: Duration? = null,
		val fastStartTime: Instant? = null,
		/**
		 * End of the newest logbook entry — the logbook, not the active-fast slot, is the
		 * record of what happened, so a fast the reader deleted stops being reported here.
		 * Used to warn on a backdated start and to date the idle status line.
		 */
		val previousLoggedFastEnd: Instant? = null,
		/** That same entry, whole: what the idle band reports under the dial. */
		val lastLoggedFast: FastingLogEntry? = null,
		val stageTitle: String = "",
		val stageDescription: String = "",
		val energyMode: String = "",
		val fatBurnTime: String = "—",
		val ketosisTime: String = "—",
		val autophagyTime: String = "—",
		val fatBurnStageState: StageState = StageState.NotStarted,
		val ketosisStageState: StageState = StageState.NotStarted,
		val autophagyStageState: StageState = StageState.NotStarted,
		val elapsedHours: Double = 0.0,
		val milliseconds: String = "00",
		val timerText: String = "00:00:00",
		val showGradientBackground: Boolean = true,
		val showFatBurn: Boolean = true,
		val showKetosis: Boolean = true,
		val showAutophagy: Boolean = true,
		val phaseAutoMode: Boolean = false,
	)

	val uiState: StateFlow<FastingUiState>

	fun onCreate()
	fun updateUi()
	fun startFast(timeStartedMills: Instant? = null)
	fun endFast(timeEnded: Instant? = null, notes: String = "")

	/** Correct the start time of the fast that is currently running. */
	fun adjustFastStart(newStart: Instant)
	fun setupAlerts()
	fun debugIncreaseFastingTimeByOneHour()
}
