package com.legbehindneck.fasttrack48.screens.fasting

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Rect
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.os.Handler
import android.os.Looper
import android.text.format.DateUtils
import android.view.PixelCopy
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.legbehindneck.fasttrack48.BuildConfig
import com.legbehindneck.fasttrack48.R
import com.legbehindneck.fasttrack48.data.FastingJourney
import com.legbehindneck.fasttrack48.data.JourneyStage
import com.legbehindneck.fasttrack48.data.Stages
import com.legbehindneck.fasttrack48.data.log.FastingLogEntry
import com.legbehindneck.fasttrack48.screens.confetti.ConfettiState
import com.legbehindneck.fasttrack48.screens.confetti.confettiEffect
import com.legbehindneck.fasttrack48.ui.theme.Black900
import com.legbehindneck.fasttrack48.ui.theme.White50
import com.legbehindneck.fasttrack48.ui.theme.fastBackgroundGradient
import com.legbehindneck.fasttrack48.utils.AppDateTime
import com.legbehindneck.fasttrack48.utils.LocalDateStyle
import com.legbehindneck.fasttrack48.utils.formatDuration
import com.legbehindneck.fasttrack48.utils.rememberFitToViewportDensity
import com.legbehindneck.fasttrack48.utils.shareFastImage
import com.legbehindneck.fasttrack48.utils.shouldUse24HourFormat
import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.Instant

@Composable
fun FastingScreen(
	contentPaddingValues: PaddingValues,
	viewModel: IFastingViewModel = koinViewModel<FastingViewModel>(),
	externalRequests: ExternalRequests = ExternalRequests(),
) {
	val scope = rememberCoroutineScope()
	val confetti = remember { ConfettiState() }
	val uiState by viewModel.uiState.collectAsState()

	LaunchedEffect(Unit) {
		viewModel.onCreate()
	}

	var showStartDateTimePicker by remember { mutableStateOf(false) }
	var showEndDateTimePicker by remember { mutableStateOf(false) }
	var showEditStartPicker by remember { mutableStateOf(false) }
	// A corrected start that reaches back into the last logged fast; held here while
	// the overlap warning is up, committed only if the user confirms.
	var pendingStart by remember { mutableStateOf<Instant?>(null) }
	var showStartSheet by remember { mutableStateOf(false) }
	var showEndSheet by remember { mutableStateOf(false) }
	// Note captured while ending a fast; carried across the "stopped earlier" picker
	var endNotes by rememberSaveable { mutableStateOf("") }

	fun onShowStartFastSelector() {
		if (!uiState.isFasting) showStartSheet = true
	}

	if (showStartSheet) {
		StartFastSheet(
			onStartNow = {
				showStartSheet = false
				viewModel.startFast()
			},
			onStartEarlier = {
				showStartSheet = false
				showStartDateTimePicker = true
			},
			onDismiss = { showStartSheet = false },
		)
	}

	if (showStartDateTimePicker) {
		val dateTimePickerState = rememberDateTimePickerDialogState()
		DateTimePickerDialog(
			onDismiss = { showStartDateTimePicker = false },
			onDateTimeSelected = { selectedDateTime ->
				viewModel.startFast(selectedDateTime)
				showStartDateTimePicker = false
			},
			title = stringResource(R.string.already_started_dialog_title),
			finishButton = stringResource(id = R.string.start_fast_button),
			state = dateTimePickerState,
			// A start in the future yields a negative elapsed time; the picker's day
			// filter cannot catch a later hour *today* on its own.
			maxInstant = remember { Clock.System.now() },
		)
	}

	fun onShowEndFastConfirmation() {
		showEndSheet = true
	}

	if (showEndSheet) {
		EndFastSheet(
			notes = endNotes,
			onNotesChange = { endNotes = it },
			onEndNow = {
				showEndSheet = false
				viewModel.endFast(notes = endNotes.trim())
				endNotes = ""
				confetti.start(scope)
			},
			onEndEarlier = {
				// Keep the note; it is applied once the end time is chosen
				showEndSheet = false
				showEndDateTimePicker = true
			},
			onDismiss = {
				showEndSheet = false
				endNotes = ""
			},
		)
	}

	if (showEndDateTimePicker) {
		val dateTimePickerState = rememberDateTimePickerDialogState()
		DateTimePickerDialog(
			onDismiss = { showEndDateTimePicker = false },
			onDateTimeSelected = { selectedDateTime ->
				viewModel.endFast(selectedDateTime, endNotes.trim())
				endNotes = ""
				showEndDateTimePicker = false
				confetti.start(scope)
			},
			title = stringResource(R.string.already_stopped_dialog_title),
			finishButton = stringResource(id = R.string.end_fast_button),
			state = dateTimePickerState,
			minInstant = uiState.fastStartTime
		)
	}

	if (showEditStartPicker) {
		val dateTimePickerState = rememberDateTimePickerDialogState()
		DateTimePickerDialog(
			onDismiss = { showEditStartPicker = false },
			onDateTimeSelected = { selectedDateTime ->
				showEditStartPicker = false
				val previousEnd = uiState.previousLoggedFastEnd
				if (previousEnd != null && selectedDateTime < previousEnd) {
					pendingStart = selectedDateTime
				} else {
					viewModel.adjustFastStart(selectedDateTime)
				}
			},
			title = stringResource(R.string.edit_start_dialog_title),
			finishButton = stringResource(id = R.string.manual_add_save_button),
			state = dateTimePickerState,
			initialInstant = uiState.fastStartTime,
			maxInstant = remember { Clock.System.now() },
		)
	}

	// Soft warning, not a block: the logbook is the user's own record and they may well
	// be correcting it next. Nothing is written until they confirm.
	pendingStart?.let { candidate ->
		val previousEnd = uiState.previousLoggedFastEnd
		val dateStyle = LocalDateStyle.current
		val use24Hour = shouldUse24HourFormat(LocalContext.current)
		val tz = remember { TimeZone.currentSystemDefault() }
		AlertDialog(
			onDismissRequest = { pendingStart = null },
			title = { Text(text = stringResource(R.string.start_overlap_title)) },
			text = {
				Text(
					text = stringResource(
						R.string.start_overlap_message,
						previousEnd?.let {
							AppDateTime.formatDateTime(it.toLocalDateTime(tz), dateStyle, use24Hour)
						} ?: "",
						AppDateTime.formatDateTime(candidate.toLocalDateTime(tz), dateStyle, use24Hour),
					)
				)
			},
			confirmButton = {
				TextButton(onClick = {
					pendingStart = null
					viewModel.adjustFastStart(candidate)
				}) {
					Text(text = stringResource(R.string.start_overlap_confirm))
				}
			},
			dismissButton = {
				TextButton(onClick = { pendingStart = null }) {
					Text(text = stringResource(R.string.cancel_button))
				}
			},
		)
	}

	// Journey stage overlay, opened from the dial or the phase rows
	var selectedStage by remember { mutableStateOf<JourneyStage?>(null) }

	// One shared time format for the center timer and the phase rows:
	// days+hours ("2d 12h") vs total hours ("60h 30m"), toggled by tapping either
	var showTotalHours by rememberSaveable { mutableStateOf(false) }

	val context = LocalContext.current
	// Window-space bounds of the shareable hero (dial + rows), tracked for capture
	var heroBounds by remember { mutableStateOf<Rect?>(null) }

	// The action pill lives inside the captured hero, and a screenshot of someone's fast
	// has no business showing them a button. Hidden by alpha rather than removed: taking
	// it out of the layout would collapse the band and shift the geometry the bounds were
	// measured against, so the capture would land on a frame that no longer matches.
	var capturing by remember { mutableStateOf(false) }

	// Share request: PixelCopy the hero region straight off the window surface
	// (so it's the screen as-is, minus the status bar and other windows), then
	// fire the chooser with a rich caption.
	LaunchedEffect(externalRequests.shareRequested) {
		if (externalRequests.shareRequested) {
			@Suppress("TooGenericExceptionCaught")
			try {
				val window = (context as? Activity)?.window
				// Clamped to the window: the hero scrolls now, so its bounds can extend past
				// the surface PixelCopy reads from, and a source rect outside that surface is
				// rejected outright. Intersecting keeps the share working — it captures what
				// is actually on screen — instead of failing whenever the reader has scrolled
				// or turned their font up.
				val bounds = heroBounds?.let { hero ->
					val decor = window?.decorView ?: return@let null
					Rect(hero).takeIf { it.intersect(0, 0, decor.width, decor.height) }
				}
				if (bounds != null && window != null && bounds.width() > 0 && bounds.height() > 0) {
					val bitmap = createBitmap(bounds.width(), bounds.height())
					val ok = try {
						capturing = true
						// One frame to recompose with the pill hidden, a second to be sure
						// that frame has actually been drawn to the window surface that
						// PixelCopy reads from.
						withFrameNanos { }
						withFrameNanos { }
						suspendCancellableCoroutine { cont ->
							PixelCopy.request(
								window, bounds, bitmap,
								{ result -> cont.resume(result == PixelCopy.SUCCESS) },
								Handler(Looper.getMainLooper())
							)
						}
					} finally {
						capturing = false
					}
					if (ok) {
						shareFastImage(context, bitmap, buildShareCaption(context, uiState))
					}
				}
			} catch (e: Throwable) {
				Napier.e("Failed to share fast", e)
			}
			externalRequests.consumeShareRequest()
		}
	}

	// Handle deep link requests to show dialogs or start/stop directly
	LaunchedEffect(externalRequests.startFastRequest) {
		externalRequests.startFastRequest?.let { req ->
			if (!uiState.isFasting) {
				if (req.startNow) {
					viewModel.startFast()
				} else {
					onShowStartFastSelector()
				}
			}
			externalRequests.consumeStartFastRequest()
		}
	}
	LaunchedEffect(externalRequests.stopFastRequested) {
		if (externalRequests.stopFastRequested) {
			if (uiState.isFasting) {
				onShowEndFastConfirmation()
			}
			externalRequests.consumeStopFastRequest()
		}
	}

	DisposableEffect(Unit) {
		viewModel.setupAlerts()
		onDispose { }
	}

	val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
	LaunchedEffect(uiState.isFasting, lifecycleState) {
		// The timer displays minute granularity, so there is no reason to poll
		// faster than once a minute. Refresh immediately (e.g. on resume), then
		// wake exactly on each whole-minute boundary so the value flips on time.
		while (uiState.isFasting && lifecycleState == Lifecycle.State.RESUMED) {
			viewModel.updateUi()
			delay((60_000L - (System.currentTimeMillis() % 60_000L)).milliseconds)
		}
	}

	val configuration = LocalConfiguration.current
	val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
	val fontScale = LocalDensity.current.fontScale

	BoxWithConstraints(
		modifier = Modifier
			.fillMaxSize()
			.fastBackgroundGradient(show = uiState.showGradientBackground)
			.confettiEffect(confetti)
			.padding(contentPaddingValues)
	) {
		val isCompact = remember(maxHeight) { maxHeight < 500.dp }

		val spacing = rememberFastingSpacing(isCompact)
		val typography = rememberFastingTypography(isCompact)

		// Cap the dial against the viewport height, not just the width. The portrait cap
		// used to be 1/phi^2 (~0.382), a number chosen to stop the dial starving a
		// full-width action button seated below it. That button now sits on the arc's own
		// terminus, so the constraint the number encoded is gone and the dial takes the
		// room back.
		val dialMaxSize = if (isLandscape) {
			// Landscape has no vertical slack to give away: the left column carries the
			// title and the dial, and the dial's own square is what the band now sits
			// inside. LandscapeChrome is the honest cost of everything that is not the
			// dial — column padding (2 x large), the stage title, and a margin so a title
			// that wraps to two lines cannot push the arc off the bottom of the screen. A
			// ratio cannot express that, because it does not shrink as the screen does.
			//
			// The rest solves for the dial rather than guessing at it. The block below the
			// title is not the dial's square but the square plus its overhang, and on any
			// phone-sized dial (0.167 * D < BandHeight + gap, i.e. D < ~455dp) that block
			// measures (1 - ARC_NOTCH_FRACTION) * D + BandHeight + gap. Inverting it gives
			// the largest dial whose band still lands inside the column, by construction.
			min(
				maxWidth * 0.46f,
				(maxHeight - LandscapeChrome - BandHeight - spacing.xlarge) /
					(1f - ARC_NOTCH_FRACTION),
			)
		} else {
			// The dial is the only elastic thing in this column — everything else is text,
			// and text grows with the reader's font scale. 0.44 of the viewport is what the
			// dial may have at scale 1.0; at 1.3 the surrounding text needs a third more
			// room and the dial hands it back rather than pushing the description off the
			// bottom. Floored at the band's own minimum width, below which the arc has
			// nothing left to hold: past that the column scrolls instead of shrinking
			// further, because the reader asked for large text and should get it.
			min(maxWidth, (maxHeight * 0.44f / fontScale).coerceAtLeast(MinBandWidth))
		}

		CompositionLocalProvider(
			LocalFastingSpacing provides spacing,
			LocalFastingTypography provides typography
		) {
			if (isLandscape) {
				Row(
					modifier = Modifier
						.fillMaxSize()
						.padding(spacing.large),
					verticalAlignment = Alignment.Top
				) {
					val headingScroll = rememberScrollState()
					CompositionLocalProvider(
						LocalDensity provides rememberFitToViewportDensity(headingScroll)
					) {
						FastHeadingContent(
							uiState = uiState,
							dialMaxSize = dialMaxSize,
							showTotalHours = showTotalHours,
							onToggleTimeFormat = { showTotalHours = !showTotalHours },
							onStageSelected = { selectedStage = it },
							onEditStart = { showEditStartPicker = true },
							onShowEndFastConfirmation = ::onShowEndFastConfirmation,
							onShowStartFastSelector = ::onShowStartFastSelector,
							viewModel = viewModel,
							capturing = capturing,
							modifier = Modifier
								.weight(1f)
								.fillMaxHeight()
								// Same escape hatch as portrait: LandscapeChrome budgets one line
								// of stage title at scale 1.0, and a reader at 2.0 has two. The
								// column gives type back first and scrolls only past the floor,
								// rather than clipping the arc.
								.verticalScroll(headingScroll)
								.padding(end = spacing.medium)
								.onGloballyPositioned { heroBounds = it.boundsInWindow().toAndroidRect() }
						)
					}

					Spacer(modifier = Modifier.size(height = spacing.large, width = 1.dp))

					FastDetailsContent(
						uiState = uiState,
						showTotalHours = showTotalHours,
						onToggleTimeFormat = { showTotalHours = !showTotalHours },
						onStageSelected = { selectedStage = it },
						modifier = Modifier
							.weight(1f)
							.fillMaxHeight()
							.padding(start = spacing.medium)
					)
				}
			} else {
				// Scrolls, and centres itself while it still fits.
				//
				// It used to be an unweighted child between two weighted spacers, which in a
				// Column means it measures against the *whole* viewport and anything past
				// the bottom is simply cut — which is what the bottom of the status line
				// was, for every reader whose font scale is above 1.0. A cluster of text
				// cannot be seated by ratio against a height it may exceed.
				//
				// heightIn(min) inside the scroll is what keeps both behaviours in one
				// layout: shorter than the viewport, the column is still viewport-tall and
				// Arrangement.Center seats it; taller, the column grows and the reader
				// scrolls. Nothing is ever clipped, at any font scale, in any locale. The
				// golden seating is the one casualty — slack can only be split by ratio if
				// there is slack, and under an accessibility font size there is none.
				//
				// Scrolling is the last resort, not the first: the block gives type back
				// until it fits, and only scrolls once it has nothing left to give.
				val heroScroll = rememberScrollState()
				CompositionLocalProvider(
					LocalDensity provides rememberFitToViewportDensity(heroScroll)
				) {
					Column(
						modifier = Modifier
							.fillMaxWidth()
							.verticalScroll(heroScroll)
							.padding(spacing.large)
							.heightIn(min = maxHeight - spacing.large * 2),
						horizontalAlignment = Alignment.CenterHorizontally,
						verticalArrangement = Arrangement.Center,
					) {
						FastHero(
							uiState = uiState,
							dialMaxSize = dialMaxSize,
							showTotalHours = showTotalHours,
							onToggleTimeFormat = { showTotalHours = !showTotalHours },
							onStageSelected = { selectedStage = it },
							onEditStart = { showEditStartPicker = true },
							onShowEndFastConfirmation = ::onShowEndFastConfirmation,
							onShowStartFastSelector = ::onShowStartFastSelector,
							viewModel = viewModel,
							capturing = capturing,
							modifier = Modifier
								.fillMaxWidth()
								.onGloballyPositioned { heroBounds = it.boundsInWindow().toAndroidRect() }
						)
					}
				}
			}
		}
	}

	selectedStage?.let { stage ->
		JourneyStageSheet(
			stage = stage,
			onDismiss = { selectedStage = null }
		)
	}
}

@Composable
private fun FastHeadingContent(
	uiState: IFastingViewModel.FastingUiState,
	dialMaxSize: Dp,
	showTotalHours: Boolean,
	onToggleTimeFormat: () -> Unit,
	onStageSelected: (JourneyStage) -> Unit,
	onEditStart: () -> Unit,
	onShowEndFastConfirmation: () -> Unit,
	onShowStartFastSelector: () -> Unit,
	viewModel: IFastingViewModel,
	capturing: Boolean,
	modifier: Modifier = Modifier
) {
	val spacing = fastingSpacing()
	val typography = fastingTypography()

	// Precise elapsed time; uiState.elapsedHours is truncated to whole hours.
	// After a fast has ended the dial rests at zero: muted milestones, no
	// heartbeat — the pulse is a reward that belongs to fasting alone.
	val elapsedHoursPrecise = if (uiState.isFasting) {
		uiState.elapsedTime?.toDouble(DurationUnit.HOURS) ?: uiState.elapsedHours
	} else {
		0.0
	}

	Column(
		modifier = modifier,
		horizontalAlignment = Alignment.CenterHorizontally
	) {
		// Stage Title. Auto-sized because the corridor is fixed — the column's own width —
		// while the string is not: "Optimal Autophagy" is 17 characters and
		// "Оптимальна автофагія" is 20. This is the width-bound case, where the only
		// alternative to shrinking a point or two is an ellipsis, so shrinking wins.
		val titleStyle = typography.stageTitle()
		BasicText(
			text = uiState.stageTitle,
			maxLines = 2,
			autoSize = TextAutoSize.StepBased(
				minFontSize = 16.sp,
				maxFontSize = titleStyle.fontSize.takeIf { it != TextUnit.Unspecified } ?: 28.sp,
				stepSize = 1.sp,
			),
			style = titleStyle.merge(
				TextStyle(
					color = MaterialTheme.colorScheme.onBackground,
					fontWeight = FontWeight.Bold,
					textAlign = TextAlign.Center,
				)
			),
			modifier = Modifier
				.fillMaxWidth()
				.padding(bottom = spacing.small)
		)

		// Dial and band share one square. The arc's 270 degrees leave a notch facing
		// down, and the notch is real estate: the two blocks drop into it instead of
		// queueing below it. Nothing about the geometry is guessed — see the constants.
		BoxWithConstraints(
			modifier = Modifier
				.widthIn(max = dialMaxSize)
				.fillMaxWidth()
				.padding(top = spacing.medium),
		) {
			val dialSize = maxWidth
			// Clear height under the arc's rounded caps, and whatever the band still needs
			// beyond it. On a full-size dial the notch swallows the band whole and the
			// overhang is zero; on a small one the box grows by just the difference.
			// Clearance floor between the arc's rounded caps and the band's top edge. On the
			// portrait dial the notch runs only ~6dp deeper than the band is tall, which put
			// the Start label within a millimetre of the stroke. spacing.xlarge (21dp =
			// 3.33mm; 13dp when compact) is the rung that reads as deliberate separation.
			// A floor, not a fixed value: a dial whose notch swallows band + gap outright
			// simply gets a deeper one.
			val overhang = (BandHeight + spacing.xlarge - dialSize * ARC_NOTCH_FRACTION)
				.coerceAtLeast(0.dp)
			// Reserves the box's height so the band is inside the layout, not floating over it.
			Spacer(modifier = Modifier.fillMaxWidth().height(dialSize + overhang))

			TimeLine(
				elapsedHours = elapsedHoursPrecise,
				modifier = Modifier
					.align(Alignment.TopCenter)
					.fillMaxWidth(),
				onStageClick = onStageSelected
			) {
				// Timer + Energy Mode, centered inside the ring — only while a fast
				// is running. Tapping the timer toggles days+hours vs total hours.
				if (uiState.isFasting) {
					Column(
						horizontalAlignment = Alignment.CenterHorizontally,
						modifier = Modifier.clickable(
							interactionSource = remember { MutableInteractionSource() },
							indication = null
						) { onToggleTimeFormat() }
					) {
						val timerText = formatDuration(
							uiState.elapsedTime ?: uiState.elapsedHours.hours,
							showTotalHours
						)
						// Auto-size so the value always fits inside the ring — the
						// Compose-native answer (the View-system autoSizeTextType
						// APIs don't apply to Compose). One line, shrinks to fit.
						BasicText(
							text = timerText,
							maxLines = 1,
							softWrap = false,
							autoSize = TextAutoSize.StepBased(
								minFontSize = 20.sp,
								maxFontSize = 60.sp,
								stepSize = 1.sp,
							),
							style = typography.timerText().merge(
								TextStyle(
									color = MaterialTheme.colorScheme.onBackground,
									fontWeight = FontWeight.Bold,
									textAlign = TextAlign.Center,
								)
							),
							modifier = Modifier.fillMaxWidth(),
						)
						BasicText(
							text = uiState.energyMode,
							maxLines = 2,
							autoSize = TextAutoSize.StepBased(
								minFontSize = 9.sp,
								maxFontSize = 15.sp,
								stepSize = 1.sp,
							),
							style = typography.energyMode().merge(
								TextStyle(
									color = MaterialTheme.colorScheme.onBackground,
									textAlign = TextAlign.Center,
								)
							),
							modifier = Modifier
								.fillMaxWidth()
								.padding(top = spacing.small)
						)
					}
				} else {
					// Idle: the ring is empty and the entrance belongs in the middle of
					// it. This slot is centred on the dial's true centre, which the
					// enclosing box is not — that one is dialSize + overhang tall, so its
					// own centre sits overhang/2 low. TimeLine's tap handler rejects
					// anything off the band radius, so nothing here competes with it.
					StartOrb(
						dialSize = dialSize,
						onClick = onShowStartFastSelector,
						capturing = capturing,
					)
				}
			}

			FastArcBand(
				uiState = uiState,
				// The notch-aligned inset, but never so deep that the two blocks are
				// squeezed below the width they actually need: on a landscape dial
				// (~315dp) the pure fraction leaves End Fast ~88dp and it wraps to
				// "End / Fast", which breaks the equal-mass balance with the start
				// datum. Below MinBandWidth the arms step outboard instead of the
				// content folding — a few dp off the cap costs less than a wrap.
				armInset = (dialSize * ARC_ARM_INSET)
					.coerceAtMost((dialSize - MinBandWidth) / 2)
					.coerceAtLeast(0.dp),
				onEditStart = onEditStart,
				onShowEndFastConfirmation = onShowEndFastConfirmation,
				onShowStartFastSelector = onShowStartFastSelector,
				capturing = capturing,
				modifier = Modifier.align(Alignment.BottomCenter),
			)

			if (BuildConfig.DEBUG && uiState.isFasting) {
				// Inside the ring, below the energy line: the dial's interior is the only
				// spot in the composition that no shipped element occupies. Gated on
				// isFasting — it advances a clock that is not running otherwise, and while
				// idle the lone Start Fast pill spans the band's centre.
				FilledTonalIconButton(
					onClick = { viewModel.debugIncreaseFastingTimeByOneHour() },
					modifier = Modifier
						.align(Alignment.Center)
						.offset(y = dialSize * 0.22f)
						.alpha(if (capturing) 0f else 1f)
				) {
					Icon(
						imageVector = Icons.Default.Add,
						contentDescription = stringResource(id = R.string.debug_add_hour_button)
					)
				}
			}
			}
	}
}

// Shared height of the two blocks that flank the arc's notch — the start datum and the
// action pill. A Fibonacci rung, comfortably over the 48dp touch minimum, and where the
// two-line datum lands on its own. Equal footprint is the point: they balance each other.
internal val BandHeight = 55.dp

// Where the arc actually ends, as fractions of the dial's side. Both fall out of
// TimeLine's own numbers: radius r = 0.5 - 1.18 * STROKE_FRACTION = 0.4283, endpoints at
// 135 and 45 degrees, round caps adding STROKE_FRACTION / 2 beyond them.
//   endpoint y = 0.5 + r * sin(45) + cap = 0.833  ->  0.167 of the side is clear below it
//   endpoint x = 0.5 - r * cos(45) - cap = 0.167  ->  the arms are that far in from the edge
internal const val ARC_NOTCH_FRACTION = 0.167f
// The arms sit a hair outboard of 0.167: at the true inset the two blocks would need more
// width than the notch has, and Ukrainian would wrap up into the arc. 1/phi^4 is the same
// constant TimeLine derives its stroke from, and it is the largest inset that still fits
// the longest locale.
internal const val ARC_ARM_INSET = 0.146f
// Floor for the band's inner width: the start datum at its longest ("Today, 10:48 AM",
// ~111dp with padding) plus the End Fast outline at its natural width (~105dp) plus a
// gutter. 233 is the Fibonacci rung that clears it.
internal val MinBandWidth = 233.dp
// Everything the landscape left column spends above and around the dial: the column's own
// 2 x large padding, one line of stage title, and enough margin that a title wrapping to
// two lines cannot push the arc off the bottom.
internal val LandscapeChrome = 96.dp

/**
 * Portrait hero: stage title, dial (with center timer + energy), the arc band, phase rows
 * and the status line — one cohesive cluster that also doubles as the shareable image.
 * The action pill sits inside it now, and is hidden by [capturing] while a share is taken.
 */
@Composable
private fun FastHero(
	uiState: IFastingViewModel.FastingUiState,
	dialMaxSize: Dp,
	showTotalHours: Boolean,
	onToggleTimeFormat: () -> Unit,
	onStageSelected: (JourneyStage) -> Unit,
	onEditStart: () -> Unit,
	onShowEndFastConfirmation: () -> Unit,
	onShowStartFastSelector: () -> Unit,
	viewModel: IFastingViewModel,
	capturing: Boolean,
	modifier: Modifier = Modifier,
) {
	val spacing = fastingSpacing()
	val typography = fastingTypography()
	val elapsed = uiState.elapsedTime ?: uiState.elapsedHours.takeIf { it > 0 }?.hours

	Column(
		modifier = modifier,
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		FastHeadingContent(
			uiState = uiState,
			dialMaxSize = dialMaxSize,
			showTotalHours = showTotalHours,
			onToggleTimeFormat = onToggleTimeFormat,
			onStageSelected = onStageSelected,
			onEditStart = onEditStart,
			onShowEndFastConfirmation = onShowEndFastConfirmation,
			onShowStartFastSelector = onShowStartFastSelector,
			viewModel = viewModel,
			capturing = capturing,
			modifier = Modifier.fillMaxWidth()
		)

		// Group separation, one rung above the 2 x small (10dp) that sits between two phase
		// rows. Without it the band-to-rows gap was *smaller* than the row-to-row gap and
		// the rows read as part of the band. It lives here rather than in PhaseRows because
		// in landscape the rows sit in the opposite column with no band above them.
		Spacer(modifier = Modifier.height(spacing.xlarge))

		PhaseRows(uiState, showTotalHours, onToggleTimeFormat, onStageSelected, elapsed)

		// Prose, and the one block here that is bound by height rather than width. It is
		// deliberately *not* auto-sized: shrinking body text is how you take back the font
		// size a reader went into system settings to ask for. The column scrolls instead,
		// so the whole sentence survives at whatever size they chose.
		Text(
			text = rememberFastStatusText(uiState, showTotalHours),
			style = typography.stageDescription(),
			color = MaterialTheme.colorScheme.onBackground,
			textAlign = TextAlign.Center,
			modifier = Modifier
				.fillMaxWidth()
				// The same rung as above: prose in a smaller, lighter face needs at least as
				// much air as the data rows to read as its own block, and 13dp left it only
				// 3dp clear of the intra-row gap.
				.padding(top = spacing.xlarge, start = spacing.medium, end = spacing.medium)
		)
	}
}

/** Rounded window-space rectangle for PixelCopy capture. */
private fun androidx.compose.ui.geometry.Rect.toAndroidRect(): Rect =
	Rect(left.roundToInt(), top.roundToInt(), right.roundToInt(), bottom.roundToInt())

/**
 * A multi-line caption for a shared fast: the lead sentence plus a line for each
 * milestone the body has actually reached (positive count-up), reusing the
 * already-localized phase labels.
 */
private fun buildShareCaption(
	context: android.content.Context,
	uiState: IFastingViewModel.FastingUiState,
): String {
	val elapsed = uiState.elapsedTime ?: return context.getString(R.string.app_name)
	val durationText = formatDuration(context, elapsed)
	val curPhase = Stages.getCurrentPhase(elapsed)
	val energyStr = if (curPhase.fatBurning) {
		context.getString(R.string.fasting_energy_mode_fat)
	} else {
		context.getString(R.string.fasting_energy_mode_glucose)
	}
	val lead = if (uiState.isFasting) {
		context.getString(R.string.share_text_fasting, durationText, energyStr)
	} else {
		context.getString(R.string.share_text_finished, durationText)
	}

	val active = IFastingViewModel.StageState.StartedActive
	val lines = buildList {
		if (uiState.fatBurnStageState == active) {
			add("🔥 " + context.getString(R.string.fast_fat_burn_label) + " " + uiState.fatBurnTime)
		}
		if (uiState.ketosisStageState == active) {
			add("💎 " + context.getString(R.string.fast_ketosis_label) + " " + uiState.ketosisTime)
		}
		if (uiState.autophagyStageState == active) {
			add("♻️ " + context.getString(R.string.fast_autophagy_label) + " " + uiState.autophagyTime)
		}
	}
	return (listOf(lead) + lines).joinToString("\n")
}

/**
 * Landscape body: phase rows plus a scroll-safe description that fills the column.
 * The start datum and the action live with the dial in the other column, on the arc.
 */
@Composable
private fun FastDetailsContent(
	uiState: IFastingViewModel.FastingUiState,
	showTotalHours: Boolean,
	onToggleTimeFormat: () -> Unit,
	onStageSelected: (JourneyStage) -> Unit,
	modifier: Modifier = Modifier
) {
	val spacing = fastingSpacing()
	val typography = fastingTypography()
	val elapsed = uiState.elapsedTime ?: uiState.elapsedHours.takeIf { it > 0 }?.hours

	Column(
		modifier = modifier,
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		PhaseRows(uiState, showTotalHours, onToggleTimeFormat, onStageSelected, elapsed)

		val description = rememberFastStatusText(uiState, showTotalHours)
		val descriptionScroll = rememberScrollState()

		Box(
			modifier = Modifier
				.fillMaxWidth()
				.weight(1f)
				.verticalScroll(descriptionScroll),
			contentAlignment = Alignment.Center
		) {
			// Keyed on the text itself, so a shorter stage description undoes the shrink a
			// longer one earned instead of inheriting it.
			CompositionLocalProvider(
				LocalDensity provides rememberFitToViewportDensity(descriptionScroll, description)
			) {
				Text(
					text = description,
					style = typography.stageDescription(),
					color = MaterialTheme.colorScheme.onBackground,
					textAlign = TextAlign.Center,
					modifier = Modifier
						.fillMaxWidth()
						.padding(vertical = spacing.medium)
				)
			}
		}
	}
}

/**
 * Fat Burn / Ketosis / Autophagy rows. Only while fasting; tap the label to open
 * its journey stage, tap the time to switch formats everywhere. In auto mode a
 * row appears only once its phase has begun (positive count-up); otherwise it
 * follows the per-phase Settings toggle.
 */
@Composable
private fun PhaseRows(
	uiState: IFastingViewModel.FastingUiState,
	showTotalHours: Boolean,
	onToggleTimeFormat: () -> Unit,
	onStageSelected: (JourneyStage) -> Unit,
	elapsed: Duration?,
) {
	if (!uiState.isFasting) return

	val spacing = fastingSpacing()
	val typography = fastingTypography()

	fun visible(show: Boolean, startHours: Int): Boolean =
		if (uiState.phaseAutoMode) {
			elapsed != null && elapsed >= startHours.hours
		} else {
			show
		}

	// label, phase start, index into the journey. Built as data rather than three copies of
	// the same call because the block is now sized as a block: the type size depends on
	// every row that is actually showing, so the set has to exist before any row composes.
	val rows = buildList {
		if (visible(uiState.showFatBurn, Stages.PHASE_FAT_BURN.hours)) {
			add(Triple(R.string.fast_fat_burn_label, Stages.PHASE_FAT_BURN.hours, 4))
		}
		if (visible(uiState.showKetosis, Stages.PHASE_KETOSIS.hours)) {
			add(Triple(R.string.fast_ketosis_label, Stages.PHASE_KETOSIS.hours, 5))
		}
		if (visible(uiState.showAutophagy, Stages.PHASE_AUTOPHAGY.hours)) {
			add(Triple(R.string.fast_autophagy_label, Stages.PHASE_AUTOPHAGY.hours, 6))
		}
	}
	if (rows.isEmpty()) return

	BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
		// What one side of the mirror actually gets: the row's own horizontal padding at
		// both edges, plus the centre gutter, removed before the halving.
		val columnWidth = (maxWidth - spacing.medium * 3) / 2

		val labelStyle = typography.phaseLabel()
		val labels = rows.map { stringResource(id = it.first) }
		val values = rows.map { phaseTimeText(elapsed, it.second, showTotalHours) }
		// Labels and values measured together: they are one typographic pair, and a value
		// set a size larger than its own label would read as the emphasis it is not.
		val fontSize = rememberSharedAutoSize(
			texts = labels + values,
			style = labelStyle,
			maxWidth = columnWidth,
			min = 11.sp,
			max = labelStyle.fontSize.takeIf { it != TextUnit.Unspecified } ?: 16.sp,
		)

		Column(modifier = Modifier.fillMaxWidth()) {
			rows.forEach { (labelRes, phaseStartHours, stageIndex) ->
				StageInfo(
					labelRes = labelRes,
					phaseStartHours = phaseStartHours,
					elapsed = elapsed,
					showTotalHours = showTotalHours,
					fontSize = fontSize,
					onToggleFormat = onToggleTimeFormat,
					onClick = { onStageSelected(FastingJourney.stages[stageIndex]) }
				)
			}
		}
	}
}

/**
 * Wall-clock now, re-read once a minute.
 *
 * Everything on the idle screen that ages — the recognition line, the band's "ended N days
 * ago" — reads from this one value, so the two can never disagree by a tick and only one
 * coroutine is alive to keep them current.
 */
@Composable
private fun rememberMinuteTick(): Instant {
	var now by remember { mutableStateOf(Clock.System.now()) }
	LaunchedEffect(Unit) {
		while (true) {
			now = Clock.System.now()
			delay(60.seconds)
		}
	}
	return now
}

/**
 * While fasting: the stage description. In the first hour after a fast: a moment of
 * recognition.
 *
 * Beyond that hour it says nothing, because the band under the dial now states the same
 * fact precisely — "3 days, 4 hours fast / ended 8 days ago" — and a second, vaguer copy
 * of it directly below was the same sentence twice. The first hour is kept: that is a
 * reward beat, not a duplicate. With no logbook to draw the band from, the old line still
 * stands in as the only thing the screen knows.
 */
@Composable
private fun rememberFastStatusText(
	uiState: IFastingViewModel.FastingUiState,
	showTotalHours: Boolean,
): String {
	if (uiState.isFasting) return uiState.stageDescription

	val now = rememberMinuteTick()

	return uiState.lastFastEndTime?.let { lastEnd ->
		val since = (now - lastEnd).coerceAtLeast(Duration.ZERO)
		when {
			since < 1.hours -> stringResource(R.string.just_finished_fast)
			uiState.lastLoggedFast != null -> ""
			else -> stringResource(
				R.string.time_since_last_fast,
				formatDuration(since, showTotalHours)
			)
		}
	} ?: ""
}

/**
 * The shoulders of the arc: what opened the journey on the left, what closes it on the
 * right, in the band directly beneath the dial.
 *
 * [TimeLine] opens its arc at 135 degrees and closes it at 45 — the lower left and the
 * lower right — leaving a 90 degree notch facing down. Those are real screen positions,
 * and this row seats content on both of them. Proximity is the only Gestalt cue that
 * binds a label to its referent, so the start datum sits under the origin it names; and
 * an open figure holds attention until something resolves it, so the terminal action sits
 * on the terminus and closes the circuit.
 *
 * The action walks the arc across the cycle. Idle, the gold pill sits at the **origin**,
 * where the journey is entered. Once fasting, that slot holds the start datum the action
 * left behind, and the violet pill appears at the **terminus**. One rule, both states.
 *
 * Datum and action get equal mass but deliberately unequal weight: identical footprint,
 * but the pill is a saturated fill and the datum is unfilled text on the gradient. Their
 * colours are the arc's own two ends — stage 0's dawn gold and stage 9's deep violet. The
 * screen's inherited [Purple500] is stock Material baseline and belongs to no palette
 * this app designed.
 */
@Composable
private fun FastArcBand(
	uiState: IFastingViewModel.FastingUiState,
	armInset: Dp,
	onEditStart: () -> Unit,
	onShowEndFastConfirmation: () -> Unit,
	onShowStartFastSelector: () -> Unit,
	capturing: Boolean,
	modifier: Modifier = Modifier,
) {
	Box(
		// [armInset] pulls both blocks in to where the arc's caps actually are, so each
		// one sits under its own endpoint rather than under the corner of the dial's
		// bounding square. The parent seats this at the bottom of that square, inside
		// the notch — which is why there is no top padding here any more.
		modifier = modifier
			.fillMaxWidth()
			.padding(horizontal = armInset),
	) {
		if (uiState.isFasting) {
			// Two halves, not SpaceBetween. Unconstrained children measure in order and the
			// datum measures first, so it took whatever "Yesterday, 22:15" wanted and left
			// the action the remainder — which in Spanish is narrower than the single word
			// "Terminar", so Compose broke the word itself. Weighted halves give the
			// terminal action a guaranteed 50% of the band, and mirror the same spine the
			// phase rows below are built on.
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically,
			) {
				// Origin: the datum the action left behind when it moved to the centre.
				FastStartBlock(
					startTime = uiState.fastStartTime,
					onEditStart = onEditStart,
					modifier = Modifier.weight(1f),
				)

				// Terminus. Nothing closes a journey that has not begun.
				Box(
					modifier = Modifier.weight(1f),
					contentAlignment = Alignment.CenterEnd,
				) {
					EndFastAction(
						onClick = onShowEndFastConfirmation,
						capturing = capturing,
					)
				}
			}
		} else {
			// The band's whole width, centred: this is a summary of the cycle, not a
			// datum bound to either shoulder of the arc.
			LastFastSummary(
				entry = uiState.lastLoggedFast,
				modifier = Modifier.align(Alignment.Center),
			)
		}
	}
}

/**
 * What the last fast came to, in the space the Start button used to occupy.
 *
 * The band is empty for as long as nobody is fasting, which is exactly when the screen has
 * the least to say. A finished fast is the one thing it can say that is both true and
 * earned, so it says it: how long that fast ran, and how long ago it ended.
 *
 * Both halves are formatted by the platform rather than by nine hand-written plural sets.
 * ICU's MeasureFormat is what gets `3 дні 4 години` right in Ukrainian (one/few/many) and
 * `3天4小时` in Chinese from the same two numbers, and DateUtils resolves the relative span
 * to "Yesterday" at the short end, where "0 days ago" would be wrong.
 *
 * An empty logbook renders nothing — the band keeps its reserved height either way, so
 * starting a fast swaps the content without moving the dial above it.
 */
@Composable
private fun LastFastSummary(
	entry: FastingLogEntry?,
	modifier: Modifier = Modifier,
) {
	if (entry == null) return

	val spacing = fastingSpacing()
	val now = rememberMinuteTick()

	val length = remember(entry) {
		val days = entry.length.inWholeDays
		val hours = entry.length.inWholeHours % 24
		val measures = if (days == 0L && hours == 0L) {
			// Under an hour the coarse unit says "0 hours fast", which reads as a
			// failure rather than as a short fast. Floored at one minute so an entry
			// logged seconds after it started still names a real quantity.
			listOf(Measure(entry.length.inWholeMinutes.coerceAtLeast(1), MeasureUnit.MINUTE))
		} else buildList {
			if (days > 0) add(Measure(days, MeasureUnit.DAY))
			// Hours are dropped only when they are zero and days already carry the value.
			if (hours > 0) add(Measure(hours, MeasureUnit.HOUR))
		}
		MeasureFormat
			.getInstance(Locale.getDefault(), MeasureFormat.FormatWidth.WIDE)
			.formatMeasures(*measures.toTypedArray())
	}

	val ended = remember(entry, now) {
		val endMillis = entry.start
			.toInstant(TimeZone.currentSystemDefault())
			.plus(entry.length)
			.toEpochMilliseconds()
		DateUtils.getRelativeTimeSpanString(
			endMillis,
			now.toEpochMilliseconds(),
			DateUtils.DAY_IN_MILLIS,
		).toString()
	}

	Column(
		modifier = modifier
			.heightIn(min = BandHeight)
			.padding(vertical = spacing.small),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.Center,
	) {
		Text(
			text = stringResource(id = R.string.last_fast_length, length),
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onBackground,
			textAlign = TextAlign.Center,
		)
		Text(
			text = stringResource(id = R.string.last_fast_ended, ended),
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
			textAlign = TextAlign.Center,
		)
	}
}

/**
 * The one destructive act the app permits, on the shoulder of the arc it closes.
 *
 * Bare text in the arc's closing violet: no fill, no border, nothing but the check glyph
 * and the label. A mis-tap costs a 48-hour fast, and a border drawn around only one of the
 * band's two blocks gave that side visual mass the start datum facing it does not have —
 * the asymmetry read as an error rather than as emphasis. Removing it leaves the violet,
 * at full chroma against a neutral background, as the only signal. A weaker affordance,
 * accepted deliberately: the control is found once per fast, it is also reachable from the
 * overflow menu, and the quiet is worth more than the half-second.
 *
 * Its opposite number, Start Fast, is no longer a sibling here — it is the orb in the
 * dial's centre, which is why this one no longer needs a width floor to balance it.
 *
 * [capturing] hides it for a shared image: a screenshot of someone's fast has no business
 * showing them a button. Alpha rather than removal, so the layout the share bounds were
 * measured against does not move underneath the capture.
 */
@Composable
private fun EndFastAction(
	onClick: () -> Unit,
	capturing: Boolean,
) {
	val spacing = fastingSpacing()
	val accent = journeyStageColor(9)

	TextButton(
		onClick = onClick,
		shape = CircleShape,
		colors = ButtonDefaults.textButtonColors(
			containerColor = Color.Transparent,
			contentColor = accent,
		),
		contentPadding = PaddingValues(
			horizontal = spacing.medium,
			vertical = spacing.small,
		),
		modifier = Modifier
			.heightIn(min = BandHeight)
			.alpha(if (capturing) 0f else 1f),
	) {
		Icon(
			Icons.Default.Check,
			contentDescription = null,
			modifier = Modifier.size(spacing.iconSize),
		)
		Spacer(modifier = Modifier.width(spacing.small))
		BasicText(
			text = stringResource(id = R.string.end_fast_button),
			// The escape valve for long locales: Ukrainian and Dutch wrap rather than
			// pushing the band past the dial's width or ellipsizing the action. Auto-sizing
			// is the second valve — with half the band guaranteed, two lines at 10sp hold
			// every translation of two words, so the break lands between them.
			maxLines = 2,
			autoSize = TextAutoSize.StepBased(
				minFontSize = 10.sp,
				maxFontSize = 14.sp,
				stepSize = 1.sp,
			),
			style = MaterialTheme.typography.titleSmall.merge(
				TextStyle(
					color = accent,
					textAlign = TextAlign.Center,
				)
			),
		)
	}
}

/**
 * Black or white label for [container], whichever wins on contrast.
 *
 * 0.179 is the W3C relative luminance at which black and white text tie; the naive 0.5
 * midpoint picks white on the dark theme's #A98BFF and lands at 2.8:1, well under AA.
 */
@Composable
internal fun contentColorOn(container: Color): Color =
	if (container.luminance() > 0.179f) Black900 else White50

/**
 * The right-hand half of a phase row: time since the phase opened, or time until it does.
 *
 * Lives outside [StageInfo] because [PhaseRows] has to know these strings *before* the rows
 * compose — they are half of what the shared type size is measured against — and a second
 * copy of the sign rule would be a second thing to keep correct.
 */
@Composable
private fun phaseTimeText(
	elapsed: Duration?,
	phaseStartHours: Int,
	showTotalHours: Boolean,
): String {
	val delta = elapsed?.minus(phaseStartHours.hours) ?: return "—"
	return if (delta >= Duration.ZERO) {
		formatDuration(delta, showTotalHours)
	} else {
		stringResource(R.string.phase_time_until, formatDuration(-delta, showTotalHours))
	}
}

@Composable
private fun StageInfo(
	labelRes: Int,
	phaseStartHours: Int,
	elapsed: Duration?,
	showTotalHours: Boolean,
	// One size for every label and every value in the block, measured by [PhaseRows] against
	// the longest of them. Per-composable auto-sizing would settle each row independently
	// and leave "Cetosis" a third larger than "Quema de Grasa" beside it, spending the
	// mirrored alignment that is the whole point of the block to save a point of type.
	fontSize: TextUnit,
	onToggleFormat: () -> Unit,
	onClick: () -> Unit,
) {
	val spacing = fastingSpacing()
	val typography = fastingTypography()

	val delta = elapsed?.minus(phaseStartHours.hours)
	val timeText = phaseTimeText(elapsed, phaseStartHours, showTotalHours)
	val timeColor: Color = when {
		// Underway: alive, affirming green — the dial's own "burn begins" hue,
		// so it tracks the theme (light/dark) instead of a fixed color.
		delta == null -> MaterialTheme.colorScheme.onBackground
		delta >= Duration.ZERO -> journeyStageColor(4)
		// Ahead: calm anticipation, never red — an upcoming phase is not a failure
		else -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
	}

	// Label right-aligned into the left half, value left-aligned into the right half, with
	// one small gutter between them. The old row was SpaceBetween across the full width,
	// which parked "Fat Burn" and "1d 15h" ~185dp apart: proximity is the only thing that
	// binds a label to its value, and at that distance the binding was gone — the eye had
	// to traverse, and on three rows it traversed three times.
	//
	// The gutter sits on the screen's centre line on purpose, not on a golden line. Title,
	// dial, timer and description are all centred here; that axis is the composition's
	// spine. A phi-split gutter would be a second vertical axis competing with it, and two
	// axes read as a mistake however well-derived the second one is. Mirrored about the
	// spine, the three rows also line up as a block: every label ends on the same x, every
	// value begins on the same x.
	//
	// The colon is gone. It existed to carry a relationship across that 185dp gap; the
	// alignment now carries it, and punctuation that duplicates alignment is noise.
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(12.dp))
			.clickable(onClick = onClick)
			.padding(horizontal = spacing.medium, vertical = spacing.small),
		verticalAlignment = Alignment.CenterVertically
	) {
		Text(
			text = stringResource(id = labelRes),
			style = typography.phaseLabel(),
			fontSize = fontSize,
			color = phaseTextColor(),
			textAlign = TextAlign.End,
			// No ellipsis: the size was chosen so every label in the block fits this half.
			maxLines = 1,
			softWrap = false,
			modifier = Modifier.weight(1f)
		)
		Spacer(modifier = Modifier.width(spacing.medium))
		Text(
			text = timeText,
			style = typography.phaseTime(),
			fontSize = fontSize,
			color = timeColor,
			textAlign = TextAlign.Start,
			maxLines = 1,
			softWrap = false,
			overflow = TextOverflow.Visible,
			modifier = Modifier
				.weight(1f)
				.clickable(
					interactionSource = remember { MutableInteractionSource() },
					indication = null
				) { onToggleFormat() }
		)
	}
}

/**
 * Confirmation for starting a fast — a calm bottom sheet replacing the old
 * system alert. "Start now" is the primary action; "I started earlier" opens
 * the date/time picker. Dismiss by swipe or scrim.
 */
@Composable
private fun StartFastSheet(
	onStartNow: () -> Unit,
	onStartEarlier: () -> Unit,
	onDismiss: () -> Unit,
) {
	ModalBottomSheet(
		onDismissRequest = onDismiss,
		sheetState = rememberModalBottomSheetState(),
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(start = 21.dp, end = 21.dp, bottom = 34.dp),
			horizontalAlignment = Alignment.CenterHorizontally,
		) {
			Text(
				text = stringResource(R.string.confirm_start_fast_title),
				style = MaterialTheme.typography.headlineSmall,
				fontWeight = FontWeight.Bold,
			)
			Spacer(modifier = Modifier.size(21.dp))
			Button(
				onClick = onStartNow,
				modifier = Modifier
					.fillMaxWidth()
					.heightIn(min = 55.dp),
			) {
				Icon(
					painter = painterResource(id = R.drawable.ic_start_fast),
					contentDescription = null,
					modifier = Modifier.padding(end = 8.dp),
				)
				Text(
					text = stringResource(R.string.sheet_start_now),
					style = MaterialTheme.typography.titleMedium,
				)
			}
			Spacer(modifier = Modifier.size(8.dp))
			TextButton(onClick = onStartEarlier, modifier = Modifier.fillMaxWidth()) {
				Text(stringResource(R.string.sheet_start_earlier))
			}
		}
	}
}

/**
 * Confirmation for ending a fast. Carries an optional Notes field saved to the
 * logbook. "End now" ends at the current time; "I stopped earlier" opens the
 * picker (the typed note is preserved and applied afterward).
 */
@Composable
private fun EndFastSheet(
	notes: String,
	onNotesChange: (String) -> Unit,
	onEndNow: () -> Unit,
	onEndEarlier: () -> Unit,
	onDismiss: () -> Unit,
) {
	ModalBottomSheet(
		onDismissRequest = onDismiss,
		sheetState = rememberModalBottomSheetState(),
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.imePadding()
				.padding(start = 21.dp, end = 21.dp, bottom = 34.dp),
			horizontalAlignment = Alignment.CenterHorizontally,
		) {
			Text(
				text = stringResource(R.string.confirm_end_fast_title),
				style = MaterialTheme.typography.headlineSmall,
				fontWeight = FontWeight.Bold,
			)
			Spacer(modifier = Modifier.size(13.dp))
			OutlinedTextField(
				value = notes,
				onValueChange = onNotesChange,
				modifier = Modifier.fillMaxWidth(),
				label = { Text(stringResource(R.string.fast_notes_label)) },
				placeholder = { Text(stringResource(R.string.fast_notes_placeholder)) },
				minLines = 2,
				maxLines = 5,
			)
			Spacer(modifier = Modifier.size(21.dp))
			// Same action one step on, so the same terminus hue and the same glyph.
			val endContainer = journeyStageColor(9)
			Button(
				onClick = onEndNow,
				shape = CircleShape,
				colors = ButtonDefaults.buttonColors(
					containerColor = endContainer,
					contentColor = contentColorOn(endContainer),
				),
				modifier = Modifier
					.fillMaxWidth()
					.heightIn(min = 55.dp),
			) {
				Icon(
					imageVector = Icons.Default.Check,
					contentDescription = null,
					modifier = Modifier.padding(end = 8.dp),
				)
				Text(
					text = stringResource(R.string.sheet_end_now),
					style = MaterialTheme.typography.titleMedium,
				)
			}
			Spacer(modifier = Modifier.size(8.dp))
			TextButton(onClick = onEndEarlier, modifier = Modifier.fillMaxWidth()) {
				Text(stringResource(R.string.sheet_end_earlier))
			}
		}
	}
}

/**
 * Overlay for one stage of the fasting journey, opened from the dial.
 * A bottom sheet: it slides in gently, never covers the dial fully,
 * and dismisses with a swipe or a tap outside.
 */
@Composable
private fun JourneyStageSheet(
	stage: JourneyStage,
	onDismiss: () -> Unit,
) {
	val stageIndex = FastingJourney.stages.indexOf(stage)
	val accent = journeyStageColor(stageIndex)

	ModalBottomSheet(
		onDismissRequest = onDismiss,
		sheetState = rememberModalBottomSheetState(),
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.verticalScroll(rememberScrollState())
				.padding(start = 21.dp, end = 21.dp, bottom = 34.dp)
		) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				Box(
					modifier = Modifier
						.size(55.dp)
						.clip(CircleShape)
						.background(accent.copy(alpha = 0.18f)),
					contentAlignment = Alignment.Center
				) {
					Text(text = stage.emoji, fontSize = 26.sp)
				}
				Spacer(modifier = Modifier.size(13.dp))
				Column {
					Text(
						text = stringResource(stage.title),
						style = MaterialTheme.typography.titleLarge,
						fontWeight = FontWeight.Bold,
					)
					val rangeText = stage.endHours?.let { end ->
						stringResource(R.string.journey_stage_hours_range, stage.startHours, end)
					} ?: stringResource(R.string.journey_stage_hours_open, stage.startHours)
					Text(
						text = rangeText,
						style = MaterialTheme.typography.labelLarge,
						color = accent,
					)
				}
			}

			Spacer(modifier = Modifier.size(21.dp))

			Text(
				text = stringResource(stage.body),
				style = MaterialTheme.typography.bodyLarge,
			)
		}
	}
}

/**
 * The app-wide duration format (see [com.legbehindneck.fasttrack48.utils.formatDuration]),
 * bound to the composition's context.
 */
@Composable
private fun formatDuration(duration: Duration, showTotalHours: Boolean): String =
    formatDuration(
        LocalContext.current,
        duration,
        showTotalHours
    )
