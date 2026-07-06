package com.eggplant.detector.detection.ncnn

data class ModelClass(
    val index: Int,
    val modelLabel: String,
    val diseaseId: String?,
    val isHealthy: Boolean = false,
) {
    init {
        require(index >= 0) { "Model class index must be non-negative." }
        require(modelLabel.isNotBlank()) { "Model label cannot be blank." }
        require(isHealthy == (diseaseId == null)) {
            "Only healthy classes may omit a disease ID."
        }
    }
}

data class ModelMetadata(
    val modelVersion: String,
    val runtimeVersion: String,
    val inputSize: Int,
    val confidenceThreshold: Float,
    val paramSha256: String,
    val binSha256: String,
    val classes: List<ModelClass>,
) {
    init {
        require(inputSize > 0) { "Model input size must be positive." }
        require(confidenceThreshold in 0f..1f) { "Confidence threshold must be normalized." }
        require(classes.map(ModelClass::index) == classes.indices.toList()) {
            "Model classes must be contiguous and ordered."
        }
        require(classes.map(ModelClass::modelLabel).distinct().size == classes.size) {
            "Model labels must be unique."
        }
    }

    fun classFor(index: Int): ModelClass? = classes.getOrNull(index)

    companion object {
        const val HEALTHY_LEAF_CLASS_INDEX = 2
        const val HEALTHY_PLANT_CLASS_INDEX = 3

        val EGGPLANT_YOLO26M = ModelMetadata(
            modelVersion = "eggplant-yolo26m-b96-20260704",
            runtimeVersion = "ncnn-20260526",
            inputSize = 640,
            confidenceThreshold = 0.2f,
            paramSha256 = "88a770cd6206f56643f35dc8f579badb650e9eae5a5e92a12f519693c6d983d2",
            binSha256 = "7a0dd4efc6841b3dd9a20f423101dfb0b6f196e7d242ec530722544fe37a3f6d",
            classes = listOf(
                ModelClass(0, "Fruit_Rot", "fruit-rot"),
                ModelClass(1, "Fruit_borer", "fruit-borer"),
                ModelClass(2, "Healthy Leaf", null, isHealthy = true),
                ModelClass(3, "Healthy Plant", null, isHealthy = true),
                ModelClass(4, "Insect-Pest", "insect-pest"),
                ModelClass(5, "Leaf-Spot", "leaf-spot"),
                ModelClass(6, "Melon_Thrips", "melon-thrips"),
                ModelClass(7, "Mosaic", "mosaic-virus"),
                ModelClass(8, "White-Mold", "white-molds"),
                ModelClass(9, "Wilt", "wilt"),
            ),
        )
    }
}
