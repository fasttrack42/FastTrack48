package com.legbehindneck.fasttrack48.utils

/** RFC 5545 text rules, shared by the logbook export and the fast-day calendar. */
internal object ICalendar {

	/** A TEXT value with its backslashes, semicolons, commas and line breaks escaped (§3.3.11). */
	fun escape(text: String): String =
		text.replace("\\", "\\\\")
			.replace(";", "\\;")
			.replace(",", "\\,")
			.replace("\r\n", "\\n")
			.replace("\n", "\\n")
			.replace("\r", "\\n")

	/** Content lines as one document: CRLF line breaks, each line folded over 75 octets (§3.1). */
	fun document(lines: List<String>): String = lines.joinToString("\r\n") { fold(it) } + "\r\n"

	/**
	 * Fold a content line to <=75 octets per physical line (continuation begins with a space).
	 * Walks code points, not chars: a supplementary character (an emoji in a note) is four
	 * UTF-8 octets and must never be split across a fold.
	 */
	private fun fold(line: String): String {
		val out = StringBuilder()
		var octets = 0
		var i = 0
		while (i < line.length) {
			val cp = line.codePointAt(i)
			val cpOctets = when {
				cp < 0x80 -> 1
				cp < 0x800 -> 2
				cp < 0x10000 -> 3
				else -> 4
			}
			if (octets + cpOctets > 75) {
				out.append("\r\n ")
				octets = 1
			}
			out.appendCodePoint(cp)
			octets += cpOctets
			i += Character.charCount(cp)
		}
		return out.toString()
	}
}
