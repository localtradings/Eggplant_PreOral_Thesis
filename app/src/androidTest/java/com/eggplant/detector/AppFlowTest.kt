package com.eggplant.detector

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import org.junit.Rule
import org.junit.Test

class AppFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeRouteShowsReferenceContentWithoutConfidenceOrRisk() {
        composeRule.onNodeWithText("Eggplant").assertIsDisplayed()
        composeRule.onNodeWithText("Disease Detector").assertIsDisplayed()
        composeRule.onNodeWithText("Scan Leaf Now").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Scan your eggplant leaves\nto detect diseases early and\nget treatment advice.",
        ).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Eggplant leaf disease hero photo")
            .assertIsDisplayed()
        composeRule.onAllNodesWithText("Confidence", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Risk", substring = true).assertCountEquals(0)
    }

    @Test
    fun librarySearchAndDetailExcludeConfidence() {
        composeRule.onNodeWithContentDescription("Navigate to Library").performClick()
        composeRule.onNodeWithText("Learn about common eggplant diseases and how to manage them.")
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Leaf Spot disease photo")
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Disease library list").performScrollToIndex(15)
        composeRule.onNodeWithContentDescription("Fruit Rot disease photo").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Disease library list").performScrollToIndex(7)
        composeRule.onNodeWithContentDescription("Open Leaf Spot details").performClick()
        composeRule.onNodeWithText("About Leaf Spot").assertIsDisplayed()
        composeRule.onAllNodesWithText("Confidence", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Risk", substring = true).assertCountEquals(0)
    }

    @Test
    fun cameraResultSaveHistoryDetailFlowShowsConfidenceOnlyOnAllowedScreens() {
        composeRule.onNodeWithContentDescription("Open mock camera").performClick()
        composeRule.onNodeWithText("Place one clear eggplant leaf inside the frame")
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Capture mock scan").performClick()

        composeRule.onNodeWithText("Scan Result").assertIsDisplayed()
        composeRule.onNodeWithText("87%").assertIsDisplayed()
        composeRule.onNodeWithText("Save to History").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Review your previous eggplant scans").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("Open Leaf Spot history details")[0].performClick()
        composeRule.onNodeWithText("History Detail").assertIsDisplayed()
        composeRule.onNodeWithText("87%").assertIsDisplayed()
        composeRule.onAllNodesWithText("Risk", substring = true).assertCountEquals(0)
    }

    @Test
    fun settingsRouteShowsLocalOnlyRows() {
        composeRule.onNodeWithContentDescription("Navigate to Settings").performClick()

        composeRule.onNodeWithText("Personalize your local app experience").assertIsDisplayed()
        composeRule.onNodeWithText("Offline Model Status").assertIsDisplayed()
        composeRule.onNodeWithText("Export History").assertIsDisplayed()
        composeRule.onNodeWithText("Confidence", substring = true).assertDoesNotExist()
    }
}
