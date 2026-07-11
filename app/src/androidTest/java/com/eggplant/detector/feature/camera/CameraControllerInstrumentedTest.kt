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
import org.junit.Assert.assertFalse
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

    @Test
    fun repeatedGalleryAnalysisCompletesSequentially() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val controller = CameraController(
            context = context,
            lifecycleOwner = TestLifecycleOwner(),
            engine = PositiveDetectionEngine(),
            onState = {},
        )
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        try {
            val first = analyze(controller, bitmap)
            val second = analyze(controller, bitmap)

            assertTrue(first.isSuccess)
            assertTrue(second.isSuccess)
            assertEquals("leaf-spot", first.getOrThrow().stability.stableDetections.single().modelClass.diseaseId)
            assertEquals("leaf-spot", second.getOrThrow().stability.stableDetections.single().modelClass.diseaseId)
        } finally {
            bitmap.recycle()
            controller.close()
        }
    }

    @Test
    fun duplicateGalleryAnalysisReturnsControlledFailure() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = BlockingDetectionEngine()
        val controller = CameraController(
            context = context,
            lifecycleOwner = TestLifecycleOwner(),
            engine = engine,
            onState = {},
        )
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val firstLatch = CountDownLatch(1)
        val secondLatch = CountDownLatch(1)
        var firstResult: Result<CameraScene>? = null
        var secondResult: Result<CameraScene>? = null
        try {
            controller.analyzeBitmap(bitmap, InputSource.GALLERY) { result ->
                firstResult = result
                firstLatch.countDown()
            }
            assertTrue("First detection did not start.", engine.detectStarted.await(5, TimeUnit.SECONDS))
            controller.analyzeBitmap(bitmap, InputSource.GALLERY) { result ->
                secondResult = result
                secondLatch.countDown()
            }

            assertTrue("Duplicate gallery callback timed out.", secondLatch.await(5, TimeUnit.SECONDS))
            assertFalse(secondResult?.isSuccess == true)
            engine.releaseDetection.countDown()
            assertTrue("First gallery callback timed out.", firstLatch.await(5, TimeUnit.SECONDS))
            assertTrue(firstResult?.isSuccess == true)
        } finally {
            engine.releaseDetection.countDown()
            bitmap.recycle()
            controller.close()
        }
    }

    @Test
    fun detectorFailureReturnsControlledStillFailure() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val controller = CameraController(
            context = context,
            lifecycleOwner = TestLifecycleOwner(),
            engine = FailingDetectionEngine(),
            onState = {},
        )
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        try {
            val result = analyze(controller, bitmap)

            assertFalse(result.isSuccess)
            assertTrue(result.exceptionOrNull()?.message?.contains("detector failed") == true)
        } finally {
            bitmap.recycle()
            controller.close()
        }
    }

    @Test
    fun repeatedLiveStartStopReturnsNoStableDiseaseWithoutSaving() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val controller = CameraController(
            context = context,
            lifecycleOwner = TestLifecycleOwner(),
            engine = PositiveDetectionEngine(),
            onState = {},
        )
        try {
            controller.startLivePreview()
            assertEquals(LivePreviewOutcome.NoStableDetection, controller.finishLivePreview(allowHealthy = false))
            controller.startLivePreview()
            assertEquals(LivePreviewOutcome.NoStableDetection, controller.finishLivePreview(allowHealthy = false))
        } finally {
            controller.close()
        }
    }

    @Test
    fun lifecycleStopCancelsLiveRetentionBeforeAStaleReleaseCanRoute() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val owner = TestLifecycleOwner().apply { start() }
        val controller = CameraController(
            context = context,
            lifecycleOwner = owner,
            engine = PositiveDetectionEngine(),
            onState = {},
        )
        try {
            controller.startLivePreview()

            owner.stop()

            assertEquals(
                LivePreviewOutcome.NoStableDetection,
                controller.finishLivePreview(allowHealthy = false),
            )
        } finally {
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

    private fun analyze(controller: CameraController, bitmap: Bitmap): Result<CameraScene> {
        val latch = CountDownLatch(1)
        var scene: Result<CameraScene>? = null
        controller.analyzeBitmap(bitmap, InputSource.GALLERY) { result ->
            scene = result
            latch.countDown()
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        return requireNotNull(scene)
    }

    private class TestLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle = registry

        fun start() {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        }

        fun stop() {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
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

    private class BlockingDetectionEngine : DetectionEngine {
        val detectStarted = CountDownLatch(1)
        val releaseDetection = CountDownLatch(1)
        override val state: EngineState = EngineState.READY

        override fun initialize(): EngineState = state

        override fun detect(frame: RgbFrame): Result<DetectionFrame> {
            detectStarted.countDown()
            releaseDetection.await(5, TimeUnit.SECONDS)
            return PositiveDetectionEngine().detect(frame)
        }

        override fun close() = Unit
    }

    private class FailingDetectionEngine : DetectionEngine {
        override val state: EngineState = EngineState.READY

        override fun initialize(): EngineState = state

        override fun detect(frame: RgbFrame): Result<DetectionFrame> =
            Result.failure(IllegalStateException("detector failed"))

        override fun close() = Unit
    }
}
