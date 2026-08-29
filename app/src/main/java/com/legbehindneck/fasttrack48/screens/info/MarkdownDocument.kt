package com.legbehindneck.fasttrack48.screens.info

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One nesting level of list indent. */
private val IndentStep = 16.dp

/** Wide enough for "10." at bodyLarge, so ordinals and their text both stay aligned. */
private val MarkerWidth = 26.dp

/**
 * Renders a Markdown document with the app's own typography.
 *
 * Lazy rather than a scrolling [androidx.compose.foundation.layout.Column]: the longest
 * bundled doc is ~10 KB, and composing it a block at a time avoids measuring the whole
 * thing on every open.
 */
@Composable
fun MarkdownDocument(
	source: String,
	modifier: Modifier = Modifier,
	contentPadding: PaddingValues = PaddingValues(),
) {
	val blocks = remember(source) { parseMarkdown(source) }
	val linkStyles = TextLinkStyles(
		style = SpanStyle(
			color = MaterialTheme.colorScheme.primary,
			textDecoration = TextDecoration.Underline,
		),
	)

	LazyColumn(
		modifier = modifier.fillMaxSize(),
		contentPadding = contentPadding,
	) {
		itemsIndexed(blocks) { index, block ->
			// The first block sits flush against the content padding; every later one
			// carries the gap that separates it from what precedes it.
			val gap = if (index == 0) 0.dp else block.leadingGap()
			when (block) {
				is MarkdownBlock.Heading -> Text(
					text = remember(block, linkStyles) { buildInline(block.text, linkStyles) },
					style = block.textStyle(),
					color = MaterialTheme.colorScheme.onSurface,
					modifier = Modifier.padding(top = gap),
				)

				is MarkdownBlock.Paragraph -> Text(
					text = remember(block, linkStyles) { buildInline(block.text, linkStyles) },
					style = MaterialTheme.typography.bodyLarge,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.padding(top = gap),
				)

				is MarkdownBlock.Item -> Row(
					modifier = Modifier.padding(
						top = gap,
						start = IndentStep * block.depth,
					),
				) {
					// A fixed-width column rather than a trailing space, so wrapped
					// lines hang under the text and never under the marker.
					Box(modifier = Modifier.width(MarkerWidth)) {
						Text(
							text = block.marker,
							style = MaterialTheme.typography.bodyLarge,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
					}
					Text(
						text = remember(block, linkStyles) { buildInline(block.text, linkStyles) },
						style = MaterialTheme.typography.bodyLarge,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}
			}
		}
	}
}

/**
 * Space above a block. Headings claim the most so they read as opening a section rather
 * than as belonging to the text above them; list items claim the least so a list holds
 * together as one object.
 */
private fun MarkdownBlock.leadingGap(): Dp = when (this) {
	is MarkdownBlock.Heading -> when (level) {
		1 -> 24.dp
		2 -> 20.dp
		else -> 16.dp
	}

	is MarkdownBlock.Paragraph -> 12.dp
	is MarkdownBlock.Item -> 6.dp
}

@Composable
private fun MarkdownBlock.Heading.textStyle(): TextStyle = when (level) {
	1 -> MaterialTheme.typography.headlineSmall
	2 -> MaterialTheme.typography.titleLarge
	else -> MaterialTheme.typography.titleMedium
}
