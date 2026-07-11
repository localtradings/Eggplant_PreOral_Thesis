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
        val rejected = DetectionGate.evaluate(detection(classIndex = 5, confidence = 0.119f), InputSource.LIVE)
        val accepted = DetectionGate.evaluate(detection(classIndex = 5, confidence = 0.12f), InputSource.LIVE)

        assertFalse(rejected.accepted)
        assertTrue(accepted.accepted)
    }

    @Test
    fun `capture and gallery use 0_15 final disease thresholds`() {
        val captureLow = DetectionGate.evaluate(detection(classIndex = 5, confidence = 0.149f), InputSource.CAPTURE)
        val captureValid = DetectionGate.evaluate(detection(classIndex = 5, confidence = 0.15f), InputSource.CAPTURE)
        val galleryLow = DetectionGate.evaluate(detection(classIndex = 5, confidence = 0.149f), InputSource.GALLERY)
        val galleryValid = DetectionGate.evaluate(detection(classIndex = 5, confidence = 0.15f), InputSource.GALLERY)

        assertFalse(captureLow.accepted)
        assertTrue(captureValid.accepted)
        assertFalse(galleryLow.accepted)
        assertTrue(galleryValid.accepted)
    }

    @Test
    fun `fruit borer requires 0_25 confidence for all sources`() {
        InputSource.entries.forEach { source ->
            val belowOverride = DetectionGate.evaluate(detection(classIndex = 1, confidence = 0.249f), source)
            val validOverride = DetectionGate.evaluate(detection(classIndex = 1, confidence = 0.25f), source)

            assertFalse("Fruit borer below override should be rejected for $source", belowOverride.accepted)
            assertEquals("confidence_below_fruit_borer_override", belowOverride.reason)
            assertTrue("Fruit borer at override should pass for $source", validOverride.accepted)
        }
    }

    @Test
    fun `healthy classes require 0_25 confidence for all sources`() {
        InputSource.entries.forEach { source ->
            val belowHealthy = DetectionGate.evaluate(detection(classIndex = 2, confidence = 0.249f), source)
            val validHealthy = DetectionGate.evaluate(detection(classIndex = 2, confidence = 0.25f), source)

            assertFalse("Healthy below override should be rejected for $source", belowHealthy.accepted)
            assertEquals("confidence_below_healthy_override", belowHealthy.reason)
            assertTrue("Healthy at override should pass for $source", validHealthy.accepted)
        }
    }

    @Test
    fun `source specific box area limits reject tiny detections but allow close ups`() {
        assertFalse(
            DetectionGate.evaluate(
                detection(classIndex = 5, confidence = 0.9f, left = 0.0f, top = 0.0f, right = 0.04f, bottom = 0.04f),
                InputSource.LIVE,
            ).accepted,
        )
        assertTrue(
            DetectionGate.evaluate(
                detection(classIndex = 5, confidence = 0.9f, left = 0.0f, top = 0.0f, right = 0.99f, bottom = 0.99f),
                InputSource.LIVE,
            ).accepted,
        )
        assertFalse(
            DetectionGate.evaluate(
                detection(classIndex = 5, confidence = 0.9f, left = 0.0f, top = 0.0f, right = 0.04f, bottom = 0.04f),
                InputSource.GALLERY,
            ).accepted,
        )
        assertTrue(
            DetectionGate.evaluate(
                detection(classIndex = 5, confidence = 0.9f, left = 0.0f, top = 0.0f, right = 0.93f, bottom = 0.93f),
                InputSource.GALLERY,
            ).accepted,
        )
    }

    @Test
    fun `still images keep small valid lesions at 0_0025 area`() {
        val validSmallCapture = DetectionGate.evaluate(
            detection(classIndex = 5, confidence = 0.9f, left = 0.0f, top = 0.0f, right = 0.05f, bottom = 0.05f),
            InputSource.CAPTURE,
        )
        val validSmallGallery = DetectionGate.evaluate(
            detection(classIndex = 5, confidence = 0.9f, left = 0.0f, top = 0.0f, right = 0.05f, bottom = 0.05f),
            InputSource.GALLERY,
        )

        assertTrue(validSmallCapture.accepted)
        assertTrue(validSmallGallery.accepted)
    }

    @Test
    fun `filter returns only detections accepted for the source`() {
        val frame = DetectionFrame(
            detections = listOf(
                detection(classIndex = 1, confidence = 0.249f),
                detection(classIndex = 5, confidence = 0.15f),
            ),
            timestampMillis = 1L,
            inferenceMillis = 10L,
            source = InputSource.GALLERY,
            sceneToken = 1L,
        )

        val gated = DetectionGate.filter(frame)

        assertEquals(listOf("leaf-spot"), gated.detections.map { it.modelClass.diseaseId })
    }

    @Test
    fun `filter preserves multiple accepted disease classes and boxes`() {
        val firstLeafSpot = detection(classIndex = 5, confidence = 0.42f, left = 0.10f, top = 0.10f, right = 0.25f, bottom = 0.30f)
        val secondLeafSpot = detection(classIndex = 5, confidence = 0.39f, left = 0.60f, top = 0.10f, right = 0.75f, bottom = 0.30f)
        val mosaic = detection(classIndex = 7, confidence = 0.34f, left = 0.25f, top = 0.45f, right = 0.80f, bottom = 0.90f)
        val frame = DetectionFrame(
            detections = listOf(firstLeafSpot, secondLeafSpot, mosaic),
            timestampMillis = 1L,
            inferenceMillis = 10L,
            source = InputSource.CAPTURE,
            sceneToken = 1L,
        )

        val gated = DetectionGate.filter(frame)

        assertEquals(listOf(firstLeafSpot, secondLeafSpot, mosaic), gated.detections)
    }

    @Test
    fun `negative synthetic frames do not fabricate a detection`() {
        val empty = DetectionFrame(
            detections = emptyList(),
            timestampMillis = 1L,
            inferenceMillis = 10L,
            source = InputSource.LIVE,
            sceneToken = 1L,
        )
        val lowConfidence = empty.copy(
            detections = listOf(detection(classIndex = 5, confidence = 0.119f)),
        )

        assertTrue(DetectionGate.filter(empty).detections.isEmpty())
        assertTrue(DetectionGate.filter(lowConfidence).detections.isEmpty())
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
