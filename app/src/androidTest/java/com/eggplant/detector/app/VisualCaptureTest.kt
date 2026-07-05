package com.eggplant.detector.app

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import org.junit.Rule
import org.junit.Test

class VisualCaptureTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun captureHomeReferenceFrame() {
        waitForStableFrame()
        saveRoot("home.png")
    }

    @Test
    fun captureLibraryReferenceFrame() {
        composeRule.onNodeWithContentDescription("Navigate to Library").performClick()
        waitForStableFrame()
        saveRoot("library.png")
    }

    private fun waitForStableFrame() {
        composeRule.waitForIdle()
        Thread.sleep(1_500)
        composeRule.waitForIdle()
    }

    private fun saveRoot(name: String) {
        val output = File(
            InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
            name,
        )
        FileOutputStream(output).use { stream ->
            composeRule.onRoot().captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
    }
}
