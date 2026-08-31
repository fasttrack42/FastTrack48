package com.legbehindneck.fasttrack48.screens.log

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.legbehindneck.fasttrack48.data.activefast.ActiveFastRepository
import com.legbehindneck.fasttrack48.data.autophagyHours
import com.legbehindneck.fasttrack48.data.ketosisHours
import com.legbehindneck.fasttrack48.data.log.FastingLogEntry
import com.legbehindneck.fasttrack48.data.log.FastingLogRepository
import com.legbehindneck.fasttrack48.data.settings.LogViewMode
import com.legbehindneck.fasttrack48.data.settings.SettingsDatasource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

@ExperimentalTime
class LogViewModel(
	private val repository: FastingLogRepository,
	private val activeFast: ActiveFastRepository,
	private val settings: SettingsDatasource,
) : ViewModel(), ILogViewModel {

	private val _uiState = MutableStateFlow(
		ILogViewModel.LogUiState(viewMode = settings.getLogViewMode())
	)
	override val uiState: StateFlow<ILogViewModel.LogUiState> = _uiState.asStateFlow()

	/**
	 * Both inputs, subscribed once for the life of the view model.
	 *
	 * Not in a method the screen calls: that call sat inside a `repeatOnLifecycle(STARTED)`
	 * block, which re-runs every time the screen comes back to the foreground and so stacked
	 * another copy of the collector on top of the one already running.
	 */
	init {
		viewModelScope.launch {
			repository.loadAll().collect { entries -> updateEntries(entries) }
		}
		// The running fast, which no logbook row describes yet.
		viewModelScope.launch {
			activeFast.observe().collect { window ->
				val start = if (window.isRunning) window.start else null
				_uiState.update { state -> state.copy(activeFastStart = start) }
			}
		}
	}

	private fun updateEntries(entries: List<FastingLogEntry>) {
		val totalKetosisHours = entries.sumOf { ketosisHours(it.length) }.roundToInt()
		val totalAutophagyHours = entries.sumOf { autophagyHours(it.length) }.roundToInt()
		val totalFastedDuration = entries.fold(Duration.ZERO) { acc, e -> acc + e.length }
		val longestFastDuration = entries.maxOfOrNull { it.length } ?: Duration.ZERO

		_uiState.update { currentState ->
			currentState.copy(
				entries = entries.sortedByDescending { it.start },
				totalKetosisHours = totalKetosisHours,
				totalAutophagyHours = totalAutophagyHours,
				totalFasts = entries.size,
				totalFastedDuration = totalFastedDuration,
				longestFastDuration = longestFastDuration,
			)
		}
	}

	override fun deleteFast(item: FastingLogEntry) {
		viewModelScope.launch(Dispatchers.IO) {
			if (!repository.delete(item)) {
				Log.w("LogViewModel", "Failed to delete Fast: $item")
			}
		}
	}

	override fun showManualAddDialog() {
		_uiState.update {
			it.copy(showManualAddDialog = true, entryToEdit = null, manualAddInitialDate = null)
		}
	}

	override fun showEditDialog(entry: FastingLogEntry) {
		_uiState.update { it.copy(showManualAddDialog = true, entryToEdit = entry) }
	}

	override fun hideManualAddDialog() {
		_uiState.update {
			it.copy(showManualAddDialog = false, entryToEdit = null, manualAddInitialDate = null)
		}
	}

	override fun setViewMode(mode: LogViewMode) {
		settings.setLogViewMode(mode)
		// Choosing a mode from the switcher is a fresh start: drop any drill-down target,
		// or tapping "Month" would keep re-opening the month the year strip last chose.
		_uiState.update { it.copy(viewMode = mode, focusedMonth = null) }
	}

	override fun focusMonth(monthStart: LocalDate) {
		settings.setLogViewMode(LogViewMode.CALENDAR)
		_uiState.update {
			it.copy(viewMode = LogViewMode.CALENDAR, focusedMonth = monthStart)
		}
	}

	override fun selectDate(date: LocalDate?) {
		_uiState.update { it.copy(selectedDate = date) }
	}

	override fun requestClearAll() {
		_uiState.update { it.copy(showClearAllConfirmation = true) }
	}

	override fun dismissClearAll() {
		_uiState.update { it.copy(showClearAllConfirmation = false) }
	}

	override fun clearAll() {
		_uiState.update { it.copy(showClearAllConfirmation = false) }
		viewModelScope.launch(Dispatchers.IO) {
			repository.deleteAllEntries()
			// The loadAll() flow emits the now-empty list and refreshes the stats.
		}
	}

	override fun requestAddForDate(date: LocalDate) {
		_uiState.update { it.copy(emptyDayToAdd = date) }
	}

	override fun dismissAddForDate() {
		_uiState.update { it.copy(emptyDayToAdd = null) }
	}

	override fun confirmAddForDate() {
		_uiState.update {
			it.copy(
				showManualAddDialog = true,
				entryToEdit = null,
				manualAddInitialDate = it.emptyDayToAdd,
				emptyDayToAdd = null,
			)
		}
	}
}
