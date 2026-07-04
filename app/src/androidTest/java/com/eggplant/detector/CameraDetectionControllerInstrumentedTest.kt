package com.eggplant.detector

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eggplant.detector.camera.CameraAnalysisState
import com.eggplant.detector.camera.CameraDetectionController
import com.eggplant.detector.detection.DetectionBox
import com.eggplant.detector.detection.DetectionEngine
import com.eggplant.detector.detection.DetectionFrame
import com.eggplant.detector.detection.EngineState
import com.eggplant.detector.detection.InputSource
import com.eggplant.detector.detection.ModelMetadata
import com.eggplant.detector.detection.NormalizedBox
import com.eggplant.detector.detection.RgbFrame
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraDetectionControllerInstrumentedTest {
    @Test
    fun galleryAnalysisPublishesItsStillSceneToCameraState() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stateLatch = CountDownLatch(1)
        val callbackLatch = CountDownLatch(1)
        var publishedState: CameraAnalysisState? = null
        val controller = CameraDetectionController(
            context = context,
            lifecycleOwner = TestLifecycleOwner(),
            engine = PositiveDetectionEngine(),
            onState = { state ->
                if (state.stableDetections.isNotEmpty()) {
                    publishedState = state
                    stateLatch.countDown()
                }
            },
        )
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)

        try {
            controller.analyzeBitmap(bitmap, InputSource.GALLERY) { callbackLatch.countDown() }

            assertTrue("Gallery callback timed out.", callbackLatch.await(5, TimeUnit.SECONDS))
            assertTrue("Gallery state was not published.", stateLatch.await(5, TimeUnit.SECONDS))
            assertNotNull(publishedState)
            assertEquals("leaf-spot", publishedState?.stableDetections?.single()?.modelClass?.diseaseId)
        } finally {
            bitmap.recycle()
            controller.close()
        }
    }

    private class TestLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle = registry
    }

    private class PositiveDetectionEngine : DetectionEngine {
        override val state: EngineState = EngineState.READY

        override fun initialize(): EngineState = state

        override fun detect(frame: RgbFrame): Result<DetectionFrame> = Result.success(
            DetectionFrame(
                detections = listOf(
                    DetectionBox(
                        modelClass = ModelMetadata.EGGPLANT_YOLO26M.classes[5],
                        confidence = 0.8f,
                        bounds = NormalizedBox(0.1f, 0.1f, 0.9f, 0.9f),
                    ),
                ),
                timestampMillis = frame.timestampMillis,
                inferenceMillis = 5,
                source = frame.source,
                sceneToken = frame.sceneToken,
            ),
        )

        override fun close() = Unit
    }
}
