package com.legbehindneck.fasttrack48.lunar

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import io.github.aakira.napier.Napier
import kotlin.math.roundToLong

/**
 * One-shot approximate location from the platform [LocationManager] - no Google Play
 * Services, so it works on F-Droid and de-Googled phones. Uses a recent last-known fix
 * when there is one, else asks one provider for a single fix with a timeout. Coordinates
 * are rounded to 0.01 degree (about 1 km): ample for sunrise, and no more than needed.
 */
object DeviceLocation {

	sealed interface Outcome {
		data class Found(val point: GeoPoint) : Outcome
		data object NoPermission : Outcome
		data object LocationOff : Outcome
		data object NotFound : Outcome
	}

	private const val FIX_TIMEOUT_MS = 30_000L
	private const val MAX_LAST_KNOWN_AGE_MS = 24 * 60 * 60 * 1000L

	/** Reports exactly once, on the main thread. */
	@MainThread
	@SuppressLint("MissingPermission") // checked first; every call is also guarded below
	fun requestOnce(context: Context, onResult: (Outcome) -> Unit) {
		val app = context.applicationContext
		if (ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION)
			!= PackageManager.PERMISSION_GRANTED
		) {
			onResult(Outcome.NoPermission)
			return
		}
		val lm = app.getSystemService(LocationManager::class.java)
		if (lm == null || !LocationManagerCompat.isLocationEnabled(lm)) {
			onResult(Outcome.LocationOff)
			return
		}
		recentLastKnown(lm)?.let {
			onResult(Outcome.Found(it.toPoint()))
			return
		}
		val provider = providers(lm).firstOrNull()
		if (provider == null) {
			onResult(Outcome.LocationOff)
			return
		}
		requestFix(lm, provider, onResult)
	}

	@SuppressLint("MissingPermission")
	private fun recentLastKnown(lm: LocationManager): Location? {
		val now = System.currentTimeMillis()
		var best: Location? = null
		for (provider in lm.allProviders) {
			val location = try {
				lm.getLastKnownLocation(provider)
			} catch (e: SecurityException) {
				null
			} catch (e: IllegalArgumentException) {
				null
			} ?: continue
			if (now - location.time > MAX_LAST_KNOWN_AGE_MS) continue
			if (best == null || location.time > best.time) best = location
		}
		return best
	}

	/** Enabled providers an approximate-location app may use, best first. */
	private fun providers(lm: LocationManager): List<String> {
		val preferred = buildList {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
			add(LocationManager.NETWORK_PROVIDER)
			add(LocationManager.GPS_PROVIDER)
		}
		val enabled = try {
			lm.getProviders(true)
		} catch (e: SecurityException) {
			emptyList()
		}
		return preferred.filter { it in enabled }
	}

	@SuppressLint("MissingPermission")
	private fun requestFix(lm: LocationManager, provider: String, onResult: (Outcome) -> Unit) {
		val main = Handler(Looper.getMainLooper())
		var done = false
		var cleanup: () -> Unit = {}

		fun finish(location: Location?) {
			if (done) return
			done = true
			main.removeCallbacksAndMessages(null)
			cleanup()
			onResult(if (location != null) Outcome.Found(location.toPoint()) else Outcome.NotFound)
		}

		try {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
				val cancel = CancellationSignal()
				cleanup = { cancel.cancel() }
				lm.getCurrentLocation(provider, cancel, { main.post(it) }) { location -> finish(location) }
			} else {
				// Every method is overridden: before API 30 they are abstract, and the
				// framework calls them.
				val listener = object : LocationListener {
					override fun onLocationChanged(location: Location) = finish(location)
					@Deprecated("Deprecated in Java")
					override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
					override fun onProviderEnabled(provider: String) {}
					override fun onProviderDisabled(provider: String) {}
				}
				cleanup = { lm.removeUpdates(listener) }
				lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
			}
			main.postDelayed({ finish(null) }, FIX_TIMEOUT_MS)
		} catch (e: SecurityException) {
			Napier.w("Location request refused for $provider", e)
			finish(null)
		} catch (e: IllegalArgumentException) {
			Napier.w("Location provider $provider unavailable", e)
			finish(null)
		}
	}

	private fun Location.toPoint() = GeoPoint(round2(latitude), round2(longitude))

	private fun round2(value: Double): Double = (value * 100.0).roundToLong() / 100.0
}
