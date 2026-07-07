package com.eggplant.detector.feature.camera

import com.eggplant.detector.detection.api.EngineState

enum class ShutterAction {
    NONE,
    CAPTURE,
    START_LIVE_PREVIEW,
    STOP_LIVE_PREVIEW,
}

class ShutterActionCoordinator {
    private var livePreviewActive = false

    fun onTap(
        processing: Boolean,
        engineState: EngineState,
    ): ShutterAction = if (!processing && engineState == EngineState.READY && !livePreviewActive) {
        ShutterAction.CAPTURE
    } else {
        ShutterAction.NONE
    }

    fun onLongPress(
        processing: Boolean,
        engineState: EngineState,
    ): ShutterAction = if (!processing && engineState == EngineState.READY && !livePreviewActive) {
        livePreviewActive = true
        ShutterAction.START_LIVE_PREVIEW
    } else {
        ShutterAction.NONE
    }

    fun onPressedChanged(isPressed: Boolean): ShutterAction {
        if (!isPressed && livePreviewActive) {
            livePreviewActive = false
            return ShutterAction.STOP_LIVE_PREVIEW
        }
        return ShutterAction.NONE
    }
}
