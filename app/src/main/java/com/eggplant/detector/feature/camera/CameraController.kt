package com.eggplant.detector.feature.camera

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.Surface
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.eggplant.detector.detection.api.DetectionEngine
import com.eggplant.detector.detection.api.DetectionStatus
import com.eggplant.detector.detection.api.EngineState
import com.eggplant.detector.detection.api.InputSource
import com.eggplant.detector.detection.api.RgbFrame
import com.eggplant.detector.detection.api.StabilityResult
import com.eggplant.detector.detection.tracking.DetectionStabilityTracker
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraController(
    context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val engine: DetectionEngine,
    private val onState: (CameraAnalysisState) -> Unit,
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val mainExecutor = ContextCompat.getMainExecutor(appContext)
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val tracker = DetectionStabilityTracker()
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var imageCapture: ImageCapture? = null
    @Volatile private var paused = false
    @Volatile private var latestScene: CameraScene? = null
    @Volatile private var state = CameraAnalysisState()

    fun start(previewView: PreviewView) {
        emit(state.copy(engineState = EngineState.UNINITIALIZED, error = null))
        analysisExecutor.execute {
            val initialized = engine.initialize()
            if (initialized != EngineState.READY) {
                emit(state.copy(engineState = initialized, error = "Detection model could not be loaded."))
                return@execute
            }
            emit(state.copy(engineState = EngineState.READY))
            val providerFuture = ProcessCameraProvider.getInstance(appContext)
            providerFuture.addListener(
                {
                    runCatching {
                        provider = providerFuture.get()
                        bindCamera(previewView)
                    }.onFailure { error ->
                        emit(state.copy(error = error.message ?: "Camera could not be opened."))
                    }
                },
                mainExecutor,
            )
        }
    }

    fun pauseAnalysis() {
        paused = true
    }

    fun resumeAnalysis() {
        paused = false
    }

    fun currentScene(): CameraScene? = latestScene

    fun markSaved() {
        tracker.markSaved()
        val scene = latestScene ?: return
        val updated = scene.copy(stability = scene.stability.copy(saveEligible = false))
        latestScene = updated
        emit(state.copy(saveEligible = false))
    }

    fun toggleTorch() {
        val activeCamera = camera ?: return
        if (!activeCamera.cameraInfo.hasFlashUnit()) return
        val requested = !state.torchEnabled
        val operation = activeCamera.cameraControl.enableTorch(requested)
        operation.addListener(
            {
                runCatching { operation.get() }
                    .onSuccess { emit(state.copy(torchEnabled = requested, error = null)) }
                    .onFailure { error -> emit(state.copy(error = error.message ?: "Flash is unavailable.")) }
            },
            mainExecutor,
        )
    }

    fun analyzeBitmap(bitmap: Bitmap, source: InputSource, onComplete: (Result<CameraScene>) -> Unit) {
        analysisExecutor.execute {
            val result = runCatching { detectBitmap(bitmap, source) }
            result.onSuccess(::emitStillScene)
            mainExecutor.execute { onComplete(result) }
        }
    }

    fun capturePhoto(onComplete: (Result<CameraScene>) -> Unit) {
        val capture = imageCapture
        if (capture == null) {
            onComplete(Result.failure(IllegalStateException("Camera capture is not ready.")))
            return
        }
        capture.takePicture(
            analysisExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val result = runCatching {
                        val bitmap = image.toBitmap()
                        try {
                            detectBitmap(bitmap, InputSource.CAPTURE, image.imageInfo.rotationDegrees)
                        } finally {
                            bitmap.recycle()
                        }
                    }
                    image.close()
                    result.onSuccess(::emitStillScene)
                    mainExecutor.execute { onComplete(result) }
                }

                override fun onError(exception: androidx.camera.core.ImageCaptureException) {
                    mainExecutor.execute { onComplete(Result.failure(exception)) }
                }
            },
        )
    }

    private fun bindCamera(previewView: PreviewView) {
        val analysisResolutionSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(640, 480),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                ),
            )
            .build()
        val captureResolutionSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(1280, 720),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                ),
            )
            .build()
        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(analysisResolutionSelector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setOutputImageRotationEnabled(true)
            .setTargetRotation(previewView.display?.rotation ?: Surface.ROTATION_0)
            .build()
            .also { useCase -> useCase.setAnalyzer(analysisExecutor, ::analyzeImage) }
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetRotation(previewView.display?.rotation ?: Surface.ROTATION_0)
            .setResolutionSelector(captureResolutionSelector)
            .build()
        provider?.unbindAll()
        camera = provider?.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            analysis,
            capture,
        )
        imageAnalysis = analysis
        imageCapture = capture
        val supportsTorch = camera?.cameraInfo?.hasFlashUnit() == true
        emit(state.copy(torchSupported = supportsTorch, torchEnabled = false, error = null))
    }

    private fun analyzeImage(image: ImageProxy) {
        try {
            if (paused || engine.state != EngineState.READY) return
            val plane = image.planes.firstOrNull() ?: return
            val buffer = plane.buffer.duplicate().apply { rewind() }
            val rgba = ByteArray(buffer.remaining()).also(buffer::get)
            val rgb = CameraFrameConverter.rgbaToRgb(rgba, image.width, image.height, plane.rowStride)
            val rotated = CameraFrameConverter.rotateRgb(
                rgb,
                image.width,
                image.height,
                image.imageInfo.rotationDegrees,
            )
            val rgbFrame = RgbFrame(
                width = rotated.width,
                height = rotated.height,
                rgbBytes = rotated.rgbBytes,
                timestampMillis = SystemClock.elapsedRealtime(),
                source = InputSource.LIVE,
                sceneToken = CameraFrameConverter.sceneToken(rotated.rgbBytes, rotated.width, rotated.height),
            )
            val detection = engine.detect(rgbFrame).getOrThrow()
            val stability = tracker.update(detection)
            latestScene = CameraScene(rgbFrame, detection, stability)
            emit(
                state.copy(
                    engineState = EngineState.READY,
                    status = stability.status,
                    visibleDetections = stability.visibleDetections,
                    stableDetections = stability.stableDetections,
                    saveEligible = stability.saveEligible,
                    inferenceMillis = detection.inferenceMillis,
                    frameWidth = rotated.width,
                    frameHeight = rotated.height,
                    error = null,
                ),
            )
        } catch (error: Throwable) {
            emit(state.copy(error = error.message ?: "Live detection failed."))
        } finally {
            image.close()
        }
    }

    private fun Bitmap.toRgbFrame(source: InputSource): RgbFrame {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        val rgb = ByteArray(width * height * 3)
        var offset = 0
        pixels.forEach { color ->
            rgb[offset++] = (color shr 16 and 0xff).toByte()
            rgb[offset++] = (color shr 8 and 0xff).toByte()
            rgb[offset++] = (color and 0xff).toByte()
        }
        return RgbFrame(
            width = width,
            height = height,
            rgbBytes = rgb,
            timestampMillis = SystemClock.elapsedRealtime(),
            source = source,
            sceneToken = CameraFrameConverter.sceneToken(rgb, width, height),
        )
    }

    private fun detectBitmap(
        bitmap: Bitmap,
        source: InputSource,
        rotationDegrees: Int = 0,
    ): CameraScene {
        check(engine.state == EngineState.READY) { "Detection model is unavailable." }
        val unrotated = bitmap.toRgbFrame(source)
        val rotated = CameraFrameConverter.rotateRgb(
            unrotated.rgbBytes,
            unrotated.width,
            unrotated.height,
            rotationDegrees,
        )
        val rgb = unrotated.copy(
            width = rotated.width,
            height = rotated.height,
            rgbBytes = rotated.rgbBytes,
            sceneToken = CameraFrameConverter.sceneToken(rotated.rgbBytes, rotated.width, rotated.height),
        )
        val detection = engine.detect(rgb).getOrThrow()
        val diseases = detection.detections.filterNot { it.modelClass.isHealthy }
        val status = when {
            diseases.isNotEmpty() -> DetectionStatus.DISEASE_DETECTED
            detection.detections.any { it.modelClass.isHealthy } -> DetectionStatus.HEALTHY
            else -> DetectionStatus.SEARCHING
        }
        return CameraScene(
            rgb,
            detection,
            StabilityResult(status, diseases, diseases, diseases.isNotEmpty()),
        )
    }

    private fun emitStillScene(scene: CameraScene) {
        latestScene = scene
        emit(
            state.copy(
                engineState = EngineState.READY,
                status = scene.stability.status,
                visibleDetections = scene.stability.visibleDetections,
                stableDetections = scene.stability.stableDetections,
                saveEligible = scene.stability.saveEligible,
                inferenceMillis = scene.detectionFrame.inferenceMillis,
                frameWidth = scene.rgbFrame.width,
                frameHeight = scene.rgbFrame.height,
                error = null,
            ),
        )
    }

    private fun emit(newState: CameraAnalysisState) {
        state = newState
        mainExecutor.execute { onState(newState) }
    }

    override fun close() {
        paused = true
        imageAnalysis?.clearAnalyzer()
        mainExecutor.execute { provider?.unbindAll() }
        engine.close()
        analysisExecutor.shutdownNow()
    }
}
