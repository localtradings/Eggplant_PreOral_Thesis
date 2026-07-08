package com.eggplant.detector.feature.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.eggplant.detector.app.EggplantAppViewModel
import com.eggplant.detector.R
import com.eggplant.detector.detection.api.DetectionBox
import com.eggplant.detector.detection.api.DetectionClassPolicy
import com.eggplant.detector.detection.api.EngineState
import com.eggplant.detector.detection.api.InputSource
import com.eggplant.detector.detection.ncnn.ModelMetadata
import com.eggplant.detector.detection.ncnn.NcnnDetectionEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CameraScreen(
    viewModel: EggplantAppViewModel,
    onBack: () -> Unit,
    onResult: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val detectHealthyLeafEnabled by viewModel.detectHealthyLeafEnabled.collectAsState()
    val detectHealthyPlantEnabled by viewModel.detectHealthyPlantEnabled.collectAsState()
    val catalog by viewModel.catalog.collectAsState()
    val healthyLeafName = stringResource(R.string.healthy_leaf_name)
    val healthyPlantName = stringResource(R.string.healthy_plant_name)
    val galleryOpenFailed = stringResource(R.string.gallery_open_failed)
    val captureFailed = stringResource(R.string.capture_failed)
    val noStableDisease = stringResource(R.string.no_stable_disease_detected)
    var cameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var permissionRequested by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionRequested = true
        cameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!cameraPermission && !permissionRequested) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (!cameraPermission) {
        CameraPermissionRequired(
            permissionRequested = permissionRequested,
            onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onBack = onBack,
        )
        return
    }

    var cameraState by remember { mutableStateOf(CameraAnalysisState()) }
    var controller by remember { mutableStateOf<CameraController?>(null) }
    var captureFlashTrigger by remember { mutableStateOf(0) }
    val resultNavigationGate = remember { ResultNavigationGate() }
    val scope = rememberCoroutineScope()
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    DisposableEffect(lifecycleOwner) {
        val created = CameraController(
            context = context,
            lifecycleOwner = lifecycleOwner,
            engine = NcnnDetectionEngine(context),
            onState = { cameraState = it },
        )
        controller = created
        created.start(previewView)
        onDispose {
            created.close()
            controller = null
        }
    }

    LaunchedEffect(controller, detectHealthyLeafEnabled, detectHealthyPlantEnabled) {
        controller?.updateClassPolicy(
            DetectionClassPolicy(
                detectHealthyLeaf = detectHealthyLeafEnabled,
                detectHealthyPlant = detectHealthyPlantEnabled,
            ),
        )
    }

    fun routeToResult() {
        resultNavigationGate.tryRoute(onResult)
    }

    fun openScene(scene: CameraScene, primary: DetectionBox) {
        viewModel.openDetectionScene(scene, primary, ::routeToResult)
    }

    fun handleStillResult(result: Result<CameraScene>, fallbackError: String) {
        cameraState = cameraState.copy(isStillImageProcessing = false)
        when (val outcome = result.toStillImageResult(fallbackError)) {
            is StillImageResult.Disease -> openScene(outcome.scene, outcome.primary)
            is StillImageResult.Healthy -> openScene(outcome.scene, outcome.primary)
            is StillImageResult.NoMatch -> viewModel.openNoMatchScene(outcome.scene, ::routeToResult)
            is StillImageResult.Failure -> cameraState = cameraState.copy(error = outcome.message)
        }
    }

    fun handleLiveRelease(outcome: LivePreviewOutcome) {
        when (outcome) {
            is LivePreviewOutcome.Disease -> openScene(outcome.scene, outcome.primary)
            is LivePreviewOutcome.Healthy -> openScene(outcome.scene, outcome.primary)
            LivePreviewOutcome.NoStableDetection -> {
                cameraState = cameraState.copy(error = noStableDisease)
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null || cameraState.isStillImageProcessing || cameraState.engineState != EngineState.READY) {
            return@rememberLauncherForActivityResult
        }
        val activeController = controller
        if (activeController == null) {
            cameraState = cameraState.copy(error = galleryOpenFailed)
            return@rememberLauncherForActivityResult
        }
        resultNavigationGate.reset()
        activeController.stopLivePreview()
        cameraState = cameraState.copy(isStillImageProcessing = true, error = null)
        scope.launch {
            val bitmap = runCatching { withContext(Dispatchers.IO) { context.decodeGalleryBitmap(uri) } }
                .getOrElse { error ->
                    cameraState = cameraState.copy(
                        isStillImageProcessing = false,
                        error = error.message ?: galleryOpenFailed,
                    )
                    return@launch
                }
            activeController.analyzeBitmap(bitmap, InputSource.GALLERY) { result ->
                bitmap.recycle()
                handleStillResult(result, galleryOpenFailed)
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        DetectionOverlay(
            state = cameraState,
            displayName = { detection ->
                when (detection.modelClass.index) {
                    ModelMetadata.HEALTHY_LEAF_CLASS_INDEX -> healthyLeafName
                    ModelMetadata.HEALTHY_PLANT_CLASS_INDEX -> healthyPlantName
                    else -> detection.modelClass.diseaseId?.let { diseaseId ->
                        catalog.firstOrNull { it.id == diseaseId }?.name
                    } ?: detection.modelClass.modelLabel.replace('_', ' ').replace('-', ' ')
                }
            },
            onDetectionClick = null,
        )
        CameraTopBar(
            state = cameraState,
            onBack = onBack,
            onToggleTorch = { controller?.toggleTorch() },
        )
        CameraStatus(cameraState, Modifier.align(Alignment.TopCenter).padding(top = 82.dp))
        CaptureFlashOverlay(captureFlashTrigger)
        CameraBottomBar(
            processing = cameraState.isStillImageProcessing,
            engineState = cameraState.engineState,
            livePreviewActive = cameraState.livePreviewActive,
            onGallery = {
                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onCapture = {
                val activeController = controller
                if (
                    activeController == null ||
                    cameraState.isStillImageProcessing ||
                    cameraState.engineState != EngineState.READY
                ) {
                    return@CameraBottomBar
                }
                resultNavigationGate.reset()
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                captureFlashTrigger += 1
                activeController.finishLivePreview(allowHealthy = false)
                cameraState = cameraState.copy(isStillImageProcessing = true, error = null)
                activeController.capturePhoto { result ->
                    handleStillResult(result, captureFailed)
                }
            },
            onStartLivePreview = {
                resultNavigationGate.reset()
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                controller?.startLivePreview()
            },
            onStopLivePreview = {
                val outcome = controller?.finishLivePreview(
                    allowHealthy = detectHealthyLeafEnabled || detectHealthyPlantEnabled,
                ) ?: LivePreviewOutcome.NoStableDetection
                handleLiveRelease(outcome)
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        if (cameraState.isStillImageProcessing) {
            StillImageProcessingOverlay(Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun StillImageProcessingOverlay(modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.54f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.72f),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(38.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(R.string.analyzing),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun CaptureFlashOverlay(trigger: Int) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger > 0) {
            alpha.snapTo(0.72f)
            alpha.animateTo(0f, tween(durationMillis = 150))
        }
    }
    Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = alpha.value)))
}
