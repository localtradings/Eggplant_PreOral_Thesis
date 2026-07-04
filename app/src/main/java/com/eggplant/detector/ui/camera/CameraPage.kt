package com.eggplant.detector.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.eggplant.detector.AppViewModel
import com.eggplant.detector.R
import com.eggplant.detector.camera.CameraAnalysisState
import com.eggplant.detector.camera.CameraDetectionController
import com.eggplant.detector.camera.CameraScene
import com.eggplant.detector.detection.DetectionBox
import com.eggplant.detector.detection.DetectionStatus
import com.eggplant.detector.detection.EngineState
import com.eggplant.detector.detection.InputSource
import com.eggplant.detector.detection.NcnnDetectionEngine
import com.eggplant.detector.detection.StabilityResult
import kotlin.math.roundToInt

@Composable
fun CameraPage(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onResult: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val autoSaveEnabled by viewModel.autoSaveEnabled.collectAsState()
    val galleryOpenFailed = stringResource(R.string.gallery_open_failed)
    val captureFailed = stringResource(R.string.capture_failed)
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
    var controller by remember { mutableStateOf<CameraDetectionController?>(null) }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    DisposableEffect(lifecycleOwner) {
        val created = CameraDetectionController(
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

    fun openScene(scene: CameraScene, primary: DetectionBox, afterReady: () -> Unit) {
        controller?.pauseAnalysis()
        viewModel.openDetectionScene(scene, primary, afterReady)
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            runCatching { context.decodeGalleryBitmap(uri) }
                .onSuccess { bitmap ->
                    controller?.analyzeBitmap(bitmap, InputSource.GALLERY) { result ->
                        bitmap.recycle()
                        result.onSuccess { scene ->
                            val primary = scene.stability.stableDetections.maxByOrNull { it.confidence }
                            if (primary != null) openScene(scene, primary, onResult)
                        }
                    }
                }
                .onFailure { cameraState = cameraState.copy(error = it.message ?: galleryOpenFailed) }
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
            onGallery = {
                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onCapture = {
                controller?.capturePhoto { result ->
                    result.onSuccess { scene ->
                        val primary = scene.stability.stableDetections.maxByOrNull { it.confidence }
                        if (primary != null) openScene(scene, primary, onResult)
                    }.onFailure {
                        cameraState = cameraState.copy(error = it.message ?: captureFailed)
                    }
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

@Composable
private fun DetectionOverlay(
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

@Composable
private fun CameraTopBar(
    state: CameraAnalysisState,
    onBack: () -> Unit,
    onToggleTorch: () -> Unit,
) {
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
private fun CameraStatus(state: CameraAnalysisState, modifier: Modifier = Modifier) {
    val text = when {
        state.error != null -> state.error
        state.engineState == EngineState.UNINITIALIZED -> stringResource(R.string.loading_model)
        state.engineState != EngineState.READY -> stringResource(R.string.detection_unavailable)
        state.status == DetectionStatus.HEALTHY -> stringResource(R.string.no_disease_detected)
        state.status == DetectionStatus.DISEASE_DETECTED -> stringResource(R.string.disease_detected_tap)
        else -> stringResource(R.string.point_camera)
    }
    Surface(modifier = modifier.padding(horizontal = 24.dp), color = Color.Black.copy(alpha = .62f), shape = RoundedCornerShape(18.dp)) {
        Text(text, Modifier.padding(horizontal = 16.dp, vertical = 10.dp), color = Color.White)
    }
}

@Composable
private fun CameraBottomBar(
    saveEnabled: Boolean,
    onGallery: () -> Unit,
    onCapture: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val captureDescription = stringResource(R.string.capture_scan)
    Row(
        modifier = modifier.fillMaxWidth().background(Color.Black.copy(alpha = .55f)).padding(horizontal = 22.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        CameraControl(Icons.Outlined.Collections, stringResource(R.string.choose_gallery), onGallery)
        FloatingActionButton(
            onClick = onCapture,
            modifier = Modifier.size(76.dp).semantics { contentDescription = captureDescription }.border(4.dp, Color.White.copy(alpha = .7f), CircleShape),
            containerColor = Color.White,
            contentColor = MaterialTheme.colorScheme.primary,
            shape = CircleShape,
        ) {
            Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(34.dp))
        }
        Button(onClick = onSave, enabled = saveEnabled, shape = RoundedCornerShape(16.dp)) {
            Text(stringResource(R.string.save_scan))
        }
    }
}

@Composable
private fun CameraPermissionRequired(
    permissionRequested: Boolean,
    onRequest: () -> Unit,
    onBack: () -> Unit,
) {
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
private fun CameraControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Surface(color = Color.Black.copy(alpha = .45f), shape = CircleShape) {
        IconButton(onClick = onClick, modifier = Modifier.size(52.dp)) {
            Icon(icon, contentDescription = description, tint = Color.White)
        }
    }
}

private fun android.content.Context.decodeGalleryBitmap(uri: Uri): Bitmap {
    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val maxDimension = maxOf(info.size.width, info.size.height)
            if (maxDimension > 1600) {
                val scale = 1600f / maxDimension
                decoder.setTargetSize((info.size.width * scale).roundToInt(), (info.size.height * scale).roundToInt())
            }
        }
    } else {
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Selected image could not be read.")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > 1600) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("Selected image could not be decoded.")
        val orientation = contentResolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        if (orientation == 0) decoded else Bitmap.createBitmap(
            decoded,
            0,
            0,
            decoded.width,
            decoded.height,
            Matrix().apply { postRotate(orientation.toFloat()) },
            true,
        ).also { decoded.recycle() }
    }
    return if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false).also { bitmap.recycle() }
}
