package com.eggplant.detector.feature.result

import androidx.compose.ui.geometry.Offset

internal data class ZoomableImageTransform(
    val scale: Float = MinScale,
    val offset: Offset = Offset.Zero,
) {
    fun applyGesture(zoomChange: Float, panChange: Offset): ZoomableImageTransform {
        val nextScale = (scale * zoomChange).coerceIn(MinScale, MaxScale)
        return if (nextScale <= MinScale) {
            copy(scale = MinScale, offset = Offset.Zero)
        } else {
            copy(scale = nextScale, offset = offset + panChange)
        }
    }

    companion object {
        const val MinScale = 1f
        const val MaxScale = 4f
    }
}
