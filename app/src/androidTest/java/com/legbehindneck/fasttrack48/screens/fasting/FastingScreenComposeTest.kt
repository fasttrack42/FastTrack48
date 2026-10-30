package com.legbehindneck.fasttrack48.screens.fasting

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.legbehindneck.fasttrack48.R
import com.legbehindneck.fasttrack48.ui.theme.FastTrackTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.hours

/**
 * Compose UI tests for the Fasting screen: the start disc while idle; the timer, the next
 * milestone and End fast while a fast runs.
 */
@RunWith(AndroidJUnit4::class)
class FastingScreenComposeTest {

	@get:Rule
	val composeTestRule = createComposeRule()

	private val context = InstrumentationRegistry.getInstrumentation().targetContext

	@Test
	fun testFastingScreen_NotFasting_ShowsStartDisc() {
		val vm = FakeFastingViewModel(IFastingViewModel.FastingUiState(isFasting = false))

		composeTestRule.setContent {
			FastTrackTheme {
				FastingScreen(contentPaddingValues = PaddingValues(0.dp), viewModel = vm)
			}
		}

		composeTestRule.onNodeWithContentDescription(context.getString(R.string.start_fast_button))
			.assertExists()
		composeTestRule.onNodeWithText(context.getString(R.string.end_fast_button))
			.assertDoesNotExist()
	}

	@Test
	fun testFastingScreen_Fasting_ShowsTimerMilestoneAndEndFast() {
		// 17 hours in: fat burning (12h) is behind, ketosis (18h) is next, autophagy (24h) after.
		val vm = FakeFastingViewModel(
			IFastingViewModel.FastingUiState(
				isFasting = true,
				elapsedTime = 17.hours,
				elapsedHours = 17.0,
				stageTitle = "Fat burning",
				stageDescription = "Your body is now burning fat for energy.",
			)
		)

		composeTestRule.setContent {
			FastTrackTheme {
				FastingScreen(contentPaddingValues = PaddingValues(0.dp), viewModel = vm)
			}
		}

		val ketosis = context.getString(R.string.fast_ketosis_label)
		val autophagy = context.getString(R.string.fast_autophagy_label)
		composeTestRule.onNodeWithText("17h").assertExists()
		composeTestRule.onNodeWithText(context.getString(R.string.milestone_in, ketosis, "1h"))
			.assertExists()
		composeTestRule.onNodeWithText(context.getString(R.string.milestone_in, autophagy, "7h"))
			.assertExists()
		composeTestRule.onNodeWithText(context.getString(R.string.end_fast_button))
			.assertExists()
		composeTestRule.onNodeWithContentDescription(context.getString(R.string.start_fast_button))
			.assertDoesNotExist()
	}
}
