package com.eggplant.detector.feature.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.eggplant.detector.app.EggplantAppViewModel
import com.eggplant.detector.R
import com.eggplant.detector.detection.api.DetectionBox
import com.eggplant.detector.detection.api.DetectionClassPolicy
import com.eggplant.detector.detection.api.DetectionStatus
import com.eggplant.detector.detection.api.InputSource
import com.eggplant.detector.detection.ncnn.NcnnDetectionEngine
import com.eggplant.detector.detection.ncnn.ModelMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CameraScreen(
    viewModel: EggplantAppViewModel,
    onBack: () -> Unit,
    onResult: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val autoSaveEnabled by viewModel.autoSaveEnabled.collectAsState()
    val detectHealthyLeafEnabled by viewModel.detectHealthyLeafEnabled.collectAsState()
    val detectHealthyPlantEnabled by viewModel.detectHealthyPlantEnabled.collectAsState()
    val catalog by viewModel.catalog.collectAsState()
    val healthyLeafName = stringResource(R.string.healthy_leaf_name)
    val healthyPlantName = stringResource(R.string.healthy_plant_name)
    val galleryOpenFailed = stringResource(R.string.gallery_open_failed)
    val captureFailed = stringResource(R.string.capture_failed)
    val noSupportedDiseaseFound = stringResource(R.string.no_supported_disease_found)
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

    fun openScene(scene: CameraScene, primary: DetectionBox, afterReady: () -> Unit) {
        controller?.pauseAnalysis()
        viewModel.openDetectionScene(scene, primary, afterReady)
    }

    fun handleStillResult(result: Result<CameraScene>, fallbackError: String) {
        cameraState = cameraState.copy(isStillImageProcessing = false)
        when (val outcome = result.toStillImageResult(fallbackError)) {
            is StillImageResult.Disease -> openScene(outcome.scene, outcome.primary, onResult)
            is StillImageResult.Healthy -> openScene(outcome.scene, outcome.primary, onResult)
            is StillImageResult.NoMatch -> {
                controller?.resumeAnalysis()
                cameraState = cameraState.copy(
                    status = DetectionStatus.SEARCHING,
                    visibleDetections = emptyList(),
                    stableDetections = emptyList(),
                    confirmedDetections = emptyList(),
                    saveEligible = false,
                    error = noSupportedDiseaseFound,
                )
            }
            is StillImageResult.Failure -> {
                controller?.resumeAnalysis()
                cameraState = cameraState.copy(error = outcome.message)
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null || cameraState.isStillImageProcessing) return@rememberLauncherForActivityResult
        val activeController = controller
        if (activeController == null) {
            cameraState = cameraState.copy(error = galleryOpenFailed)
            return@rememberLauncherForActivityResult
        }
        activeController.pauseAnalysis()
        cameraState = cameraState.copy(isStillImageProcessing = true, error = null)
        scope.launch {
            val bitmap = runCatching { withContext(Dispatchers.IO) { context.decodeGalleryBitmap(uri) } }
                .getOrElse { error ->
                    activeController.resumeAnalysis()
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

    LaunchedEffect(autoSaveEnabled, cameraState.saveEligible, controller?.currentScene()?.rgbFrame?.sceneToken) {
        val activeController = controller
        val scene = activeController?.currentScene()
        val primary = scene?.stability?.stableDetections?.maxByOrNull { it.confidence }
        if (autoSaveEnabled && cameraState.saveEligible && scene != null && primary != null) {
            viewModel.openDetectionScene(scene, primary) {
                viewModel.saveCurrentResult { saved -> if (saved) activeController.markSaved() }
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
            onDetectionClick = { selected ->
                controller?.currentScene()?.let { scene -> openScene(scene, selected, onResult) }
            },
        )
        CameraTopBar(
            state = cameraState,
            onBack = onBack,
            onToggleTorch = { controller?.toggleTorch() },
        )
        CameraStatus(cameraState, Modifier.align(Alignment.TopCenter).padding(top = 82.dp))
        CameraBottomBar(
            saveEnabled = cameraState.saveEligible,
            processing = cameraState.isStillImageProcessing,
            onGallery = {
                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onCapture = {
                val activeController = controller
                if (activeController == null || cameraState.isStillImageProcessing) return@CameraBottomBar
                activeController.pauseAnalysis()
                cameraState = cameraState.copy(isStillImageProcessing = true, error = null)
                activeController.capturePhoto { result ->
                    handleStillResult(result, captureFailed)
                }
            },
            onSave = {
                val activeController = controller
                val scene = activeController?.currentScene()
                val primary = scene?.stability?.stableDetections?.maxByOrNull { it.confidence }
                if (scene != null && primary != null) {
                    viewModel.openDetectionScene(scene, primary) {
                        viewModel.saveCurrentResult { saved ->
                            if (saved) {
                                activeController.markSaved()
                                onSaved()
                            }
                        }
                    }
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
