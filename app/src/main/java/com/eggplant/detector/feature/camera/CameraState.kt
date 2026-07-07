package com.eggplant.detector.feature.camera

import com.eggplant.detector.detection.api.DetectionBox
import com.eggplant.detector.detection.api.DetectionFrame
import com.eggplant.detector.detection.api.DetectionStatus
import com.eggplant.detector.detection.api.EngineState
import com.eggplant.detector.detection.api.RgbFrame
import com.eggplant.detector.detection.api.StabilityResult

data class CameraScene(
    val rgbFrame: RgbFrame,
    val detectionFrame: DetectionFrame,
    val stability: StabilityResult,
)

data class CameraAnalysisState(
    val engineState: EngineState = EngineState.UNINITIALIZED,
    val status: DetectionStatus = DetectionStatus.SEARCHING,
    val visibleDetections: List<DetectionBox> = emptyList(),
    val stableDetections: List<DetectionBox> = emptyList(),
    val confirmedDetections: List<DetectionBox> = emptyList(),
    val saveEligible: Boolean = false,
    val inferenceMillis: Long? = null,
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val torchSupported: Boolean = false,
    val torchEnabled: Boolean = false,
    val livePreviewActive: Boolean = false,
    val isStillImageProcessing: Boolean = false,
    val error: String? = null,
)
