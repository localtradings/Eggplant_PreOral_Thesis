package com.eggplant.detector.model

import com.eggplant.detector.detection.NormalizedBox
import java.time.LocalDateTime

data class ScanDetectionResult(
    val id: String,
    val diseaseId: String,
    val name: String,
    val modelClassIndex: Int,
    val modelLabel: String,
    val confidence: Int,
    val bounds: NormalizedBox,
)

data class ScanResult(
    val id: String,
    val name: String,
    val category: ScanCategory,
    val confidence: Int,
    val scannedAt: LocalDateTime,
    val signs: List<String>,
    val treatment: String,
    val diseaseId: String = "unknown",
    val source: String = "unknown",
    val modelVersion: String = "eggplant-yolo26m-ncnn-fp16-640",
    val imagePath: String? = null,
    val detections: List<ScanDetectionResult> = emptyList(),
    val saveMode: String = "MANUAL",
) {
    init {
        require(confidence in 0..100) { "Confidence must be between 0 and 100." }
    }
}
