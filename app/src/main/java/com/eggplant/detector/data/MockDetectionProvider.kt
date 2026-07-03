package com.eggplant.detector.data

import com.eggplant.detector.model.DetectionProvider
import com.eggplant.detector.model.ScanResult
import java.time.LocalDateTime

class MockDetectionProvider(
    private val nowProvider: () -> LocalDateTime = { LocalDateTime.now() },
) : DetectionProvider {
    override fun detectCapture(): ScanResult = MockDetectionData.capture(nowProvider())

    override fun detectGallery(): ScanResult = MockDetectionData.gallery(nowProvider())
}
