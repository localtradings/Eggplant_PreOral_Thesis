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

        assertEquals("eggplant-yolo26m-v3-clean-768-20260707", metadata.modelVersion)
        assertEquals(768, metadata.inputSize)
        assertEquals(0.15f, metadata.confidenceThreshold)
        assertEquals("ncnn-20260526", metadata.runtimeVersion)
        assertEquals(
            "8f5eb9a0e6c01a45a1b68333b72f868a38fd24e451fb0fcd862b11e337881289",
            metadata.paramSha256,
        )
        assertEquals(
            "d6cd0a038cdd16e1d8bb3c44fa2b18043636c51f0b59446df9ce176cb39a5239",
            metadata.binSha256,
        )
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
