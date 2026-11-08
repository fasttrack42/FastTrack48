package com.legbehindneck.fasttrack48.data.knowledge

import androidx.annotation.StringRes
import com.legbehindneck.fasttrack48.R

/** What a selection needs beyond the cards: the user's choices and the fast-days feature state. */
data class KnowledgeContext(val state: KnowledgeState, val fastDaysEnabled: Boolean)

/** Body facts already in the Profile tab; never asked for separately. */
data class KnowledgeProfile(val ageYears: Int, val bmi: Double?, val female: Boolean) {
	val senior: Boolean get() = ageYears >= 65
	val lowBmi: Boolean get() = bmi != null && bmi < 20.0
}

enum class HelperGroup(@StringRes val title: Int) {
	Body(R.string.kb_group_body), Mind(R.string.kb_group_mind), Progress(R.string.kb_group_progress)
}

/** The "How's it going?" chips: recognition, not recall (INTEGRATION.md §3C). */
enum class HelperTopic(
	@StringRes val label: Int,
	val group: HelperGroup,
	val symptom: String? = null,
	val event: String? = null,
	val ids: List<String> = emptyList(),
) {
	Headache(R.string.kb_chip_headache, HelperGroup.Body, symptom = "headache"),
	Dizzy(R.string.kb_chip_dizzy, HelperGroup.Body, symptom = "dizzy"),
	Tired(R.string.kb_chip_tired, HelperGroup.Body, symptom = "fatigue"),
	Cold(R.string.kb_chip_cold, HelperGroup.Body, symptom = "cold"),
	Sleep(R.string.kb_chip_sleep, HelperGroup.Body, symptom = "insomnia"),
	Cramps(R.string.kb_chip_cramps, HelperGroup.Body, symptom = "cramps"),
	Heartburn(R.string.kb_chip_heartburn, HelperGroup.Body, symptom = "heartburn"),
	Bloated(R.string.kb_chip_bloated, HelperGroup.Body, symptom = "bloating"),
	LooseStools(R.string.kb_chip_loose, HelperGroup.Body, symptom = "diarrhea"),
	Constipated(R.string.kb_chip_constipated, HelperGroup.Body, symptom = "constipation"),
	Hungry(R.string.kb_chip_hungry, HelperGroup.Mind, ids = listOf("FC-050", "FC-053", "FC-052", "FC-054", "FC-051", "FC-055")),
	Craving(R.string.kb_chip_craving, HelperGroup.Mind, ids = listOf("FC-181", "FC-182", "FC-183", "FC-056")),
	Bored(R.string.kb_chip_bored, HelperGroup.Mind, ids = listOf("FC-186", "FC-050")),
	Stressed(R.string.kb_chip_stressed, HelperGroup.Mind, ids = listOf("FC-101", "FC-105", "FC-180")),
	SlippedUp(R.string.kb_chip_slipped, HelperGroup.Mind, ids = listOf("FC-180", "FC-099", "FC-098")),
	ScaleStuck(R.string.kb_chip_stalled, HelperGroup.Progress, event = "plateau"),
	Restarting(R.string.kb_chip_restart, HelperGroup.Progress, ids = listOf("FC-090", "FC-097", "FC-187")),
}

object KnowledgeSelector {

	/** A card surfaces on its own (tip chip, end-of-fast card) at most this many times, ever. */
	const val AUTO_SURFACE_CAP = 3

	/** The "Can I have…?" quick lookup at the top of the library. */
	val DrinkLookup = listOf("FC-060", "FC-061", "FC-067")

	private const val HUNGRY_IS_FINE = "FC-016"
	private const val REFEEDING = "FC-018"
	private const val BREAK_GENTLY = "FC-070"
	private const val EAT_NORMALLY = "FC-072"
	private const val WHO_SHOULD_NOT_FAST = "FC-001"
	private const val CONDITION_OR_MEDICINE = "FC-029"
	private const val OVER_65 = "FC-015"
	private const val PERIOD_STOPS = "FC-021"
	private const val BMI_NOTE_INTERVAL_MILLIS = 30L * 24 * 60 * 60 * 1000

	val all: List<KnowledgeCard> get() = KnowledgeCatalog
	private val byId: Map<String, KnowledgeCard> by lazy { KnowledgeCatalog.associateBy { it.id } }

	fun card(id: String): KnowledgeCard? = byId[id]

	fun isVisible(card: KnowledgeCard, ctx: KnowledgeContext): Boolean {
		val s = ctx.state.settings
		return when (card.category.default) {
			Visibility.Locked -> true
			Visibility.FastDays -> ctx.fastDaysEnabled
			Visibility.On, Visibility.Off -> card.category in s.enabled &&
				(card.category != KnowledgeCategory.Medical || card.conditions.any { it in s.conditions })
		}
	}

	/** Pull surfaces: a stable order (weightier safety first, then stronger evidence). */
	private val stable = compareByDescending<KnowledgeCard> { it.safety.ordinal }
		.thenBy { it.grade.ordinal }
		.thenBy { it.id }

	private fun visible(ctx: KnowledgeContext, filter: (KnowledgeCard) -> Boolean) =
		all.filter { filter(it) && isVisible(it, ctx) }

	fun forStage(stage: Int, ctx: KnowledgeContext): List<KnowledgeCard> =
		visible(ctx) { stage in it.stages }.sortedWith(stable)

	fun byIds(ids: List<String>, ctx: KnowledgeContext): List<KnowledgeCard> =
		ids.mapNotNull(byId::get).filter { isVisible(it, ctx) }

	/**
	 * The one card a stage's tip chip offers: the weightiest unseen-most card still under the
	 * exposure cap. Novelty first, so the same tip doesn't greet every fast. Discomfort cards
	 * never surface on their own: naming a symptom nobody reported primes it (nocebo), so they
	 * stay pull-only (stage sheet, pager, "How's it going?").
	 */
	fun stageTip(stage: Int, ctx: KnowledgeContext): KnowledgeCard? {
		val seen = ctx.state.seen
		return visible(ctx) {
			stage in it.stages && it.category != KnowledgeCategory.Comfort && (seen[it.id] ?: 0) < AUTO_SURFACE_CAP
		}
			.sortedWith(
				compareByDescending<KnowledgeCard> { it.safety.ordinal }
					.thenBy { seen[it.id] ?: 0 }
					.thenBy { it.grade.ordinal }
					.thenBy { it.id }
			)
			.firstOrNull()
	}

	/** Cards for the tip pager: the chosen card first, then the rest of its stage, at most [max]. */
	fun stagePager(stage: Int, firstId: String, ctx: KnowledgeContext, max: Int = 5): List<KnowledgeCard> {
		val cards = forStage(stage, ctx)
		val first = cards.firstOrNull { it.id == firstId } ?: card(firstId)
		return (listOfNotNull(first) + cards.filter { it.id != firstId }).take(max)
	}

	fun helper(topic: HelperTopic, ctx: KnowledgeContext): List<KnowledgeCard> {
		val symptom = topic.symptom
		val event = topic.event
		val cards = when {
			symptom != null -> visible(ctx) { symptom in it.symptoms }.sortedWith(stable)
			event != null -> visible(ctx) { event in it.events }.sortedWith(stable)
			else -> byIds(topic.ids, ctx)
		}
		// Every body complaint closes with the one line that tells normal from not (P7): after
		// the answer to what was asked, so a benign complaint isn't met with a warning first.
		if (topic.group != HelperGroup.Body) return cards
		val pin = card(HUNGRY_IS_FINE) ?: return cards
		return cards.filter { it.id != pin.id } + pin
	}

	/**
	 * The single card the end-of-fast sheet offers, chosen by the fast's length. Refeeding risk
	 * is the card's own threshold (five days); from three days the gentle-refeed card shows
	 * every time, since the stage copy asks for it.
	 */
	fun endOfFast(elapsedHours: Double, ctx: KnowledgeContext): KnowledgeCard? {
		val seen = ctx.state.seen
		fun allowed(id: String) = card(id)?.takeIf { isVisible(it, ctx) }
		fun capped(id: String) = allowed(id)?.takeIf { (seen[id] ?: 0) < AUTO_SURFACE_CAP }
		return when {
			elapsedHours >= 120.0 -> card(REFEEDING) // locked essentials: always visible
			elapsedHours >= 72.0 -> allowed(BREAK_GENTLY)
			elapsedHours >= 24.0 -> capped(BREAK_GENTLY)
			elapsedHours >= 12.0 -> capped(EAT_NORMALLY)
			else -> null
		}
	}

	/** Essentials for the start-fast sheet: onboarding once, then only what the profile calls for. */
	fun beforeStart(ctx: KnowledgeContext, profile: KnowledgeProfile, nowMillis: Long): List<KnowledgeCard> {
		val st = ctx.state
		val ids = buildList {
			if (!st.onboardingShown) {
				add(WHO_SHOULD_NOT_FAST)
				add(CONDITION_OR_MEDICINE)
			} else if (profile.lowBmi && nowMillis - st.bmiNoteAtMillis >= BMI_NOTE_INTERVAL_MILLIS) {
				add(WHO_SHOULD_NOT_FAST)
			}
			if (profile.senior && !st.age65Shown) add(OVER_65)
		}
		return ids.distinct().mapNotNull(byId::get)
	}

	/** Shown once to those who set female in Profile, while the category is still off. */
	fun offerWomen(ctx: KnowledgeContext, profile: KnowledgeProfile): Boolean =
		profile.female && !ctx.state.womenOfferShown &&
			KnowledgeCategory.Women !in ctx.state.settings.enabled

	fun periodStopsCard(): KnowledgeCard? = card(PERIOD_STOPS)

	/** Library sections in reading order; the journey follows the dial. Empty sections are left out. */
	fun library(ctx: KnowledgeContext): List<Pair<KnowledgeCategory, List<KnowledgeCard>>> {
		val order = listOf(
			KnowledgeCategory.Journey, KnowledgeCategory.Mind, KnowledgeCategory.Food,
			KnowledgeCategory.Rhythm, KnowledgeCategory.Comfort, KnowledgeCategory.Women,
			KnowledgeCategory.Medical, KnowledgeCategory.Traditions, KnowledgeCategory.Essentials,
		)
		return order.mapNotNull { cat ->
			val cards = visible(ctx) { it.category == cat }.let { list ->
				if (cat == KnowledgeCategory.Journey) list.sortedWith(compareBy({ it.stages.minOrNull() ?: Int.MAX_VALUE }, { it.id }))
				else list.sortedBy { it.id }
			}
			cards.takeIf { it.isNotEmpty() }?.let { cat to it }
		}
	}

	/** Case- and accent-insensitive search over a card's text, resolved by [text]. */
	fun search(query: String, ctx: KnowledgeContext, text: (Int) -> String): List<KnowledgeCard> {
		val q = normalize(query)
		if (q.isBlank()) return emptyList()
		return visible(ctx) { card ->
			listOf(card.title, card.claim, card.why, card.action, card.caveat)
				.filter { it != 0 }
				.any { normalize(text(it)).contains(q) }
		}.sortedWith(stable)
	}

	private fun normalize(s: String): String =
		java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
			.replace(Regex("\\p{Mn}+"), "")
			.trim()
}
