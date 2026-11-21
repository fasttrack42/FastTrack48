package com.legbehindneck.fasttrack48.screens.intro

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.legbehindneck.fasttrack48.R
import com.legbehindneck.fasttrack48.ui.theme.FastTrackTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI test that steps through the intro slides.
 */
@RunWith(AndroidJUnit4::class)
class IntroComposeTest {

	@get:Rule
	val composeTestRule = createComposeRule()

	private val context = InstrumentationRegistry.getInstrumentation().targetContext

	@Test
	fun testIntroSlides() {
		// Set up the compose content
		composeTestRule.setContent {
			FastTrackTheme {
				IntroScreen(onComplete = {})
			}
		}

		// The first slide: what the app is for
		composeTestRule.onNodeWithText(context.getString(R.string.intro_00_title)).assertExists()
		composeTestRule.onNodeWithText(context.getString(R.string.next_button)).performClick()

		// The second and last slide: the research, then Done
		composeTestRule.onNodeWithText(context.getString(R.string.intro_03_title)).assertExists()
		composeTestRule.onNodeWithText(context.getString(R.string.done_button)).performClick()
	}

	@Test
	fun testSkipIntro() {
		// Set up the compose content
		composeTestRule.setContent {
			FastTrackTheme {
				IntroScreen(onComplete = {})
			}
		}

		// Verify the first slide is displayed
		composeTestRule.onNodeWithText(context.getString(R.string.intro_00_title)).assertExists()

		// Click the Skip button to skip the intro
		composeTestRule.onNodeWithText(context.getString(R.string.skip_button)).performClick()
	}
}
