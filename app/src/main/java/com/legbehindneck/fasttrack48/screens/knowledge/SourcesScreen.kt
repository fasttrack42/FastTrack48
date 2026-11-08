package com.legbehindneck.fasttrack48.screens.knowledge

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.legbehindneck.fasttrack48.R
import com.legbehindneck.fasttrack48.data.knowledge.KnowledgeReference
import com.legbehindneck.fasttrack48.data.knowledge.KnowledgeReferences
import com.legbehindneck.fasttrack48.data.knowledge.ReferenceKind
import com.legbehindneck.fasttrack48.utils.MAX_COLUMN_WIDTH

/**
 * Sources & credits: who the Library draws on, that none of them endorse the app, that it is
 * not medical advice, then every cited work in full. Citations stay untranslated so each reads
 * exactly as published and can be looked up; the prose around them is translated.
 */
@Composable
fun SourcesScreen(paddingValues: PaddingValues, modifier: Modifier = Modifier) {
	val context = LocalContext.current
	val groups = remember {
		ReferenceKind.entries
			.map { kind -> kind to KnowledgeReferences.filter { it.kind == kind } }
			.filter { (_, refs) -> refs.isNotEmpty() }
	}

	Box(modifier = modifier.fillMaxSize()) {
		LazyColumn(
			modifier = Modifier
				.widthIn(max = MAX_COLUMN_WIDTH)
				.fillMaxHeight()
				.align(Alignment.TopCenter),
			contentPadding = PaddingValues(
				start = 16.dp,
				end = 16.dp,
				top = paddingValues.calculateTopPadding() + 16.dp,
				bottom = paddingValues.calculateBottomPadding() + 24.dp,
			),
			verticalArrangement = Arrangement.spacedBy(13.dp),
		) {
			item(key = "credits") {
				Text(text = stringResource(R.string.kb_sources_credits), style = MaterialTheme.typography.bodyMedium)
			}
			item(key = "independence") { Note(R.string.kb_sources_independence) }
			item(key = "medical") { Note(R.string.kb_sources_medical) }
			groups.forEach { (kind, refs) ->
				item(key = "h_" + kind.name) {
					Text(
						text = stringResource(kind.heading()),
						style = MaterialTheme.typography.titleMedium,
						fontWeight = FontWeight.Bold,
						color = MaterialTheme.colorScheme.primary,
						modifier = Modifier.padding(top = 8.dp),
					)
				}
				items(refs, key = { it.kind.name + it.text }) { ReferenceRow(it) { url -> openUrl(context, url) } }
			}
		}
	}
}

@Composable
private fun Note(@StringRes res: Int) {
	Text(
		text = stringResource(res),
		style = MaterialTheme.typography.bodySmall,
		color = MaterialTheme.colorScheme.onSurfaceVariant,
	)
}

/** The citation, then its link spelled out: visible, verifiable, and a tap opens it. */
@Composable
private fun ReferenceRow(ref: KnowledgeReference, onOpen: (String) -> Unit) {
	val linked = ref.url.isNotEmpty()
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.clickable(enabled = linked, role = Role.Button) { onOpen(ref.url) },
	) {
		Text(text = ref.text, style = MaterialTheme.typography.bodySmall)
		if (linked) {
			Text(
				text = ref.url.removePrefix("https://"),
				style = MaterialTheme.typography.labelSmall,
				color = MaterialTheme.colorScheme.primary,
			)
		}
	}
}

@StringRes
private fun ReferenceKind.heading(): Int = when (this) {
	ReferenceKind.Book -> R.string.kb_sources_books
	ReferenceKind.Study -> R.string.kb_sources_studies
	ReferenceKind.Talk -> R.string.kb_sources_talks
}

/** The app has no network access of its own; a link opens in whatever browser the phone has. */
private fun openUrl(context: Context, url: String) {
	try {
		context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
	} catch (_: ActivityNotFoundException) {
		// No browser installed: the link stays visible on screen to copy by hand.
	}
}
