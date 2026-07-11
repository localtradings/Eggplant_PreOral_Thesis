package com.eggplant.detector.feature.result

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.eggplant.detector.app.EggplantAppViewModel
import com.eggplant.detector.R
import com.eggplant.detector.app.SaveState
import com.eggplant.detector.app.ResultWarning
import com.eggplant.detector.app.SnapshotState
import com.eggplant.detector.app.CloudActionState
import com.eggplant.detector.core.ui.components.ConfidenceDisplay
import com.eggplant.detector.core.ui.components.PrimaryButton
import com.eggplant.detector.core.ui.components.ResultArtwork
import com.eggplant.detector.domain.model.ScanOutcome
import com.eggplant.detector.domain.model.ScanResult
import com.eggplant.detector.domain.model.ScanCategory
import com.eggplant.detector.detection.api.DetectionBox
import com.eggplant.detector.detection.api.DetectionStatus
import com.eggplant.detector.detection.ncnn.ModelMetadata
import com.eggplant.detector.feature.camera.CameraAnalysisState
import com.eggplant.detector.feature.camera.DetectionOverlay
import com.eggplant.detector.feature.camera.OverlayContentScale
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DetectionResultScreen(
    viewModel: EggplantAppViewModel,
    title: String,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onScanAgain: () -> Unit,
) {
    val result by viewModel.currentResult.collectAsState()
    val saveState by viewModel.saveState.collectAsState()
    val resultWarning by viewModel.resultWarning.collectAsState()
    val snapshotState by viewModel.snapshotState.collectAsState()
    val cloudAction by viewModel.cloudActionState.collectAsState()
    val requestDraft by viewModel.diseaseRequestDraft.collectAsState()
    var showShareDialog by remember { mutableStateOf(false) }
    var showRequestDialog by remember { mutableStateOf(false) }
    var requestedName by remember { mutableStateOf("") }
    var requestNotes by remember { mutableStateOf("") }
    var rightsConsent by remember { mutableStateOf(false) }
    val requestPhotoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 2),
    ) { uris -> viewModel.addDiseaseRequestPhotos(uris) }
    ResultReport(
        result = result,
        title = title,
        onBack = onBack,
        actions = {
            if (snapshotState == SnapshotState.PREPARING) {
                Text(
                    localized("Preparing photo…", "Inihahanda ang larawan…"),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (resultWarning == ResultWarning.SNAPSHOT_UNAVAILABLE) {
                Text(
                    stringResource(R.string.snapshot_unavailable_warning),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (saveState == SaveState.FAILED && result?.outcome == ScanOutcome.DISEASE) {
                Text(
                    stringResource(R.string.save_history_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (result?.outcome == ScanOutcome.DISEASE) {
                PrimaryButton(
                    text = if (saveState == SaveState.SAVING) stringResource(R.string.saving_history) else stringResource(R.string.save_history),
                    onClick = onSave,
                    icon = Icons.Outlined.CheckCircle,
                    enabled = saveState != SaveState.SAVING && snapshotState != SnapshotState.PREPARING,
                )
                if (result?.confidence ?: 0 >= 50 && result?.source != "gallery") {
                    OutlinedButton(
                        onClick = { showShareDialog = true },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(18.dp),
                        enabled = snapshotState == SnapshotState.READY && cloudAction != CloudActionState.Working,
                    ) { Text(stringResource(R.string.share_to_global)) }
                }
            }
            if (result?.outcome == ScanOutcome.NO_MATCH) {
                OutlinedButton(
                    onClick = {
                        viewModel.beginDiseaseRequest()
                        showRequestDialog = true
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(18.dp),
                    enabled = snapshotState == SnapshotState.READY && cloudAction != CloudActionState.Working,
                ) { Text(stringResource(R.string.request_this_disease)) }
            }
            when (val action = cloudAction) {
                CloudActionState.Idle -> Unit
                CloudActionState.Working -> Text(localized("Working…", "Isinasagawa…"), color = MaterialTheme.colorScheme.primary)
                is CloudActionState.Queued -> Text(action.message, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
                is CloudActionState.Error -> Text(action.message, color = MaterialTheme.colorScheme.error)
            }
            OutlinedButton(
                onClick = onScanAgain,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = null)
                Text("  ${stringResource(R.string.scan_again)}")
            }
        },
    )
    if (showShareDialog) {
        AlertDialog(
            onDismissRequest = { showShareDialog = false },
            title = { Text(stringResource(R.string.share_to_global)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    result?.let { SnapshotPreview(it, Modifier.fillMaxWidth().height(132.dp)) }
                    Text(localized("Publish this real scan photo anonymously? It will appear immediately after server validation and expire after 180 days.", "I-publish nang anonymous ang tunay na larawan ng scan na ito? Lalabas ito matapos ang server validation at mag-e-expire pagkalipas ng 180 araw."))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (!viewModel.globalSharingEnabled.value) viewModel.setGlobalSharing(true)
                    viewModel.shareCurrentResult()
                    showShareDialog = false
                }) { Text(stringResource(R.string.share_to_global)) }
            },
            dismissButton = { TextButton(onClick = { showShareDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (showRequestDialog) {
        AlertDialog(
            onDismissRequest = {
                viewModel.cancelDiseaseRequestDraft()
                showRequestDialog = false
            },
            title = { Text(stringResource(R.string.request_this_disease)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    result?.let { SnapshotPreview(it, Modifier.fillMaxWidth().height(132.dp)) }
                    Text(stringResource(R.string.real_photo_required))
                    Text(
                        stringResource(R.string.request_photo_count, requestDraft.photoPaths.size, 3),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    OutlinedButton(
                        onClick = {
                            requestPhotoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        enabled = requestDraft.photoPaths.size < 3 && !requestDraft.addingPhotos,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (requestDraft.addingPhotos) stringResource(R.string.adding_photos)
                            else stringResource(R.string.add_request_photos),
                        )
                    }
                    requestDraft.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    OutlinedTextField(requestedName, { requestedName = it.take(120) }, label = { Text(stringResource(R.string.requested_disease_name)) }, singleLine = true)
                    OutlinedTextField(requestNotes, { requestNotes = it.take(2000) }, label = { Text(stringResource(R.string.optional_notes)) }, minLines = 2)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(rightsConsent, { rightsConsent = it })
                        Text(localized("I own this photo or have permission to submit it.", "Ako ang may-ari ng larawan o may pahintulot akong isumite ito."))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = requestedName.trim().length >= 2 && rightsConsent &&
                        requestDraft.photoPaths.isNotEmpty() && !requestDraft.addingPhotos &&
                        cloudAction != CloudActionState.Working,
                    onClick = {
                        viewModel.submitDiseaseRequest(
                            requestedName,
                            requestNotes.takeIf { it.isNotBlank() },
                            rightsConsent,
                        ) { success ->
                            if (success) {
                                showRequestDialog = false
                                requestedName = ""
                                requestNotes = ""
                                rightsConsent = false
                            }
                        }
                    },
                ) { Text(stringResource(R.string.submit_request)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.cancelDiseaseRequestDraft()
                    showRequestDialog = false
                }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
fun ResultReport(
    result: ScanResult?,
    title: String,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {},
) {
    if (result == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(localized("No scan result available", "Walang available na resulta ng scan"))
            OutlinedButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        SnapshotPreview(result, Modifier.fillMaxWidth().height(250.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(localized("Detected result", "Natukoy na resulta"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(result.name, style = MaterialTheme.typography.headlineMedium)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(50),
                    ) {
                        Text(
                            localizedCategory(result),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                ConfidenceDisplay(result.confidence)
            }
        }
        when (result.outcome) {
            ScanOutcome.DISEASE -> {
                ReportSection(stringResource(R.string.signs_detected), result.signs.joinToString("\n") { "• $it" })
                val additional = result.detections
                    .filter { it.diseaseId != result.diseaseId }
                    .groupBy { it.diseaseId }
                    .mapNotNull { (_, detections) -> detections.maxByOrNull { it.confidence } }
                    .sortedByDescending { it.confidence }
                if (additional.isNotEmpty()) {
                    ReportSection(
                        localized("Also detected", "Natukoy rin"),
                        additional.joinToString("\n") { "• ${it.name} — ${it.confidence}%" },
                    )
                }
                ReportSection(stringResource(R.string.recommended_action), result.treatment)
            }
            ScanOutcome.HEALTHY_CONFIRMED -> {
                ReportSection(
                    localized("Healthy result", "Malusog na resulta"),
                    localized(
                        "No supported disease was detected in this confirmed healthy area. Healthy-only results are not saved to History.",
                        "Walang suportadong sakit na nakita sa kumpirmadong malusog na bahaging ito. Hindi sine-save sa Kasaysayan ang healthy-only na resulta.",
                    ),
                )
            }
            ScanOutcome.NO_MATCH -> {
                ReportSection(
                    localized("No supported disease detected", "Walang suportadong sakit na natukoy"),
                    localized(
                        "The selected image loaded correctly, but the packaged detector did not confirm a supported eggplant disease. Try a closer, brighter, steadier photo.",
                        "Nabuksan nang tama ang napiling larawan, pero walang nakumpirmang suportadong sakit ng talong ang detector. Subukan ang mas malapit, mas maliwanag, at mas matatag na larawan.",
                    ),
                )
            }
        }
        Text(
            localized("On-device model result for educational screening only. Confirm crop concerns with a qualified agricultural specialist.", "Resulta ng on-device model para lamang sa paunang pagsusuri. Kumpirmahin sa kwalipikadong espesyalista ang problema sa pananim."),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        actions()
    }
}

@Composable
private fun SnapshotPreview(result: ScanResult, modifier: Modifier = Modifier) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, result.imagePath) {
        value = withContext(Dispatchers.IO) {
            result.imagePath?.let(::File)?.takeIf(File::isFile)?.let { BitmapFactory.decodeFile(it.absolutePath) }
        }
    }
    val snapshot = bitmap
    var viewerOpen by remember(result.imagePath) { mutableStateOf(false) }
    if (snapshot == null) {
        ResultArtwork(result.category, result.name, modifier, result.diseaseId)
        return
    }
    val openViewerDescription = stringResource(R.string.open_result_image_viewer)
    val overlayState = result.toOverlayState(snapshot.width, snapshot.height)
    val displayName = result.overlayDisplayName()
    Box(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black)
            .clickable(
                role = Role.Button,
                onClickLabel = openViewerDescription,
                onClick = { viewerOpen = true },
            )
            .semantics { contentDescription = openViewerDescription },
    ) {
        Image(
            bitmap = snapshot.asImageBitmap(),
            contentDescription = localized("Saved scan snapshot", "Naka-save na larawan ng scan"),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
        DetectionOverlay(
            state = overlayState,
            displayName = displayName,
            onDetectionClick = null,
            contentScale = OverlayContentScale.FIT,
        )
    }
    if (viewerOpen) {
        ZoomableResultImageDialog(
            snapshot = snapshot,
            overlayState = overlayState,
            displayName = displayName,
            onDismiss = { viewerOpen = false },
        )
    }
}

@Composable
private fun ZoomableResultImageDialog(
    snapshot: android.graphics.Bitmap,
    overlayState: CameraAnalysisState,
    displayName: (DetectionBox) -> String,
    onDismiss: () -> Unit,
) {
    var transform by remember(snapshot) { mutableStateOf(ZoomableImageTransform()) }
    var showBoxes by remember(snapshot) { mutableStateOf(true) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clipToBounds(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = transform.scale
                        scaleY = transform.scale
                        translationX = transform.offset.x
                        translationY = transform.offset.y
                    }
                    .pointerInput(snapshot) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            transform = transform.applyGesture(zoomChange = zoom, panChange = pan)
                        }
                    },
            ) {
                Image(
                    bitmap = snapshot.asImageBitmap(),
                    contentDescription = stringResource(R.string.saved_scan_snapshot),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
                if (showBoxes) {
                    DetectionOverlay(
                        state = overlayState,
                        displayName = displayName,
                        onDetectionClick = null,
                        contentScale = OverlayContentScale.FIT,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = .72f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.close_result_image_viewer),
                        tint = Color.White,
                    )
                }
                TextButton(onClick = { showBoxes = !showBoxes }) {
                    Text(
                        stringResource(if (showBoxes) R.string.hide_detection_boxes else R.string.show_detection_boxes),
                        color = Color.White,
                    )
                }
            }
        }
    }
}

private fun ScanResult.toOverlayState(frameWidth: Int, frameHeight: Int): CameraAnalysisState {
    val overlayDetections = detections.mapNotNull { detection ->
        ModelMetadata.EGGPLANT_YOLO26M.classFor(detection.modelClassIndex)?.let { modelClass ->
            DetectionBox(modelClass, detection.confidence / 100f, detection.bounds)
        }
    }
    return CameraAnalysisState(
        status = when (outcome) {
            ScanOutcome.DISEASE -> DetectionStatus.DISEASE_DETECTED
            ScanOutcome.HEALTHY_CONFIRMED -> DetectionStatus.HEALTHY
            ScanOutcome.NO_MATCH -> DetectionStatus.SEARCHING
        },
        visibleDetections = overlayDetections,
        stableDetections = overlayDetections.filterNot { it.modelClass.isHealthy },
        confirmedDetections = overlayDetections,
        frameWidth = frameWidth,
        frameHeight = frameHeight,
    )
}

private fun ScanResult.overlayDisplayName(): (DetectionBox) -> String = { detection ->
    detections.firstOrNull { it.modelClassIndex == detection.modelClass.index }?.name
        ?: detection.modelClass.modelLabel.replace('_', ' ').replace('-', ' ')
}

@Composable
private fun localizedCategory(result: ScanResult): String = when (result.category) {
    ScanCategory.LEAF_DISEASE -> stringResource(R.string.leaf_disease)
    ScanCategory.FRUIT_DISEASE -> stringResource(R.string.fruit_disease)
    ScanCategory.NO_DISEASE_DETECTED -> when (result.outcome) {
        ScanOutcome.HEALTHY_CONFIRMED -> localized("Healthy", "Malusog")
        ScanOutcome.NO_MATCH -> localized("Unconfirmed", "Hindi kumpirmado")
        ScanOutcome.DISEASE -> localized("Unconfirmed", "Hindi kumpirmado")
    }
}

@Composable
private fun localized(english: String, filipino: String): String {
    val language = androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language
    return if (language == "fil" || language == "tl") filipino else english
}

@Composable
private fun ReportSection(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(body, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
