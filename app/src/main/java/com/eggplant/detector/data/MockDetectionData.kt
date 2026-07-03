package com.eggplant.detector.data

import com.eggplant.detector.model.ScanCategory
import com.eggplant.detector.model.ScanResult
import java.time.LocalDateTime

object MockDetectionData {
    fun capture(now: LocalDateTime): ScanResult = ScanResult(
        id = "capture-leaf-spot",
        name = "Leaf Spot",
        category = ScanCategory.LEAF_DISEASE,
        confidence = 87,
        scannedAt = now,
        signs = listOf("Circular brown spots", "Yellowing around affected tissue", "Dry spot centers"),
        treatment = "Mock guidance: remove badly affected leaves, keep foliage dry, and consult a local agricultural specialist.",
    )

    fun gallery(now: LocalDateTime): ScanResult = ScanResult(
        id = "gallery-fruit-borer",
        name = "Fruit Borer",
        category = ScanCategory.FRUIT_DISEASE,
        confidence = 91,
        scannedAt = now,
        signs = listOf("Small entry hole", "Material near the opening", "Possible internal feeding damage"),
        treatment = "Mock guidance: remove visibly damaged fruit and consult a local agricultural specialist for crop-safe management.",
    )
}
