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
            modelVersion = "eggplant-yolo26m-v3-clean-768-20260707",
            runtimeVersion = "ncnn-20260526",
            inputSize = 768,
            confidenceThreshold = 0.15f,
            paramSha256 = "8f5eb9a0e6c01a45a1b68333b72f868a38fd24e451fb0fcd862b11e337881289",
            binSha256 = "d6cd0a038cdd16e1d8bb3c44fa2b18043636c51f0b59446df9ce176cb39a5239",
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
