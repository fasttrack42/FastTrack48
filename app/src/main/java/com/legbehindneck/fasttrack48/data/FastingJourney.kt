package com.legbehindneck.fasttrack48.data

import androidx.annotation.StringRes
import com.legbehindneck.fasttrack48.R
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * One stage of the fasting journey as shown on the dial.
 * The canonical stage text lives in fasting-stages.md at the repo root;
 */
data class JourneyStage(
	val startHours: Int,
	/** null means the stage is open-ended (the final stage) */
	val endHours: Int?,
	val emoji: String,
	@StringRes val title: Int,
	@StringRes val body: Int,
)

object FastingJourney {
	val stages = listOf(
		JourneyStage(0, 2, "🌅", R.string.journey_stage_0_title, R.string.journey_stage_0_body),
		JourneyStage(2, 5, "🌊", R.string.journey_stage_1_title, R.string.journey_stage_1_body),
		JourneyStage(5, 8, "⚖️", R.string.journey_stage_2_title, R.string.journey_stage_2_body),
		JourneyStage(8, 12, "🧪", R.string.journey_stage_3_title, R.string.journey_stage_3_body),
		JourneyStage(12, 20, "🔥", R.string.journey_stage_4_title, R.string.journey_stage_4_body),
		JourneyStage(20, 24, "💎", R.string.journey_stage_5_title, R.string.journey_stage_5_body),
		JourneyStage(24, 48, "♻️", R.string.journey_stage_6_title, R.string.journey_stage_6_body),
		JourneyStage(48, 54, "💪", R.string.journey_stage_7_title, R.string.journey_stage_7_body),
		JourneyStage(54, 72, "🎯", R.string.journey_stage_8_title, R.string.journey_stage_8_body),
		JourneyStage(72, null, "🌱", R.string.journey_stage_9_title, R.string.journey_stage_9_body),
	)

	fun indexFor(elapsedHours: Double): Int =
		stages.indexOfLast { elapsedHours >= it.startHours }.coerceAtLeast(0)

	fun stageFor(elapsedHours: Double): JourneyStage = stages[indexFor(elapsedHours)]

	/** The milestones the screen counts down to and the stage alerts announce ([Stages.phases]). */
	val FAT_BURN: JourneyStage get() = stages[4]
	val KETOSIS: JourneyStage get() = stages[5]
	val AUTOPHAGY: JourneyStage get() = stages[6]
	val DEEP_AUTOPHAGY: JourneyStage get() = stages[7]

	/**
	 * Milestones worth confetti the first time any fast reaches them. None past a day:
	 * confetti at 48 or 72 h would reward escalation, and from 72 h the app itself asks for
	 * medical supervision.
	 */
	private val firsts: List<Duration> get() = listOf(FAT_BURN, KETOSIS, AUTOPHAGY).map { it.startHours.hours }

	/**
	 * Whether a fast of [length] ends with confetti, given the longest fast logged before it
	 * (null: none). By default only for a first: the first time any fast reaches fat burning,
	 * ketosis or autophagy. A reward that comes every time stops registering; one tied to a
	 * real first keeps its meaning. [everyFast], the user's opt-in, celebrates every fast that
	 * reaches fat burning.
	 */
	fun celebrates(length: Duration, longestBefore: Duration?, everyFast: Boolean): Boolean {
		val milestones = firsts
		if (everyFast) return length >= milestones.first()
		val before = longestBefore ?: Duration.ZERO
		return milestones.any { length >= it && before < it }
	}
}
