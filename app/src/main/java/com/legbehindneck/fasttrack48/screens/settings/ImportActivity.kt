package com.legbehindneck.fasttrack48.screens.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import com.legbehindneck.fasttrack48.data.log.FastingLogRepository
import com.legbehindneck.fasttrack48.data.log.ImportResult
import com.legbehindneck.fasttrack48.screens.main.MainActivity
import org.koin.android.ext.android.inject

/**
 * The door a `.csv`, `.json`, `.zip` or `.ics` file comes through when it is tapped in a file
 * manager, or shared to this app from one.
 *
 * A transparent trampoline rather than a branch inside [MainActivity]: an import is a
 * one-shot side effect, not a screen, and giving it its own activity keeps the launcher
 * activity's task and back stack untouched — with an empty `taskAffinity` this runs inside
 * the file manager's own task. Putting the intent filter on [MainActivity] instead would
 * build a second copy of it inside that same foreign task, which only `singleTask` could
 * undo, at the cost of retargeting every other way into the app.
 *
 * This activity also does the reading, and must: the read grant that comes with the intent is
 * scoped to the task that received it. What crosses to [MainActivity] is the result — a
 * handful of numbers — not the URI, so there is no grant to propagate and nothing to fail.
 * [MainActivity] shows them over the Log page, where the imported fasts are.
 *
 * The declared MIME type is used only to get listed in the chooser; what is actually imported
 * is decided by [importLogFromUri] sniffing the bytes, so a manager that mislabels a FastTrack
 * CSV still works and a `.zip` that is not an EasyFast backup fails with a toast rather than
 * corrupting the logbook.
 */
class ImportActivity : AppCompatActivity() {
	private val logRepository by inject<FastingLogRepository>()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		// A recreated instance would import the same URI a second time. The manifest already
		// absorbs configuration changes; this covers process death mid-import, where the safe
		// answer is to do nothing rather than to double-apply.
		if (savedInstanceState != null) {
			finish()
			return
		}

		val uri = intent?.let(::importUri)
		if (uri == null) {
			// An intent with nothing to read still gets an answer. Silence after tapping a
			// file is the one outcome the user cannot act on.
			openApp(ImportResult(unreadable = true))
			finish()
			return
		}

		// finish() only once the read is done: the coroutine is scoped to this lifecycle.
		importLogFromUri(logRepository, uri) { result ->
			openApp(result)
			finish()
		}
	}

	/**
	 * Brings the app forward carrying [result].
	 *
	 * NEW_TASK because this activity deliberately has no task affinity and so runs inside the
	 * file manager's task; the flag sends [MainActivity] to the app's own task instead of
	 * stranding it in someone else's back stack. CLEAR_TOP or SINGLE_TOP reuses a running
	 * instance rather than stacking a second one -- the logbook is collected from a Room
	 * Flow, so an already-open Log tab shows the new rows without being recreated.
	 *
	 * A successful import opens on the Log page: the imported fasts are the reason the app
	 * was opened at all, and the Fasting screen shows none of them. A failed one does not
	 * move the user off whatever page they left the app on — nothing landed in the logbook,
	 * so there is nothing there to look at.
	 */
	private fun openApp(result: ImportResult) {
		startActivity(
			Intent(this, MainActivity::class.java)
				.putExtra(MainActivity.OPEN_LOG_EXTRA, result.ok)
				.putImportResult(result)
				.addFlags(
					Intent.FLAG_ACTIVITY_NEW_TASK or
						Intent.FLAG_ACTIVITY_CLEAR_TOP or
						Intent.FLAG_ACTIVITY_SINGLE_TOP
				)
		)
	}

	/** The file to read: the data URI for VIEW, the stream extra for SEND. */
	private fun importUri(intent: Intent): Uri? = when (intent.action) {
		Intent.ACTION_SEND ->
			IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)

		else -> intent.data
	}
}

/**
 * The result, written into an intent as plain primitives.
 *
 * Primitives rather than a serialized object because that is the whole dependency budget this
 * needs: the app carries `kotlinx-serialization-core` but not `-json`, and `@Parcelize` would
 * mean adding the `kotlin-parcelize` plugin for one data class that crosses one boundary.
 */
internal fun Intent.putImportResult(result: ImportResult): Intent = apply {
	putExtra(EXTRA_RESULT_PRESENT, true)
	putExtra(EXTRA_IMPORTED, result.imported)
	putExtra(EXTRA_REPLACED, result.replaced)
	putExtra(EXTRA_SKIPPED_OVERLAPPING, result.skippedOverlapping)
	putExtra(EXTRA_SKIPPED_INVALID, result.skippedInvalid)
	putExtra(EXTRA_ONGOING_RESTORED, result.ongoingRestored)
	putExtra(EXTRA_ONGOING_DECLINED, result.ongoingDeclined)
	putExtra(EXTRA_UNREADABLE, result.unreadable)
	putExtra(EXTRA_OK, result.ok)
}

/** The result an intent carries, or null when it carries none. */
internal fun Intent.readImportResult(): ImportResult? {
	if (!getBooleanExtra(EXTRA_RESULT_PRESENT, false)) return null
	return ImportResult(
		imported = getIntExtra(EXTRA_IMPORTED, 0),
		replaced = getIntExtra(EXTRA_REPLACED, 0),
		skippedOverlapping = getIntExtra(EXTRA_SKIPPED_OVERLAPPING, 0),
		skippedInvalid = getIntExtra(EXTRA_SKIPPED_INVALID, 0),
		ongoingRestored = getBooleanExtra(EXTRA_ONGOING_RESTORED, false),
		ongoingDeclined = getBooleanExtra(EXTRA_ONGOING_DECLINED, false),
		unreadable = getBooleanExtra(EXTRA_UNREADABLE, false),
		ok = getBooleanExtra(EXTRA_OK, false),
	)
}

/**
 * Forgets the result once it has been shown. The intent outlives the call that read it and
 * would otherwise raise the dialog again on every recreation, long after the import.
 */
internal fun Intent.clearImportResult() {
	removeExtra(EXTRA_RESULT_PRESENT)
}

private const val EXTRA_RESULT_PRESENT = "IMPORT_RESULT"
private const val EXTRA_IMPORTED = "IMPORT_IMPORTED"
private const val EXTRA_REPLACED = "IMPORT_REPLACED"
private const val EXTRA_SKIPPED_OVERLAPPING = "IMPORT_SKIPPED_OVERLAPPING"
private const val EXTRA_SKIPPED_INVALID = "IMPORT_SKIPPED_INVALID"
private const val EXTRA_ONGOING_RESTORED = "IMPORT_ONGOING_RESTORED"
private const val EXTRA_ONGOING_DECLINED = "IMPORT_ONGOING_DECLINED"
private const val EXTRA_UNREADABLE = "IMPORT_UNREADABLE"
private const val EXTRA_OK = "IMPORT_OK"
