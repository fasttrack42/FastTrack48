package com.legbehindneck.fasttrack48.data.log

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDateTime
import kotlin.time.Duration
import kotlin.time.Instant

interface FastingLogRepository {
	fun logFast(startTime: Instant, endTime: Instant, notes: String = "")
	fun loadAll(): Flow<List<FastingLogEntry>>
	fun delete(item: FastingLogEntry): Boolean
	/** Delete the entire logbook; returns the number of entries removed. */
	fun deleteAllEntries(): Int
	fun addLogEntry(start: LocalDateTime, length: Duration, notes: String = "")

	/**
	 * End of the most recent logged fast, or null when the logbook is empty. Used to
	 * warn when an active fast is backdated into a window that is already recorded.
	 */
	fun latestLoggedEnd(): Instant?

	/**
	 * The most recent logged fast — greatest end, not greatest start — or null when the
	 * logbook is empty. Read by the idle Fasting screen, which reports what the last fast
	 * came to in the band under the dial.
	 */
	fun latestLoggedFast(): FastingLogEntry?
	// notes defaults to the entry's current notes so an edit that omits them preserves them
	fun updateLogEntry(
        entry: FastingLogEntry,
		start: LocalDateTime,
        length: Duration,
        notes: String = entry.notes
	): Boolean
	suspend fun exportLog(): String
	suspend fun importLog(cvsExport: String): Boolean

	/** Export the logbook as an iCalendar (RFC 5545) document. */
	suspend fun exportIcs(): String

	/** Export the logbook as an ActivityStreams 2.0 (JSON-LD) document. */
	suspend fun exportActivityStreams(): String

	/**
	 * Import fasts from an EasyFast backup ZIP (we only read its `fasts.json`).
	 * Any imported fast whose [start, finish) range overlaps an existing log
	 * entry is skipped and counted, so repeated imports never duplicate data.
	 */
	suspend fun importEasyFastBackup(zipBytes: ByteArray): ImportResult

	/** Import fasts from an iCalendar (RFC 5545) document, skipping overlaps. */
	suspend fun importIcs(icsText: String): ImportResult

	/** Import fasts from an ActivityStreams 2.0 (JSON-LD) document, skipping overlaps. */
	suspend fun importActivityStreams(jsonText: String): ImportResult
}

/** Outcome of a backup import. */
data class ImportResult(
	val imported: Int,
	val skippedOverlapping: Int,
	val ok: Boolean,
)
