package com.eggplant.detector.feature.camera

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eggplant.detector.feature.camera.CameraAnalysisState
import com.eggplant.detector.feature.camera.CameraController
import com.eggplant.detector.detection.api.DetectionBox
import com.eggplant.detector.detection.api.DetectionEngine
import com.eggplant.detector.detection.api.DetectionClassPolicy
import com.eggplant.detector.detection.api.DetectionFrame
import com.eggplant.detector.detection.api.DetectionStatus
import com.eggplant.detector.detection.api.EngineState
import com.eggplant.detector.detection.api.InputSource
import com.eggplant.detector.detection.ncnn.ModelMetadata
import com.eggplant.detector.detection.api.NormalizedBox
import com.eggplant.detector.detection.api.RgbFrame
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraControllerInstrumentedTest {
    @Test
    fun galleryAnalysisPublishesItsStillSceneToCameraState() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stateLatch = CountDownLatch(1)
        val callbackLatch = CountDownLatch(1)
        var publishedState: CameraAnalysisState? = null
        val controller = CameraController(
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

    @Test
    fun galleryHealthyResultIsSuppressedByDefault() {
        val result = analyzeGallery(classIndex = 2)

        assertEquals(DetectionStatus.SEARCHING, result.stability.status)
        assertTrue(result.stability.visibleDetections.isEmpty())
        assertTrue(result.stability.confirmedDetections.isEmpty())
    }

    @Test
    fun galleryHealthyLeafIsConfirmedWhenItsPolicyIsEnabled() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val controller = CameraController(
            context = context,
            lifecycleOwner = TestLifecycleOwner(),
            engine = FixedDetectionEngine(classIndex = 2),
            onState = {},
        )
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val latch = CountDownLatch(1)
        var scene: com.eggplant.detector.feature.camera.CameraScene? = null

        try {
            controller.updateClassPolicy(DetectionClassPolicy(detectHealthyLeaf = true))
            controller.analyzeBitmap(bitmap, InputSource.GALLERY) { result ->
                scene = result.getOrNull()
                latch.countDown()
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS))
            assertEquals(DetectionStatus.HEALTHY, scene?.stability?.status)
            assertEquals(2, scene?.stability?.confirmedDetections?.single()?.modelClass?.index)
            assertTrue(scene?.stability?.stableDetections?.isEmpty() == true)
            assertTrue(scene?.stability?.saveEligible == false)
        } finally {
            bitmap.recycle()
            controller.close()
        }
    }

    private fun analyzeGallery(classIndex: Int): com.eggplant.detector.feature.camera.CameraScene {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val controller = CameraController(
            context = context,
            lifecycleOwner = TestLifecycleOwner(),
            engine = FixedDetectionEngine(classIndex),
            onState = {},
        )
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val latch = CountDownLatch(1)
        var scene: com.eggplant.detector.feature.camera.CameraScene? = null
        try {
            controller.analyzeBitmap(bitmap, InputSource.GALLERY) { result ->
                scene = result.getOrThrow()
                latch.countDown()
            }
            assertTrue(latch.await(5, TimeUnit.SECONDS))
            return requireNotNull(scene)
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

    private class FixedDetectionEngine(private val classIndex: Int) : DetectionEngine {
        override val state: EngineState = EngineState.READY

        override fun initialize(): EngineState = state

        override fun detect(frame: RgbFrame): Result<DetectionFrame> = Result.success(
            DetectionFrame(
                detections = listOf(
                    DetectionBox(
                        modelClass = ModelMetadata.EGGPLANT_YOLO26M.classes[classIndex],
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
