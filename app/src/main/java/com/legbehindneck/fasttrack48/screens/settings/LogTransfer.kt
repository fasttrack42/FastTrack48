package com.legbehindneck.fasttrack48.screens.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.coroutineScope
import com.legbehindneck.fasttrack48.R
import com.legbehindneck.fasttrack48.data.log.FastingLogRepository
import com.legbehindneck.fasttrack48.data.log.ImportResult
import com.legbehindneck.fasttrack48.data.log.LogExportFormat
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import java.io.File
import java.util.Locale
import kotlin.time.Clock

/**
 * Moving the logbook in and out of the app, from anywhere that can host an activity result.
 *
 * These two operations used to live inside [SettingsActivity], which was fine while Settings
 * was the only door to them. It no longer is: the overflow menu offers the same two actions
 * on every page, and a second copy of the format sniffing would be a second thing to keep
 * correct. Extensions on the activity rather than a class, because the only state either one
 * needs is the activity itself — a launcher registration, a content resolver and a file
 * provider.
 */

/**
 * Registers the import picker and returns its launcher. Must be called before the activity
 * reaches STARTED, i.e. from `onCreate`, which is what `registerForActivityResult` demands.
 *
 * Launch it with an unfiltered wildcard type: the file's type is decided by sniffing its bytes,
 * not by its extension or the MIME type a file manager claims for it.
 */
fun AppCompatActivity.registerLogImport(
	logRepository: FastingLogRepository,
	onResult: (ImportResult) -> Unit = {},
): ActivityResultLauncher<String> =
	registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
		// A cancelled picker hands back null and is not an outcome worth reporting.
		uri?.let { importLogFromUri(logRepository, it, onResult) }
	}

/**
 * Reads [uri], detects its format from its bytes, merges it into the logbook and hands the
 * outcome to [onFinished] on the main thread.
 *
 * Every import path this app can take is auto-detected here — a ZIP is an EasyFast backup,
 * `BEGIN:VCALENDAR` is an iCalendar export, a JSON document mentioning ActivityStreams is one
 * of ours, and anything else is handed to the CSV reader, which handles both the legacy
 * FastTrack layout and the current one. Nothing here trusts the URI's extension or its
 * declared MIME type, which is what lets the same code serve both the in-app picker and a
 * file tapped in an explorer ([ImportActivity]).
 *
 * Nothing is shown from here. The result is a value, and the caller decides where it is
 * rendered — over the Log page in [com.legbehindneck.fasttrack48.screens.main.MainActivity],
 * over Settings, wherever the import was asked for. [onFinished] runs whether the import
 * succeeded or failed; a trampoline activity uses it to finish only once the work is actually
 * done, since the coroutine is scoped to the lifecycle and would be cancelled by an early
 * `finish()`.
 */
fun AppCompatActivity.importLogFromUri(
	logRepository: FastingLogRepository,
	uri: Uri,
	onFinished: (ImportResult) -> Unit = {},
) {
	lifecycle.coroutineScope.launch(Dispatchers.Default) {
		val result = try {
			val bytes = contentResolver.openInputStream(uri)?.use { s -> s.readBytes() }
			if (bytes == null) {
				ImportResult(unreadable = true)
			} else if (isZip(bytes)) {
				logRepository.importEasyFastBackup(bytes)
			} else {
				val text = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
				val head = text.trimStart()
				when {
					head.startsWith("BEGIN:VCALENDAR", ignoreCase = true) ->
						logRepository.importIcs(text)

					(head.startsWith("{") || head.startsWith("[")) &&
						text.contains("activitystreams") ->
						logRepository.importActivityStreams(text)

					else -> logRepository.importLog(text)
				}
			}
		} catch (e: Exception) {
			// Only the read can land here — every importer catches its own parse failures
			// and reports them as a result — so this is the file being gone or ungranted.
			Napier.w("Failed to read the file to import", e)
			ImportResult(unreadable = true)
		}

		withContext(Dispatchers.Main) { onFinished(result) }
	}
}

/**
 * Writes the logbook to the cache in [format] and hands it to the system share sheet.
 *
 * Nothing is written outside the app's own cache directory — the file leaves only through
 * the chooser the user picks, under a one-shot read grant. The authority is derived from
 * [android.content.Context.getPackageName], so the debug build's `.dev` suffix resolves to
 * its own provider rather than colliding with a release install.
 */
fun AppCompatActivity.exportFasts(
	logRepository: FastingLogRepository,
	format: LogExportFormat,
) {
	lifecycle.coroutineScope.launch {
		val content = when (format) {
			LogExportFormat.CSV -> logRepository.exportLog()
			LogExportFormat.ICS -> logRepository.exportIcs()
			LogExportFormat.ACTIVITY_STREAMS -> logRepository.exportActivityStreams()
		}

		// Locale-independent timestamp: fastingLogbook-YYYY-MM-DD-HHMM.<ext>
		val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
		val stamp = String.format(
			Locale.ROOT, "%04d-%02d-%02d-%02d%02d",
			now.year, now.month.number, now.day, now.hour, now.minute
		)
		val exportFile = File(cacheDir, "fastingLogbook-$stamp.${format.extension}")

		try {
			exportFile.writeText(content)

			val fileUri = FileProvider.getUriForFile(
				this@exportFasts,
				"${packageName}.fileprovider",
				exportFile
			)

			val sendIntent: Intent = Intent().apply {
				action = Intent.ACTION_SEND
				putExtra(Intent.EXTRA_STREAM, fileUri)
				type = format.mimeType
				addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
			}

			val shareIntent = Intent.createChooser(sendIntent, getString(R.string.app_name))
			startActivity(shareIntent)
		} catch (_: Exception) {
			Toast.makeText(
				this@exportFasts,
				getString(R.string.export_failed),
				Toast.LENGTH_SHORT
			).show()
		}
	}
}

/** ZIP local-file-header magic bytes (PK). */
private fun isZip(bytes: ByteArray): Boolean =
	bytes.size >= 4 &&
		bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
		bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()
