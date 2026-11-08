package com.legbehindneck.fasttrack48.data.knowledge

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class KnowledgeSettings(
	/** Toggleable categories that are switched on. */
	val enabled: Set<KnowledgeCategory>,
	val conditions: Set<HealthCondition>,
	val tipsDuringFasts: Boolean,
	val evidenceLabels: Boolean,
)

data class KnowledgeState(
	val settings: KnowledgeSettings,
	/** How many times each card has been surfaced automatically (tip chip, end-of-fast card). */
	val seen: Map<String, Int>,
	val onboardingShown: Boolean,
	val womenOfferShown: Boolean,
	val age65Shown: Boolean,
	val bmiNoteAtMillis: Long,
)

/**
 * Knowledge preferences and exposure memory, in their own file ("knowledge.xml"), backed up with
 * the rest of the shared preferences. The health-condition chips are kept apart in
 * "knowledge_device.xml", which backup and device transfer exclude (res/xml/backup_rules.xml,
 * data_extraction_rules.xml): Settings promises they are saved only on this phone. Every
 * mutation updates [state], so open screens follow along.
 */
object KnowledgeStore {

	private const val PREFS = "knowledge"
	/** Device-bound: never backed up or transferred. Holds [KEY_CONDITIONS] only. */
	private const val PREFS_DEVICE = "knowledge_device"
	private const val KEY_CATEGORY = "cat_"
	private const val KEY_CONDITIONS = "conditions"
	private const val KEY_TIPS = "tips_during_fasts"
	private const val KEY_LABELS = "evidence_labels"
	private const val KEY_SEEN = "seen_"
	private const val KEY_ONBOARDING = "onboarding_shown"
	private const val KEY_WOMEN_OFFER = "women_offer_shown"
	private const val KEY_AGE65 = "age65_shown"
	private const val KEY_BMI_NOTE = "bmi_note_at"
	/** "fastStartMillis|stageIndex|cardId|dismissed": the tip chip chosen for the current stage. */
	private const val KEY_STAGE_CHIP = "stage_chip"
	private const val NO_CARD = "-"

	@Volatile
	private var flow: MutableStateFlow<KnowledgeState>? = null

	fun state(context: Context): StateFlow<KnowledgeState> = ensure(context)

	fun current(context: Context): KnowledgeState = ensure(context).value

	private fun ensure(context: Context): MutableStateFlow<KnowledgeState> =
		flow ?: synchronized(this) {
			flow ?: MutableStateFlow(load(context)).also { flow = it }
		}

	private fun prefs(context: Context): SharedPreferences =
		context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

	private fun devicePrefs(context: Context): SharedPreferences =
		context.applicationContext.getSharedPreferences(PREFS_DEVICE, Context.MODE_PRIVATE)

	private fun load(context: Context): KnowledgeState {
		val p = prefs(context)
		val enabled = KnowledgeCategory.entries
			.filter { it.toggleable && p.getBoolean(KEY_CATEGORY + it.id, it.default == Visibility.On) }
			.toSet()
		val conditions = devicePrefs(context).getStringSet(KEY_CONDITIONS, null).orEmpty()
			.mapNotNull(HealthCondition::fromId).toSet()
		val seen = p.all.entries
			.filter { it.key.startsWith(KEY_SEEN) && it.value is Int }
			.associate { it.key.removePrefix(KEY_SEEN) to (it.value as Int) }
		return KnowledgeState(
			settings = KnowledgeSettings(
				enabled = enabled,
				conditions = conditions,
				tipsDuringFasts = p.getBoolean(KEY_TIPS, true),
				evidenceLabels = p.getBoolean(KEY_LABELS, true),
			),
			seen = seen,
			onboardingShown = p.getBoolean(KEY_ONBOARDING, false),
			womenOfferShown = p.getBoolean(KEY_WOMEN_OFFER, false),
			age65Shown = p.getBoolean(KEY_AGE65, false),
			bmiNoteAtMillis = p.getLong(KEY_BMI_NOTE, 0L),
		)
	}

	private inline fun mutate(
		context: Context,
		device: Boolean = false,
		write: SharedPreferences.Editor.() -> Unit,
	) {
		(if (device) devicePrefs(context) else prefs(context)).edit(commit = false, action = write)
		ensure(context).update { load(context) }
	}

	fun setCategory(context: Context, category: KnowledgeCategory, on: Boolean) {
		if (!category.toggleable) return
		mutate(context) { putBoolean(KEY_CATEGORY + category.id, on) }
	}

	fun setCondition(context: Context, condition: HealthCondition, on: Boolean) {
		val now = current(context).settings.conditions
		val next = if (on) now + condition else now - condition
		mutate(context, device = true) { putStringSet(KEY_CONDITIONS, next.mapTo(HashSet()) { it.id }) }
	}

	fun setTipsDuringFasts(context: Context, on: Boolean) = mutate(context) { putBoolean(KEY_TIPS, on) }

	fun setEvidenceLabels(context: Context, on: Boolean) = mutate(context) { putBoolean(KEY_LABELS, on) }

	fun markSeen(context: Context, ids: Collection<String>) {
		if (ids.isEmpty()) return
		val seen = current(context).seen
		mutate(context) { ids.toSet().forEach { putInt(KEY_SEEN + it, (seen[it] ?: 0) + 1) } }
	}

	/** Forgets exposure memory and one-time offers, so tips can surface again. Keeps choices. */
	fun resetSeen(context: Context) {
		val p = prefs(context)
		mutate(context) {
			p.all.keys.filter { it.startsWith(KEY_SEEN) }.forEach { remove(it) }
			remove(KEY_STAGE_CHIP)
			remove(KEY_WOMEN_OFFER)
			remove(KEY_AGE65)
			remove(KEY_BMI_NOTE)
		}
	}

	fun markOnboardingShown(context: Context) = mutate(context) { putBoolean(KEY_ONBOARDING, true) }

	fun markWomenOfferShown(context: Context) = mutate(context) { putBoolean(KEY_WOMEN_OFFER, true) }

	fun markAge65Shown(context: Context) = mutate(context) { putBoolean(KEY_AGE65, true) }

	fun markBmiNote(context: Context, nowMillis: Long) = mutate(context) { putLong(KEY_BMI_NOTE, nowMillis) }

	/**
	 * The tip chip for [stage] of the fast that began at [fastStartMillis]: chosen once per
	 * stage per fast (by [pick]) and remembered, so recomposition, rotation or a restart never
	 * reshuffles it. A newly chosen card counts as one automatic exposure. Returns the card id,
	 * or null when there is nothing to show or the chip was dismissed.
	 */
	fun stageChip(context: Context, fastStartMillis: Long, stage: Int, pick: () -> KnowledgeCard?): String? {
		val p = prefs(context)
		val record = p.getString(KEY_STAGE_CHIP, null)?.split('|')
		if (record != null && record.size == 4 &&
			record[0] == fastStartMillis.toString() && record[1] == stage.toString()
		) {
			return record[2].takeIf { record[3] == "0" && it != NO_CARD }
		}
		val card = pick()
		p.edit { putString(KEY_STAGE_CHIP, "$fastStartMillis|$stage|${card?.id ?: NO_CARD}|0") }
		if (card != null) markSeen(context, listOf(card.id))
		return card?.id
	}

	fun dismissStageChip(context: Context, fastStartMillis: Long, stage: Int) {
		val p = prefs(context)
		val record = p.getString(KEY_STAGE_CHIP, null)?.split('|') ?: return
		if (record.size == 4 && record[0] == fastStartMillis.toString() && record[1] == stage.toString()) {
			p.edit { putString(KEY_STAGE_CHIP, "${record[0]}|${record[1]}|${record[2]}|1") }
		}
	}
}
