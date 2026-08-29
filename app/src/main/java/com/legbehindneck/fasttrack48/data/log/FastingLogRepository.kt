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
	/**
	 * Export as CSV. A fast in progress is written as a row with its End, Duration (s) and
	 * Duration cells blank — this app's own format is the only one of the three that can
	 * carry an unfinished fast without distorting it.
	 */
	suspend fun exportLog(): String

	/**
	 * Import a logbook CSV. A row with blank End *and* blank Duration (s) is a fast that
	 * was still running at export and is restored as the fast in progress — unless one is
	 * already running, in which case the row is dropped and the live fast is left alone.
	 */
	suspend fun importLog(cvsExport: String): Boolean

	/**
	 * Export the logbook as an iCalendar (RFC 5545) document. iCalendar cannot express an
	 * unfinished event — a DTSTART with no DTEND is zero-length, not open — so a fast in
	 * progress is exported closed at the moment of export.
	 */
	suspend fun exportIcs(): String

	/**
	 * Export the logbook as an ActivityStreams 2.0 (JSON-LD) document. AS2 makes `endTime`
	 * optional, so a fast in progress is exported as an Event with a `startTime` only.
	 */
	suspend fun exportActivityStreams(): String

	/**
	 * Import fasts from an EasyFast backup ZIP (we only read its `fasts.json`).
	 * Any imported fast whose [start, finish) range overlaps an existing log
	 * entry is skipped and counted, so repeated imports never duplicate data.
	 */
	suspend fun importEasyFastBackup(zipBytes: ByteArray): ImportResult

	/** Import fasts from an iCalendar (RFC 5545) document, skipping overlaps. */
	suspend fun importIcs(icsText: String): ImportResult

	/**
	 * Import fasts from an ActivityStreams 2.0 (JSON-LD) document, skipping overlaps. An
	 * Event with no `endTime` and no `duration` is restored as the fast in progress, on the
	 * same terms as the CSV importer: only when no fast is already running.
	 */
	suspend fun importActivityStreams(jsonText: String): ImportResult
}

/** Outcome of a backup import. */
data class ImportResult(
	val imported: Int,
	val skippedOverlapping: Int,
	val ok: Boolean,
)
