package com.eggplant.detector.detection.api

import com.eggplant.detector.detection.ncnn.ModelMetadata
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionClassPolicyTest {
    private val metadata = ModelMetadata.EGGPLANT_YOLO26M

    @Test
    fun `healthy classes are disabled independently and diseases stay enabled`() {
        val bothDisabled = DetectionClassPolicy()
        val leafOnly = DetectionClassPolicy(detectHealthyLeaf = true)
        val plantOnly = DetectionClassPolicy(detectHealthyPlant = true)

        assertFalse(bothDisabled.allows(metadata.classes[2]))
        assertFalse(bothDisabled.allows(metadata.classes[3]))
        assertTrue(leafOnly.allows(metadata.classes[2]))
        assertFalse(leafOnly.allows(metadata.classes[3]))
        assertFalse(plantOnly.allows(metadata.classes[2]))
        assertTrue(plantOnly.allows(metadata.classes[3]))
        metadata.classes.filterNot { it.isHealthy }.forEach { disease ->
            assertTrue(bothDisabled.allows(disease))
        }
    }

    @Test
    fun `filtering a healthy winner does not manufacture a disease result`() {
        val healthyLeaf = detection(classIndex = 2)
        val leafSpot = detection(classIndex = 5)

        assertTrue(DetectionClassPolicy().filter(listOf(healthyLeaf)).isEmpty())
        assertTrue(DetectionClassPolicy().filter(listOf(healthyLeaf, leafSpot)) == listOf(leafSpot))
    }

    private fun detection(classIndex: Int) = DetectionBox(
        modelClass = metadata.classes[classIndex],
        confidence = 0.8f,
        bounds = NormalizedBox(0.1f, 0.1f, 0.8f, 0.8f),
    )
}
