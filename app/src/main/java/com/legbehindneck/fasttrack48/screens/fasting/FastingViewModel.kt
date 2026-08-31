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
import com.legbehindneck.fasttrack48.data.activefast.ActiveFastWindow
import com.legbehindneck.fasttrack48.data.log.FastingLogEntry
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

	private data class StageStrings(val title: String, val description: String, val energyMode: String)

	// Declared ahead of init: viewModelScope runs on Dispatchers.Main.immediate, so a
	// collector started there can deliver its first value before construction finishes,
	// and render() reads this.
	private val EMPTY_STAGE = StageStrings("", "", "")

	/**
	 * Every input this screen has, subscribed once for the life of the view model.
	 *
	 * The state used to be a snapshot recomputed only when *this* view model performed a
	 * mutation, which made it wrong whenever anyone else touched the data: a fast deleted
	 * on the log screen was still reported under the dial, and only a process restart put
	 * the two back in agreement. Wiring is in `init` rather than in [onCreate] for the same
	 * class of reason — [onCreate] is driven from a `LaunchedEffect` that re-runs whenever
	 * the screen re-enters composition, which would stack a second copy of every collector
	 * on top of the first.
	 */
	init {
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

		// The active fast, from the store rather than from whatever this view model last
		// wrote. Backdating a start, the importer restoring an unfinished fast and a widget
		// action all arrive here by the same path.
		viewModelScope.launch {
			repository.observe().collect { window -> render(window) }
		}

		// The logbook, from Room's own invalidation. A delete, an edit, a manual add or a
		// cleared logbook reaches the band under the dial without this screen being asked.
		viewModelScope.launch {
			logRepository.loadAll().collect { entries -> renderLatestLogged(entries) }
		}
	}

	override fun onCreate() {
		// Idempotent by construction: the collectors are in init, and both calls below are
		// pure re-derivations of state that is already current.
		updateUi()
		setupFastingNotification()
	}

	/**
	 * Newest logbook entry: one scan, two facts. Its end warns when a corrected start would
	 * reach back into an already-recorded window, and the entry itself is what the idle band
	 * reports under the dial. Newest by *end*, not by start, which is what makes it the fast
	 * the reader just finished.
	 */
	private fun renderLatestLogged(entries: List<FastingLogEntry>) {
		val tz = TimeZone.currentSystemDefault()
		val latest = entries.maxByOrNull { it.start.toInstant(tz).plus(it.length) }
		val end = latest?.let { it.start.toInstant(tz).plus(it.length) }
		_uiState.update { state ->
			state.copy(previousLoggedFastEnd = end, lastLoggedFast = latest)
		}
	}

	/**
	 * Re-derive the dial from the clock. The window itself is pushed by [init]'s collector;
	 * this exists for the minute tick, where nothing in the data changed and only the
	 * elapsed time did.
	 */
	override fun updateUi() {
		render(ActiveFastWindow(repository.getFastStart(), repository.getFastEnd()))
	}

	private fun render(window: ActiveFastWindow) {
		// One read and one state emission: separate emissions here would each trigger
		// their own recomposition of the dial + rows.
		val isFasting = window.isRunning
		val fastStart = window.start
		val fastEnd = window.end

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
					stageTitle = "",
					stageDescription = "",
					energyMode = "",
				)
			}
		}
	}

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

			// The written row comes back through loadAll(); nothing to refresh by hand.
			viewModelScope.launch(Dispatchers.IO) {
				saveFastToLog(repository.getFastStart(), repository.getFastEnd(), notes)
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
