package com.eggplant.detector.detection.ncnn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelMetadataTest {
    @Test
    fun `deployment metadata preserves exact training label order`() {
        val metadata = ModelMetadata.EGGPLANT_YOLO26M

        assertEquals(640, metadata.inputSize)
        assertEquals(0.2f, metadata.confidenceThreshold)
        assertEquals("ncnn-20260526", metadata.runtimeVersion)
        assertEquals(
            listOf(
                "Fruit_Rot",
                "Fruit_borer",
                "Healthy Leaf",
                "Healthy Plant",
                "Insect-Pest",
                "Leaf-Spot",
                "Melon_Thrips",
                "Mosaic",
                "White-Mold",
                "Wilt",
            ),
            metadata.classes.map(ModelClass::modelLabel),
        )
        assertEquals((0..9).toList(), metadata.classes.map(ModelClass::index))
    }

    @Test
    fun `healthy classes cannot be persisted as diseases`() {
        val metadata = ModelMetadata.EGGPLANT_YOLO26M

        metadata.classes.filter(ModelClass::isHealthy).forEach { modelClass ->
            assertNull(modelClass.diseaseId)
        }
        metadata.classes.filterNot(ModelClass::isHealthy).forEach { modelClass ->
            assertFalse(modelClass.diseaseId.isNullOrBlank())
        }
        assertEquals(2, metadata.classes.count(ModelClass::isHealthy))
        assertEquals(8, metadata.classes.count { !it.isHealthy })
    }

    @Test
    fun `deployment artifacts are checksum pinned`() {
        val metadata = ModelMetadata.EGGPLANT_YOLO26M

        assertTrue(metadata.modelVersion.isNotBlank())
        assertTrue(metadata.paramSha256.matches(Regex("[0-9a-f]{64}")))
        assertTrue(metadata.binSha256.matches(Regex("[0-9a-f]{64}")))
    }
}
