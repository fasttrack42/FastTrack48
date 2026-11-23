package com.legbehindneck.fasttrack48

import android.app.Application
import com.legbehindneck.fasttrack48.di.mainModule
import com.legbehindneck.fasttrack48.lunar.LunarReminderScheduler
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * Process start, which happens for an alarm, a job or a widget update as well as for the app.
 * Kept to what every one of them needs: no file is read here (the profile loads where it is
 * shown) and no widget preview is published (the widget declares a static preview layout).
 */
class FastTrackApp : Application() {
	override fun onCreate() {
		super.onCreate()

		if (BuildConfig.DEBUG) {
			Napier.base(DebugAntilog())
		}

		startKoin {
			androidContext(this@FastTrackApp)
			modules(mainModule)
		}

		// Post any Ekadashi / Purnima / Amavasya reminder an alarm missed (force-stop, Doze) and
		// re-arm; then again each time a fast starts or ends, which opens the quiet after it.
		LunarReminderScheduler.start(this)
	}
}
