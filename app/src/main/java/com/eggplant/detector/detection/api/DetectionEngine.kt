package com.eggplant.detector.detection.api

import java.nio.ByteBuffer

enum class EngineState {
    UNINITIALIZED,
    READY,
    FAILED,
    CLOSED,
}

data class RgbFrame(
    val width: Int,
    val height: Int,
    val rgbBytes: ByteArray,
    val timestampMillis: Long,
    val source: InputSource,
    val sceneToken: Long,
) {
    init {
        require(width > 0 && height > 0) { "Frame dimensions must be positive." }
        require(rgbBytes.size == width * height * 3) { "RGB frame must contain three bytes per pixel." }
    }
}

/**
 * A borrowed CameraX RGBA analysis frame. The buffer is only valid for the
 * duration of [RgbaDetectionEngine.detectRgba] and must not be retained.
 * CameraX packs each pixel as red, green, blue, alpha.
 */
data class RgbaFrame(
    val width: Int,
    val height: Int,
    val rowStride: Int,
    val rgbaBytes: ByteBuffer,
    val cropLeft: Int = 0,
    val cropTop: Int = 0,
    val cropWidth: Int = width,
    val cropHeight: Int = height,
    val rotationDegrees: Int,
    val timestampMillis: Long,
    val source: InputSource,
    val sceneToken: Long,
) {
    init {
        require(width > 0 && height > 0) { "Frame dimensions must be positive." }
        require(rowStride >= width * 4) { "RGBA row stride is too small." }
        require(rgbaBytes.isDirect) { "Camera frame buffer must be direct." }
        require(rgbaBytes.capacity() >= rowStride * height) { "Camera frame buffer is incomplete." }
        require(cropLeft >= 0 && cropTop >= 0 && cropWidth > 0 && cropHeight > 0) {
            "Camera frame crop rectangle is invalid."
        }
        require(cropLeft + cropWidth <= width && cropTop + cropHeight <= height) {
            "Camera frame crop rectangle is outside the buffer."
        }
        require(rotationDegrees in setOf(0, 90, 180, 270)) { "Unsupported frame rotation." }
    }
}

interface DetectionEngine : AutoCloseable {
    val state: EngineState
    fun initialize(): EngineState
    fun detect(frame: RgbFrame): Result<DetectionFrame>
}

interface RgbaDetectionEngine {
    fun detectRgba(frame: RgbaFrame): Result<DetectionFrame>
}

interface WarmableDetectionEngine {
    fun warmUp(): Result<Unit>
}

sealed interface InferenceOutcome {
    data class Success(val frame: DetectionFrame) : InferenceOutcome
    data class Failure(val code: String, val cause: Throwable) : InferenceOutcome
    data object Cancelled : InferenceOutcome
}

fun Result<DetectionFrame>.toInferenceOutcome(cancelled: Boolean = false): InferenceOutcome = when {
    cancelled -> InferenceOutcome.Cancelled
    isSuccess -> InferenceOutcome.Success(getOrThrow())
    else -> {
        val failure = exceptionOrNull() ?: IllegalStateException("Unknown inference failure.")
        InferenceOutcome.Failure(
            code = failure.message?.substringBefore(':')?.takeIf { it.startsWith("NCNN_") } ?: "INFERENCE_FAILED",
            cause = failure,
        )
    }
}

class FakeDetectionEngine(
    private val detector: (RgbFrame) -> List<DetectionBox>,
) : DetectionEngine {
    override var state: EngineState = EngineState.UNINITIALIZED
        private set

    override fun initialize(): EngineState {
        if (state != EngineState.CLOSED) {
            state = EngineState.READY
        }
        return state
    }

    override fun detect(frame: RgbFrame): Result<DetectionFrame> {
        if (state != EngineState.READY) {
            return Result.failure(IllegalStateException("Detection engine is not ready."))
        }
        return runCatching {
            DetectionFrame(
                detections = detector(frame),
                timestampMillis = frame.timestampMillis,
                inferenceMillis = 0,
                source = frame.source,
                sceneToken = frame.sceneToken,
            )
        }
    }

    override fun close() {
        state = EngineState.CLOSED
    }
}
