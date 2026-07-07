package com.eggplant.detector.feature.camera

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
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
import com.eggplant.detector.BuildConfig
import com.eggplant.detector.detection.api.DetectionEngine
import com.eggplant.detector.detection.api.DetectionClassPolicy
import com.eggplant.detector.detection.api.DetectionFrame
import com.eggplant.detector.detection.api.DetectionGate
import com.eggplant.detector.detection.api.DetectionGateDecision
import com.eggplant.detector.detection.api.DetectionBox
import com.eggplant.detector.detection.api.DetectionStatus
import com.eggplant.detector.detection.api.EngineState
import com.eggplant.detector.detection.api.InputSource
import com.eggplant.detector.detection.api.RgbFrame
import com.eggplant.detector.detection.api.StabilityResult
import com.eggplant.detector.detection.tracking.DetectionStabilityTracker
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.Locale

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
    private val livePreviewSession = LivePreviewSession()
    private val startRequested = AtomicBoolean(false)
    private val captureInFlight = AtomicBoolean(false)
    private val galleryInFlight = AtomicBoolean(false)
    private val stillRequestToken = AtomicLong(0L)
    private val liveToken = AtomicLong(0L)
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var imageCapture: ImageCapture? = null
    @Volatile private var closed = false
    @Volatile private var paused = true
    @Volatile private var classPolicy = DetectionClassPolicy()
    @Volatile private var latestScene: CameraScene? = null
    @Volatile private var lastLiveAnalyzeStartedMillis = 0L
    @Volatile private var liveStartedAtMillis = 0L
    @Volatile private var liveFirstFrameLogged = false
    @Volatile private var liveFirstFeedbackLogged = false
    @Volatile private var state = CameraAnalysisState()

    fun start(previewView: PreviewView) {
        if (closed || !startRequested.compareAndSet(false, true)) return
        emit(state.copy(engineState = EngineState.UNINITIALIZED, error = null))
        enqueueAnalysis(
            onRejected = {
                emit(state.copy(engineState = EngineState.FAILED, error = "Detection model could not be started."))
            },
        ) {
            val initialized = engine.initialize()
            if (closed) return@enqueueAnalysis
            if (initialized != EngineState.READY) {
                emit(state.copy(engineState = initialized, error = "Detection model could not be loaded."))
                return@enqueueAnalysis
            }
            emit(state.copy(engineState = EngineState.READY))
            val providerFuture = ProcessCameraProvider.getInstance(appContext)
            providerFuture.addListener(
                {
                    if (closed) return@addListener
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

    private fun pauseAnalysis() {
        paused = true
    }

    private fun resumeAnalysis() {
        paused = false
    }

    fun currentScene(): CameraScene? = latestScene

    fun startLivePreview() {
        if (closed || engine.state != EngineState.READY) return
        val token = livePreviewSession.start()
        liveToken.set(token)
        liveStartedAtMillis = SystemClock.elapsedRealtime()
        lastLiveAnalyzeStartedMillis = 0L
        liveFirstFrameLogged = false
        liveFirstFeedbackLogged = false
        resumeAnalysis()
        tracker.reset()
        latestScene = null
        emit(
            state.copy(
                livePreviewActive = true,
                status = DetectionStatus.SEARCHING,
                visibleDetections = emptyList(),
                stableDetections = emptyList(),
                confirmedDetections = emptyList(),
                saveEligible = false,
                error = null,
            ),
        )
    }

    fun stopLivePreview() {
        finishLivePreview(allowHealthy = false)
    }

    fun finishLivePreview(allowHealthy: Boolean): LivePreviewOutcome {
        pauseAnalysis()
        liveToken.set(0L)
        liveStartedAtMillis = 0L
        val outcome = livePreviewSession.stop(allowHealthy)
        tracker.reset()
        latestScene = null
        emit(
            state.copy(
                livePreviewActive = false,
                status = DetectionStatus.SEARCHING,
                visibleDetections = emptyList(),
                stableDetections = emptyList(),
                confirmedDetections = emptyList(),
                saveEligible = false,
                error = null,
            ),
        )
        return outcome
    }

    fun updateClassPolicy(policy: DetectionClassPolicy) {
        if (closed) return
        if (classPolicy == policy) return
        classPolicy = policy
        tracker.reset()
        latestScene = null
        emit(
            state.copy(
                status = DetectionStatus.SEARCHING,
                visibleDetections = emptyList(),
                stableDetections = emptyList(),
                confirmedDetections = emptyList(),
                saveEligible = false,
                error = null,
            ),
        )
    }

    fun markSaved() {
        if (closed) return
        tracker.markSaved()
        val scene = latestScene ?: return
        val updated = scene.copy(stability = scene.stability.copy(saveEligible = false))
        latestScene = updated
        emit(state.copy(saveEligible = false))
    }

    fun toggleTorch() {
        if (closed) return
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
        if (closed) {
            onComplete(Result.failure(IllegalStateException("Camera controller is closed.")))
            return
        }
        val inFlight = if (source == InputSource.GALLERY) galleryInFlight else null
        if (inFlight != null && !inFlight.compareAndSet(false, true)) {
            onComplete(Result.failure(IllegalStateException("Gallery analysis is already running.")))
            return
        }
        val requestToken = stillRequestToken.incrementAndGet()
        enqueueAnalysis(
            onRejected = {
                inFlight?.set(false)
                onComplete(Result.failure(IllegalStateException("Detection executor is unavailable.")))
            },
        ) {
            val result = runCatching { detectBitmap(bitmap, source) }
            completeStillRequest(requestToken, inFlight, result, onComplete)
        }
    }

    fun capturePhoto(onComplete: (Result<CameraScene>) -> Unit) {
        if (closed) {
            onComplete(Result.failure(IllegalStateException("Camera controller is closed.")))
            return
        }
        if (!captureInFlight.compareAndSet(false, true)) {
            onComplete(Result.failure(IllegalStateException("A capture is already running.")))
            return
        }
        val capture = imageCapture
        if (capture == null) {
            captureInFlight.set(false)
            onComplete(Result.failure(IllegalStateException("Camera capture is not ready.")))
            return
        }
        val requestToken = stillRequestToken.incrementAndGet()
        try {
            capture.takePicture(
                analysisExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val result = runCatching {
                            check(!closed) { "Camera controller is closed." }
                            val bitmap = image.toBitmap()
                            try {
                                detectBitmap(bitmap, InputSource.CAPTURE, image.imageInfo.rotationDegrees)
                            } finally {
                                bitmap.recycle()
                            }
                        }
                        image.close()
                        completeStillRequest(requestToken, captureInFlight, result, onComplete)
                    }

                    override fun onError(exception: androidx.camera.core.ImageCaptureException) {
                        completeStillRequest(requestToken, captureInFlight, Result.failure(exception), onComplete)
                    }
                },
            )
        } catch (error: RejectedExecutionException) {
            captureInFlight.set(false)
            mainExecutor.execute {
                onComplete(Result.failure(IllegalStateException("Detection executor is unavailable.", error)))
            }
        } catch (error: RuntimeException) {
            captureInFlight.set(false)
            mainExecutor.execute { onComplete(Result.failure(error)) }
        }
    }

    private fun completeStillRequest(
        requestToken: Long,
        inFlight: AtomicBoolean?,
        result: Result<CameraScene>,
        onComplete: (Result<CameraScene>) -> Unit,
    ) {
        if (isCurrentStillRequest(requestToken)) {
            result.onSuccess(::emitStillScene)
        }
        mainExecutor.execute {
            inFlight?.set(false)
            if (isCurrentStillRequest(requestToken)) {
                onComplete(result)
            }
        }
    }

    private fun isCurrentStillRequest(requestToken: Long): Boolean = !closed && stillRequestToken.get() == requestToken

    private fun enqueueAnalysis(onRejected: () -> Unit, task: () -> Unit): Boolean {
        if (closed || analysisExecutor.isShutdown || analysisExecutor.isTerminated) {
            mainExecutor.execute(onRejected)
            return false
        }
        return try {
            analysisExecutor.execute {
                if (!closed) task()
            }
            true
        } catch (error: RejectedExecutionException) {
            mainExecutor.execute(onRejected)
            false
        }
    }

    private fun bindCamera(previewView: PreviewView) {
        if (closed) return
        val analysisResolutionSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(1024, 768),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                ),
            )
            .build()
        val captureResolutionSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(1280, 960),
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
        emit(state.copy(torchSupported = supportsTorch, torchEnabled = false, livePreviewActive = false, error = null))
    }

    private fun analyzeImage(image: ImageProxy) {
        try {
            val token = liveToken.get()
            val now = SystemClock.elapsedRealtime()
            if (closed || paused || token == 0L || engine.state != EngineState.READY) return
            if (now - lastLiveAnalyzeStartedMillis < LIVE_FRAME_THROTTLE_MILLIS) return
            lastLiveAnalyzeStartedMillis = now
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
                timestampMillis = now,
                source = InputSource.LIVE,
                sceneToken = CameraFrameConverter.sceneToken(rotated.rgbBytes, rotated.width, rotated.height),
            )
            val rawDetection = engine.detect(rgbFrame).getOrThrow()
            if (!isCurrentLiveRequest(token)) return
            val detection = gateDetections(rawDetection)
            val stability = tracker.update(detection)
            logLiveLatencyIfNeeded(
                token = token,
                analyzeStartedAtMillis = now,
                analyzeFinishedAtMillis = SystemClock.elapsedRealtime(),
                detection = detection,
                stability = stability,
            )
            val scene = CameraScene(rgbFrame, detection, stability)
            latestScene = scene
            livePreviewSession.record(token, scene)
            emit(
                state.copy(
                    engineState = EngineState.READY,
                    status = stability.status,
                    visibleDetections = stability.visibleDetections,
                    stableDetections = stability.stableDetections,
                    confirmedDetections = stability.confirmedDetections,
                    saveEligible = stability.saveEligible,
                    inferenceMillis = detection.inferenceMillis,
                    frameWidth = rotated.width,
                    frameHeight = rotated.height,
                    livePreviewActive = true,
                    error = null,
                ),
            )
        } catch (error: Throwable) {
            if (!closed) emit(state.copy(error = error.message ?: "Live detection failed."))
        } finally {
            image.close()
        }
    }

    private fun isCurrentLiveRequest(token: Long): Boolean = !closed && !paused && liveToken.get() == token

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
        val rawDetection = engine.detect(rgb).getOrThrow()
        val detection = gateDetections(rawDetection)
        val diseases = detection.detections.filterNot { it.modelClass.isHealthy }
        val status = when {
            diseases.isNotEmpty() -> DetectionStatus.DISEASE_DETECTED
            detection.detections.any { it.modelClass.isHealthy } -> DetectionStatus.HEALTHY
            else -> DetectionStatus.SEARCHING
        }
        return CameraScene(
            rgb,
            detection,
            StabilityResult(
                status = status,
                stableDetections = diseases,
                visibleDetections = detection.detections,
                saveEligible = diseases.isNotEmpty(),
                confirmedDetections = detection.detections,
            ),
        )
    }

    private fun gateDetections(rawDetection: DetectionFrame): DetectionFrame {
        val policyFiltered = rawDetection.copy(detections = classPolicy.filter(rawDetection.detections))
        val gated = DetectionGate.filter(policyFiltered) { detection, decision ->
            logRejectedDetection(rawDetection.source, detection, decision)
        }
        if (BuildConfig.DEBUG) {
            Log.d(
                LOG_TAG,
                "source=${rawDetection.source} raw=${rawDetection.detections.size} " +
                    "policy=${policyFiltered.detections.size} gated=${gated.detections.size}",
            )
        }
        return gated
    }

    private fun logRejectedDetection(
        source: InputSource,
        detection: DetectionBox,
        decision: DetectionGateDecision,
    ) {
        if (!BuildConfig.DEBUG) return
        val area = (detection.bounds.right - detection.bounds.left) *
            (detection.bounds.bottom - detection.bounds.top)
        Log.d(
            LOG_TAG,
            "source=$source rejected class=${detection.modelClass.index}:${detection.modelClass.modelLabel} " +
                "confidence=${String.format(Locale.US, "%.3f", detection.confidence)} " +
                "area=${String.format(Locale.US, "%.4f", area)} reason=${decision.reason}",
        )
    }

    private fun logLiveLatencyIfNeeded(
        token: Long,
        analyzeStartedAtMillis: Long,
        analyzeFinishedAtMillis: Long,
        detection: DetectionFrame,
        stability: StabilityResult,
    ) {
        if (!BuildConfig.DEBUG || token != liveToken.get()) return
        val startedAt = liveStartedAtMillis
        if (startedAt == 0L) return
        if (!liveFirstFrameLogged) {
            liveFirstFrameLogged = true
            Log.d(
                LOG_TAG,
                "live_first_frame_latency_ms=${analyzeFinishedAtMillis - startedAt} " +
                    "analysis_queue_ms=${analyzeStartedAtMillis - startedAt} " +
                    "inference_ms=${detection.inferenceMillis} detections=${detection.detections.size}",
            )
        }
        if (!liveFirstFeedbackLogged && stability.status != DetectionStatus.SEARCHING) {
            liveFirstFeedbackLogged = true
            Log.d(
                LOG_TAG,
                "live_first_feedback_latency_ms=${analyzeFinishedAtMillis - startedAt} " +
                    "status=${stability.status} confirmed=${stability.confirmedDetections.size}",
            )
        }
    }

    private fun emitStillScene(scene: CameraScene) {
        latestScene = scene
        emit(
            state.copy(
                engineState = EngineState.READY,
                status = scene.stability.status,
                visibleDetections = scene.stability.visibleDetections,
                stableDetections = scene.stability.stableDetections,
                confirmedDetections = scene.stability.confirmedDetections,
                saveEligible = scene.stability.saveEligible,
                inferenceMillis = scene.detectionFrame.inferenceMillis,
                frameWidth = scene.rgbFrame.width,
                frameHeight = scene.rgbFrame.height,
                livePreviewActive = false,
                error = null,
            ),
        )
    }

    private fun emit(newState: CameraAnalysisState) {
        if (closed) return
        state = newState
        mainExecutor.execute {
            if (!closed) onState(newState)
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        paused = true
        liveToken.set(0L)
        stillRequestToken.incrementAndGet()
        captureInFlight.set(false)
        galleryInFlight.set(false)
        imageAnalysis?.clearAnalyzer()
        mainExecutor.execute {
            runCatching { provider?.unbindAll() }
            camera = null
            imageAnalysis = null
            imageCapture = null
            provider = null
        }
        engine.close()
        analysisExecutor.shutdownNow()
    }

    companion object {
        private const val LIVE_FRAME_THROTTLE_MILLIS = 150L
        private const val LOG_TAG = "EggplantDetection"
    }
}
