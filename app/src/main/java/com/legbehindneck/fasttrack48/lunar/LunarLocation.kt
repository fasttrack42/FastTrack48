package com.legbehindneck.fasttrack48.lunar

import android.content.Context
import java.time.Instant
import java.time.ZoneId
import android.icu.util.TimeZone as IcuTimeZone

/** The point sunrise is computed for, and whether the user shared it. */
data class ResolvedLocation(val point: GeoPoint, val fromDevice: Boolean)

/**
 * Where sunrise is computed. The location the user shared wins while the phone stays in
 * the time zone it was taken in; otherwise (never shared, or travelling) the time zone's
 * representative point is used, so reminders follow the phone across zones.
 */
object LunarLocation {

	fun resolve(context: Context, zone: ZoneId = ZoneId.systemDefault()): ResolvedLocation {
		val saved = LunarSettingsStore.loadLocation(context)
		if (saved != null && saved.zoneId == zone.id) return ResolvedLocation(saved.point, true)
		return ResolvedLocation(forZone(zone), false)
	}

	/** The zone's principal city, an equivalent zone's, or a rough point from its offset. */
	fun forZone(zone: ZoneId): GeoPoint =
		ZoneLocations.lookup(zone.id) ?: equivalentZone(zone.id) ?: fromOffset(zone)

	/** The city part of a zone id, e.g. "Europe/Madrid" -> "Madrid", for display. */
	fun zoneLabel(zone: ZoneId): String = zone.id.substringAfterLast('/').replace('_', ' ')

	private fun equivalentZone(id: String): GeoPoint? {
		val canonical = IcuTimeZone.getCanonicalID(id)
		if (canonical != null) ZoneLocations.lookup(canonical)?.let { return it }
		for (i in 0 until IcuTimeZone.countEquivalentIDs(id)) {
			ZoneLocations.lookup(IcuTimeZone.getEquivalentID(id, i))?.let { return it }
		}
		return null
	}

	/** Etc/GMT±N and other city-less zones: the offset's meridian at a mid latitude. */
	private fun fromOffset(zone: ZoneId): GeoPoint {
		val offsetSeconds = zone.rules.getStandardOffset(Instant.now()).totalSeconds
		return GeoPoint(30.0, (offsetSeconds / 240.0).coerceIn(-180.0, 180.0))
	}
}
