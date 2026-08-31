package com.legbehindneck.fasttrack48.screens.log

import com.legbehindneck.fasttrack48.data.log.FastingLogEntry
import com.legbehindneck.fasttrack48.data.settings.LogViewMode
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate
import kotlin.time.Duration
import kotlin.time.Instant

interface ILogViewModel {
	data class LogUiState(
		val entries: List<FastingLogEntry> = emptyList(),
		val totalKetosisHours: Int = 0,
		val totalAutophagyHours: Int = 0,
		val totalFasts: Int = 0,
		val totalFastedDuration: Duration = Duration.ZERO,
		val longestFastDuration: Duration = Duration.ZERO,
		val showManualAddDialog: Boolean = false,
		val entryToEdit: FastingLogEntry? = null,
		val viewMode: LogViewMode = LogViewMode.LIST,
		/**
		 * Start of the fast currently running, or null when none is. It is not a logbook
		 * entry yet — the row is only written when the fast ends — so the calendar has to be
		 * told about it separately or it draws the last three days as empty.
		 */
		val activeFastStart: Instant? = null,
		val selectedDate: LocalDate? = null,
		val showClearAllConfirmation: Boolean = false,
		// An empty (past/today) calendar day awaiting "add a fast here?" confirmation.
		val emptyDayToAdd: LocalDate? = null,
		// Date to preselect in the Manual Add picker (e.g. from an empty calendar day).
		val manualAddInitialDate: LocalDate? = null,
	)

	val uiState: StateFlow<LogUiState>

	fun deleteFast(item: FastingLogEntry)
	fun showManualAddDialog()
	fun showEditDialog(entry: FastingLogEntry)
	fun hideManualAddDialog()
	fun setViewMode(mode: LogViewMode)
	fun selectDate(date: LocalDate?)
	fun requestClearAll()
	fun dismissClearAll()
	fun clearAll()
	fun requestAddForDate(date: LocalDate)
	fun dismissAddForDate()
	fun confirmAddForDate()
}
