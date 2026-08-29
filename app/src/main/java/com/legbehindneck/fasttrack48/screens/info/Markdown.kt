package com.legbehindneck.fasttrack48.screens.info

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * A Markdown reader for exactly the constructs the bundled docs use.
 *
 * A census of all eighteen `.md` files across the nine locales finds seven: headings
 * h1-h3, paragraphs, ordered and bulleted lists (nested two deep), bold, italic and
 * links. No images, tables, code, HTML, block quotes or rules.
 *
 * This is hand-rolled rather than delegated to a library because the lightest Markdown
 * renderer available for Compose unconditionally installs an HTML parser, a GFM table
 * extension, a syntax highlighter and a Coil/OkHttp image loader at build time, so R8
 * cannot strip any of it — measured at ~787 KB of shipped APK for those seven
 * constructs, plus an HTTP stack in an app that never touches the network.
 *
 * Anything outside the supported set degrades to its literal source text. That is the
 * intended failure mode: a translator who writes an unsupported construct sees it
 * verbatim rather than seeing it silently vanish.
 */
sealed interface MarkdownBlock {

	/** [level] is 1..6 as written, though only 1..3 occur today. */
	data class Heading(val level: Int, val text: String) : MarkdownBlock

	data class Paragraph(val text: String) : MarkdownBlock

	/**
	 * A list row. [marker] is already resolved to what should be drawn — a bullet glyph
	 * or an ordinal like `3.` — so the renderer stays pure layout and never counts.
	 */
	data class Item(val depth: Int, val marker: String, val text: String) : MarkdownBlock
}

private val HEADING = Regex("""^(#{1,6})\s+(.*)$""")
private val BULLET = Regex("""^([ \t]*)[-*+][ \t]+(.*)$""")
private val ORDERED = Regex("""^([ \t]*)\d+[.)][ \t]+(.*)$""")

/** Bullet glyphs by nesting depth; the last one repeats for anything deeper. */
private val BULLETS = listOf("•", "◦", "▪")

/** Two spaces per level, which is what every bundled doc uses. */
private const val INDENT_UNIT = 2
private const val MAX_DEPTH = 3

/**
 * Splits [source] into renderable blocks.
 *
 * Paragraphs join their soft-wrapped source lines with a space, per Markdown. Ordinals
 * are counted per nesting depth and reset by anything that ends the list — a heading, a
 * paragraph, or a bullet at the same depth — so two adjacent numbered lists do not
 * continue each other. Blank lines deliberately do not reset them, so a loose list
 * (items separated by blank lines) still numbers continuously.
 */
fun parseMarkdown(source: String): List<MarkdownBlock> {
	val blocks = ArrayList<MarkdownBlock>()
	val paragraph = StringBuilder()
	val ordinals = HashMap<Int, Int>()

	fun flushParagraph() {
		if (paragraph.isNotEmpty()) {
			blocks += MarkdownBlock.Paragraph(paragraph.toString())
			paragraph.setLength(0)
			ordinals.clear()
		}
	}

	for (raw in source.lineSequence()) {
		val line = raw.trimEnd()
		val heading = HEADING.matchEntire(line)
		val ordered = if (heading == null) ORDERED.matchEntire(line) else null
		// A line can satisfy both BULLET and ORDERED only if it starts with a digit,
		// which BULLET cannot match, so the order of these two tests is immaterial.
		val bullet = if (heading == null && ordered == null) BULLET.matchEntire(line) else null

		when {
			line.isBlank() -> flushParagraph()

			heading != null -> {
				flushParagraph()
				ordinals.clear()
				blocks += MarkdownBlock.Heading(
					level = heading.groupValues[1].length,
					text = heading.groupValues[2].trim(),
				)
			}

			ordered != null -> {
				flushParagraph()
				val depth = depthOf(ordered.groupValues[1])
				// Deeper counters are stale once we step back out to a shallower level.
				ordinals.keys.removeAll { it > depth }
				val n = (ordinals[depth] ?: 0) + 1
				ordinals[depth] = n
				blocks += MarkdownBlock.Item(depth, "$n.", ordered.groupValues[2].trim())
			}

			bullet != null -> {
				flushParagraph()
				val depth = depthOf(bullet.groupValues[1])
				ordinals.keys.removeAll { it >= depth }
				blocks += MarkdownBlock.Item(
					depth = depth,
					marker = BULLETS[minOf(depth, BULLETS.lastIndex)],
					text = bullet.groupValues[2].trim(),
				)
			}

			else -> {
				if (paragraph.isNotEmpty()) paragraph.append(' ')
				paragraph.append(line.trim())
			}
		}
	}
	flushParagraph()
	return blocks
}

/** Tabs count as one indent unit each; mixed indentation is thus still monotonic. */
private fun depthOf(indent: String): Int {
	val columns = indent.sumOf { if (it == '\t') INDENT_UNIT else 1 }
	return minOf(columns / INDENT_UNIT, MAX_DEPTH)
}

// Ordered alternation: bold is tested before italic so `**x**` never reads as an empty
// italic. Link URLs reject whitespace and parentheses, which keeps a trailing `(*note*)`
// after a link from being swallowed into the href.
private val INLINE = Regex("""\[([^\[\]]+)]\(([^()\s]+)\)|\*\*(.+?)\*\*|\*([^*]+)\*""")

/** Guards against pathological input; well-formed nesting terminates on its own. */
private const val MAX_INLINE_NESTING = 4

/**
 * Renders inline Markdown in [text] into styled spans, resolving links against
 * [linkStyles] so they pick up the caller's theme.
 */
fun buildInline(text: String, linkStyles: TextLinkStyles): AnnotatedString =
	buildAnnotatedString { appendInline(text, linkStyles, depth = 0) }

private fun AnnotatedString.Builder.appendInline(
	text: String,
	linkStyles: TextLinkStyles,
	depth: Int,
) {
	if (depth >= MAX_INLINE_NESTING) {
		append(text)
		return
	}

	var cursor = 0
	for (match in INLINE.findAll(text)) {
		append(text, cursor, match.range.first)
		val groups = match.groups
		when {
			groups[1] != null -> {
				val start = length
				appendInline(groups[1]!!.value, linkStyles, depth + 1)
				addLink(LinkAnnotation.Url(groups[2]!!.value, linkStyles), start, length)
			}

			groups[3] != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
				appendInline(groups[3]!!.value, linkStyles, depth + 1)
			}

			groups[4] != null -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
				appendInline(groups[4]!!.value, linkStyles, depth + 1)
			}
		}
		cursor = match.range.last + 1
	}
	append(text, cursor, text.length)
}
