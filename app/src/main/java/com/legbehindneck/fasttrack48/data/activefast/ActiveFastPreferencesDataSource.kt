package com.legbehindneck.fasttrack48.data.activefast

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.legbehindneck.fasttrack48.data.Data
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.time.Instant

class ActiveFastPreferencesDataSource(appContext: Context) : ActiveFastDataSource {

	// The historical default-preferences file, addressed directly now that
	// android.preference.PreferenceManager is deprecated (same file, same data).
	private val storage = appContext.getSharedPreferences(
		"${appContext.packageName}_preferences", Context.MODE_PRIVATE
	)

	override fun getFastStart(): Instant? {
		val mills = storage.getLong(Data.KEY_FAST_START, -1)
		return if (mills > -1) {
			Instant.fromEpochMilliseconds(mills)
		} else {
			null
		}
	}

	override fun getFastEnd(): Instant? {
		val mills = storage.getLong(Data.KEY_FAST_END, -1)
		return if (mills > -1) {
			Instant.fromEpochMilliseconds(mills)
		} else {
			null
		}
	}

	/**
	 * Both halves are re-read on every notification rather than tracked incrementally.
	 * SharedPreferences commits the whole edit to its in-memory map *before* notifying,
	 * and notifies once per changed key, so the first callback of a two-key write already
	 * observes the final pair and distinctUntilChanged drops the second.
	 */
	override fun observe(): Flow<ActiveFastWindow> = callbackFlow {
		trySend(window())

		val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
			// The same preferences file the settings datasource uses, so filtering by key
			// is not an optimisation: without it every settings toggle would re-render the
			// dial. key is null when the file is cleared wholesale — not a change to ours.
			if (key == Data.KEY_FAST_START || key == Data.KEY_FAST_END) {
				trySend(window())
			}
		}
		storage.registerOnSharedPreferenceChangeListener(listener)
		awaitClose { storage.unregisterOnSharedPreferenceChangeListener(listener) }
	}.distinctUntilChanged()

	private fun window() = ActiveFastWindow(getFastStart(), getFastEnd())

	override fun setFastStart(time: Instant) {
		storage.edit { putLong(Data.KEY_FAST_START, time.toEpochMilliseconds()) }
	}

	override fun setFastEnd(time: Instant) {
		storage.edit { putLong(Data.KEY_FAST_END, time.toEpochMilliseconds()) }
	}

	override fun clearFastStart() {
		storage.edit { putLong(Data.KEY_FAST_START, -1) }
	}

	override fun clearFastEnd() {
		storage.edit { putLong(Data.KEY_FAST_END, -1) }
	}
}