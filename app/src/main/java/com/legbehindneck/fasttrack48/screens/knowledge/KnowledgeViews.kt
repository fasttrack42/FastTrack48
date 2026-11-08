package com.legbehindneck.fasttrack48.screens.knowledge

import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.satchel.Satchel
import cafe.adriel.satchel.ktx.getOrSet
import com.legbehindneck.fasttrack48.R
import com.legbehindneck.fasttrack48.data.Data
import com.legbehindneck.fasttrack48.data.FastingJourney
import com.legbehindneck.fasttrack48.data.Gender
import com.legbehindneck.fasttrack48.data.Profile
import com.legbehindneck.fasttrack48.data.knowledge.HelperGroup
import com.legbehindneck.fasttrack48.data.knowledge.HelperTopic
import com.legbehindneck.fasttrack48.data.knowledge.KnowledgeCard
import com.legbehindneck.fasttrack48.data.knowledge.KnowledgeCategory
import com.legbehindneck.fasttrack48.data.knowledge.KnowledgeContext
import com.legbehindneck.fasttrack48.data.knowledge.KnowledgeProfile
import com.legbehindneck.fasttrack48.data.knowledge.KnowledgeSelector
import com.legbehindneck.fasttrack48.data.knowledge.KnowledgeStore
import com.legbehindneck.fasttrack48.lunar.LunarSettingsStore

/** The user's knowledge choices, live, plus whether fast days are switched on. */
@Composable
fun rememberKnowledgeContext(): KnowledgeContext {
	val context = LocalContext.current
	val state by KnowledgeStore.state(context).collectAsState()
	val fastDays = remember {
		val lunar = LunarSettingsStore.load(context)
		lunar.ekadashiEnabled || lunar.fullMoonEnabled || lunar.newMoonEnabled
	}
	return KnowledgeContext(state, fastDays)
}

/** Age, BMI and sex from the Profile tab, read once per screen. Never asked for here. */
@Composable
fun rememberKnowledgeProfile(): KnowledgeProfile = remember {
	val p = runCatching { Satchel.storage.getOrSet(Data.KEY_PROFILE, Profile()) }.getOrDefault(Profile())
	val bmi = if (p.heightCm > 0.0 && p.weightKg > 0.0) p.weightKg / ((p.heightCm / 100.0) * (p.heightCm / 100.0)) else null
	KnowledgeProfile(ageYears = p.ageYears, bmi = bmi, female = p.gender == Gender.Female)
}

/**
 * One card, progressive disclosure (P4): title and claim; a tap reveals why, what to do,
 * caveat, evidence label and sources. Calm styling for every card, warnings included (P7).
 */
@Composable
fun KnowledgeCardView(
	card: KnowledgeCard,
	showLabel: Boolean,
	modifier: Modifier = Modifier,
	initiallyExpanded: Boolean = false,
	expandable: Boolean = true,
) {
	var expanded by rememberSaveable(card.id) { mutableStateOf(initiallyExpanded) }
	val open = expanded || !expandable
	Surface(
		shape = RoundedCornerShape(16.dp),
		color = MaterialTheme.colorScheme.surfaceContainerLow,
		modifier = modifier.fillMaxWidth(),
	) {
		Column(
			modifier = Modifier
				.clickable(enabled = expandable) { expanded = !expanded }
				.animateContentSize()
				.padding(16.dp)
		) {
			Row(verticalAlignment = Alignment.Top) {
				Text(
					text = stringResource(card.title),
					style = MaterialTheme.typography.titleMedium,
					fontWeight = FontWeight.SemiBold,
					modifier = Modifier.weight(1f),
				)
				if (expandable) {
					Icon(
						imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
						contentDescription = stringResource(if (expanded) R.string.kb_collapse else R.string.kb_expand),
						tint = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}
			}
			Spacer(modifier = Modifier.size(6.dp))
			Text(text = stringResource(card.claim), style = MaterialTheme.typography.bodyMedium)
			if (open) {
				CardSection(R.string.kb_why, card.why)
				if (card.action != 0) CardSection(R.string.kb_what_to_do, card.action)
				if (card.caveat != 0) CardSection(R.string.kb_caveat, card.caveat)
				Spacer(modifier = Modifier.size(12.dp))
				if (showLabel) {
					Text(
						text = stringResource(card.grade.label),
						style = MaterialTheme.typography.labelMedium,
						color = MaterialTheme.colorScheme.primary,
					)
				}
				if (card.sources.isNotEmpty()) {
					Text(
						text = stringResource(R.string.kb_sources) + ": " + card.sources.joinToString(" · "),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						modifier = Modifier.padding(top = 4.dp),
					)
				}
			}
		}
	}
}

@Composable
private fun CardSection(@StringRes heading: Int, @StringRes body: Int) {
	Text(
		text = stringResource(heading),
		style = MaterialTheme.typography.labelLarge,
		fontWeight = FontWeight.Bold,
		color = MaterialTheme.colorScheme.onSurfaceVariant,
		modifier = Modifier.padding(top = 12.dp),
	)
	Text(text = stringResource(body), style = MaterialTheme.typography.bodyMedium)
}

/** Swipeable set of cards, one page each, with "1 of 3" and dots. Opened from the tip chip. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgePagerSheet(cards: List<KnowledgeCard>, showLabel: Boolean, onDismiss: () -> Unit) {
	if (cards.isEmpty()) return
	ModalBottomSheet(
		onDismissRequest = onDismiss,
		sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
	) {
		val pager = rememberPagerState { cards.size }
		Column(modifier = Modifier.padding(bottom = 34.dp)) {
			HorizontalPager(
				state = pager,
				contentPadding = PaddingValues(horizontal = 21.dp),
				pageSpacing = 12.dp,
				verticalAlignment = Alignment.Top,
			) { page ->
				KnowledgeCardView(
					card = cards[page],
					showLabel = showLabel,
					expandable = false,
					modifier = Modifier.verticalScroll(rememberScrollState()),
				)
			}
			if (cards.size > 1) {
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(top = 13.dp),
					horizontalArrangement = Arrangement.Center,
					verticalAlignment = Alignment.CenterVertically,
				) {
					repeat(cards.size) { i ->
						Box(
							modifier = Modifier
								.padding(horizontal = 3.dp)
								.size(7.dp)
								.clip(CircleShape)
								.background(
									if (i == pager.currentPage) MaterialTheme.colorScheme.primary
									else MaterialTheme.colorScheme.outlineVariant
								)
						)
					}
					Spacer(modifier = Modifier.size(10.dp))
					Text(
						text = stringResource(R.string.kb_pager_position, pager.currentPage + 1, cards.size),
						style = MaterialTheme.typography.labelMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}
			}
		}
	}
}

/**
 * Under the dial while fasting: the stage's tip chip (once per stage per fast, never in the
 * first 2 h, optional in Settings) and the "How's it going?" helper. The only automatic surface.
 */
@Composable
fun KnowledgeFastingRow(
	isFasting: Boolean,
	fastStartMillis: Long?,
	elapsedHours: Double,
	modifier: Modifier = Modifier,
) {
	if (!isFasting || fastStartMillis == null) return
	val context = LocalContext.current
	val ctx = rememberKnowledgeContext()
	val stage = FastingJourney.indexFor(elapsedHours)
	val tipsOn = ctx.state.settings.tipsDuringFasts
	var chipId by remember(fastStartMillis, stage) { mutableStateOf<String?>(null) }
	var pagerCards by remember { mutableStateOf<List<KnowledgeCard>?>(null) }
	var helperOpen by rememberSaveable { mutableStateOf(false) }

	LaunchedEffect(fastStartMillis, stage, tipsOn) {
		chipId = if (tipsOn && stage >= 1) {
			KnowledgeStore.stageChip(context, fastStartMillis, stage) { KnowledgeSelector.stageTip(stage, ctx) }
		} else null
	}
	val chip = chipId?.let(KnowledgeSelector::card)?.takeIf { KnowledgeSelector.isVisible(it, ctx) }

	Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
		if (chip != null) {
			AssistChip(
				onClick = {
					KnowledgeStore.dismissStageChip(context, fastStartMillis, stage)
					pagerCards = KnowledgeSelector.stagePager(stage, chip.id, ctx)
					chipId = null
				},
				label = {
					Text(
						text = stringResource(R.string.kb_tip_prefix, stringResource(chip.title)),
						maxLines = 2,
						overflow = TextOverflow.Ellipsis,
					)
				},
				leadingIcon = { Text(text = "💡") },
			)
		}
		TextButton(onClick = { helperOpen = true }, contentPadding = PaddingValues(horizontal = 4.dp)) {
			Text(text = stringResource(R.string.kb_hows_it_going))
		}
	}

	pagerCards?.let { cards ->
		KnowledgePagerSheet(cards, ctx.state.settings.evidenceLabels, onDismiss = { pagerCards = null })
	}
	if (helperOpen) {
		HowsItGoingSheet(ctx = ctx, onDismiss = { helperOpen = false })
	}
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HowsItGoingSheet(ctx: KnowledgeContext, onDismiss: () -> Unit) {
	ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
		var topic by rememberSaveable { mutableStateOf<HelperTopic?>(null) }
		val results = remember { BringIntoViewRequester() }
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.verticalScroll(rememberScrollState())
				.padding(start = 21.dp, end = 21.dp, bottom = 34.dp)
		) {
			Text(
				text = stringResource(R.string.kb_hows_it_going),
				style = MaterialTheme.typography.titleLarge,
				fontWeight = FontWeight.Bold,
			)
			Text(
				text = stringResource(R.string.kb_helper_prompt),
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.padding(top = 4.dp),
			)
			HelperGroup.entries.forEach { group ->
				Text(
					text = stringResource(group.title),
					style = MaterialTheme.typography.labelLarge,
					fontWeight = FontWeight.Bold,
					modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
				)
				FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					HelperTopic.entries.filter { it.group == group }.forEach { t ->
						FilterChip(
							selected = topic == t,
							onClick = { topic = if (topic == t) null else t },
							label = { Text(stringResource(t.label)) },
						)
					}
				}
			}
			topic?.let { t ->
				val cards = remember(t, ctx) { KnowledgeSelector.helper(t, ctx) }
				LaunchedEffect(t) {
					// One frame, so the answers are laid out before they're scrolled to.
					withFrameNanos { }
					results.bringIntoView()
				}
				Column(
					modifier = Modifier
						.padding(top = 16.dp)
						.bringIntoViewRequester(results),
					verticalArrangement = Arrangement.spacedBy(8.dp),
				) {
					if (cards.isEmpty()) {
						Text(text = stringResource(R.string.kb_no_results), style = MaterialTheme.typography.bodyMedium)
					}
					cards.forEach { KnowledgeCardView(it, ctx.state.settings.evidenceLabels) }
				}
			}
		}
	}
}

/** Stage sheet: up to three cards for this stage, then "Show all". Collapsed, so it costs nothing. */
@Composable
fun GoDeeperSection(stageIndex: Int, modifier: Modifier = Modifier) {
	val ctx = rememberKnowledgeContext()
	val cards = remember(stageIndex, ctx) { KnowledgeSelector.forStage(stageIndex, ctx) }
	if (cards.isEmpty()) return
	var showAll by rememberSaveable(stageIndex) { mutableStateOf(false) }
	Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
		Text(
			text = stringResource(R.string.kb_go_deeper),
			style = MaterialTheme.typography.titleMedium,
			fontWeight = FontWeight.Bold,
		)
		(if (showAll) cards else cards.take(3)).forEach {
			KnowledgeCardView(it, ctx.state.settings.evidenceLabels)
		}
		if (!showAll && cards.size > 3) {
			TextButton(onClick = { showAll = true }) {
				Text(text = stringResource(R.string.kb_show_all, cards.size))
			}
		}
	}
}

/**
 * Start-fast sheet: the onboarding essentials on the first fast, then only what the profile calls
 * for (BMI under 20 every 30 days, 65+ once). Chosen on first composition and kept, so marking
 * them shown doesn't pull them out from under the reader.
 */
@Composable
fun BeforeStartCards(modifier: Modifier = Modifier) {
	val context = LocalContext.current
	val ctx = rememberKnowledgeContext()
	val profile = rememberKnowledgeProfile()
	val firstTime = remember { !ctx.state.onboardingShown }
	val cards = remember { KnowledgeSelector.beforeStart(ctx, profile, System.currentTimeMillis()) }
	if (cards.isEmpty()) return
	LaunchedEffect(Unit) {
		val now = System.currentTimeMillis()
		if (firstTime) KnowledgeStore.markOnboardingShown(context)
		if (profile.lowBmi && cards.any { it.id == "FC-001" }) KnowledgeStore.markBmiNote(context, now)
		if (cards.any { it.id == "FC-015" }) KnowledgeStore.markAge65Shown(context)
	}
	Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
		Text(
			text = stringResource(if (firstTime) R.string.kb_first_fast_title else R.string.kb_before_you_start),
			style = MaterialTheme.typography.titleSmall,
			fontWeight = FontWeight.Bold,
		)
		cards.forEach { KnowledgeCardView(it, ctx.state.settings.evidenceLabels) }
	}
}

/** End-fast sheet: one card about breaking this fast, chosen by its length (P9). */
@Composable
fun EndOfFastCard(elapsedHours: Double, modifier: Modifier = Modifier) {
	val context = LocalContext.current
	val ctx = rememberKnowledgeContext()
	val card = remember { KnowledgeSelector.endOfFast(elapsedHours, ctx) } ?: return
	LaunchedEffect(card.id) { KnowledgeStore.markSeen(context, listOf(card.id)) }
	KnowledgeCardView(card, ctx.state.settings.evidenceLabels, modifier)
}

/** Fast-days sheet: the tradition cards, plus the medical note on dry fasts when it applies. */
@Composable
fun FastDaysKnowledge(modifier: Modifier = Modifier) {
	val base = rememberKnowledgeContext()
	// The sheet only opens with fast days in use; don't let a stale read hide its own notes.
	val ctx = base.copy(fastDaysEnabled = true)
	val cards = remember(ctx) {
		KnowledgeSelector.all.filter {
			(it.category == KnowledgeCategory.Traditions ||
				(it.category == KnowledgeCategory.Medical && "fastday" in it.events)) &&
				KnowledgeSelector.isVisible(it, ctx)
		}
	}
	if (cards.isEmpty()) return
	Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
		Text(
			text = stringResource(R.string.kb_fast_days_about),
			style = MaterialTheme.typography.titleMedium,
			fontWeight = FontWeight.Bold,
		)
		cards.forEach { KnowledgeCardView(it, ctx.state.settings.evidenceLabels) }
	}
}
