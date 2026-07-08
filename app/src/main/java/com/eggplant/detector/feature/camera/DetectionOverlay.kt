package com.eggplant.detector.feature.camera

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eggplant.detector.R
import com.eggplant.detector.detection.api.DetectionBox
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
internal fun DetectionOverlay(
    state: CameraAnalysisState,
    displayName: (DetectionBox) -> String,
    onDetectionClick: ((DetectionBox) -> Unit)?,
    contentScale: OverlayContentScale = OverlayContentScale.CROP,
) {
    if (state.frameWidth <= 0 || state.frameHeight <= 0) return
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val viewWidthPx = with(density) { maxWidth.toPx() }
        val viewHeightPx = with(density) { maxHeight.toPx() }
        val scale = when (contentScale) {
            OverlayContentScale.CROP -> maxOf(viewWidthPx / state.frameWidth, viewHeightPx / state.frameHeight)
            OverlayContentScale.FIT -> min(viewWidthPx / state.frameWidth, viewHeightPx / state.frameHeight)
        }
        val renderedWidth = state.frameWidth * scale
        val renderedHeight = state.frameHeight * scale
        val offsetX = (viewWidthPx - renderedWidth) / 2f
        val offsetY = (viewHeightPx - renderedHeight) / 2f
        val items = presentOverlayDetections(
            visible = state.visibleDetections,
            confirmed = state.confirmedDetections,
            displayName = displayName,
        )
        items.forEach { item ->
            val detection = item.detection
            val left = offsetX + detection.bounds.left * renderedWidth
            val top = offsetY + detection.bounds.top * renderedHeight
            val width = (detection.bounds.right - detection.bounds.left) * renderedWidth
            val height = (detection.bounds.bottom - detection.bounds.top) * renderedHeight
            val isCompact = min(width, height) < with(density) { 72.dp.toPx() }
            val borderWidth = if (isCompact) 2.dp else 3.dp
            val shape = RoundedCornerShape(if (isCompact) 6.dp else 10.dp)
            val description = if (item.phase == OverlayPhase.CONFIRMED) {
                stringResource(
                    R.string.detection_box_description,
                    displayName(detection),
                )
            } else {
                stringResource(R.string.analyzing_detection)
            }
            Box(
                modifier = Modifier
                    .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                    .size(with(density) { width.toDp() }, with(density) { height.toDp() })
                    .border(borderWidth, Color(0xFFFFB44C), shape)
                    .then(
                        if (item.phase == OverlayPhase.CONFIRMED && onDetectionClick != null) {
                            Modifier.clickable { onDetectionClick(detection) }
                        } else {
                            Modifier
                        },
                    )
                    .semantics { contentDescription = description },
            )

            item.label?.let { label ->
                val labelHeight = if (isCompact) 24.dp else 30.dp
                val labelHeightPx = with(density) { labelHeight.toPx() }
                val gapPx = with(density) { 4.dp.toPx() }
                val maxLabelWidth = min(with(density) { 180.dp.toPx() }, viewWidthPx)
                val labelX = left.coerceIn(0f, (viewWidthPx - maxLabelWidth).coerceAtLeast(0f))
                val labelY = if (top >= labelHeightPx + gapPx) {
                    top - labelHeightPx - gapPx
                } else {
                    (top + height + gapPx).coerceAtMost((viewHeightPx - labelHeightPx).coerceAtLeast(0f))
                }
                Surface(
                    modifier = Modifier
                        .offset { IntOffset(labelX.roundToInt(), labelY.roundToInt()) }
                        .widthIn(max = 180.dp),
                    color = Color(0xFFE89526),
                    shape = RoundedCornerShape(if (isCompact) 6.dp else 8.dp),
                    shadowElevation = 2.dp,
                ) {
                    Text(
                        label,
                        modifier = Modifier.padding(
                            horizontal = if (isCompact) 4.dp else 6.dp,
                            vertical = if (isCompact) 2.dp else 3.dp,
                        ),
                        color = Color(0xFF201407),
                        fontSize = if (isCompact) 9.sp else 11.sp,
                        lineHeight = if (isCompact) 11.sp else 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

internal enum class OverlayContentScale {
    CROP,
    FIT,
}
