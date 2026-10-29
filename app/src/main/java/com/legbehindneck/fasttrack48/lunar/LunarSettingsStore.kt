package com.legbehindneck.fasttrack48.lunar

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** A location the user chose to share, and the time zone it was taken in. */
data class SavedLocation(val point: GeoPoint, val zoneId: String, val savedAtMillis: Long)

/**
 * Persists the Ekadashi / Purnima / Amavasya reminder choices. Device-bound state (the shared
 * location and the scheduler's watermark) lives in a separate file that backup and
 * device transfer exclude (res/xml/backup_rules.xml, data_extraction_rules.xml), so a
 * location never leaves the phone.
 */
object LunarSettingsStore {

	private const val PREFS = "lunar_reminders"
	private const val DEVICE_PREFS = "lunar_device"

	private const val KEY_EKADASHI = "ekadashi_enabled"
	private const val KEY_TRADITION = "tradition"
	private const val KEY_EKADASHI_LEADS = "ekadashi_leads"
	private const val KEY_FULL_MOON = "full_moon_enabled"
	private const val KEY_FULL_MOON_LEADS = "full_moon_leads"
	private const val KEY_NEW_MOON = "new_moon_enabled"
	private const val KEY_NEW_MOON_LEADS = "new_moon_leads"
	private const val KEY_MOON_DAY_STYLE = "moon_day_style"
	private const val KEY_HOUR = "hour"

	private const val KEY_LAT = "latitude"
	private const val KEY_LON = "longitude"
	private const val KEY_LOCATION_ZONE = "location_zone"
	private const val KEY_LOCATION_TIME = "location_time"
	private const val KEY_WATERMARK = "watermark"

	private fun prefs(context: Context): SharedPreferences =
		context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

	private fun devicePrefs(context: Context): SharedPreferences =
		context.applicationContext.getSharedPreferences(DEVICE_PREFS, Context.MODE_PRIVATE)

	fun load(context: Context): LunarReminderSettings {
		val p = prefs(context)
		val defaults = LunarReminderSettings()
		return LunarReminderSettings(
			ekadashiEnabled = p.getBoolean(KEY_EKADASHI, defaults.ekadashiEnabled),
			tradition = p.getString(KEY_TRADITION, null)
				?.let { name -> EkadashiTradition.entries.firstOrNull { it.name == name } },
			ekadashiLeadDays = parseLeads(p.getString(KEY_EKADASHI_LEADS, null), defaults.ekadashiLeadDays),
			fullMoonEnabled = p.getBoolean(KEY_FULL_MOON, defaults.fullMoonEnabled),
			fullMoonLeadDays = parseLeads(p.getString(KEY_FULL_MOON_LEADS, null), defaults.fullMoonLeadDays),
			newMoonEnabled = p.getBoolean(KEY_NEW_MOON, defaults.newMoonEnabled),
			newMoonLeadDays = parseLeads(p.getString(KEY_NEW_MOON_LEADS, null), defaults.newMoonLeadDays),
			moonDayStyle = p.getString(KEY_MOON_DAY_STYLE, null)
				?.let { name -> MoonDayStyle.entries.firstOrNull { it.name == name } }
				?: defaults.moonDayStyle,
			hourOfDay = p.getInt(KEY_HOUR, defaults.hourOfDay).coerceIn(0, 23),
		)
	}

	fun save(context: Context, settings: LunarReminderSettings) {
		prefs(context).edit {
			putBoolean(KEY_EKADASHI, settings.ekadashiEnabled)
			if (settings.tradition == null) remove(KEY_TRADITION) else putString(KEY_TRADITION, settings.tradition.name)
			putString(KEY_EKADASHI_LEADS, settings.ekadashiLeadDays.sorted().joinToString(","))
			putBoolean(KEY_FULL_MOON, settings.fullMoonEnabled)
			putString(KEY_FULL_MOON_LEADS, settings.fullMoonLeadDays.sorted().joinToString(","))
			putBoolean(KEY_NEW_MOON, settings.newMoonEnabled)
			putString(KEY_NEW_MOON_LEADS, settings.newMoonLeadDays.sorted().joinToString(","))
			putString(KEY_MOON_DAY_STYLE, settings.moonDayStyle.name)
			putInt(KEY_HOUR, settings.hourOfDay.coerceIn(0, 23))
		}
	}

	fun loadLocation(context: Context): SavedLocation? {
		val p = devicePrefs(context)
		val zone = p.getString(KEY_LOCATION_ZONE, null) ?: return null
		if (!p.contains(KEY_LAT) || !p.contains(KEY_LON)) return null
		val lat = Double.fromBits(p.getLong(KEY_LAT, 0L))
		val lon = Double.fromBits(p.getLong(KEY_LON, 0L))
		if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
		return SavedLocation(GeoPoint(lat, lon), zone, p.getLong(KEY_LOCATION_TIME, 0L))
	}

	/** Saves [location], or forgets the shared location when it is null. */
	fun saveLocation(context: Context, location: SavedLocation?) {
		devicePrefs(context).edit {
			if (location == null) {
				remove(KEY_LAT)
				remove(KEY_LON)
				remove(KEY_LOCATION_ZONE)
				remove(KEY_LOCATION_TIME)
			} else {
				putLong(KEY_LAT, location.point.latitude.toRawBits())
				putLong(KEY_LON, location.point.longitude.toRawBits())
				putString(KEY_LOCATION_ZONE, location.zoneId)
				putLong(KEY_LOCATION_TIME, location.savedAtMillis)
			}
		}
	}

	/** Reminders that fire at or before this instant (epoch millis) are already handled. */
	internal fun watermark(context: Context): Long? =
		devicePrefs(context).let { if (it.contains(KEY_WATERMARK)) it.getLong(KEY_WATERMARK, 0L) else null }

	internal fun setWatermark(context: Context, epochMillis: Long) {
		devicePrefs(context).edit { putLong(KEY_WATERMARK, epochMillis) }
	}

	private fun parseLeads(raw: String?, default: Set<Int>): Set<Int> {
		if (raw == null) return default
		return raw.split(',')
			.mapNotNull { it.trim().toIntOrNull() }
			.filter { it in LunarReminderSettings.LEAD_OPTIONS }
			.toSet()
	}
}
