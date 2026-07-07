package com.eggplant.detector.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.platform.app.InstrumentationRegistry
import com.eggplant.detector.R
import com.eggplant.detector.feature.camera.CameraScene
import com.eggplant.detector.detection.api.DetectionBox
import com.eggplant.detector.detection.api.DetectionFrame
import com.eggplant.detector.detection.api.DetectionStatus
import com.eggplant.detector.detection.api.InputSource
import com.eggplant.detector.detection.ncnn.ModelMetadata
import com.eggplant.detector.detection.api.NormalizedBox
import com.eggplant.detector.detection.api.RgbFrame
import com.eggplant.detector.detection.api.StabilityResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import com.eggplant.detector.app.ResultWarning

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
    fun cameraRouteShowsLiveControlsWithoutTemporaryResults() {
        grantCameraPermission()
        composeRule.onNodeWithContentDescription("Open camera").performClick()
        composeRule.onNodeWithContentDescription("Capture scan").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Choose from gallery").assertIsDisplayed()
        composeRule.onAllNodesWithText("temporary", substring = true).assertCountEquals(0)
    }

    @Test
    fun settingsRouteShowsLocalOnlyRows() {
        composeRule.onNodeWithContentDescription("Navigate to Settings").performClick()

        composeRule.onNodeWithText("Personalize your local app experience").assertIsDisplayed()
        composeRule.onNodeWithText("Detection Status").assertIsDisplayed()
        composeRule.onNodeWithText("Units").assertDoesNotExist()
        composeRule.onNodeWithText("Filipino (Tagalog)").assertDoesNotExist()
        composeRule.onNodeWithText("Export History").assertDoesNotExist()
        composeRule.onNodeWithText("Confidence", substring = true).assertDoesNotExist()
    }

    @Test
    fun healthyDetectionSwitchesAreIndependentAndDefaultOff() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val leafLabel = context.getString(R.string.detect_healthy_leaf)
        val plantLabel = context.getString(R.string.detect_healthy_plant)

        composeRule.onNodeWithContentDescription("Navigate to Settings").performClick()

        composeRule.onNodeWithText(leafLabel).performScrollTo().assertIsDisplayed().assertIsOff()
        composeRule.onNodeWithText(plantLabel).performScrollTo().assertIsDisplayed().assertIsOff()
        composeRule.onNodeWithText(leafLabel).performScrollTo().performClick()
        composeRule.onNodeWithText(leafLabel).assertIsOn()
        composeRule.onNodeWithText(plantLabel).assertIsOff()
    }

    @Test
    fun homeActionsOpenTheirDestinations() {
        composeRule.onNodeWithContentDescription("Open notifications").performClick()
        composeRule.onNodeWithText("Notifications").assertIsDisplayed()
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("View All", substring = true).performClick()
        composeRule.onNodeWithText("Review your previous eggplant scans").assertIsDisplayed()
    }

    @Test
    fun reselectingHomeScrollsToTheTop() {
        composeRule.onNodeWithContentDescription("Home content").performScrollToIndex(4)
        composeRule.onNodeWithContentDescription("Navigate to Home").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Eggplant").assertIsDisplayed()
    }

    @Test
    fun returningFromLibraryKeepsHomeControlsInteractive() {
        composeRule.onNodeWithContentDescription("Navigate to Library").performClick()
        composeRule.onNodeWithContentDescription("Navigate to Home").performClick()
        composeRule.onNodeWithText("Eggplant").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Open notifications").performClick()
        composeRule.onNodeWithText("Notifications").assertIsDisplayed()
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Care Guide").performClick()
        composeRule.onNodeWithText("Clean the lens").assertIsDisplayed()
    }

    @Test
    fun libraryFilterButtonOpensFilterSheet() {
        composeRule.onNodeWithContentDescription("Navigate to Library").performClick()
        composeRule.onNodeWithContentDescription("Filter diseases").performClick()
        composeRule.onNodeWithText("Filter diseases").assertIsDisplayed()
        composeRule.onAllNodesWithText("Fruit Disease")[2].performClick()
        composeRule.onNodeWithText("Fruit Rot").assertIsDisplayed()
    }

    @Test
    fun languageSwitchesTheWholeAppAndSurvivesRecreation() {
        composeRule.onNodeWithContentDescription("Navigate to Settings").performClick()
        composeRule.onNodeWithText("Language").performClick()
        composeRule.onNodeWithText("Filipino (Tagalog)").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("Mga Setting").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Aklatan").assertIsDisplayed()
        composeRule.activityRule.scenario.recreate()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("Mga Setting").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Wika").performClick()
        composeRule.onNodeWithText("English").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("Settings").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun historyStartsWithoutFabricatedSampleScans() {
        composeRule.onNodeWithContentDescription("Navigate to History").performClick()
        composeRule.onNodeWithText("Sample scans").assertDoesNotExist()
    }

    @Test
    fun supportActionsOpenCompletePages() {
        composeRule.onNodeWithText("Care Guide").performClick()
        composeRule.onNodeWithText("Clean the lens").assertIsDisplayed()
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Offline Use").performClick()
        composeRule.onNodeWithText("What works offline").assertIsDisplayed()
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Navigate to Library").performClick()
        composeRule.onNodeWithContentDescription("Open help and FAQ").performClick()
        composeRule.onNodeWithText("Does the app need Internet?").assertIsDisplayed()
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Navigate to Settings").performClick()
        composeRule.onNodeWithText("Data & Privacy").performScrollTo().performClick()
        composeRule.onNodeWithText("Local history").assertIsDisplayed()
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("About App").performScrollTo().performClick()
        composeRule.onNodeWithText("Technology").assertIsDisplayed()
    }

    @Test
    fun groupedDetectionCanBeSavedAndReopenedWithoutMockProvider() {
        val viewModel = EggplantAppViewModel(initialHistory = emptyList())
        val detection = DetectionBox(
            ModelMetadata.EGGPLANT_YOLO26M.classFor(5)!!,
            .87f,
            NormalizedBox(.1f, .1f, .8f, .8f),
        )
        val rgb = RgbFrame(2, 2, ByteArray(12), 1, InputSource.CAPTURE, 1)
        val scene = CameraScene(
            rgb,
            DetectionFrame(listOf(detection), 1, 1, InputSource.CAPTURE, 1),
            StabilityResult(DetectionStatus.DISEASE_DETECTED, listOf(detection), listOf(detection), true),
        )

        viewModel.openDetectionScene(scene, detection)
        assertTrue(viewModel.saveCurrentResult())
        assertEquals(1, viewModel.history.value.size)

        viewModel.openHistoryResult(viewModel.history.value.single())
        assertEquals("leaf-spot", viewModel.currentResult.value?.diseaseId)
    }

    @Test
    fun failedDatabaseSaveIsReportedAndDoesNotCreateHistory() {
        val viewModel = EggplantAppViewModel(
            initialHistory = emptyList(),
            scanSaver = { error("Test database failure") },
        )
        var completed: Boolean? = null
        val detection = DetectionBox(
            ModelMetadata.EGGPLANT_YOLO26M.classFor(5)!!,
            .87f,
            NormalizedBox(.1f, .1f, .8f, .8f),
        )
        val rgb = RgbFrame(2, 2, ByteArray(12), 1, InputSource.CAPTURE, 1)
        val scene = CameraScene(
            rgb,
            DetectionFrame(listOf(detection), 1, 1, InputSource.CAPTURE, 1),
            StabilityResult(DetectionStatus.DISEASE_DETECTED, listOf(detection), listOf(detection), true),
        )

        viewModel.openDetectionScene(scene, detection)
        viewModel.saveCurrentResult { success -> completed = success }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        composeRule.waitUntil(5_000) { completed != null }
        assertFalse(completed ?: true)
        assertEquals(SaveState.FAILED, viewModel.saveState.value)
        assertEquals(emptyList<com.eggplant.detector.domain.model.ScanResult>(), viewModel.history.value)
    }

    @Test
    fun snapshotFailureStillOpensAUsableResultWithWarning() {
        val detection = DetectionBox(
            ModelMetadata.EGGPLANT_YOLO26M.classFor(5)!!,
            .87f,
            NormalizedBox(.1f, .1f, .8f, .8f),
        )
        val rgb = RgbFrame(2, 2, ByteArray(12), 1, InputSource.GALLERY, 1)
        val scene = CameraScene(
            rgb,
            DetectionFrame(listOf(detection), 1, 1, InputSource.GALLERY, 1),
            StabilityResult(
                DetectionStatus.DISEASE_DETECTED,
                listOf(detection),
                listOf(detection),
                true,
                listOf(detection),
            ),
        )
        val viewModel = EggplantAppViewModel(
            initialHistory = emptyList(),
            snapshotStager = { error("Test snapshot failure") },
        )
        var ready = false

        viewModel.openDetectionScene(scene, detection) { ready = true }
        composeRule.waitUntil(5_000) { ready }

        assertEquals("Leaf Spot", viewModel.currentResult.value?.name)
        assertEquals(null, viewModel.currentResult.value?.imagePath)
        assertEquals(ResultWarning.SNAPSHOT_UNAVAILABLE, viewModel.resultWarning.value)
    }

    private fun grantCameraPermission() {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("pm grant com.eggplant.detector android.permission.CAMERA")
            .close()
    }
}
