package com.eggplant.detector.detection.api

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

interface DetectionEngine : AutoCloseable {
    val state: EngineState
    fun initialize(): EngineState
    fun detect(frame: RgbFrame): Result<DetectionFrame>
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
