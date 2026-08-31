package com.legbehindneck.fasttrack48.screens.log

import com.legbehindneck.fasttrack48.data.log.FastingLogEntry
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

/**
 * The year strip's bar heights come straight out of [dailyFastedHours], and a silent error
 * there is invisible at one device pixel per day -- so the arithmetic is pinned here.
 */
@ExperimentalTime
class FastDayCoverageTest {

	private val utc = TimeZone.UTC
	private val window = LocalDate(2025, 8, 1)..LocalDate(2025, 9, 30)

	private fun entry(y: Int, m: Int, d: Int, hour: Int, lengthHours: Double, id: Int = 1) =
		FastingLogEntry(
			id = id,
			start = LocalDateTime(y, m, d, hour, 0),
			length = (lengthHours * 60).toInt().minutes,
		)

	private fun hoursOf(
		entries: List<FastingLogEntry>,
		activeFastStart: kotlin.time.Instant? = null,
		now: kotlin.time.Instant = LocalDateTime(2025, 9, 30, 23, 59).toInstant(utc),
		tz: TimeZone = utc,
		window: ClosedRange<LocalDate> = this.window,
	) = dailyFastedHours(entries, activeFastStart, now, window, tz)

	@Test
	fun `multi-day fast is split across its days`() {
		// 27 Aug 18:00 -> 30 Aug 06:00 = 60h: 6 + 24 + 24 + 6.
		val loads = hoursOf(listOf(entry(2025, 8, 27, 18, 60.0)))

		assertEquals(6.0, loads.getValue(LocalDate(2025, 8, 27)).hours, 1e-9)
		assertEquals(24.0, loads.getValue(LocalDate(2025, 8, 28)).hours, 1e-9)
		assertEquals(24.0, loads.getValue(LocalDate(2025, 8, 29)).hours, 1e-9)
		assertEquals(6.0, loads.getValue(LocalDate(2025, 8, 30)).hours, 1e-9)
		assertEquals(4, loads.size)
		// Every day of the run carries the whole fast's length, so the run draws one hue.
		assertTrue(loads.values.all { it.longestFast == 60.hours })
	}

	@Test
	fun `fast inside one day counts only that day`() {
		val loads = hoursOf(listOf(entry(2025, 8, 12, 8, 9.5)))

		assertEquals(1, loads.size)
		assertEquals(9.5, loads.getValue(LocalDate(2025, 8, 12)).hours, 1e-9)
	}

	@Test
	fun `fast ending exactly at midnight does not claim the next day`() {
		val loads = hoursOf(listOf(entry(2025, 8, 12, 20, 4.0)))

		assertEquals(4.0, loads.getValue(LocalDate(2025, 8, 12)).hours, 1e-9)
		assertNull(loads[LocalDate(2025, 8, 13)])
	}

	@Test
	fun `overlapping fasts on one day cap at 24 hours`() {
		val loads = hoursOf(
			listOf(
				entry(2025, 8, 15, 0, 20.0, id = 1),
				entry(2025, 8, 15, 6, 18.0, id = 2),
			)
		)

		assertEquals(24.0, loads.getValue(LocalDate(2025, 8, 15)).hours, 1e-9)
		// The longer of the two decides the colour.
		assertEquals(20.hours, loads.getValue(LocalDate(2025, 8, 15)).longestFast)
	}

	@Test
	fun `running fast is counted only through now`() {
		val start = LocalDateTime(2025, 9, 10, 12, 0).toInstant(utc)
		val now = LocalDateTime(2025, 9, 11, 18, 0).toInstant(utc)

		val loads = hoursOf(entries = emptyList(), activeFastStart = start, now = now)

		assertEquals(12.0, loads.getValue(LocalDate(2025, 9, 10)).hours, 1e-9)
		assertEquals(18.0, loads.getValue(LocalDate(2025, 9, 11)).hours, 1e-9)
		assertNull(loads[LocalDate(2025, 9, 12)])
	}

	@Test
	fun `days outside the window are dropped`() {
		// A fast running from before the window to after it fills the window and no more.
		val loads = hoursOf(
			listOf(entry(2025, 7, 30, 0, 24.0 * 70)),
			window = LocalDate(2025, 8, 1)..LocalDate(2025, 8, 3),
		)

		assertEquals(3, loads.size)
		assertTrue(loads.values.all { it.hours == 24.0 })
	}

	@Test
	fun `spring forward day reports at most 23 hours`() {
		// 30 Mar 2025 is 23 hours long in Europe/Berlin.
		val berlin = TimeZone.of("Europe/Berlin")
		val loads = dailyFastedHours(
			entries = listOf(entry(2025, 3, 29, 12, 48.0)),
			activeFastStart = null,
			now = LocalDateTime(2025, 4, 5, 0, 0).toInstant(berlin),
			window = LocalDate(2025, 3, 1)..LocalDate(2025, 3, 31),
			tz = berlin,
		)

		assertEquals(23.0, loads.getValue(LocalDate(2025, 3, 30)).hours, 1e-9)
	}

	@Test
	fun `an inverted window yields nothing`() {
		val loads = hoursOf(
			listOf(entry(2025, 8, 12, 8, 9.5)),
			window = LocalDate(2025, 9, 1)..LocalDate(2025, 8, 1),
		)

		assertTrue(loads.isEmpty())
	}
}
