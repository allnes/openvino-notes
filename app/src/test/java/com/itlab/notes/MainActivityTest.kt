package com.itlab.notes

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun mainActivityShowsGreeting() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Hello Android!").assertTextEquals("Hello Android!")
        }
    }

    @Test
    fun greetingPreviewShowsGreeting() {
        composeRule.setContent {
            greetingPreview()
        }

        composeRule.onNodeWithText("Hello Android!").assertTextEquals("Hello Android!")
    }
}
