package com.eggplant.detector.app

import com.eggplant.detector.feature.camera.CameraScene
import com.eggplant.detector.detection.api.DetectionBox
import com.eggplant.detector.detection.api.DetectionFrame
import com.eggplant.detector.detection.api.DetectionStatus
import com.eggplant.detector.detection.api.InputSource
import com.eggplant.detector.detection.ncnn.ModelMetadata
import com.eggplant.detector.detection.api.NormalizedBox
import com.eggplant.detector.detection.api.RgbFrame
import com.eggplant.detector.detection.api.StabilityResult
import com.eggplant.detector.domain.model.ScanOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EggplantAppViewModelTest {
    @Test
    fun `healthy detection controls default off and update independently`() {
        val viewModel = EggplantAppViewModel()

        assertFalse(viewModel.detectHealthyLeafEnabled.value)
        assertFalse(viewModel.detectHealthyPlantEnabled.value)

        viewModel.setDetectHealthyLeaf(true)
        assertTrue(viewModel.detectHealthyLeafEnabled.value)
        assertFalse(viewModel.detectHealthyPlantEnabled.value)

        viewModel.setDetectHealthyPlant(true)
        assertTrue(viewModel.detectHealthyLeafEnabled.value)
        assertTrue(viewModel.detectHealthyPlantEnabled.value)
    }

    @Test
    fun `unused unit preference is not part of the application state`() {
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("com.eggplant.detector.app.UnitPreference")
        }
    }

    @Test
    fun `separate capture attempts receive unique result ids`() {
        val viewModel = EggplantAppViewModel()
        val (scene, primary) = diseaseScene(5, InputSource.CAPTURE)

        viewModel.openDetectionScene(scene, primary)
        val first = requireNotNull(viewModel.currentResult.value)
        viewModel.openDetectionScene(scene, primary)
        val second = requireNotNull(viewModel.currentResult.value)

        assertNotEquals(first.id, second.id)
    }

    @Test
    fun `saving current result is idempotent`() {
        val viewModel = EggplantAppViewModel(initialHistory = emptyList())
        val (scene, primary) = diseaseScene(5, InputSource.CAPTURE)

        viewModel.openDetectionScene(scene, primary)
        assertTrue(viewModel.saveCurrentResult())
        assertTrue(!viewModel.saveCurrentResult())
        assertEquals(1, viewModel.history.value.size)
    }

    @Test
    fun `latest saved result becomes last scan`() {
        val viewModel = EggplantAppViewModel(initialHistory = emptyList())
        val (scene, primary) = diseaseScene(1, InputSource.GALLERY)

        viewModel.openDetectionScene(scene, primary)
        viewModel.saveCurrentResult()

        assertEquals("Fruit Borer", viewModel.lastScan.value?.name)
    }

    @Test
    fun `opening a detected scene creates one grouped result`() {
        val leafSpot = DetectionBox(
            ModelMetadata.EGGPLANT_YOLO26M.classFor(5)!!,
            0.87f,
            NormalizedBox(0.1f, 0.2f, 0.5f, 0.7f),
        )
        val wilt = DetectionBox(
            ModelMetadata.EGGPLANT_YOLO26M.classFor(9)!!,
            0.78f,
            NormalizedBox(0.5f, 0.2f, 0.9f, 0.8f),
        )
        val rgb = RgbFrame(2, 2, ByteArray(12), 10, InputSource.LIVE, 1)
        val detected = DetectionFrame(listOf(leafSpot, wilt), 10, 100, InputSource.LIVE, 1)
        val scene = CameraScene(
            rgb,
            detected,
            StabilityResult(DetectionStatus.DISEASE_DETECTED, listOf(leafSpot, wilt), listOf(leafSpot, wilt), true),
        )
        val viewModel = EggplantAppViewModel(initialHistory = emptyList())

        viewModel.openDetectionScene(scene, leafSpot)

        assertEquals("Leaf Spot", viewModel.currentResult.value?.name)
        assertEquals(ScanOutcome.DISEASE, viewModel.currentResult.value?.outcome)
        assertEquals(listOf("leaf-spot", "wilt"), viewModel.currentResult.value?.detections?.map { it.diseaseId })
    }

    @Test
    fun `healthy result opens with its class name but cannot be saved`() {
        val healthyLeaf = DetectionBox(
            ModelMetadata.EGGPLANT_YOLO26M.classFor(2)!!,
            0.91f,
            NormalizedBox(0.1f, 0.2f, 0.5f, 0.7f),
        )
        val rgb = RgbFrame(2, 2, ByteArray(12), 10, InputSource.GALLERY, 1)
        val scene = CameraScene(
            rgb,
            DetectionFrame(listOf(healthyLeaf), 10, 100, InputSource.GALLERY, 1),
            StabilityResult(
                status = DetectionStatus.HEALTHY,
                stableDetections = emptyList(),
                visibleDetections = listOf(healthyLeaf),
                saveEligible = false,
                confirmedDetections = listOf(healthyLeaf),
            ),
        )
        val viewModel = EggplantAppViewModel(initialHistory = emptyList())

        viewModel.openDetectionScene(scene, healthyLeaf)

        assertEquals("Healthy Leaf", viewModel.currentResult.value?.name)
        assertEquals(ScanOutcome.HEALTHY_CONFIRMED, viewModel.currentResult.value?.outcome)
        assertEquals(com.eggplant.detector.domain.model.ScanCategory.NO_DISEASE_DETECTED, viewModel.currentResult.value?.category)
        assertFalse(viewModel.saveCurrentResult())
        assertTrue(viewModel.history.value.isEmpty())
    }

    @Test
    fun `no match scene opens a result without enabling save`() {
        val rgb = RgbFrame(2, 2, ByteArray(12), 10, InputSource.GALLERY, 1)
        val scene = CameraScene(
            rgb,
            DetectionFrame(emptyList(), 10, 100, InputSource.GALLERY, 1),
            StabilityResult(
                status = DetectionStatus.SEARCHING,
                stableDetections = emptyList(),
                visibleDetections = emptyList(),
                saveEligible = false,
                confirmedDetections = emptyList(),
            ),
        )
        val viewModel = EggplantAppViewModel(initialHistory = emptyList())

        viewModel.openNoMatchScene(scene)

        assertEquals(ScanOutcome.NO_MATCH, viewModel.currentResult.value?.outcome)
        assertEquals(com.eggplant.detector.domain.model.ScanCategory.NO_DISEASE_DETECTED, viewModel.currentResult.value?.category)
        assertFalse(viewModel.saveCurrentResult())
        assertTrue(viewModel.history.value.isEmpty())
    }
}

private fun diseaseScene(classIndex: Int, source: InputSource): Pair<CameraScene, DetectionBox> {
    val detection = DetectionBox(
        ModelMetadata.EGGPLANT_YOLO26M.classFor(classIndex)!!,
        0.87f,
        NormalizedBox(0.1f, 0.2f, 0.5f, 0.7f),
    )
    val rgb = RgbFrame(2, 2, ByteArray(12), 10, source, 1)
    val frame = DetectionFrame(listOf(detection), 10, 100, source, 1)
    return CameraScene(
        rgb,
        frame,
        StabilityResult(DetectionStatus.DISEASE_DETECTED, listOf(detection), listOf(detection), true),
    ) to detection
}
