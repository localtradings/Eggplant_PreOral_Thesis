package com.eggplant.detector.model

interface DetectionProvider {
    fun detectCapture(): ScanResult
    fun detectGallery(): ScanResult
}
