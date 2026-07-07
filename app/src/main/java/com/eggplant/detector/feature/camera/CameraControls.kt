package com.eggplant.detector.feature.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.eggplant.detector.R
import com.eggplant.detector.detection.api.DetectionStatus
import com.eggplant.detector.detection.api.EngineState

@Composable
internal fun CameraTopBar(state: CameraAnalysisState, onBack: () -> Unit, onToggleTorch: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        CameraControl(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.close_camera), onBack)
        if (state.torchSupported) {
            CameraControl(
                if (state.torchEnabled) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff,
                stringResource(if (state.torchEnabled) R.string.turn_flash_off else R.string.turn_flash_on),
                onToggleTorch,
            )
        } else {
            Spacer(Modifier.size(52.dp))
        }
    }
}

@Composable
internal fun CameraStatus(state: CameraAnalysisState, modifier: Modifier = Modifier) {
    val text = when {
        state.error != null -> state.error
        state.engineState == EngineState.UNINITIALIZED -> stringResource(R.string.loading_model)
        state.engineState != EngineState.READY -> stringResource(R.string.detection_unavailable)
        state.isStillImageProcessing -> stringResource(R.string.analyzing)
        state.livePreviewActive && state.status == DetectionStatus.HEALTHY -> stringResource(R.string.live_preview_healthy)
        state.livePreviewActive && state.status == DetectionStatus.DISEASE_DETECTED -> stringResource(R.string.live_preview_disease)
        state.livePreviewActive -> stringResource(R.string.live_preview_active)
        state.status == DetectionStatus.HEALTHY -> stringResource(R.string.no_disease_detected)
        else -> stringResource(R.string.point_camera)
    }
    Surface(modifier = modifier.padding(horizontal = 24.dp), color = Color.Black.copy(alpha = .62f), shape = RoundedCornerShape(18.dp)) {
        Text(text, Modifier.padding(horizontal = 16.dp, vertical = 10.dp), color = Color.White)
    }
}

@Composable
internal fun CameraBottomBar(
    processing: Boolean,
    engineState: EngineState,
    livePreviewActive: Boolean,
    onGallery: () -> Unit,
    onCapture: () -> Unit,
    onStartLivePreview: () -> Unit,
    onStopLivePreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val captureDescription = stringResource(R.string.capture_scan)
    val livePreviewDescription = stringResource(R.string.hold_for_live_preview)
    val shutterCoordinator = remember { ShutterActionCoordinator() }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    LaunchedEffect(isPressed) {
        if (shutterCoordinator.onPressedChanged(isPressed) == ShutterAction.STOP_LIVE_PREVIEW) {
            onStopLivePreview()
        }
    }
    Row(
        modifier = modifier.fillMaxWidth().background(Color.Black.copy(alpha = .55f)).padding(horizontal = 22.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        CameraControl(
            Icons.Outlined.Collections,
            stringResource(R.string.choose_gallery),
            onGallery,
            enabled = !processing && engineState == EngineState.READY && !livePreviewActive,
        )
        Surface(
            modifier = Modifier
                .size(76.dp)
                .border(4.dp, Color.White.copy(alpha = .7f), CircleShape)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = !processing && engineState == EngineState.READY,
                    role = Role.Button,
                    onClickLabel = captureDescription,
                    onLongClickLabel = livePreviewDescription,
                    onClick = {
                        if (shutterCoordinator.onTap(processing, engineState) == ShutterAction.CAPTURE) {
                            onCapture()
                        }
                    },
                    onLongClick = {
                        if (shutterCoordinator.onLongPress(processing, engineState) == ShutterAction.START_LIVE_PREVIEW) {
                            onStartLivePreview()
                        }
                    },
                )
                .semantics {
                    contentDescription = captureDescription
                },
            color = if (livePreviewActive) MaterialTheme.colorScheme.primary else Color.White,
            contentColor = if (livePreviewActive) Color.White else MaterialTheme.colorScheme.primary,
            shape = CircleShape,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(34.dp))
            }
        }
        Spacer(Modifier.size(52.dp))
    }
}

@Composable
internal fun CameraPermissionRequired(permissionRequested: Boolean, onRequest: () -> Unit, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.camera_permission_required), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(if (permissionRequested) R.string.camera_permission_retry else R.string.camera_permission_body),
            modifier = Modifier.padding(vertical = 16.dp),
        )
        Button(onClick = onRequest) { Text(stringResource(R.string.allow_camera)) }
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back)) }
    }
}

@Composable
private fun CameraControl(icon: ImageVector, description: String, onClick: () -> Unit, enabled: Boolean = true) {
    Surface(color = Color.Black.copy(alpha = .45f), shape = CircleShape) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(52.dp)) {
            Icon(icon, contentDescription = description, tint = Color.White)
        }
    }
}
