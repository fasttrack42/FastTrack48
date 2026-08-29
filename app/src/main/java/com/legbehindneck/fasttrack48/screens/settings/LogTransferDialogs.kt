package com.legbehindneck.fasttrack48.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.legbehindneck.fasttrack48.R
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
