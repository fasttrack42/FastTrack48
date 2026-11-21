package com.legbehindneck.fasttrack48.data

import com.legbehindneck.fasttrack48.R
import com.legbehindneck.fasttrack48.data.activefast.FastCheckIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * One stage timeline: the screen's milestones, the stage alerts, the readout, the ongoing
 * notification and the log all start a stage at the same hour. Plus who gets confetti, and
 * when a fast earns "Still fasting?".
 */
class StageTableTest {

	@Test
	fun phases_startAtTheJourneysMilestones() {
		assertEquals(listOf(0, 12, 20, 24, 48), Stages.phases.map { it.hours })
		assertEquals(FastingJourney.FAT_BURN.startHours, Stages.PHASE_FAT_BURN.hours)
		assertEquals(FastingJourney.KETOSIS.startHours, Stages.PHASE_KETOSIS.hours)
		assertEquals(FastingJourney.AUTOPHAGY.startHours, Stages.PHASE_AUTOPHAGY.hours)
		assertEquals(FastingJourney.DEEP_AUTOPHAGY.startHours, Stages.PHASE_OPTIMAL_AUTOPHAGY.hours)
	}

	@Test
	fun readout_switchesStageAtTheSameHours() {
		fun titleAt(hours: Long) = Stages.stage[Stages.stage.indexOfLast { it.hours <= hours }.coerceAtLeast(0)].title
		assertEquals(R.string.stage_fasting_title, titleAt(0))
		assertEquals(R.string.stage_fasting_title, titleAt(11))
		assertEquals(R.string.stage_fat_burning_title, titleAt(12))
		assertEquals(R.string.stage_fat_fasting_sweet_spot_title, titleAt(16))
		assertEquals(R.string.stage_fat_peak_fat_burning_title, titleAt(20))
		assertEquals(R.string.stage_autophagy_title, titleAt(24))
		assertEquals(R.string.stage_optimal_autophagy_title, titleAt(48))
		// The energy mode follows: fat from the fat-burn milestone, not later.
		assertFalse(Stages.getCurrentPhase(11.hours + 59.minutes).fatBurning)
		assertTrue(Stages.getCurrentPhase(12.hours).fatBurning)
	}

	@Test
	fun confetti_onlyForFirsts_byDefault() {
		// Empty logbook: the first fast to reach fat burning celebrates, a shorter one does not.
		assertTrue(FastingJourney.celebrates(12.hours, null, everyFast = false))
		assertFalse(FastingJourney.celebrates(11.hours + 59.minutes, null, everyFast = false))
		// A routine 18 h after a 16 h best crosses nothing new.
		assertFalse(FastingJourney.celebrates(18.hours, 16.hours, everyFast = false))
		// First ketosis, first autophagy.
		assertTrue(FastingJourney.celebrates(20.hours, 16.hours, everyFast = false))
		assertTrue(FastingJourney.celebrates(30.hours, 22.hours, everyFast = false))
		// Nothing past a day is celebrated: no reward for escalation.
		assertFalse(FastingJourney.celebrates(72.hours, 30.hours, everyFast = false))
	}

	@Test
	fun confetti_everyFast_isTheOptIn() {
		assertTrue(FastingJourney.celebrates(16.hours, 40.hours, everyFast = true))
		assertFalse(FastingJourney.celebrates(11.hours, 40.hours, everyFast = true))
	}

	@Test
	fun checkIn_pastADayAndAHalf_andPastTheUsualFasts() {
		assertEquals(36.hours, FastCheckIn.askAfter(emptyList()))
		assertEquals(36.hours, FastCheckIn.askAfter(listOf(16.hours, 18.hours)))
		assertEquals(72.hours, FastCheckIn.askAfter(listOf(16.hours, 48.hours)))
	}
}
