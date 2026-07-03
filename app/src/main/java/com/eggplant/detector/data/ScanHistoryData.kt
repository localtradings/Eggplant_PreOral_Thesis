package com.eggplant.detector.data

import com.eggplant.detector.model.ScanCategory
import com.eggplant.detector.model.ScanResult
import java.time.LocalDateTime

object ScanHistoryData {
    fun seed(now: LocalDateTime = LocalDateTime.now()): List<ScanResult> = listOf(
        MockDetectionData.capture(now.minusHours(2)).copy(id = "history-leaf-spot"),
        MockDetectionData.gallery(now.minusDays(1).withHour(15).withMinute(20)).copy(id = "history-fruit-borer"),
        ScanResult(
            id = "history-healthy",
            name = "Healthy",
            category = ScanCategory.NO_DISEASE_DETECTED,
            confidence = 94,
            scannedAt = LocalDateTime.of(2026, 6, 24, 10, 15),
            signs = listOf("No visible disease signs in this mock result"),
            treatment = "Continue regular monitoring and good crop care.",
        ),
        ScanResult(
            id = "history-mosaic-virus",
            name = "Mosaic Virus",
            category = ScanCategory.LEAF_DISEASE,
            confidence = 82,
            scannedAt = LocalDateTime.of(2026, 6, 12, 8, 40),
            signs = listOf("Mottled yellow-green pattern", "Distorted leaf shape"),
            treatment = "Mock guidance: isolate suspicious plants and consult a local agricultural specialist.",
        ),
    )
}
