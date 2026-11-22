package com.legbehindneck.fasttrack48.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.io.File

/**
 * The body profile: four values in SharedPreferences ("profile"), read when a screen needs
 * them. It used to live in a Satchel file that was read and deserialized on the main thread at
 * every process start, through an unmaintained library on a deprecated coroutines API.
 *
 * That file is migrated the first time the profile is read and then deleted. A backup made by
 * an older version still carries it and restores it, so the migration stays: it costs one
 * existence check per read once the file is gone. [Profile] and [Gender] keep their R8 keep
 * rules for it, since Java serialization needs their names.
 */
object ProfileStore {
	private const val PREFS = "profile"
	private const val KEY_AGE = "age_years"
	private const val KEY_HEIGHT = "height_cm"
	private const val KEY_WEIGHT = "weight_kg"
	private const val KEY_GENDER = "gender"

	fun load(context: Context): Profile {
		migrateLegacyFile(context)
		val p = prefs(context)
		return Profile(
			ageYears = p.getInt(KEY_AGE, 0),
			heightCm = Double.fromBits(p.getLong(KEY_HEIGHT, 0.0.toRawBits())),
			weightKg = Double.fromBits(p.getLong(KEY_WEIGHT, 0.0.toRawBits())),
			gender = p.getString(KEY_GENDER, null)?.let { name -> Gender.entries.firstOrNull { it.name == name } },
		)
	}

	fun save(context: Context, profile: Profile) = write(prefs(context), profile, commit = false)

	/**
	 * The Satchel file, if one is still there: its profile moves into the preferences (unless
	 * they already hold one, which is newer) and the file is deleted. An unreadable file is
	 * deleted too; it held nothing that could be read back.
	 *
	 * An all-zero profile is not moved. Satchel wrote one on the first visit to the Profile tab
	 * (getOrSet with the defaults), and the sex in it was the old default, Male, not a choice:
	 * moved, it would show as chosen.
	 */
	@Synchronized
	private fun migrateLegacyFile(context: Context) {
		val file = File(context.filesDir, Data.STORAGE_PATH)
		if (!file.exists()) return
		val p = prefs(context)
		if (!p.contains(KEY_AGE)) {
			val legacy = runCatching { SafeRawSatchelSerializer.deserialize(file.readBytes())[Data.KEY_PROFILE] }
				.getOrNull() as? Profile
			val entered = legacy != null && (legacy.ageYears != 0 || legacy.heightCm != 0.0 || legacy.weightKg != 0.0)
			// Committed before the file goes, so a crash in between cannot lose the profile.
			if (entered) write(p, legacy, commit = true)
		}
		file.delete()
	}

	private fun write(p: SharedPreferences, profile: Profile, commit: Boolean) = p.edit(commit = commit) {
		putInt(KEY_AGE, profile.ageYears)
		putLong(KEY_HEIGHT, profile.heightCm.toRawBits())
		putLong(KEY_WEIGHT, profile.weightKg.toRawBits())
		val gender = profile.gender
		if (gender == null) remove(KEY_GENDER) else putString(KEY_GENDER, gender.name)
	}

	private fun prefs(context: Context): SharedPreferences =
		context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
