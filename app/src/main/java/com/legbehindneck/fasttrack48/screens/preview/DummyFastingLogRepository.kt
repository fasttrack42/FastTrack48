package com.legbehindneck.fasttrack48.screens.preview

import com.legbehindneck.fasttrack48.data.log.FastingLogEntry
import com.legbehindneck.fasttrack48.data.log.FastingLogRepository
import com.legbehindneck.fasttrack48.data.log.ImportResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.LocalDateTime
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Dummy implementation of FastingLogRepository for preview purposes
 */
class DummyFastingLogRepository(private val entries: List<FastingLogEntry> = emptyList()) : FastingLogRepository {
	override fun logFast(startTime: Instant, endTime: Instant, notes: String) {}
	override fun loadAll(): Flow<List<FastingLogEntry>> = flow {}
	override fun delete(item: FastingLogEntry) = true
	override fun deleteAllEntries() = entries.size
	override fun addLogEntry(start: LocalDateTime, length: Duration, notes: String) {}
	override fun updateLogEntry(entry: FastingLogEntry, start: LocalDateTime, length: Duration, notes: String) = true
	// Previews never raise the backdate-overlap warning, so no entry is "most recent".
	override fun latestLoggedEnd(): Instant? = null

	// The idle band's summary, on the other hand, is worth seeing in a preview.
	override fun latestLoggedFast(): FastingLogEntry? = entries.lastOrNull()
	override suspend fun exportLog(): String = ""
	override suspend fun importLog(cvsExport: String) = ImportResult(imported = 1, ok = true)
	override suspend fun importEasyFastBackup(zipBytes: ByteArray) = ImportResult(imported = 1, ok = true)
	override suspend fun exportIcs(): String = ""
	override suspend fun exportActivityStreams(): String = ""
	override suspend fun importIcs(icsText: String) = ImportResult(imported = 1, ok = true)
	override suspend fun importActivityStreams(jsonText: String) = ImportResult(imported = 1, ok = true)
}
