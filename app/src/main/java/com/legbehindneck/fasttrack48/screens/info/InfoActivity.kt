package com.legbehindneck.fasttrack48.screens.info

import android.os.Bundle
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import com.legbehindneck.fasttrack48.R
import com.legbehindneck.fasttrack48.data.settings.SettingsDatasource
import com.legbehindneck.fasttrack48.screens.knowledge.LibraryScreen
import com.legbehindneck.fasttrack48.screens.knowledge.SourcesScreen
import com.legbehindneck.fasttrack48.ui.theme.FastTrackTheme
import org.koin.android.ext.android.inject

/**
 * The Library, and its Sources & credits page. Opened on the Library from the menu, or straight
 * on Sources from About ([sourcesIntent]); back from Sources returns to wherever it was opened.
 */
class InfoActivity : AppCompatActivity() {
	private val settings by inject<SettingsDatasource>()

	companion object {
		private const val EXTRA_SOURCES = "sources"

		fun sourcesIntent(context: Context): Intent =
			Intent(context, InfoActivity::class.java).putExtra(EXTRA_SOURCES, true)
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

		val startedOnSources = intent.getBooleanExtra(EXTRA_SOURCES, false)
		setContent {
			var onSources by rememberSaveable { mutableStateOf(startedOnSources) }
			// Each page keeps its scroll position and search across the switch.
			val pages = rememberSaveableStateHolder()
			val returnToLibrary = onSources && !startedOnSources
			BackHandler(enabled = returnToLibrary) { onSources = false }
			FastTrackTheme(themeMode = settings.getThemeMode()) {
				Scaffold(
					topBar = {
						TopAppBar(
							colors = TopAppBarDefaults.topAppBarColors(
								containerColor = MaterialTheme.colorScheme.primaryContainer,
								titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
								actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
								navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
							),
							modifier = Modifier
								.fillMaxWidth(),
							title = {
								Text(stringResource(id = if (onSources) R.string.kb_sources_title else R.string.kb_library_title))
							},
							navigationIcon = {
								IconButton(onClick = { if (returnToLibrary) onSources = false else finish() }) {
									Icon(
										imageVector = Icons.AutoMirrored.Filled.ArrowBack,
										contentDescription = "Back"
									)
								}
							}
						)
					}
				) { paddingValues ->
					pages.SaveableStateProvider(if (onSources) "sources" else "library") {
						if (onSources) {
							SourcesScreen(paddingValues = paddingValues, modifier = Modifier.fillMaxSize())
						} else {
							LibraryScreen(
								paddingValues = paddingValues,
								onOpenSources = { onSources = true },
								modifier = Modifier.fillMaxSize(),
							)
						}
					}
				}
			}
		}
	}

	override fun onSupportNavigateUp(): Boolean {
		onBackPressed()
		return true
	}
}
