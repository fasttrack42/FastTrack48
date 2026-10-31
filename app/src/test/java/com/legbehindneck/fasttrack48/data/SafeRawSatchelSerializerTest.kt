package com.legbehindneck.fasttrack48.data

import cafe.adriel.satchel.ktx.serialize
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Serializable

class SafeRawSatchelSerializerTest {

	/** What a minified release wrote: R8's field names, its own serialVersionUID. */
	private enum class LegacySex { Male, Female }

	private class LegacyProfile(
		val d: Int,
		val e: Double,
		val f: Double,
		val g: LegacySex,
	) : Serializable {
		companion object {
			private const val serialVersionUID = -4419200345L
		}
	}

	/**
	 * Renames a class inside a serialized stream to a same-length name no class loader can
	 * find, as n8.j is in a build that no longer has it.
	 */
	private fun ByteArray.renameClass(from: String): ByteArray {
		val old = from.toByteArray(Charsets.UTF_8)
		val new = "zz.".plus("q".repeat(old.size - 3)).toByteArray(Charsets.UTF_8)
		val out = copyOf()
		var i = 0
		var found = false
		while (i <= out.size - old.size) {
			if ((old.indices).all { out[i + it] == old[it] }) {
				new.copyInto(out, i)
				found = true
				i += old.size
			} else {
				i++
			}
		}
		assertTrue("class name $from not in the stream", found)
		return out
	}

	@Test
	fun profileWrittenByAMinifiedRelease_isReadByShape() {
		val bytes = mapOf(
			Data.KEY_PROFILE to LegacyProfile(41, 172.5, 68.25, LegacySex.Female),
			Data.KEY_INTRO_SEEN to true,
		).serialize()
			.renameClass(LegacyProfile::class.java.name)
			.renameClass(LegacySex::class.java.name)

		val read = SafeRawSatchelSerializer.deserialize(bytes)

		assertEquals(Profile(41, 172.5, 68.25, Gender.Female), read[Data.KEY_PROFILE])
		assertEquals(true, read[Data.KEY_INTRO_SEEN])
	}

	@Test
	fun currentProfile_roundTrips() {
		val profile = Profile(35, 180.0, 77.0, Gender.Male)
		val bytes = runBlocking { SafeRawSatchelSerializer.serialize(mapOf(Data.KEY_PROFILE to profile)) }
		assertEquals(profile, SafeRawSatchelSerializer.deserialize(bytes)[Data.KEY_PROFILE])
	}

	@Test
	fun garbage_readsAsEmpty() {
		assertTrue(SafeRawSatchelSerializer.deserialize(byteArrayOf(1, 2, 3)).isEmpty())
		assertTrue(SafeRawSatchelSerializer.deserialize(ByteArray(0)).isEmpty())
	}
}
