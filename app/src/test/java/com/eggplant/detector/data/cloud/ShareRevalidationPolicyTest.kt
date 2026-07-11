package com.eggplant.detector.data.cloud

import com.eggplant.detector.detection.api.DetectionBox
import com.eggplant.detector.detection.api.DetectionFrame
import com.eggplant.detector.detection.api.InputSource
import com.eggplant.detector.detection.api.NormalizedBox
import com.eggplant.detector.detection.ncnn.ModelMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareRevalidationPolicyTest {
    @Test
    fun `uses the highest capture-gated confidence for the same disease`() {
        val frame = frame(
            detection(5, 0.62f),
            detection(5, 0.55f),
            detection(9, 0.96f),
        )

        assertEquals(0.62f, ShareRevalidationPolicy.acceptedConfidence(frame, "leaf-spot"))
    }

    @Test
    fun `rejects a different disease and same disease below fifty percent`() {
        assertNull(
            ShareRevalidationPolicy.acceptedConfidence(
                frame(detection(5, 0.49f), detection(9, 0.96f)),
                "leaf-spot",
            ),
        )
    }

    @Test
    fun `applies capture box gating before accepting a share`() {
        val tiny = DetectionBox(
            modelClass = requireNotNull(ModelMetadata.EGGPLANT_YOLO26M.classFor(5)),
            confidence = 0.90f,
            bounds = NormalizedBox(0f, 0f, 0.01f, 0.01f),
        )

        assertNull(ShareRevalidationPolicy.acceptedConfidence(frame(tiny), "leaf-spot"))
    }

    private fun frame(vararg detections: DetectionBox) = DetectionFrame(
        detections = detections.toList(),
        timestampMillis = 1L,
        inferenceMillis = 1L,
        source = InputSource.CAPTURE,
        sceneToken = 1L,
    )

    private fun detection(classIndex: Int, confidence: Float) = DetectionBox(
        modelClass = requireNotNull(ModelMetadata.EGGPLANT_YOLO26M.classFor(classIndex)),
        confidence = confidence,
        bounds = NormalizedBox(0.1f, 0.1f, 0.7f, 0.7f),
    )
}
