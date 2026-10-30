package com.legbehindneck.fasttrack48.data

import cafe.adriel.satchel.ktx.serialize
import cafe.adriel.satchel.serializer.SatchelSerializer
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ObjectStreamClass

/**
 * Satchel's Java serialization, made to survive minification. Unreadable data reads as empty
 * rather than crashing the app.
 *
 * Releases before the keep rules (proguard-rules.pro) let R8 rename [Profile] and [Gender] and
 * drop [Profile]'s serialVersionUID, so each wrote them under names of its own: n8.j with
 * fields d, e, f, g and a computed serialVersionUID in 14.1003.10, other names before. A plain
 * stream cannot find those classes, and the whole map was lost on the next update. They are
 * recognised by shape instead: the only app classes stored are the profile (an int, two
 * doubles and an enum) and its enum (no fields, serialVersionUID 0, as every enum).
 */
object SafeRawSatchelSerializer : SatchelSerializer {

	override suspend fun serialize(data: Map<String, Any>): ByteArray =
		data.serialize()

	override fun deserialize(data: ByteArray): Map<String, Any> {
		if (data.isEmpty()) return emptyMap()
		return try {
			LegacyTolerantStream(ByteArrayInputStream(data)).use {
				@Suppress("UNCHECKED_CAST")
				it.readObject() as Map<String, Any>
			}
		} catch (e: Exception) {
			emptyMap()
		}
	}
}

/**
 * An [ObjectInputStream] that reads a renamed profile or gender as today's classes. Today's
 * descriptor replaces the stored one, which skips the serialVersionUID check; the values still
 * line up, because a stream lays primitive fields out sorted by name, and the stored profile's
 * (d: int, e: double, f: double) and today's (ageYears, heightCm, weightKg) sort into the same
 * int-double-double order, followed by the one object field.
 */
internal class LegacyTolerantStream(input: InputStream) : ObjectInputStream(input) {

	override fun readClassDescriptor(): ObjectStreamClass {
		val stored = super.readClassDescriptor()
		val name = stored.name
		return when {
			name == PROFILE.name || name == GENDER.name -> stored
			name.startsWith("java.") || name.startsWith("kotlin.") || name.startsWith("[") -> stored
			isProfileShaped(stored) -> PROFILE
			stored.serialVersionUID == 0L && stored.fields.isEmpty() -> GENDER
			else -> stored
		}
	}

	private fun isProfileShaped(stored: ObjectStreamClass): Boolean =
		stored.fields.map { it.typeCode }.sorted() == PROFILE_SHAPE

	private companion object {
		val PROFILE: ObjectStreamClass = ObjectStreamClass.lookup(Profile::class.java)
		val GENDER: ObjectStreamClass = ObjectStreamClass.lookup(Gender::class.java)
		/** The type codes of [Profile]'s fields, sorted: double, double, int, object. */
		val PROFILE_SHAPE = listOf('D', 'D', 'I', 'L')
	}
}
