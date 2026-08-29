package com.legbehindneck.fasttrack48.screens.fasting

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.legbehindneck.fasttrack48.AlertService
import com.legbehindneck.fasttrack48.FastingNotificationManager
import com.legbehindneck.fasttrack48.R
import com.legbehindneck.fasttrack48.data.Phase
import com.legbehindneck.fasttrack48.data.Stages
import com.legbehindneck.fasttrack48.data.descriptionFor
import com.legbehindneck.fasttrack48.data.activefast.ActiveFastRepository
import com.legbehindneck.fasttrack48.data.log.FastingLogRepository
import com.legbehindneck.fasttrack48.data.settings.SettingsDatasource
import com.legbehindneck.fasttrack48.utils.formatDuration
import com.legbehindneck.fasttrack48.widget.WidgetUpdater
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.DurationUnit
import kotlin.time.Instant

class FastingViewModel(
	private val appContext: Context,
	private val repository: ActiveFastRepository,
	private val logRepository: FastingLogRepository,
	private val settingsDatasource: SettingsDatasource,
	private val clock: Clock,
) : ViewModel(), IFastingViewModel {

	private val _uiState = MutableStateFlow(
		IFastingViewModel.FastingUiState(
			isFasting = repository.isFasting(),
			showGradientBackground = settingsDatasource.getShowFancyBackground(),
		)
	)
	override val uiState: StateFlow<IFastingViewModel.FastingUiState> = _uiState.asStateFlow()

	override fun onCreate() {
		viewModelScope.launch {
			settingsDatasource.showFancyBackgroundFlow().collect { enabled ->
				_uiState.update { state -> state.copy(showGradientBackground = enabled) }
			}
		}

		viewModelScope.launch {
			settingsDatasource.phaseVisibilityFlow().collect { v ->
				_uiState.update { state ->
					state.copy(
						showFatBurn = v.fatBurn,
						showKetosis = v.ketosis,
						showAutophagy = v.autophagy,
						phaseAutoMode = v.autoMode,
					)
				}
			}
		}

		refreshPreviousLoggedEnd()
		updateUi()
		setupFastingNotification()
	}

	/**
	 * Newest logbook entry, read off the IO dispatcher: one scan, two facts. Its end
	 * warns when a corrected start would reach back into an already-recorded window — a
	 * stale value there costs a missed warning, never a wrong write — and the entry
	 * itself is what the idle band reports under the dial.
	 */
	private fun refreshPreviousLoggedEnd() {
		viewModelScope.launch(Dispatchers.IO) { readLatestLoggedFast() }
	}

	private fun readLatestLoggedFast() {
		val latest = logRepository.latestLoggedFast()
		val end = latest?.let {
			it.start.toInstant(TimeZone.currentSystemDefault()).plus(it.length)
		}
		_uiState.update { state ->
			state.copy(previousLoggedFastEnd = end, lastLoggedFast = latest)
		}
	}

	override fun updateUi() {
		// One read of the repository and one state emission per tick: separate
		// emissions here each trigger their own recomposition of the dial + rows.
		val isFasting = repository.isFasting()
		val fastStart = repository.getFastStart()
		val fastEnd = repository.getFastEnd()

		_uiState.update { state ->
			if (fastStart != null) {
				val elapsedTime = fastEnd?.minus(fastStart) ?: clock.now().minus(fastStart)

				// Stage copy is shown only while a fast is actually running.
				val stage = if (isFasting) computeStage(elapsedTime) else EMPTY_STAGE

				val fatBurn = getPhaseTimeAndStageState(Stages.PHASE_FAT_BURN, elapsedTime)
				val ketosis = getPhaseTimeAndStageState(Stages.PHASE_KETOSIS, elapsedTime)
				val autophagy = getPhaseTimeAndStageState(Stages.PHASE_AUTOPHAGY, elapsedTime)

				state.copy(
					isFasting = isFasting,
					elapsedTime = elapsedTime,
					elapsedHours = elapsedTime.inWholeHours.toDouble(),
					fastStartTime = fastStart,
					lastFastEndTime = fastEnd,
					timerText = formatDuration(appContext, elapsedTime),
					milliseconds = "",
					stageTitle = stage.title,
					stageDescription = stage.description,
					energyMode = stage.energyMode,
					fatBurnTime = fatBurn.first,
					fatBurnStageState = fatBurn.second,
					ketosisTime = ketosis.first,
					ketosisStageState = ketosis.second,
					autophagyTime = autophagy.first,
					autophagyStageState = autophagy.second,
				)
			} else {
				state.copy(
					isFasting = isFasting,
					elapsedTime = null,
					elapsedHours = 0.0,
					fastStartTime = null,
					lastFastEndTime = fastEnd,
					stageTitle = "",
					stageDescription = "",
					energyMode = "",
				)
			}
		}
	}

	private data class StageStrings(val title: String, val description: String, val energyMode: String)

	private val EMPTY_STAGE = StageStrings("", "", "")

	private fun computeStage(elapsedTime: Duration): StageStrings {
		val elapsedHours = elapsedTime.inWholeHours.toInt()

		var stageIndex = Stages.stage.indexOfLast { it.hours <= elapsedHours }
		if (stageIndex < 0) stageIndex = 0
		val stage = Stages.stage[stageIndex]

		val curPhase = Stages.getCurrentPhase(elapsedTime)
		val energyMode = appContext.getString(
			R.string.fasting_energy_mode,
			appContext.getString(
				if (curPhase.fatBurning) R.string.fasting_energy_mode_fat
				else R.string.fasting_energy_mode_glucose
			)
		)

		return StageStrings(
			title = appContext.getString(stage.title),
			description = appContext.getString(descriptionFor(stage, elapsedTime.inWholeHours)),
			energyMode = energyMode,
		)
	}

	private fun getPhaseTimeAndStageState(
		phase: Phase,
		elapsedTime: Duration
	): Pair<String, IFastingViewModel.StageState> {
		val phaseHours = phase.hours
		val timeText: String
		val stageState: IFastingViewModel.StageState

		if (elapsedTime.toDouble(DurationUnit.HOURS) > phaseHours) {
			// The phase is underway: how long you've been in it
			timeText = formatDuration(appContext, elapsedTime.minus(phaseHours.hours))
			stageState = IFastingViewModel.StageState.StartedActive
		} else {
			// The phase is ahead: frame it as anticipation, not deficit
			val timeUntil = phaseHours.hours.minus(elapsedTime)
			timeText = appContext.getString(R.string.phase_time_until, formatDuration(appContext, timeUntil))
			stageState = IFastingViewModel.StageState.StartedInactive
		}

		return Pair(timeText, stageState)
	}

	override fun startFast(timeStartedMills: Instant?) {
		if (!repository.isFasting()) {
			repository.startFast(timeStartedMills)

			updateUi()
			setupAlerts()
			setupFastingNotification()
			updateWidgets()

			Napier.i("Started fast!")
		} else {
			Napier.w("Cannot start fast with one in progress")
		}
	}

	override fun endFast(timeEnded: Instant?, notes: String) {
		if (repository.isFasting()) {
			repository.endFast(timeEnded)

			viewModelScope.launch(Dispatchers.IO) {
				saveFastToLog(repository.getFastStart(), repository.getFastEnd(), notes)
				readLatestLoggedFast()
			}

			Napier.i("Fast ended!")

			updateUi()
			setupAlerts()
			setupFastingNotification()
			updateWidgets()
		} else {
			Napier.w("Cannot end fast, there is none started")
		}
	}

	/**
	 * Correct the start of a running fast. Everything downstream — the dial, the phase
	 * rows, the ongoing notification, the widget — is derived from this one instant, so
	 * the write is the whole change; the rest is re-deriving what was already stale.
	 */
	override fun adjustFastStart(newStart: Instant) {
		if (!repository.isFasting()) {
			Napier.w("Cannot adjust the start time, no fast is running")
			return
		}

		// Authoritative clamp. The picker guards the upper bound too, but it can sit
		// open for minutes, and a start in the future yields a negative elapsed time.
		repository.setFastStart(minOf(newStart, clock.now()))

		// Phase alerts are JobScheduler jobs armed at absolute wall-clock moments, and
		// AlertService.scheduleAlert deliberately skips any job that is already pending.
		// Without this cancel the old, now-wrong alerts survive the reschedule.
		AlertService.cancelAlerts(appContext)
		setupAlerts()
		// Re-arms the hourly update that cancelAlerts tore down, so it must follow.
		setupFastingNotification()

		updateUi()
		updateWidgets()

		Napier.i("Adjusted fast start time")
	}

	override fun setupAlerts() {
		val shouldAlert = settingsDatasource.getFastingAlerts()

		if (repository.isFasting()) {
			if (shouldAlert) {
				val elapsedTime = repository.getElapsedFastTime()
				AlertService.scheduleAlerts(elapsedTime, appContext)
			}
			// User doesn't want notifications
			else {
				AlertService.cancelAlerts(appContext)
			}
		}
		// No notifications if we aren't fasting
		else {
			AlertService.cancelAlerts(appContext)
		}
	}

	private fun setupFastingNotification() {
		val shouldShowNotification = settingsDatasource.getShowFastingNotification()

		if (repository.isFasting() && shouldShowNotification) {
			val elapsedTime = repository.getElapsedFastTime()
			FastingNotificationManager.postFastingNotification(appContext, elapsedTime)
			AlertService.scheduleHourlyUpdate(appContext)
		} else {
			FastingNotificationManager.cancelFastingNotification(appContext)
			AlertService.cancelHourlyUpdates(appContext)
		}
	}

	override fun debugIncreaseFastingTimeByOneHour() {
		val currentStartTime = repository.getFastStart()
		if (repository.isFasting() && currentStartTime != null) {
			adjustFastStart(currentStartTime - 1.hours)
			Napier.d("Debug: Increased fasting time by 1 hour")
		} else {
			Napier.d("Debug: Cannot increase fasting time when not fasting")
		}
	}

	private fun updateWidgets() {
		WidgetUpdater.updateWidgets(appContext)
	}

	private suspend fun saveFastToLog(startTime: Instant?, endTime: Instant?, notes: String) =
		withContext(Dispatchers.Default) {
			if (startTime != null && endTime != null) {
				logRepository.logFast(startTime, endTime, notes)
			} else {
				Napier.e("No start time when ending fast!")
			}
		}
}
