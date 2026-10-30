package com.legbehindneck.fasttrack48.utils

import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * The largest file an import reads, and the most a zip entry may inflate to. Far beyond any
 * real logbook (a CSV row is ~80 bytes, so this is millions of fasts); it stops a zip bomb or
 * an endless stream. It is not a heap guard: reading and parsing take several times the file
 * size, which outgrows the heap of most phones well before this cap, so the import path also
 * treats an OutOfMemoryError as an unreadable file (see importLogFromUri).
 */
const val MAX_IMPORT_BYTES = 200 * 1024 * 1024

/** Reads the whole stream, or returns null when it holds more than [limit] bytes. */
fun InputStream.readAtMost(limit: Int): ByteArray? {
	val out = ByteArrayOutputStream()
	val buffer = ByteArray(64 * 1024)
	var total = 0
	while (true) {
		val n = read(buffer)
		if (n < 0) break
		total += n
		if (total > limit) return null
		out.write(buffer, 0, n)
	}
	return out.toByteArray()
}
