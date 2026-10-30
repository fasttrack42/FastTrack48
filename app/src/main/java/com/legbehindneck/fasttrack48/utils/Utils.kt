package com.legbehindneck.fasttrack48.utils

import android.content.Context
import android.provider.Settings
import android.text.format.DateFormat
import androidx.annotation.RawRes
import androidx.core.text.util.LocalePreferences
import java.util.*

fun android.content.res.Resources.getRawTextFile(@RawRes id: Int) =
	openRawResource(id).bufferedReader().use { it.readText() }

fun shouldUse24HourFormat(context: Context): Boolean {
	// The explicit "Use 24-hour format" switch in system Settings wins on every version;
	// LocalePreferences alone reads only the locale's hour cycle and would ignore it.
	when (Settings.System.getString(context.contentResolver, Settings.System.TIME_12_24)) {
		"24" -> return true
		"12" -> return false
	}
	return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
		when (LocalePreferences.getHourCycle()) {
			LocalePreferences.HourCycle.H11, LocalePreferences.HourCycle.H12 -> false
			LocalePreferences.HourCycle.H23, LocalePreferences.HourCycle.H24 -> true
			else -> {
				Locale.getDefault() != Locale.US
			}
		}
	} else {
		DateFormat.is24HourFormat(context)
	}
}