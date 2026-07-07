package com.eggplant.detector.detection.api

import com.eggplant.detector.detection.ncnn.ModelMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionGateTest {
    @Test
    fun `fruit borer class order remains stable before applying override`() {
        val fruitBorer = ModelMetadata.EGGPLANT_YOLO26M.classFor(1)

        assertEquals("Fruit_borer", fruitBorer?.modelLabel)
        assertEquals("fruit-borer", fruitBorer?.diseaseId)
    }

    @Test
    fun `live uses low confidence threshold for non fruit borer diseases`() {
        val accepted = DetectionGate.evaluate(detection(classIndex = 5, confidence = 0.15f), InputSource.LIVE)

        assertTrue(accepted.accepted)
    }

    @Test
    fun `capture and gallery use stricter thresholds than live`() {
        val captureLow = DetectionGate.evaluate(detection(classIndex = 5, confidence = 0.19f), InputSource.CAPTURE)
        val captureValid = DetectionGate.evaluate(detection(classIndex = 5, confidence = 0.20f), InputSource.CAPTURE)
        val galleryLow = DetectionGate.evaluate(detection(classIndex = 5, confidence = 0.24f), InputSource.GALLERY)
        val galleryValid = DetectionGate.evaluate(detection(classIndex = 5, confidence = 0.25f), InputSource.GALLERY)

        assertFalse(captureLow.accepted)
        assertTrue(captureValid.accepted)
        assertFalse(galleryLow.accepted)
        assertTrue(galleryValid.accepted)
    }

    @Test
    fun `fruit borer requires 0_35 confidence for all sources`() {
        InputSource.entries.forEach { source ->
            val belowOverride = DetectionGate.evaluate(detection(classIndex = 1, confidence = 0.34f), source)
            val validOverride = DetectionGate.evaluate(detection(classIndex = 1, confidence = 0.35f), source)

            assertFalse("Fruit borer below override should be rejected for $source", belowOverride.accepted)
            assertEquals("confidence_below_fruit_borer_override", belowOverride.reason)
            assertTrue("Fruit borer at override should pass for $source", validOverride.accepted)
        }
    }

    @Test
    fun `source specific box area limits reject tiny and oversized detections`() {
        assertFalse(
            DetectionGate.evaluate(
                detection(classIndex = 5, confidence = 0.9f, left = 0.0f, top = 0.0f, right = 0.04f, bottom = 0.04f),
                InputSource.LIVE,
            ).accepted,
        )
        assertFalse(
            DetectionGate.evaluate(
                detection(classIndex = 5, confidence = 0.9f, left = 0.0f, top = 0.0f, right = 0.99f, bottom = 0.99f),
                InputSource.LIVE,
            ).accepted,
        )
        assertFalse(
            DetectionGate.evaluate(
                detection(classIndex = 5, confidence = 0.9f, left = 0.0f, top = 0.0f, right = 0.06f, bottom = 0.06f),
                InputSource.GALLERY,
            ).accepted,
        )
        assertFalse(
            DetectionGate.evaluate(
                detection(classIndex = 5, confidence = 0.9f, left = 0.0f, top = 0.0f, right = 0.93f, bottom = 0.93f),
                InputSource.GALLERY,
            ).accepted,
        )
    }

    @Test
    fun `filter returns only detections accepted for the source`() {
        val frame = DetectionFrame(
            detections = listOf(
                detection(classIndex = 1, confidence = 0.34f),
                detection(classIndex = 5, confidence = 0.25f),
            ),
            timestampMillis = 1L,
            inferenceMillis = 10L,
            source = InputSource.GALLERY,
            sceneToken = 1L,
        )

        val gated = DetectionGate.filter(frame)

        assertEquals(listOf("leaf-spot"), gated.detections.map { it.modelClass.diseaseId })
    }

    private fun detection(
        classIndex: Int,
        confidence: Float,
        left: Float = 0.10f,
        top: Float = 0.10f,
        right: Float = 0.45f,
        bottom: Float = 0.45f,
    ) = DetectionBox(
        modelClass = ModelMetadata.EGGPLANT_YOLO26M.classFor(classIndex)!!,
        confidence = confidence,
        bounds = NormalizedBox(left, top, right, bottom),
    )
}
