package com.eggplant.detector.model

import java.time.LocalDateTime

data class ScanResult(
    val id: String,
    val name: String,
    val category: ScanCategory,
    val confidence: Int,
    val scannedAt: LocalDateTime,
    val signs: List<String>,
    val treatment: String,
) {
    init {
        require(confidence in 0..100) { "Confidence must be between 0 and 100." }
    }
}
