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
	suspend fun importLog(cvsExport: String): ImportResult

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

/**
 * What an import did, in the terms the user is shown.
 *
 * Every importer reports through this one shape, even though they do not all behave the same:
 * the CSV reader keys on the start second and *replaces* what it finds there, while the backup
 * readers *skip* anything overlapping a fast that is already logged. Both are deliberate — a
 * CSV is this app's own export, a backup is someone else's — and the difference is only
 * visible here, as [replaced] against [skippedOverlapping].
 *
 * [unreadable] is the one failure that is not about content: the file could not be opened or
 * read at all. Everything else that goes wrong leaves [ok] false with the counts saying why.
 */
data class ImportResult(
	/** Fasts added to the logbook. */
	val imported: Int = 0,
	/** CSV only: existing entries at the same start second, overwritten in place. */
	val replaced: Int = 0,
	/** Backup formats: records dropped because they overlapped a fast already logged. */
	val skippedOverlapping: Int = 0,
	/** Rows or records that could not be parsed. */
	val skippedInvalid: Int = 0,
	/** The file held a fast in progress and it is now the running one. */
	val ongoingRestored: Boolean = false,
	/** The file held a fast in progress, but one was already running and was left alone. */
	val ongoingDeclined: Boolean = false,
	/** The file could not be opened or read — moved, deleted, or no longer granted. */
	val unreadable: Boolean = false,
	/** Whether the file was understood and something in it was acted on. */
	val ok: Boolean = false,
)
