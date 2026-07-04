package com.eggplant.detector.detection

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

object NativeDetectionMapper {
    private const val VALUES_PER_DETECTION = 6

    fun map(
        values: FloatArray,
        imageWidth: Int,
        imageHeight: Int,
        metadata: ModelMetadata = ModelMetadata.EGGPLANT_YOLO26M,
    ): List<DetectionBox> {
        require(imageWidth > 0 && imageHeight > 0) { "Image dimensions must be positive." }
        require(values.size % VALUES_PER_DETECTION == 0) { "Native detections must contain six values each." }
        return values.asList().chunked(VALUES_PER_DETECTION).mapNotNull { row ->
            val modelClass = metadata.classFor(row[0].toInt()) ?: return@mapNotNull null
            val confidence = row[1]
            if (!confidence.isFinite() || confidence < metadata.confidenceThreshold || confidence > 1f) {
                return@mapNotNull null
            }
            val left = row[2].coerceIn(0f, imageWidth.toFloat()) / imageWidth
            val top = row[3].coerceIn(0f, imageHeight.toFloat()) / imageHeight
            val right = row[4].coerceIn(0f, imageWidth.toFloat()) / imageWidth
            val bottom = row[5].coerceIn(0f, imageHeight.toFloat()) / imageHeight
            if (right <= left || bottom <= top) return@mapNotNull null
            DetectionBox(modelClass, confidence, NormalizedBox(left, top, right, bottom))
        }
    }
}
