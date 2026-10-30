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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

	private val running = MutableStateFlow(false)

	/**
	 * Whether a request is under way. Process-wide rather than screen state: a Settings screen
	 * recreated mid-request (rotation) still shows the request and cannot start a second one.
	 */
	val inProgress: StateFlow<Boolean> = running.asStateFlow()

	/**
	 * Reports exactly once, on the main thread. Ignored while another request is running;
	 * that one's outcome is still reported to the caller that started it.
	 */
	@MainThread
	@SuppressLint("MissingPermission") // checked first; every call is also guarded below
	fun requestOnce(context: Context, onResult: (Outcome) -> Unit) {
		if (running.value) return
		running.value = true
		val report: (Outcome) -> Unit = { outcome ->
			running.value = false
			onResult(outcome)
		}
		val app = context.applicationContext
		if (ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION)
			!= PackageManager.PERMISSION_GRANTED
		) {
			report(Outcome.NoPermission)
			return
		}
		val lm = app.getSystemService(LocationManager::class.java)
		if (lm == null || !LocationManagerCompat.isLocationEnabled(lm)) {
			report(Outcome.LocationOff)
			return
		}
		recentLastKnown(lm)?.let {
			report(Outcome.Found(it.toPoint()))
			return
		}
		val providers = providers(lm)
		if (providers.isEmpty()) {
			report(Outcome.LocationOff)
			return
		}
		requestFix(lm, providers, report)
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

	/**
	 * One fix from the first provider that accepts the request. A provider that refuses
	 * outright (an approximate-location app asking GPS before API 31) hands over to the next;
	 * one that accepts and then finds nothing ends in [Outcome.NotFound] at the timeout.
	 */
	@SuppressLint("MissingPermission")
	private fun requestFix(lm: LocationManager, providers: List<String>, onResult: (Outcome) -> Unit) {
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

		for (provider in providers) {
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
				return
			} catch (e: SecurityException) {
				Napier.w("Location request refused for $provider", e)
			} catch (e: IllegalArgumentException) {
				Napier.w("Location provider $provider unavailable", e)
			}
		}
		finish(null)
	}

	private fun Location.toPoint() = GeoPoint(round2(latitude), round2(longitude))

	private fun round2(value: Double): Double = (value * 100.0).roundToLong() / 100.0
}
