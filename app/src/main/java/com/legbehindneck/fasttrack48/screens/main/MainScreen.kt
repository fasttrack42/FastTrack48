package com.legbehindneck.fasttrack48.screens.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.legbehindneck.fasttrack48.R
import com.legbehindneck.fasttrack48.screens.fasting.ExternalRequests
import com.legbehindneck.fasttrack48.screens.fasting.FastingScreen
import com.legbehindneck.fasttrack48.screens.fasting.FastingViewModel
import com.legbehindneck.fasttrack48.screens.log.LogScreen
import com.legbehindneck.fasttrack48.screens.log.LogViewModel
import com.legbehindneck.fasttrack48.screens.profile.ProfileScreen
import com.legbehindneck.fasttrack48.utils.Utils
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.ExperimentalTime

enum class ScreenPages {
	Fasting,
	Log,
	Profile;

	companion object {
		fun fromOrdinal(ordinal: Int): ScreenPages {
			return when (ordinal) {
				0 -> Fasting
				1 -> Log
				2 -> Profile
				else -> throw IllegalArgumentException("Invalid ordinal")
			}
		}
	}
}

@OptIn(ExperimentalFoundationApi::class)
@ExperimentalTime
@Composable
fun MainScreen(
	onShareClick: () -> Unit,
	onInfoClick: () -> Unit,
	onAboutClick: () -> Unit,
	onSettingsClick: () -> Unit,
	onStartFastClick: () -> Unit = {},
	onEndFastClick: () -> Unit = {},
	onExportClick: () -> Unit = {},
	onImportClick: () -> Unit = {},
	pageRequest: ScreenPages? = null,
	consumePageRequest: () -> Unit = {},
	externalRequests: ExternalRequests = ExternalRequests(),
) {
	val pagerState =
		rememberPagerState(
			initialPage = ScreenPages.Fasting.ordinal,
			pageCount = { ScreenPages.entries.size })
	val coroutineScope = rememberCoroutineScope()

	// An entry point that names a page -- currently a file imported from a file manager,
	// which lands on the Log. Not animated: the app is only now becoming visible, so a
	// scroll from the Fasting page would be a transition out of a screen the user never
	// saw. Consumed once, so the page is free to be swiped away afterwards.
	LaunchedEffect(pageRequest) {
		pageRequest?.let { page ->
			pagerState.scrollToPage(page.ordinal)
			consumePageRequest()
		}
	}

	// Same activity-scoped LogViewModel instance the Log page uses, so the
	// contextual "Clear logbook" overflow action drives its confirmation state.
	val logViewModel: LogViewModel = koinViewModel()
	val logState by logViewModel.uiState.collectAsState()

	// Same activity-scoped FastingViewModel the Fasting page holds, so the overflow
	// menu offers exactly the one fasting action that is legal right now rather than
	// both. Reading it here costs nothing: the page below already created it.
	val fastingViewModel: FastingViewModel = koinViewModel()
	val fastingState by fastingViewModel.uiState.collectAsState()

	val fastingTitle = stringResource(id = R.string.title_fasting)
	val logTitle = stringResource(id = R.string.title_log)
	val profileTitle = stringResource(id = R.string.title_profile)

	val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
	val compactHeight = windowSizeClass.minHeightDp < windowSizeClass.minWidthDp

	// No top app bar: the bottom navigation already names the screen, and the
	// reclaimed space belongs to the content. Actions float in the top-right.
	Scaffold(
		bottomBar = {
			if (compactHeight.not()) {
				NavigationBar(
					modifier = Modifier.Companion
						.background(MaterialTheme.colorScheme.primary)
						.fillMaxWidth()
				) {
					NavigationBarItem(
						icon = {
							Icon(
								painter = painterResource(id = R.drawable.ic_fasting),
								contentDescription = fastingTitle,
							)
						},
						label = { Text(fastingTitle) },
						selected = pagerState.currentPage == ScreenPages.Fasting.ordinal,
						onClick = {
							coroutineScope.launch {
								pagerState.animateScrollToPage(0)
							}
						}
					)

					NavigationBarItem(
						icon = {
							Icon(
								painter = painterResource(id = R.drawable.ic_log),
								contentDescription = logTitle
							)
						},
						label = { Text(logTitle) },
						selected = pagerState.currentPage == 1,
						onClick = {
							coroutineScope.launch {
								pagerState.animateScrollToPage(1)
							}
						}
					)

					NavigationBarItem(
						icon = {
							Icon(
								painter = painterResource(id = R.drawable.ic_profile),
								contentDescription = profileTitle
							)
						},
						label = { Text(profileTitle) },
						selected = pagerState.currentPage == 2,
						onClick = {
							coroutineScope.launch {
								pagerState.animateScrollToPage(2)
							}
						}
					)
				}
			}
		}
	) { paddingValues ->
		val layoutDirection = LocalLayoutDirection.current

		// Where the floating overflow button sits, expressed once, here, where the button
		// is actually placed — and folded into the padding every page already receives.
		//
		// It used to be the pages' problem: the button is a Box-aligned overlay and
		// reserves nothing, so each screen that happened to put something under the
		// top-end corner carved out its own clearance by hand. That is one constant per
		// consumer and a silent collision for every consumer that forgets, which is how
		// the stage title ended up underneath it. Chrome that floats declares its own
		// extent; content is told about it. No screen needs to know this button exists.
		//
		// Which axis it costs depends on where the button is relative to the content. In
		// portrait it is directly above the content, so it costs height. In landscape the
		// content is two columns and the button is over the end one's top corner, so it
		// costs width — and the dial column, which is starved for height and has nothing
		// under the corner, pays nothing.
		val contentPadding = if (compactHeight) {
			PaddingValues(
				end = paddingValues.calculateEndPadding(layoutDirection) + TopActionsExtent,
				bottom = paddingValues.calculateBottomPadding(),
			)
		} else {
			PaddingValues(
				start = paddingValues.calculateStartPadding(layoutDirection),
				top = paddingValues.calculateTopPadding() + TopActionsExtent,
				end = paddingValues.calculateEndPadding(layoutDirection),
				bottom = paddingValues.calculateBottomPadding(),
			)
		}

		Box(modifier = Modifier.fillMaxSize()) {
			if (compactHeight) {

				Row(
					modifier = Modifier
						.padding(top = paddingValues.calculateTopPadding())
						.fillMaxSize()
				) {
					NavigationRail {
					NavigationRailItem(
						icon = {
							Icon(
								painter = painterResource(id = R.drawable.ic_fasting),
								contentDescription = fastingTitle,
							)
						},
						label = { Text(fastingTitle) },
						selected = pagerState.currentPage == 0,
						onClick = {
							coroutineScope.launch {
								pagerState.animateScrollToPage(0)
							}
						}
					)

					NavigationRailItem(
						icon = {
							Icon(
								painter = painterResource(id = R.drawable.ic_log),
								contentDescription = logTitle
							)
						},
						label = { Text(logTitle) },
						selected = pagerState.currentPage == 1,
						onClick = {
							coroutineScope.launch {
								pagerState.animateScrollToPage(1)
							}
						}
					)

					NavigationRailItem(
						icon = {
							Icon(
								painter = painterResource(id = R.drawable.ic_profile),
								contentDescription = profileTitle
							)
						},
						label = { Text(profileTitle) },
						selected = pagerState.currentPage == 2,
						onClick = {
							coroutineScope.launch {
								pagerState.animateScrollToPage(2)
							}
						}
					)
				}

				Content(
					Modifier.weight(1f),
					contentPaddingValues = contentPadding,
					pagerState,
					externalRequests,
				)
			}
			} else {
				Content(
					Modifier.fillMaxSize(),
					contentPaddingValues = contentPadding,
					pagerState,
					externalRequests,
				)
			}

			FloatingTopActions(
				showShare = pagerState.currentPage == ScreenPages.Fasting.ordinal,
				showInfo = pagerState.currentPage == ScreenPages.Fasting.ordinal,
				showClearLog = pagerState.currentPage == ScreenPages.Log.ordinal,
				clearLogEnabled = logState.totalFasts > 0,
				onClearLogClick = { logViewModel.requestClearAll() },
				showFastingAction = pagerState.currentPage == ScreenPages.Fasting.ordinal,
				isFasting = fastingState.isFasting,
				onStartFastClick = onStartFastClick,
				onEndFastClick = onEndFastClick,
				onExportClick = onExportClick,
				onImportClick = onImportClick,
				onShareClick = onShareClick,
				onInfoClick = onInfoClick,
				onAboutClick = onAboutClick,
				onSettingsClick = onSettingsClick,
				modifier = Modifier
					.align(Alignment.TopEnd)
					.padding(
						top = paddingValues.calculateTopPadding() + 4.dp,
						end = 8.dp,
					)
			)
		}
	}
}

// The vertical room the floating overflow button occupies below the window's top inset: a
// 48dp IconButton plus the 4dp it is offset by, rounded to the next Fibonacci rung. Read by
// MainScreen alone, which is the only place that knows where the button is put.
private val TopActionsExtent = 55.dp

/**
 * The old top app bar, distilled to a single translucent overflow button in the top-right
 * corner. Everything lives behind it now.
 */
@Composable
private fun FloatingTopActions(
	showShare: Boolean,
	showInfo: Boolean,
	showClearLog: Boolean,
	clearLogEnabled: Boolean,
	onClearLogClick: () -> Unit,
	showFastingAction: Boolean,
	isFasting: Boolean,
	onStartFastClick: () -> Unit,
	onEndFastClick: () -> Unit,
	onExportClick: () -> Unit,
	onImportClick: () -> Unit,
	onShareClick: () -> Unit,
	onInfoClick: () -> Unit,
	onAboutClick: () -> Unit,
	onSettingsClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	var showMenu by remember { mutableStateOf(false) }

	Surface(
		modifier = modifier,
		shape = RoundedCornerShape(24.dp),
		color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
	) {
		Row(verticalAlignment = Alignment.CenterVertically) {
			Box {
				IconButton(onClick = { showMenu = !showMenu }) {
					Icon(
						imageVector = Icons.Default.MoreVert,
						contentDescription = stringResource(id = R.string.more_options_button_description),
					)
				}

				DropdownMenu(
					expanded = showMenu,
					onDismissRequest = { showMenu = false }
				) {
					// Share and Info used to be permanent pills beside this one, and three
					// icons wide the overlay reached far enough across the top of the screen
					// to sit on the stage title beneath it in any locale whose title is
					// longer than English's. Neither is used often enough to be worth a
					// heading: Share is a once-a-fast flourish and Info is read once. They
					// are still contextual — the Fasting page is the only one either
					// describes — so the same two flags that used to animate the pills in
					// and out now decide whether the items exist at all.
					if (showShare) {
						DropdownMenuItem(
							text = { Text(stringResource(id = R.string.action_share)) },
							leadingIcon = {
								Icon(
									imageVector = Icons.Default.Share,
									contentDescription = null,
								)
							},
							onClick = {
								onShareClick()
								showMenu = false
							},
						)
					}

					if (showInfo) {
						DropdownMenuItem(
							text = { Text(stringResource(id = R.string.action_info)) },
							leadingIcon = {
								Icon(
									imageVector = Icons.Default.Info,
									contentDescription = null,
								)
							},
							onClick = {
								onInfoClick()
								showMenu = false
							},
						)
					}

					if (showShare || showInfo) {
						HorizontalDivider()
					}

					// The primary action already sits on the dial, but a menu is where
					// users go looking when they cannot find a control, so the same act
					// is mirrored here. Only ever one item: starting a fast while one is
					// running is not a thing the app can do, and offering both would ask
					// the reader to work out which applies. It routes through the same
					// dialogs the dial's own button opens — no second code path.
					if (showFastingAction) {
						DropdownMenuItem(
							text = {
								Text(
									stringResource(
										id = if (isFasting) R.string.end_fast_button
										else R.string.start_fast_button
									)
								)
							},
							leadingIcon = {
								Icon(
									imageVector = if (isFasting) Icons.Default.Check
									else Icons.Default.PlayArrow,
									contentDescription = null,
								)
							},
							onClick = {
								if (isFasting) onEndFastClick() else onStartFastClick()
								showMenu = false
							},
						)
						HorizontalDivider()
					}

					// The logbook's two doors, on every page. They are the app's strongest
					// claim — data leaves in open formats and arrives from a competitor's
					// backup — and until now they were buried a screen deep in Settings,
					// where nobody looking for them would think to look. Both still live
					// there too; this is a shortcut, not a move.
					DropdownMenuItem(
						text = { Text(stringResource(id = R.string.action_import)) },
						leadingIcon = {
							Icon(
								imageVector = Icons.Default.FileDownload,
								contentDescription = null,
							)
						},
						onClick = {
							onImportClick()
							showMenu = false
						},
					)

					DropdownMenuItem(
						text = { Text(stringResource(id = R.string.action_export)) },
						leadingIcon = {
							Icon(
								imageVector = Icons.Default.FileUpload,
								contentDescription = null,
							)
						},
						onClick = {
							onExportClick()
							showMenu = false
						},
					)


					DropdownMenuItem(
						text = { Text(stringResource(id = R.string.action_settings)) },
						leadingIcon = {
							Icon(
								imageVector = Icons.Default.Settings,
								contentDescription = null,
							)
						},
						onClick = {
							onSettingsClick()
							showMenu = false
						},
					)

					DropdownMenuItem(
						text = { Text(stringResource(id = R.string.action_about)) },
						leadingIcon = {
							Icon(
								imageVector = Icons.Outlined.Info,
								contentDescription = null,
							)
						},
						onClick = {
							onAboutClick()
							showMenu = false
						},
					)

					// Contextual, destructive: only on the Log page, and only when
					// there is something to clear. Set apart by a divider and error
					// tone so it can't be mistaken for the routine actions above.
					if (showClearLog) {
						HorizontalDivider()
						DropdownMenuItem(
							text = {
								Text(
									text = stringResource(id = R.string.menu_clear_logbook),
									color = MaterialTheme.colorScheme.error,
								)
							},
							leadingIcon = {
								Icon(
									imageVector = Icons.Default.DeleteSweep,
									contentDescription = null,
									tint = MaterialTheme.colorScheme.error,
								)
							},
							enabled = clearLogEnabled,
							onClick = {
								onClearLogClick()
								showMenu = false
							},
						)
					}
				}
			}
		}
	}
}

@Composable
private fun Content(
	modifier: Modifier,
	contentPaddingValues: PaddingValues,
	pagerState: PagerState,
	externalRequests: ExternalRequests,
) {
	val stateHolder = rememberSaveableStateHolder()
	HorizontalPager(
		modifier = modifier,
		state = pagerState,
		key = { page -> page },
		beyondViewportPageCount = pagerState.pageCount,
	) { page ->
		stateHolder.SaveableStateProvider(key = page) {
			PageContainer(
				page = ScreenPages.fromOrdinal(page),
				contentPaddingValues = contentPaddingValues,
				externalRequests = externalRequests,
			)
		}
	}
}

@ExperimentalTime
@Composable
private fun PageContainer(
	page: ScreenPages,
	contentPaddingValues: PaddingValues,
	externalRequests: ExternalRequests,
) {
	when (page) {
		ScreenPages.Fasting -> {
			FastingScreen(
				contentPaddingValues = contentPaddingValues,
				externalRequests = externalRequests,
			)
		}

		ScreenPages.Log -> {
			LogScreen(contentPaddingValues)
		}

		ScreenPages.Profile -> {
			val context = LocalContext.current
			ProfileScreen(
				contentPaddingValues = contentPaddingValues,
				onShowInfoDialog = { titleRes, contentRes ->
					Utils.showInfoDialog(
						titleRes,
						contentRes,
						context
					)
				}
			)
		}
	}
}
