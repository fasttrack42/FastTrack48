package com.legbehindneck.fasttrack48.screens.fasting

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Rect
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import com.legbehindneck.fasttrack48.utils.WordSafe
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
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
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
import com.legbehindneck.fasttrack48.screens.confetti.ConfettiState
import com.legbehindneck.fasttrack48.screens.confetti.confettiEffect
import com.legbehindneck.fasttrack48.screens.fastdays.FastDayData
import com.legbehindneck.fasttrack48.screens.fastdays.FastDayEntry
import com.legbehindneck.fasttrack48.screens.fastdays.FastDaysSheet
import com.legbehindneck.fasttrack48.screens.fastdays.rememberFastDays
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
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
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
				// Confetti, like the praise line, only for a fast long enough to count, and only
				// for the fast this sheet was about (it may have ended elsewhere a moment ago).
				val earned = uiState.isFasting && (uiState.elapsedTime ?: Duration.ZERO) >= PraiseThreshold
				viewModel.endFast(notes = endNotes.trim())
				endNotes = ""
				if (earned) confetti.start(scope)
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
				val start = uiState.fastStartTime?.takeIf { uiState.isFasting }
				viewModel.endFast(selectedDateTime, endNotes.trim())
				endNotes = ""
				showEndDateTimePicker = false
				if (start != null && selectedDateTime - start >= PraiseThreshold) confetti.start(scope)
			},
			title = stringResource(R.string.already_stopped_dialog_title),
			finishButton = stringResource(id = R.string.end_fast_button),
			state = dateTimePickerState,
			minInstant = uiState.fastStartTime,
			// The day filter alone lets a later hour *today* through: an end in the future.
			maxInstant = remember { Clock.System.now() },
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
					text = AppDateTime.dropDoubledPeriods(
						stringResource(
							R.string.start_overlap_message,
							previousEnd?.let {
								AppDateTime.formatDateTime(it.toLocalDateTime(tz), dateStyle, use24Hour)
							} ?: "",
							AppDateTime.formatDateTime(candidate.toLocalDateTime(tz), dateStyle, use24Hour),
						)
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

	// A fast started or ended elsewhere (the notification, the widget, a shortcut) leaves the
	// sheets and pickers for the other state with nothing to act on: close them, and drop a
	// note typed for a fast that is already logged.
	LaunchedEffect(uiState.isFasting) {
		if (uiState.isFasting) {
			showStartSheet = false
			showStartDateTimePicker = false
		} else {
			showEndSheet = false
			showEndDateTimePicker = false
			showEditStartPicker = false
			pendingStart = null
			endNotes = ""
		}
	}

	// Journey stage overlay, opened from the dial or the phase rows
	var selectedStage by remember { mutableStateOf<JourneyStage?>(null) }

	// The next followed fast day, under the idle dial; a tap opens the year of them.
	val fastDays = rememberFastDays(FAST_DAY_ENTRY_SPAN)
	var showFastDays by rememberSaveable { mutableStateOf(false) }

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

		// Landscape only; portrait is built from one square (HeroGeometry).
		//
		// Landscape has no vertical slack to give away: the left column carries the dial, and the
		// dial's own square is what the band sits inside. The column's padding (2 x large) is the
		// honest cost of everything that is not the dial, and so is the clearance above it for the
		// hour labels and the arc head's glow, which reach ~9dp past the dial's square. The rest
		// solves for the dial rather than guessing at it: the block is the square plus its overhang,
		// which on any phone-sized dial measures (1 - ARC_NOTCH_FRACTION) * D + BandHeight + gap.
		// Inverting it gives the largest dial whose band still lands inside the column.
		val landscapeDial = min(
			maxWidth * 0.46f,
			(maxHeight - spacing.large * 2 - labelClearance(spacing) - BandHeight - spacing.xlarge) /
				(1f - ARC_NOTCH_FRACTION),
		)

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
						BoxWithConstraints(
							modifier = Modifier
								.weight(1f)
								.fillMaxHeight()
								// The column gives type back first and scrolls only past the floor, rather
								// than clipping the arc.
								.verticalScroll(headingScroll)
								.padding(end = spacing.medium)
								.onGloballyPositioned { heroBounds = it.boundsInWindow().toAndroidRect() },
							contentAlignment = Alignment.TopCenter,
						) {
							FastDial(
								// The scroll clips at its own edge, so the labels' clearance is inside it.
								modifier = Modifier.padding(top = labelClearance(spacing)),
								uiState = uiState,
								dialSize = min(maxWidth, landscapeDial),
								maxBandWidth = maxWidth,
								showTotalHours = showTotalHours,
								onToggleTimeFormat = { showTotalHours = !showTotalHours },
								onStageSelected = { selectedStage = it },
								onEditStart = { showEditStartPicker = true },
								onShowEndFastConfirmation = ::onShowEndFastConfirmation,
								onShowStartFastSelector = ::onShowStartFastSelector,
								viewModel = viewModel,
								capturing = capturing,
							)
						}
					}

					Spacer(modifier = Modifier.size(height = spacing.large, width = 1.dp))

					FastDetailsContent(
						uiState = uiState,
						dialSize = landscapeDial,
						showTotalHours = showTotalHours,
						onStageSelected = { selectedStage = it },
						fastDays = fastDays,
						onOpenFastDays = { showFastDays = true },
						modifier = Modifier
							.weight(1f)
							.fillMaxHeight()
							.padding(start = spacing.medium)
					)
				}
			} else {
				// One square, the screen's width, and the golden rectangle built on it (HeroGeometry).
				// The same rectangle in every state, so the dial never moves when a fast starts or
				// ends. The column still scrolls, as the last resort: text that outgrows the gnomon
				// (a large font setting, a long locale) first gives type back, then scrolls, and the
				// dial stays where the rectangle put it either way.
				val geometry = remember(maxWidth, maxHeight) { HeroGeometry.of(maxWidth, maxHeight) }
				// Read here: inside the Column below, the outer constraints are out of scope.
				val screenWidth = maxWidth
				val heroScroll = rememberScrollState()
				CompositionLocalProvider(
					LocalDensity provides rememberFitToViewportDensity(heroScroll)
				) {
					Column(
						modifier = Modifier
							.fillMaxWidth()
							.verticalScroll(heroScroll),
						horizontalAlignment = Alignment.CenterHorizontally,
					) {
						Spacer(modifier = Modifier.height(geometry.topSlack))
						FastHero(
							geometry = geometry,
							uiState = uiState,
							showTotalHours = showTotalHours,
							onToggleTimeFormat = { showTotalHours = !showTotalHours },
							onStageSelected = { selectedStage = it },
							onEditStart = { showEditStartPicker = true },
							onShowEndFastConfirmation = ::onShowEndFastConfirmation,
							onShowStartFastSelector = ::onShowStartFastSelector,
							viewModel = viewModel,
							capturing = capturing,
							maxBandWidth = screenWidth,
							fastDays = fastDays,
							onOpenFastDays = { showFastDays = true },
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

	if (showFastDays) {
		FastDaysSheet(dateStyle = LocalDateStyle.current, onDismiss = { showFastDays = false })
	}
}

/** The idle line looks two months ahead: always past the next fast, short enough to be quick. */
private const val FAST_DAY_ENTRY_SPAN = 60

/**
 * The dial with everything that lives on it: the ring, its centre, the band in its notch and
 * the debug control. One square of [dialSize], plus whatever the band hangs below it.
 */
@Composable
private fun FastDial(
	uiState: IFastingViewModel.FastingUiState,
	dialSize: Dp,
	maxBandWidth: Dp,
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

	// Precise elapsed time; uiState.elapsedHours is truncated to whole hours.
	// After a fast has ended the dial rests at zero: muted milestones, no
	// heartbeat — the pulse is a reward that belongs to fasting alone.
	val elapsedHoursPrecise = if (uiState.isFasting) {
		uiState.elapsedTime?.toDouble(DurationUnit.HOURS) ?: uiState.elapsedHours
	} else {
		0.0
	}

	// The arc's 270 degrees leave a notch facing down, and the notch is real estate: the band
	// drops into it instead of queueing below it. On a full-size dial the notch swallows the
	// band whole; on a small one the box grows by just the difference.
	val overhang = bandOverhang(dialSize, spacing)
	// The notch-aligned inset, but never so deep that the two blocks get less than the width
	// they need: below MinBandWidth the arms step outboard instead of the content folding,
	// past the dial's own edge if they must, never past the room the caller has.
	val armInset = (dialSize * ARC_ARM_INSET)
		.coerceAtMost((dialSize - MinBandWidth) / 2)
		.coerceAtLeast(0.dp)
	val bandWidth = max(dialSize - armInset * 2, MinBandWidth).coerceAtMost(maxBandWidth)

	Box(modifier = modifier.width(dialSize)) {
		// Reserves the box's height so the band is inside the layout, not floating over it.
		Spacer(modifier = Modifier.fillMaxWidth().height(dialSize + overhang))

		TimeLine(
			elapsedHours = elapsedHoursPrecise,
			modifier = Modifier
				.align(Alignment.TopCenter)
				.fillMaxWidth(),
			onStageClick = onStageSelected
		) {
			if (uiState.isFasting) {
				FastingReadout(
					uiState = uiState,
					elapsedHours = elapsedHoursPrecise,
					showTotalHours = showTotalHours,
					onToggleTimeFormat = onToggleTimeFormat,
				)
			} else {
				// Idle: the ring is empty and the entrance belongs in the middle of it.
				// TimeLine's tap handler rejects anything off the band radius, so nothing
				// here competes with it.
				StartOrb(
					dialSize = dialSize,
					onClick = onShowStartFastSelector,
					capturing = capturing,
				)
			}
		}

		FastArcBand(
			uiState = uiState,
			showTotalHours = showTotalHours,
			onToggleTimeFormat = onToggleTimeFormat,
			onEditStart = onEditStart,
			onShowEndFastConfirmation = onShowEndFastConfirmation,
			capturing = capturing,
			// requiredWidth: on a dial narrower than MinBandWidth the band overhangs it equally
			// on both sides rather than squeezing the start time.
			modifier = Modifier
				.align(Alignment.BottomCenter)
				.requiredWidth(bandWidth),
		)

		if (BuildConfig.DEBUG && uiState.isFasting) {
			// Inside the ring, below the timer: the dial's interior is the only spot in the
			// composition that no shipped element occupies. Gated on isFasting: it advances a
			// clock that is not running otherwise.
			FilledTonalIconButton(
				onClick = { viewModel.debugIncreaseFastingTimeByOneHour() },
				modifier = Modifier
					.align(Alignment.TopCenter)
					.offset(y = dialSize * 0.72f - 20.dp)
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

/**
 * The ring's centre while fasting: the stage's name over the time. One focal point, the time,
 * with its caption, instead of a headline above the dial competing with it. The dot wears the
 * colour of the lit arc's head, which ties the name to where the ring is lit. A tap anywhere
 * here switches the time between days + hours and total hours.
 */
@Composable
private fun FastingReadout(
	uiState: IFastingViewModel.FastingUiState,
	elapsedHours: Double,
	showTotalHours: Boolean,
	onToggleTimeFormat: () -> Unit,
) {
	val spacing = fastingSpacing()
	val typography = fastingTypography()
	val titleStyle = typography.stageTitle()

	Column(
		horizontalAlignment = Alignment.CenterHorizontally,
		modifier = Modifier.clickable(
			interactionSource = remember { MutableInteractionSource() },
			indication = null
		) { onToggleTimeFormat() }
	) {
		if (uiState.stageTitle.isNotEmpty()) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				Box(
					modifier = Modifier
						.size(spacing.medium)
						.clip(CircleShape)
						.background(journeyStageColor(FastingJourney.indexFor(elapsedHours)))
				)
				Spacer(modifier = Modifier.width(spacing.small))
				// Auto-sized because the corridor is the ring's inscribed square while the name
				// is not fixed: "Optimal autophagy" is 17 characters, "Оптимальна автофагія"
				// 20. Shrinking a point or two beats an ellipsis on a two-word name.
				BasicText(
					text = uiState.stageTitle,
					maxLines = 2,
					autoSize = TextAutoSize.WordSafe(
						minFontSize = 11.sp,
						maxFontSize = titleStyle.fontSize.takeIf { it != TextUnit.Unspecified } ?: 16.sp,
						stepSize = 1.sp,
					),
					style = titleStyle.merge(
						TextStyle(
							color = MaterialTheme.colorScheme.onBackground,
							textAlign = TextAlign.Center,
						)
					),
				)
			}
		}
		val timerText = formatDuration(
			uiState.elapsedTime ?: uiState.elapsedHours.hours,
			showTotalHours
		)
		// Auto-size so the value always fits inside the ring. One line, shrinks to fit.
		BasicText(
			text = timerText,
			maxLines = 1,
			softWrap = false,
			autoSize = TextAutoSize.WordSafe(
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
	}
}

// Shared height of the two blocks that flank the arc's notch — the start datum and the
// action. A Fibonacci rung, comfortably over the 48dp touch minimum, and where the two-line
// datum lands on its own. Equal footprint is the point: they balance each other.
internal val BandHeight = 55.dp

// Where the arc actually ends, as a fraction of the dial's side, from TimeLine's own numbers:
// stroke s = 1/phi^6, ring radius r = 0.5 - RING_INSET * s = 0.4342, endpoints at 135 and 45
// degrees, round caps adding s / 2 beyond them.
//   endpoint y = 0.5 + r * sin(45) + s / 2 = 0.835  ->  0.165 of the side is clear below it
//   endpoint x = 0.5 - r * cos(45) - s / 2 = 0.165  ->  the arms are that far in from the edge
internal val ARC_NOTCH_FRACTION =
	0.5f - (0.5f - RING_INSET * STROKE_FRACTION) * 0.70710677f - STROKE_FRACTION / 2f
// The arms sit a hair outboard of 0.165: at the true inset the two blocks would need more
// width than the notch has, and Ukrainian would wrap up into the arc. 1/phi^4, two steps up
// the chain from the stroke, is the largest inset that still fits the longest locale.
internal const val ARC_ARM_INSET = 0.146f
// Floor for the band's inner width: the start datum at its longest ("Today, 10:48 AM",
// ~111dp with padding) plus End fast at its natural width (~105dp) plus a gutter. 233 is the
// Fibonacci rung that clears it.
internal val MinBandWidth = 233.dp

/**
 * Room above the dial's square for what the ring draws past it: the current stage's hour
 * label and the arc head's glow, both under 10dp beyond the square. In portrait the width
 * square's own margin covers it; landscape seats the dial against a clipping edge.
 */
private fun labelClearance(spacing: FastingSpacing): Dp = spacing.xlarge

/** How far the band hangs below the dial's square: none once the notch is deep enough. */
private fun bandOverhang(dialSize: Dp, spacing: FastingSpacing): Dp =
	// The floor between the arc's rounded caps and the band's top edge is spacing.xlarge:
	// 21dp (13 when compact) is the rung that reads as deliberate separation.
	(BandHeight + spacing.xlarge - dialSize * ARC_NOTCH_FRACTION).coerceAtLeast(0.dp)

/**
 * The portrait layout, built from one square whose side S is the screen's width (Hambidge's
 * dynamic symmetry):
 *
 *   dial     S / √2   the square inside the circle inscribed in the width square
 *   φS                the half-square diagonal, S·√5 / 2, swung down from the middle of the
 *                     square's side, closes the golden rectangle S × φS
 *   gnomon   S / φ    the strip under the square, where everything that changes lives
 *
 * When φS is taller than the screen, S comes from the height instead (S = H / φ) and every
 * ratio holds. Spare height splits 1 : φ above and below. The rectangle is the same in every
 * state, so the dial sits still when a fast starts or ends. On a 411 × 891dp phone its centre
 * lands at 0.39 of the height above the navigation bar, near the golden section, from the
 * construction alone.
 */
internal data class HeroGeometry(
	val square: Dp,
	val dial: Dp,
	val topSlack: Dp,
	/** The gnomon's height plus the slack under it: the floor for the content below the square. */
	val gnomon: Dp,
) {
	companion object {
		fun of(width: Dp, height: Dp): HeroGeometry {
			val square = min(width, height / PHI)
			val slack = (height - square * PHI).coerceAtLeast(0.dp)
			val above = slack / (1f + PHI)
			return HeroGeometry(
				square = square,
				dial = square / SQRT2,
				topSlack = above,
				// Less one dp: the parts are rounded to pixels one by one, and a single pixel
				// past the viewport would read as overflow and shrink the type for nothing.
				gnomon = (square / PHI + (slack - above) - 1.dp).coerceAtLeast(0.dp),
			)
		}
	}
}

private const val SQRT2 = 1.4142135f

/**
 * Portrait hero: the width square with the dial centred in it, then the gnomon with the
 * milestones and the stage note (or, idle, the praise for a fast just finished). The whole of
 * it doubles as the shareable image; the action and the disc are hidden by [capturing] while
 * a share is taken.
 */
@Composable
private fun FastHero(
	geometry: HeroGeometry,
	uiState: IFastingViewModel.FastingUiState,
	showTotalHours: Boolean,
	onToggleTimeFormat: () -> Unit,
	onStageSelected: (JourneyStage) -> Unit,
	onEditStart: () -> Unit,
	onShowEndFastConfirmation: () -> Unit,
	onShowStartFastSelector: () -> Unit,
	viewModel: IFastingViewModel,
	capturing: Boolean,
	maxBandWidth: Dp,
	fastDays: FastDayData?,
	onOpenFastDays: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val spacing = fastingSpacing()
	val inset = (geometry.square - geometry.dial) / 2
	val overhang = bandOverhang(geometry.dial, spacing)

	Column(
		modifier = modifier,
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		// The width square, its height a floor: a dial too small to keep the band inside its
		// own margin (under ~204dp) grows the square instead of overlapping the gnomon.
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.heightIn(min = geometry.square)
				.padding(top = inset),
			contentAlignment = Alignment.TopCenter,
		) {
			FastDial(
				uiState = uiState,
				dialSize = geometry.dial,
				maxBandWidth = maxBandWidth,
				showTotalHours = showTotalHours,
				onToggleTimeFormat = onToggleTimeFormat,
				onStageSelected = onStageSelected,
				onEditStart = onEditStart,
				onShowEndFastConfirmation = onShowEndFastConfirmation,
				onShowStartFastSelector = onShowStartFastSelector,
				viewModel = viewModel,
				capturing = capturing,
			)
		}

		// The gnomon. Its text runs at the dial's width, so the dial's edges carry down as the
		// column's edges. The band normally ends ~32dp above it; when it ends closer (a small
		// dial), the gnomon's content starts lower to keep the group gap.
		val bandGap = (inset - overhang).coerceAtLeast(0.dp)
		Column(
			modifier = Modifier
				.width(max(geometry.dial, MinBandWidth))
				.heightIn(min = geometry.gnomon)
				.padding(top = (spacing.xlarge - bandGap).coerceAtLeast(0.dp)),
		) {
			FastGnomonContent(
				uiState = uiState,
				dialSize = geometry.dial,
				showTotalHours = showTotalHours,
				onStageSelected = onStageSelected,
				fastDays = fastDays,
				onOpenFastDays = onOpenFastDays,
				capturing = capturing,
			)
		}
	}
}

/**
 * What lives under the dial. Fasting: the next milestone with its bar, then the stage note.
 * Idle: the next followed fast day, then the praise for a fast just finished, when it was long
 * enough to earn it. The fast-day line is a control, so a shared image leaves it out.
 */
@Composable
private fun FastGnomonContent(
	uiState: IFastingViewModel.FastingUiState,
	dialSize: Dp,
	showTotalHours: Boolean,
	onStageSelected: (JourneyStage) -> Unit,
	fastDays: FastDayData?,
	onOpenFastDays: () -> Unit,
	capturing: Boolean,
) {
	val spacing = fastingSpacing()
	val typography = fastingTypography()

	if (uiState.isFasting) {
		MilestoneBlock(
			uiState = uiState,
			dialSize = dialSize,
			showTotalHours = showTotalHours,
			onStageSelected = onStageSelected,
		)
		if (uiState.stageDescription.isNotEmpty()) {
			// Prose, start-aligned: centred text over more than two lines reads slower,
			// because every line starts somewhere new. Not auto-sized: shrinking body text
			// takes back the size the reader asked for; the column scrolls instead.
			Text(
				text = uiState.stageDescription,
				style = typography.stageDescription(),
				color = MaterialTheme.colorScheme.onBackground,
				textAlign = TextAlign.Start,
				modifier = Modifier
					.fillMaxWidth()
					.padding(top = spacing.xlarge)
			)
		}
	} else {
		Column(verticalArrangement = Arrangement.spacedBy(spacing.xlarge)) {
			FastDayEntry(
				data = fastDays,
				dateStyle = LocalDateStyle.current,
				onClick = onOpenFastDays,
				modifier = Modifier.alpha(if (capturing) 0f else 1f),
			)
			rememberPraiseText(uiState)?.let { praise ->
				Text(
					text = praise,
					style = typography.stageDescription(),
					color = MaterialTheme.colorScheme.onBackground,
					textAlign = TextAlign.Center,
					modifier = Modifier.fillMaxWidth()
				)
			}
		}
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
 * Landscape body: the milestones plus a scroll-safe note that fills the column. The start
 * datum and the action live with the dial in the other column, on the arc.
 */
@Composable
private fun FastDetailsContent(
	uiState: IFastingViewModel.FastingUiState,
	dialSize: Dp,
	showTotalHours: Boolean,
	onStageSelected: (JourneyStage) -> Unit,
	fastDays: FastDayData?,
	onOpenFastDays: () -> Unit,
	modifier: Modifier = Modifier
) {
	val spacing = fastingSpacing()
	val typography = fastingTypography()

	Column(modifier = modifier) {
		MilestoneBlock(
			uiState = uiState,
			dialSize = dialSize,
			showTotalHours = showTotalHours,
			onStageSelected = onStageSelected,
		)
		if (!uiState.isFasting) {
			FastDayEntry(
				data = fastDays,
				dateStyle = LocalDateStyle.current,
				onClick = onOpenFastDays,
				modifier = Modifier.padding(top = spacing.medium),
			)
		}

		val praise = rememberPraiseText(uiState)
		val description = if (uiState.isFasting) uiState.stageDescription else praise.orEmpty()
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
					textAlign = if (uiState.isFasting) TextAlign.Start else TextAlign.Center,
					modifier = Modifier
						.fillMaxWidth()
						.padding(vertical = spacing.medium)
				)
			}
		}
	}
}

/**
 * A phase the milestone block can name: its label and its journey stage. The hour it begins is
 * the stage's own (12, 18 and 24 h), so the block, the dial and the stage sheet agree.
 */
private data class Milestone(@StringRes val label: Int, val stageIndex: Int) {
	val hours: Int get() = FastingJourney.stages[stageIndex].startHours
}

private val Milestones = listOf(
	Milestone(R.string.fast_fat_burn_label, 4),
	Milestone(R.string.fast_ketosis_label, 5),
	Milestone(R.string.fast_autophagy_label, 6),
)

/**
 * The next milestone, how far along the way to it the fast is, and the one after:
 *
 *   Ketosis in 2h 37m ......................... 18h
 *   [==========------------------------------------]
 *   Autophagy in 8h 37m
 *
 * The bar runs from the last milestone passed (or the fast's start) to the next one, so it
 * fills once per milestone instead of crawling across the whole fast: a near goal pulls
 * harder than a far one. Text in ink, colour on the bar only.
 *
 * The milestones are the ones the Settings toggles name, and they follow them: a hidden
 * phase is skipped. Auto mode reveals a phase only once it has begun, so it names nothing
 * ahead; the block then says how long the latest phase has been running. A tap on the
 * headline opens that stage.
 */
@Composable
private fun MilestoneBlock(
	uiState: IFastingViewModel.FastingUiState,
	dialSize: Dp,
	showTotalHours: Boolean,
	onStageSelected: (JourneyStage) -> Unit,
	modifier: Modifier = Modifier,
) {
	val elapsed = uiState.elapsedTime ?: uiState.elapsedHours.takeIf { it > 0 }?.hours
	if (!uiState.isFasting || elapsed == null) return

	val spacing = fastingSpacing()
	val typography = fastingTypography()
	val ink = MaterialTheme.colorScheme.onBackground
	val muted = ink.copy(alpha = 0.7f)

	val shown = Milestones.filter {
		uiState.phaseAutoMode || when (it.stageIndex) {
			4 -> uiState.showFatBurn
			5 -> uiState.showKetosis
			else -> uiState.showAutophagy
		}
	}
	val passed = shown.filter { elapsed >= it.hours.hours }
	val ahead = if (uiState.phaseAutoMode) emptyList() else shown.filter { elapsed < it.hours.hours }
	val next = ahead.firstOrNull()

	Column(modifier = modifier.fillMaxWidth()) {
		if (next != null) {
			val from = passed.lastOrNull()?.hours ?: 0
			val progress = ((elapsed.toDouble(DurationUnit.HOURS) - from) / (next.hours - from))
				.toFloat()
				.coerceIn(0f, 1f)
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.clip(RoundedCornerShape(spacing.small))
					.clickable { onStageSelected(FastingJourney.stages[next.stageIndex]) },
				verticalAlignment = Alignment.Bottom,
			) {
				Text(
					text = stringResource(
						R.string.milestone_in,
						stringResource(next.label),
						formatDuration(next.hours.hours - elapsed, showTotalHours),
					),
					style = typography.milestone(),
					color = ink,
					modifier = Modifier.weight(1f),
				)
				Text(
					text = "${next.hours}h",
					style = typography.milestoneDetail(),
					color = muted,
				)
			}
			Spacer(modifier = Modifier.height(spacing.medium))
			MilestoneBar(
				progress = progress,
				color = journeyStageColor(next.stageIndex),
				height = (dialSize / (PHI * PHI * PHI * PHI * PHI * PHI * PHI * PHI)).coerceAtLeast(4.dp),
			)
			ahead.getOrNull(1)?.let { then ->
				Text(
					text = stringResource(
						R.string.milestone_in,
						stringResource(then.label),
						formatDuration(then.hours.hours - elapsed, showTotalHours),
					),
					style = typography.milestoneDetail(),
					color = muted,
					modifier = Modifier.padding(top = spacing.medium),
				)
			}
		} else {
			passed.lastOrNull()?.let { last ->
				Text(
					text = stringResource(
						R.string.milestone_for,
						stringResource(last.label),
						formatDuration(elapsed - last.hours.hours, showTotalHours),
					),
					style = typography.milestone(),
					color = ink,
					modifier = Modifier
						.clip(RoundedCornerShape(spacing.small))
						.clickable { onStageSelected(FastingJourney.stages[last.stageIndex]) },
				)
			}
		}
	}
}

/** Track and fill. Its height is D / phi^8 (~6dp on a 291dp dial), never under 4dp. */
@Composable
private fun MilestoneBar(progress: Float, color: Color, height: Dp) {
	Box(
		modifier = Modifier
			.fillMaxWidth()
			.height(height)
			.clip(CircleShape)
			.background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f))
	) {
		Box(
			modifier = Modifier
				.fillMaxWidth(progress)
				.fillMaxHeight()
				.clip(CircleShape)
				.background(color)
		)
	}
}

/**
 * Wall-clock now, re-read once a minute.
 *
 * Everything on the idle screen that ages reads from this one value, so two lines can never
 * disagree by a tick.
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

/** A fast has to reach the journey's fat-burning stage (12h) to earn praise or confetti. */
private val PraiseThreshold = FastingJourney.stages[4].startHours.hours

/**
 * In the first hour after a fast long enough to count, a word of recognition with the fast's
 * length: "Well done: you fasted for 16 hours, 5 minutes." Nothing for a shorter fast; praise
 * that greets a one-minute fast stops meaning anything.
 */
@Composable
private fun rememberPraiseText(uiState: IFastingViewModel.FastingUiState): String? {
	val now = rememberMinuteTick()
	val locale = LocalConfiguration.current.locales[0]
	val entry = uiState.lastLoggedFast ?: return null
	val end = uiState.previousLoggedFastEnd ?: return null
	val since = now - end
	if (entry.length < PraiseThreshold || since < Duration.ZERO || since >= 1.hours) return null
	return stringResource(R.string.fast_well_done, formatSpan(entry.length, false, locale).withNoBreakSpaces())
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
 * Idle, the band spans the notch, centred: how long since the last fast ended. Datum and
 * action get equal mass but deliberately unequal weight: identical footprint, but the action
 * is the arc's closing violet and the datum is quiet text on the gradient.
 */
@Composable
private fun FastArcBand(
	uiState: IFastingViewModel.FastingUiState,
	showTotalHours: Boolean,
	onToggleTimeFormat: () -> Unit,
	onEditStart: () -> Unit,
	onShowEndFastConfirmation: () -> Unit,
	capturing: Boolean,
	modifier: Modifier = Modifier,
) {
	Box(modifier = modifier) {
		if (uiState.isFasting) {
			// Two halves, not SpaceBetween. Unconstrained children measure in order and the
			// datum measures first, so it took whatever "Yesterday, 22:15" wanted and left
			// the action the remainder — which in Spanish is narrower than the single word
			// "Terminar", so Compose broke the word itself. Weighted halves give the
			// terminal action a guaranteed 50% of the band.
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically,
			) {
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
			TimeSinceLastFast(
				lastEnd = uiState.previousLoggedFastEnd,
				showTotalHours = showTotalHours,
				onToggleTimeFormat = onToggleTimeFormat,
				modifier = Modifier.align(Alignment.Center),
			)
		}
	}
}

/**
 * How long since the last fast ended, in the notch under the empty ring: "Time since last
 * fast: 42 days, 3 hours".
 *
 * Under a minute it says so rather than "0 minutes". Under an hour, minutes; under a day,
 * hours and minutes; from a day on, days and hours. A zero part is dropped ("2 days", not
 * "2 days, 0 hours"). From a day on a tap switches to total hours ("1,011 hours"), the same
 * switch as the running timer's; below a day the two forms are the same, so the tap is not
 * offered there. A last fast that ends in the future (a changed clock) reads as under a
 * minute rather than as a negative span.
 *
 * Units and plurals come from ICU, which gets "42 дні, 3 години" and "42天3小时" right from
 * the same numbers. The value's spaces are no-break spaces, so on a narrow band the line
 * breaks after the colon, never inside the duration. An empty logbook renders nothing; the
 * band keeps its height either way, so the dial does not move.
 */
@Composable
private fun TimeSinceLastFast(
	lastEnd: Instant?,
	showTotalHours: Boolean,
	onToggleTimeFormat: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val now = rememberMinuteTick()
	if (lastEnd == null) return

	val spacing = fastingSpacing()
	val locale = LocalConfiguration.current.locales[0]
	val since = (now - lastEnd).coerceAtLeast(Duration.ZERO)
	val value = if (since < 1.minutes) {
		stringResource(R.string.duration_under_minute)
	} else {
		remember(since.inWholeMinutes, showTotalHours, locale) { formatSpan(since, showTotalHours, locale) }
	}
	val canToggle = since >= 1.days

	Box(
		modifier = modifier
			.heightIn(min = BandHeight)
			.clip(RoundedCornerShape(spacing.large))
			.then(if (canToggle) Modifier.clickable(onClick = onToggleTimeFormat) else Modifier)
			.padding(horizontal = spacing.small, vertical = spacing.small),
		contentAlignment = Alignment.Center,
	) {
		Text(
			text = stringResource(R.string.time_since_last_fast, value.withNoBreakSpaces()),
			style = MaterialTheme.typography.bodyMedium.copy(lineBreak = LineBreak.Simple),
			color = MaterialTheme.colorScheme.onBackground,
			textAlign = TextAlign.Center,
		)
	}
}

/**
 * A span as ICU writes it in [locale]: minutes under an hour, hours and minutes under a day,
 * then days and hours, or total hours when [totalHours]. A zero part is dropped.
 */
private fun formatSpan(span: Duration, totalHours: Boolean, locale: Locale): String {
	val minutes = span.inWholeMinutes.coerceAtLeast(1)
	val measures = when {
		minutes < MinutesPerHour -> listOf(Measure(minutes, MeasureUnit.MINUTE))
		minutes < MinutesPerDay -> buildList {
			add(Measure(minutes / MinutesPerHour, MeasureUnit.HOUR))
			if (minutes % MinutesPerHour > 0) add(Measure(minutes % MinutesPerHour, MeasureUnit.MINUTE))
		}
		totalHours -> listOf(Measure(minutes / MinutesPerHour, MeasureUnit.HOUR))
		else -> buildList {
			add(Measure(minutes / MinutesPerDay, MeasureUnit.DAY))
			val hours = minutes % MinutesPerDay / MinutesPerHour
			if (hours > 0) add(Measure(hours, MeasureUnit.HOUR))
		}
	}
	return MeasureFormat
		.getInstance(locale, MeasureFormat.FormatWidth.WIDE)
		.formatMeasures(*measures.toTypedArray())
}

private const val MinutesPerHour = 60L
private const val MinutesPerDay = 24L * 60L

private fun String.withNoBreakSpaces(): String = replace(' ', ' ')

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
			autoSize = TextAutoSize.WordSafe(
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
						// Ink, not the stage colour: the light ramp sits near 2:1 on the sheet.
						color = MaterialTheme.colorScheme.onSurfaceVariant,
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
