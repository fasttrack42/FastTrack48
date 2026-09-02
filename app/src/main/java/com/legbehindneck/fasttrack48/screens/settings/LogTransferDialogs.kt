package com.legbehindneck.fasttrack48.screens.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.legbehindneck.fasttrack48.R
import com.legbehindneck.fasttrack48.data.log.ImportResult
import com.legbehindneck.fasttrack48.data.log.LogExportFormat

/**
 * Which shape the logbook leaves in. Every entry in [LogExportFormat] is offered — the enum
 * is the single source of truth, so a format added there appears here without an edit.
 *
 * Shared by the Settings screen and the overflow menu, which is the point: two doors to the
 * same action should not be two dialogs to keep in step.
 */
@Composable
fun ExportFormatDialog(
	onDismiss: () -> Unit,
	onSelect: (LogExportFormat) -> Unit,
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(stringResource(id = R.string.export_choose_format)) },
		text = {
			Column {
				LogExportFormat.entries.forEach { format ->
					Text(
						text = stringResource(id = format.labelRes),
						style = MaterialTheme.typography.bodyLarge,
						modifier = Modifier
							.fillMaxWidth()
							.clickable {
								onDismiss()
								onSelect(format)
							}
							.padding(vertical = 12.dp),
					)
				}
			}
		},
		confirmButton = {
			TextButton(onClick = onDismiss) {
				Text(stringResource(id = R.string.cancel_button))
			}
		},
	)
}

/**
 * What the import picker will accept, shown once before it opens.
 *
 * The Settings row carries this as a permanent subtitle; a menu item has no room for one,
 * and an unfiltered file picker with no guidance is a dead end for anyone who has not
 * already exported something. Shown on the first use of the menu route only — after that the
 * user knows, and a dialog between them and the picker is friction they did not ask for.
 */
@Composable
fun ImportFormatsDialog(
	onDismiss: () -> Unit,
	onContinue: () -> Unit,
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(stringResource(id = R.string.import_formats_title)) },
		text = { Text(stringResource(id = R.string.import_formats_message)) },
		confirmButton = {
			TextButton(onClick = onContinue) {
				Text(stringResource(id = R.string.import_formats_continue))
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text(stringResource(id = R.string.cancel_button))
			}
		},
	)
}

/**
 * What an import actually did, shown where the import was asked for.
 *
 * This replaces a toast, and the reasons are the reasons a toast was wrong here: an import is
 * the one moment the user hands the app a file and cannot see what happened to it, the answer
 * has four numbers in it rather than one, and when the file came from a file manager the toast
 * appeared over the file manager while this app was still launching — routinely missed.
 *
 * Rendered as a table of labels and counts rather than a sentence. That is not only a layout
 * choice: a sentence with a number in it needs plural forms in every locale (Ukrainian has
 * three), while "Added — 12" needs none, and reads as the statistics the user came for.
 * Zero rows are left out, so a clean import is two lines rather than six padded zeros.
 */
@Composable
fun ImportResultDialog(
	result: ImportResult,
	onDismiss: () -> Unit,
) {
	// A file that read fine and yielded nothing is not a success with an empty table; it is
	// the same dead end as a file that would not parse, and says so in the same words.
	val nothingHappened = result.imported == 0 && result.replaced == 0 &&
		result.skippedOverlapping == 0 && result.skippedInvalid == 0 &&
		!result.ongoingRestored && !result.ongoingDeclined
	val succeeded = result.ok && !result.unreadable && !nothingHappened

	AlertDialog(
		onDismissRequest = onDismiss,
		title = {
			Text(
				stringResource(
					id = if (succeeded) R.string.import_result_title
					else R.string.import_result_title_failed
				)
			)
		},
		text = {
			if (!succeeded) {
				// Two different dead ends, two different answers: the file could not be
				// read at all, or it was read and held nothing we recognise.
				Text(
					stringResource(
						id = if (result.unreadable) R.string.import_error_unreadable
						else R.string.import_error_empty
					)
				)
			} else {
				Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
					ImportStatRow(R.string.import_result_added, result.imported)
					ImportStatRow(R.string.import_result_updated, result.replaced)
					ImportStatRow(R.string.import_result_already_logged, result.skippedOverlapping)
					ImportStatRow(R.string.import_result_unreadable_rows, result.skippedInvalid)

					if (result.ongoingRestored) {
						Text(
							text = stringResource(id = R.string.import_result_ongoing_restored),
							style = MaterialTheme.typography.bodyMedium,
							modifier = Modifier.padding(top = 6.dp),
						)
					}
					if (result.ongoingDeclined) {
						Text(
							text = stringResource(id = R.string.import_result_ongoing_declined),
							style = MaterialTheme.typography.bodyMedium,
							modifier = Modifier.padding(top = 6.dp),
						)
					}
				}
			}
		},
		confirmButton = {
			TextButton(onClick = onDismiss) {
				Text(stringResource(id = R.string.done_button))
			}
		},
	)
}

/**
 * One line of the table, or nothing at all when the count is zero.
 *
 * The label takes the leftover width and wraps rather than truncating — German and Ukrainian
 * labels are long, and a wrapped label still reads while an ellipsised one does not.
 */
@Composable
private fun ImportStatRow(@StringRes labelRes: Int, count: Int) {
	if (count == 0) return

	Row(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.SpaceBetween,
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(
			text = stringResource(id = labelRes),
			style = MaterialTheme.typography.bodyMedium,
			modifier = Modifier
				.weight(1f)
				.padding(end = 16.dp),
		)
		Text(
			text = count.toString(),
			style = MaterialTheme.typography.bodyMedium,
			fontWeight = FontWeight.Medium,
		)
	}
}
