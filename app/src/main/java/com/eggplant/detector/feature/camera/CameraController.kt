package com.eggplant.detector.feature.camera

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import android.util.Size
import android.view.View
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.DefaultLifecycleObserver
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
import com.eggplant.detector.detection.api.RgbaDetectionEngine
import com.eggplant.detector.detection.api.RgbaFrame
import com.eggplant.detector.detection.api.WarmableDetectionEngine
import com.eggplant.detector.detection.api.StabilityResult
import com.eggplant.detector.detection.tracking.DetectionStabilityTracker
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.Locale
import java.util.concurrent.TimeUnit

class CameraController(
    context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val engine: DetectionEngine,
    private val onState: (CameraAnalysisState) -> Unit,
    private val closeEngineOnClose: Boolean = true,
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
    private val sessionLock = Any()
    private val stateRevision = AtomicLong(0L)
    private val deliveredRevision = AtomicLong(0L)
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var previewUseCase: Preview? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var imageCapture: ImageCapture? = null
    private var rotationPreviewView: PreviewView? = null
    private var rotationLayoutListener: View.OnLayoutChangeListener? = null
    @Volatile private var closed = false
    @Volatile private var paused = true
    @Volatile private var classPolicy = DetectionClassPolicy()
    @Volatile private var latestScene: CameraScene? = null
    @Volatile private var lastLiveAnalyzeStartedMillis = 0L
    @Volatile private var liveStartedAtMillis = 0L
    @Volatile private var liveFirstFrameLogged = false
    @Volatile private var liveFirstFeedbackLogged = false
    @Volatile private var liveFrameDiagnosticsLogged = false
    @Volatile private var targetRotation = Surface.ROTATION_0
    @Volatile private var state = CameraAnalysisState()

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            cancelForLifecycleStop()
        }

        override fun onDestroy(owner: LifecycleOwner) {
            close()
        }
    }

    init {
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
    }

    fun start(previewView: PreviewView) {
        if (closed || !startRequested.compareAndSet(false, true)) return
        observePreviewRotation(previewView)
        emit(state.copy(engineState = EngineState.UNINITIALIZED, error = null))
        val providerFuture = ProcessCameraProvider.getInstance(appContext)
        providerFuture.addListener(
            {
                if (closed) return@addListener
                runCatching {
                    provider = providerFuture.get()
                    // PreviewView's ViewPort is only available after layout. Posting the
                    // bind keeps preview, analysis and capture on one crop contract.
                    previewView.post { if (!closed) bindCamera(previewView) }
                }.onFailure { error ->
                    emit(state.copy(error = error.message ?: "Camera could not be opened."))
                }
            },
            mainExecutor,
        )
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
            warmDetectorInBackground()
        }
    }

    fun startDetectorOnly() {
        if (closed || !startRequested.compareAndSet(false, true)) return
        emit(state.copy(engineState = EngineState.UNINITIALIZED, error = null))
        enqueueAnalysis(
            onRejected = { emit(state.copy(engineState = EngineState.FAILED, error = "Detection model could not be started.")) },
        ) {
            val initialized = engine.initialize()
            if (!closed) {
                emit(state.copy(engineState = initialized, error = if (initialized == EngineState.READY) null else "Detection model could not be loaded."))
                if (initialized == EngineState.READY) warmDetectorInBackground()
            }
        }
    }

    private fun warmDetectorInBackground() {
        val warmable = engine as? WarmableDetectionEngine ?: return
        enqueueAnalysis(onRejected = {}) { warmable.warmUp() }
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
        synchronized(sessionLock) {
            val token = livePreviewSession.start()
            liveToken.set(token)
            liveStartedAtMillis = SystemClock.elapsedRealtime()
            lastLiveAnalyzeStartedMillis = 0L
            liveFirstFrameLogged = false
            liveFirstFeedbackLogged = false
            liveFrameDiagnosticsLogged = false
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
                    qualityHint = null,
                    error = null,
                ),
            )
        }
    }

    fun stopLivePreview() {
        finishLivePreview(allowHealthy = false)
    }

    fun finishLivePreview(allowHealthy: Boolean): LivePreviewOutcome {
        return synchronized(sessionLock) {
            pauseAnalysis()
            liveToken.set(0L)
            liveStartedAtMillis = 0L
            livePreviewSession.stop(allowHealthy).also {
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
                        qualityHint = null,
                        error = null,
                    ),
                )
            }
        }
    }

    fun updateClassPolicy(policy: DetectionClassPolicy) {
        if (closed) return
        if (classPolicy == policy) return
        synchronized(sessionLock) {
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
                    qualityHint = null,
                    error = null,
                ),
            )
        }
    }

    fun markSaved() {
        if (closed) return
        synchronized(sessionLock) {
            tracker.markSaved()
            val scene = latestScene ?: return
            val updated = scene.copy(stability = scene.stability.copy(saveEligible = false))
            latestScene = updated
            emit(state.copy(saveEligible = false))
        }
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

    fun focusAt(x: Float, y: Float, previewView: PreviewView) {
        val activeCamera = camera ?: return
        val point = previewView.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(3, TimeUnit.SECONDS)
            .build()
        activeCamera.cameraControl.startFocusAndMetering(action)
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

    fun capturePhoto(
        emitScene: Boolean = true,
        onComplete: (Result<CameraScene>) -> Unit,
    ) {
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
                            val rotationDegrees = image.imageInfo.rotationDegrees
                            val bitmap = try {
                                image.toBitmap()
                            } finally {
                                image.close()
                            }
                            try {
                                detectBitmap(bitmap, InputSource.CAPTURE, rotationDegrees)
                            } finally {
                                bitmap.recycle()
                            }
                        }
                        completeStillRequest(requestToken, captureInFlight, result, onComplete, emitScene)
                    }

                    override fun onError(exception: androidx.camera.core.ImageCaptureException) {
                        completeStillRequest(
                            requestToken,
                            captureInFlight,
                            Result.failure(exception),
                            onComplete,
                            emitScene,
                        )
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
        emitScene: Boolean = true,
    ) {
        if (emitScene && isCurrentStillRequest(requestToken)) {
            result.onSuccess(::emitStillScene)
        }
        mainExecutor.execute {
            inFlight?.set(false)
            if (isCurrentStillRequest(requestToken)) {
                onComplete(result)
            } else {
                onComplete(Result.failure(CancellationException("Still-image analysis was cancelled.")))
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

    /**
     * CameraX is lifecycle-bound, but in-flight app work needs an explicit
     * cancellation boundary too. Otherwise a frame/capture completed after a
     * background transition can reopen a result belonging to a dead preview.
     */
    private fun cancelForLifecycleStop() {
        if (closed) return
        stillRequestToken.incrementAndGet()
        captureInFlight.set(false)
        galleryInFlight.set(false)
        synchronized(sessionLock) {
            pauseAnalysis()
            liveToken.set(0L)
            liveStartedAtMillis = 0L
            livePreviewSession.cancel()
            tracker.reset()
            latestScene = null
            emit(
                state.copy(
                    livePreviewActive = false,
                    isStillImageProcessing = false,
                    status = DetectionStatus.SEARCHING,
                    visibleDetections = emptyList(),
                    stableDetections = emptyList(),
                    confirmedDetections = emptyList(),
                    saveEligible = false,
                    qualityHint = null,
                    error = null,
                ),
            )
        }
    }

    private fun observePreviewRotation(previewView: PreviewView) {
        rotationLayoutListener?.let { listener ->
            rotationPreviewView?.removeOnLayoutChangeListener(listener)
        }
        rotationPreviewView = previewView
        val listener = View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            updateTargetRotation(view.display?.rotation ?: Surface.ROTATION_0)
        }
        rotationLayoutListener = listener
        previewView.addOnLayoutChangeListener(listener)
        updateTargetRotation(previewView.display?.rotation ?: Surface.ROTATION_0)
    }

    /** Safe to call on any thread; CameraX use cases are changed on main. */
    private fun updateTargetRotation(rotation: Int) {
        if (rotation !in setOf(Surface.ROTATION_0, Surface.ROTATION_90, Surface.ROTATION_180, Surface.ROTATION_270)) {
            return
        }
        if (targetRotation == rotation) return
        targetRotation = rotation
        mainExecutor.execute {
            if (closed) return@execute
            previewUseCase?.targetRotation = rotation
            imageAnalysis?.targetRotation = rotation
            imageCapture?.targetRotation = rotation
        }
    }

    private fun bindCamera(previewView: PreviewView) {
        if (closed) return
        val displayRotation = previewView.display?.rotation ?: targetRotation
        updateTargetRotation(displayRotation)
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
                    Size(1024, 768),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                ),
            )
            .build()
        val preview = Preview.Builder()
            .setTargetRotation(displayRotation)
            .build()
            .also { it.surfaceProvider = previewView.surfaceProvider }
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(analysisResolutionSelector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setOutputImageRotationEnabled(true)
            .setTargetRotation(displayRotation)
            .build()
            .also { useCase -> useCase.setAnalyzer(analysisExecutor, ::analyzeImage) }
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetRotation(displayRotation)
            .setResolutionSelector(captureResolutionSelector)
            .build()
        provider?.unbindAll()
        val viewPort = previewView.viewPort
        camera = if (viewPort != null) {
            val group = UseCaseGroup.Builder()
                .setViewPort(viewPort)
                .addUseCase(preview)
                .addUseCase(analysis)
                .addUseCase(capture)
                .build()
            provider?.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, group)
        } else {
            provider?.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis,
                capture,
            )
        }
        previewUseCase = preview
        imageAnalysis = analysis
        imageCapture = capture
        val supportsTorch = camera?.cameraInfo?.hasFlashUnit() == true
        emit(state.copy(torchSupported = supportsTorch, torchEnabled = false, livePreviewActive = false, error = null))
    }

    private fun analyzeImage(image: ImageProxy) {
        val token = liveToken.get()
        try {
            val now = SystemClock.elapsedRealtime()
            if (closed || paused || token == 0L || engine.state != EngineState.READY) return
            if (now - lastLiveAnalyzeStartedMillis < LIVE_FRAME_THROTTLE_MILLIS) return
            lastLiveAnalyzeStartedMillis = now
            val conversionStartedAtNanos = SystemClock.elapsedRealtimeNanos()
            val plane = image.planes.firstOrNull() ?: return
            val sourceBuffer = plane.buffer.slice()
            val crop = image.cropRect
            val cropWidth = crop.width()
            val cropHeight = crop.height()
            if (cropWidth <= 0 || cropHeight <= 0) return
            val rotationDegrees = image.imageInfo.rotationDegrees
            logLiveFrameDiagnosticsIfNeeded(
                token = token,
                width = image.width,
                height = image.height,
                rowStride = plane.rowStride,
                pixelStride = plane.pixelStride,
                cropWidth = cropWidth,
                cropHeight = cropHeight,
                rotationDegrees = rotationDegrees,
                directBuffer = sourceBuffer.isDirect,
            )
            val frameQuality = FrameQualityEvaluator.evaluateRgba(
                sourceBuffer,
                image.width,
                image.height,
                plane.rowStride,
                crop.left,
                crop.top,
                cropWidth,
                cropHeight,
            )
            val outputWidth = if (rotationDegrees in setOf(90, 270)) cropHeight else cropWidth
            val outputHeight = if (rotationDegrees in setOf(90, 270)) cropWidth else cropHeight
            val directEngine = engine as? RgbaDetectionEngine
            val directFrame = if (sourceBuffer.isDirect && directEngine != null) {
                RgbaFrame(
                    width = image.width,
                    height = image.height,
                    rowStride = plane.rowStride,
                    rgbaBytes = sourceBuffer,
                    cropLeft = crop.left,
                    cropTop = crop.top,
                    cropWidth = cropWidth,
                    cropHeight = cropHeight,
                    rotationDegrees = rotationDegrees,
                    timestampMillis = now,
                    source = InputSource.LIVE,
                    sceneToken = CameraFrameConverter.sceneTokenRgba(
                        sourceBuffer,
                        image.width,
                        image.height,
                        plane.rowStride,
                        crop.left,
                        crop.top,
                        cropWidth,
                        cropHeight,
                    ),
                )
            } else {
                null
            }
            var fallbackRgb: RotatedRgb? = null
            fun materializeFallbackRgb(): RotatedRgb {
                fallbackRgb?.let { return it }
                val rgba = CameraFrameConverter.copyRgbaPlane(
                    sourceBuffer,
                    plane.rowStride * image.height,
                )
                val rgb = CameraFrameConverter.rgbaToRgb(
                    rgba,
                    image.width,
                    image.height,
                    plane.rowStride,
                    crop.left,
                    crop.top,
                    cropWidth,
                    cropHeight,
                )
                return CameraFrameConverter.rotateRgb(rgb, cropWidth, cropHeight, rotationDegrees)
                    .also { fallbackRgb = it }
            }
            val sceneToken = directFrame?.sceneToken
                ?: CameraFrameConverter.sceneToken(
                    materializeFallbackRgb().rgbBytes,
                    outputWidth,
                    outputHeight,
                )
            val conversionMillis = (SystemClock.elapsedRealtimeNanos() - conversionStartedAtNanos) / 1_000_000
            val rawDetection = if (directFrame != null) {
                requireNotNull(directEngine).detectRgba(directFrame).getOrElse { directError ->
                    logDirectFrameFallback(directError)
                    val rotated = materializeFallbackRgb()
                    engine.detect(
                        RgbFrame(
                            width = rotated.width,
                            height = rotated.height,
                            rgbBytes = rotated.rgbBytes,
                            timestampMillis = now,
                            source = InputSource.LIVE,
                            sceneToken = sceneToken,
                        ),
                    ).getOrThrow()
                }
            } else {
                val rotated = materializeFallbackRgb()
                engine.detect(
                    RgbFrame(
                        width = rotated.width,
                        height = rotated.height,
                        rgbBytes = rotated.rgbBytes,
                        timestampMillis = now,
                        source = InputSource.LIVE,
                        sceneToken = sceneToken,
                    ),
                ).getOrThrow()
            }
            val gatedDetection = gateDetections(rawDetection)
            val closeUp = gatedDetection.detections.maxOfOrNull {
                (it.bounds.right - it.bounds.left) * (it.bounds.bottom - it.bounds.top)
            }?.let { it > .82f } == true
            val qualityHint = if (closeUp) FrameQualityHint.TOO_CLOSE else frameQuality
            // Poor frames should offer guidance instead of accidentally
            // confirming a Healthy result. Disease boxes remain visible so a
            // valid lesion is never silently discarded merely for being dark.
            val qualityFilteredDetection = if (qualityHint == null) gatedDetection else gatedDetection.copy(
                detections = gatedDetection.detections.filterNot { it.modelClass.isHealthy },
            )
            val liveUpdate = synchronized(sessionLock) {
                if (!isCurrentLiveRequest(token)) return@synchronized null
                val trackerStability = tracker.update(qualityFilteredDetection)
                if (qualityHint != null) livePreviewSession.discardHealthy()
                val stability = if (qualityHint == null) trackerStability else trackerStability.withoutHealthyConfirmation()
                qualityFilteredDetection to stability
            } ?: return
            val (detection, stability) = liveUpdate
            logLiveLatencyIfNeeded(
                token = token,
                analyzeStartedAtMillis = now,
                analyzeFinishedAtMillis = SystemClock.elapsedRealtime(),
                detection = detection,
                stability = stability,
                conversionMillis = conversionMillis,
            )
            if (stability.visibleDetections.isNotEmpty() || stability.confirmedDetections.isNotEmpty()) {
                val rotated = materializeFallbackRgb()
                val rgbFrame = RgbFrame(
                    width = rotated.width,
                    height = rotated.height,
                    rgbBytes = rotated.rgbBytes,
                    timestampMillis = now,
                    source = InputSource.LIVE,
                    sceneToken = sceneToken,
                )
                synchronized(sessionLock) {
                    if (isCurrentLiveRequest(token)) {
                        val scene = CameraScene(rgbFrame, detection, stability)
                        latestScene = scene
                        livePreviewSession.record(token, scene)
                    }
                }
            }
            synchronized(sessionLock) {
                if (isCurrentLiveRequest(token)) {
                    emit(
                        state.copy(
                            engineState = EngineState.READY,
                            status = stability.status,
                            visibleDetections = stability.visibleDetections,
                            stableDetections = stability.stableDetections,
                            confirmedDetections = stability.confirmedDetections,
                            saveEligible = stability.saveEligible,
                            inferenceMillis = detection.inferenceMillis,
                            frameWidth = outputWidth,
                            frameHeight = outputHeight,
                            livePreviewActive = true,
                            qualityHint = qualityHint,
                            error = null,
                        ),
                    )
                }
            }
        } catch (error: Throwable) {
            synchronized(sessionLock) {
                if (isCurrentLiveRequest(token)) {
                    emit(state.copy(error = error.message ?: "Live detection failed."))
                }
            }
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
        val startedAtNanos = SystemClock.elapsedRealtimeNanos()
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
        val conversionMillis = (SystemClock.elapsedRealtimeNanos() - startedAtNanos) / 1_000_000
        val rawDetection = engine.detect(rgb).getOrThrow()
        val detection = gateDetections(rawDetection)
        if (BuildConfig.DEBUG) {
            val totalMillis = (SystemClock.elapsedRealtimeNanos() - startedAtNanos) / 1_000_000
            Log.d(
                LOG_TAG,
                "still_latency source=$source frame=${rgb.width}x${rgb.height} " +
                    "conversion_ms=$conversionMillis inference_ms=${detection.inferenceMillis} " +
                    "total_ms=$totalMillis detections=${detection.detections.size}",
            )
        }
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

    private fun StabilityResult.withoutHealthyConfirmation(): StabilityResult {
        val visible = visibleDetections.filterNot { it.modelClass.isHealthy }
        val confirmed = confirmedDetections.filterNot { it.modelClass.isHealthy }
        val status = if (confirmed.any { !it.modelClass.isHealthy }) {
            DetectionStatus.DISEASE_DETECTED
        } else {
            DetectionStatus.SEARCHING
        }
        return copy(
            status = status,
            visibleDetections = visible,
            confirmedDetections = confirmed,
        )
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

    /**
     * Debug-only, non-sensitive frame diagnostics for OEM/ABI investigations.
     * It intentionally omits installation IDs, serials, image contents, URIs,
     * account data, and precise device names.
     */
    private fun logLiveFrameDiagnosticsIfNeeded(
        token: Long,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int,
        cropWidth: Int,
        cropHeight: Int,
        rotationDegrees: Int,
        directBuffer: Boolean,
    ) {
        if (!BuildConfig.DEBUG || token != liveToken.get() || liveFrameDiagnosticsLogged) return
        liveFrameDiagnosticsLogged = true
        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        Log.d(
            LOG_TAG,
            "live_camera_frame api=${Build.VERSION.SDK_INT} abi=$abi " +
                "frame=${width}x$height crop=${cropWidth}x$cropHeight " +
                "row_stride=$rowStride pixel_stride=$pixelStride rotation=$rotationDegrees " +
                "direct_buffer=$directBuffer",
        )
    }

    private fun logDirectFrameFallback(error: Throwable) {
        if (!BuildConfig.DEBUG) return
        Log.w(
            LOG_TAG,
            "live_direct_rgba_failed_falling_back_to_rgb type=${error::class.java.simpleName}",
        )
    }

    private fun logLiveLatencyIfNeeded(
        token: Long,
        analyzeStartedAtMillis: Long,
        analyzeFinishedAtMillis: Long,
        detection: DetectionFrame,
        stability: StabilityResult,
        conversionMillis: Long,
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
                    "conversion_ms=$conversionMillis inference_ms=${detection.inferenceMillis} " +
                    "detections=${detection.detections.size}",
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
                qualityHint = null,
                error = null,
            ),
        )
    }

    private fun emit(newState: CameraAnalysisState) {
        if (closed) return
        val revision = stateRevision.incrementAndGet()
        val versioned = newState.copy(revision = revision)
        state = versioned
        mainExecutor.execute {
            if (!closed && deliveredRevision.getAndUpdate { previous -> maxOf(previous, revision) } <= revision) {
                onState(versioned)
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        paused = true
        liveToken.set(0L)
        stillRequestToken.incrementAndGet()
        captureInFlight.set(false)
        galleryInFlight.set(false)
        rotationLayoutListener?.let { listener ->
            rotationPreviewView?.removeOnLayoutChangeListener(listener)
        }
        rotationLayoutListener = null
        rotationPreviewView = null
        imageAnalysis?.clearAnalyzer()
        mainExecutor.execute {
            runCatching { provider?.unbindAll() }
            camera = null
            previewUseCase = null
            imageAnalysis = null
            imageCapture = null
            provider = null
        }
        if (closeEngineOnClose) engine.close()
        analysisExecutor.shutdownNow()
    }

    companion object {
        private const val LIVE_FRAME_THROTTLE_MILLIS = 150L
        private const val LOG_TAG = "EggplantDetection"
    }
}
