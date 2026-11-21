package com.legbehindneck.fasttrack48.data

import androidx.annotation.StringRes
import com.legbehindneck.fasttrack48.R
import kotlin.time.Duration
import kotlin.time.DurationUnit


object Stages {
	val PHASE_GLUCOSE = GlucosePhase()
	val PHASE_FAT_BURN = FatBurningPhase()
	val PHASE_KETOSIS = KetosisPhase()
	val PHASE_AUTOPHAGY = AutophagyPhase()
	val PHASE_OPTIMAL_AUTOPHAGY = OptimalAutophagyPhase()
	val END_TIME = 72f

	val phases = arrayOf(PHASE_GLUCOSE, PHASE_FAT_BURN, PHASE_KETOSIS, PHASE_AUTOPHAGY, PHASE_OPTIMAL_AUTOPHAGY)

	fun getCurrentPhase(elapsedTime: Duration): Phase {
		return phases.findLast { elapsedTime.toDouble(DurationUnit.HOURS) >= it.hours } ?: PHASE_GLUCOSE
	}

	/**
	 * The one-line readout under the dial and in the ongoing notification. Its boundaries are
	 * the journey's milestones ([FastingJourney]), so the readout, the countdown, the stage
	 * alerts and the log never disagree about when a stage begins; "Ketones rising" is a step
	 * inside fat burning.
	 */
	val stage = arrayOf(
		Stage(hours = 0, title = R.string.stage_fasting_title, description = R.string.stage_fasting_description),
		Stage(
			hours = FastingJourney.FAT_BURN.startHours,
			title = R.string.stage_fat_burning_title,
			description = R.string.stage_fat_burning_description
		),
		Stage(
			hours = 16,
			title = R.string.stage_fat_fasting_sweet_spot_title,
			description = R.string.stage_fat_fasting_sweet_spot_description
		),
		Stage(
			hours = FastingJourney.KETOSIS.startHours,
			title = R.string.stage_fat_peak_fat_burning_title,
			description = R.string.stage_fat_peak_fat_burning_description
		),
		Stage(
			hours = FastingJourney.AUTOPHAGY.startHours,
			title = R.string.stage_autophagy_title,
			description = R.string.stage_autophagy_description
		),
		Stage(
			hours = FastingJourney.DEEP_AUTOPHAGY.startHours,
			title = R.string.stage_optimal_autophagy_title,
			description = R.string.stage_optimal_autophagy_description
		),
	)
}

/** Hours spent in ketosis for a fast of the given [length] (0 if it never reached ketosis). */
fun ketosisHours(length: Duration): Double {
	val start = Stages.PHASE_KETOSIS.hours.toDouble()
	return (length.toDouble(DurationUnit.HOURS) - start).coerceAtLeast(0.0)
}

/** Hours spent in autophagy for a fast of the given [length] (0 if it never reached autophagy). */
fun autophagyHours(length: Duration): Double {
	val start = Stages.PHASE_AUTOPHAGY.hours.toDouble()
	return (length.toDouble(DurationUnit.HOURS) - start).coerceAtLeast(0.0)
}

/**
 * The description to show for [stage] at [elapsedHours]. Past 72 h the deep-autophagy
 * stage switches to copy that asks for medical supervision and a gentle refeed.
 */
@StringRes
fun descriptionFor(stage: Stage, elapsedHours: Long): Int =
	if (stage.description == R.string.stage_optimal_autophagy_description && elapsedHours >= 72L) {
		R.string.stage_optimal_autophagy_description_past72
	} else {
		stage.description
	}

sealed class Phase(
	val hours: Int,
	@StringRes val title: Int,
	val fatBurning: Boolean,
	val ketosis: Boolean,
	val autophagy: Boolean
)

// Onsets are the journey's milestones, the single source for every surface that names a stage.
class GlucosePhase : Phase(0, R.string.phase_glucose, false, false, false)
class FatBurningPhase : Phase(FastingJourney.FAT_BURN.startHours, R.string.phase_fat_burning, true, false, false)
class KetosisPhase : Phase(FastingJourney.KETOSIS.startHours, R.string.phase_ketosis, true, true, false)
class AutophagyPhase : Phase(FastingJourney.AUTOPHAGY.startHours, R.string.phase_autophagy, true, true, true)
class OptimalAutophagyPhase : Phase(FastingJourney.DEEP_AUTOPHAGY.startHours, R.string.phase_optimal_autophagy, true, true, true)

data class Stage(val hours: Int, @StringRes val title: Int, @StringRes val description: Int)