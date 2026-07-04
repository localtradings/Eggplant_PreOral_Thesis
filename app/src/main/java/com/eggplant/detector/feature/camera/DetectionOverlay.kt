package com.eggplant.detector.feature.camera

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.eggplant.detector.R
import com.eggplant.detector.detection.api.DetectionBox
import kotlin.math.roundToInt

@Composable
internal fun DetectionOverlay(
    state: CameraAnalysisState,
    onDetectionClick: (DetectionBox) -> Unit,
) {
    if (state.frameWidth <= 0 || state.frameHeight <= 0) return
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val viewWidthPx = with(density) { maxWidth.toPx() }
        val viewHeightPx = with(density) { maxHeight.toPx() }
        val scale = maxOf(viewWidthPx / state.frameWidth, viewHeightPx / state.frameHeight)
        val renderedWidth = state.frameWidth * scale
        val renderedHeight = state.frameHeight * scale
        val offsetX = (viewWidthPx - renderedWidth) / 2f
        val offsetY = (viewHeightPx - renderedHeight) / 2f
        state.visibleDetections.forEach { detection ->
            val left = offsetX + detection.bounds.left * renderedWidth
            val top = offsetY + detection.bounds.top * renderedHeight
            val width = (detection.bounds.right - detection.bounds.left) * renderedWidth
            val height = (detection.bounds.bottom - detection.bounds.top) * renderedHeight
            val boxDescription = stringResource(
                R.string.detection_box_description,
                detection.modelClass.modelLabel,
                (detection.confidence * 100).roundToInt(),
            )
            Column(
                modifier = Modifier
                    .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                    .size(with(density) { width.toDp() }, with(density) { height.toDp() })
                    .border(3.dp, Color(0xFFFFB44C), RoundedCornerShape(12.dp))
                    .clickable { onDetectionClick(detection) }
                    .semantics { contentDescription = boxDescription },
            ) {
                Surface(color = Color(0xFFFFB44C), shape = RoundedCornerShape(bottomEnd = 8.dp)) {
                    Text(
                        "${detection.modelClass.modelLabel.replace('-', ' ')} · ${(detection.confidence * 100).roundToInt()}%",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color(0xFF201407),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}
