package com.eggplant.detector.model

data class Disease(
    val id: String,
    val name: String,
    val type: DiseaseType,
    val symptomPreview: String,
    val signs: List<String>,
    val treatment: String,
    val prevention: String,
)
