package com.eggplant.detector.detection.api

import com.eggplant.detector.detection.ncnn.ModelClass
import com.eggplant.detector.detection.ncnn.ModelMetadata

data class DetectionClassPolicy(
    val detectHealthyLeaf: Boolean = false,
    val detectHealthyPlant: Boolean = false,
) {
    fun allows(modelClass: ModelClass): Boolean = when (modelClass.index) {
        ModelMetadata.HEALTHY_LEAF_CLASS_INDEX -> detectHealthyLeaf
        ModelMetadata.HEALTHY_PLANT_CLASS_INDEX -> detectHealthyPlant
        else -> true
    }

    fun filter(detections: List<DetectionBox>): List<DetectionBox> =
        detections.filter { allows(it.modelClass) }
}
