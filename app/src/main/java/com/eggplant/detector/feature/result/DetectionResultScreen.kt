package com.eggplant.detector.feature.result

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.eggplant.detector.app.EggplantAppViewModel
import com.eggplant.detector.R
import com.eggplant.detector.app.SaveState
import com.eggplant.detector.core.ui.components.ConfidenceDisplay
import com.eggplant.detector.core.ui.components.PrimaryButton
import com.eggplant.detector.core.ui.components.ResultArtwork
import com.eggplant.detector.domain.model.ScanResult
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

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
    ResultReport(
        result = result,
        title = title,
        onBack = onBack,
        actions = {
            if (saveState == SaveState.FAILED) {
                Text(
                    stringResource(R.string.save_history_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            PrimaryButton(
                text = if (saveState == SaveState.SAVING) stringResource(R.string.saving_history) else stringResource(R.string.save_history),
                onClick = onSave,
                icon = Icons.Outlined.CheckCircle,
                enabled = saveState != SaveState.SAVING,
            )
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
                            localizedCategory(result.category),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                ConfidenceDisplay(result.confidence)
            }
        }
        ReportSection(stringResource(R.string.signs_detected), result.signs.joinToString("\n") { "• $it" })
        if (result.detections.size > 1) {
            ReportSection(
                localized("Also detected", "Natukoy rin"),
                result.detections.joinToString("\n") { "• ${it.name} — ${it.confidence}%" },
            )
        }
        ReportSection(stringResource(R.string.recommended_action), result.treatment)
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
    if (snapshot == null) {
        ResultArtwork(result.category, result.name, modifier, result.diseaseId)
        return
    }
    BoxWithConstraints(modifier.background(Color.Black, RoundedCornerShape(24.dp))) {
        Image(
            bitmap = snapshot.asImageBitmap(),
            contentDescription = localized("Saved scan snapshot", "Naka-save na larawan ng scan"),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
        val density = LocalDensity.current
        val viewWidth = with(density) { maxWidth.toPx() }
        val viewHeight = with(density) { maxHeight.toPx() }
        val scale = minOf(viewWidth / snapshot.width, viewHeight / snapshot.height)
        val renderedWidth = snapshot.width * scale
        val renderedHeight = snapshot.height * scale
        val offsetX = (viewWidth - renderedWidth) / 2f
        val offsetY = (viewHeight - renderedHeight) / 2f
        result.detections.forEach { detection ->
            val left = offsetX + detection.bounds.left * renderedWidth
            val top = offsetY + detection.bounds.top * renderedHeight
            val width = (detection.bounds.right - detection.bounds.left) * renderedWidth
            val height = (detection.bounds.bottom - detection.bounds.top) * renderedHeight
            Box(
                Modifier
                    .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                    .size(with(density) { width.toDp() }, with(density) { height.toDp() })
                    .border(3.dp, Color(0xFFFFB44C), RoundedCornerShape(10.dp)),
            )
        }
    }
}

@Composable
private fun localizedCategory(category: com.eggplant.detector.domain.model.ScanCategory): String = when (category) {
    com.eggplant.detector.domain.model.ScanCategory.LEAF_DISEASE -> stringResource(R.string.leaf_disease)
    com.eggplant.detector.domain.model.ScanCategory.FRUIT_DISEASE -> stringResource(R.string.fruit_disease)
    com.eggplant.detector.domain.model.ScanCategory.NO_DISEASE_DETECTED -> localized("Healthy", "Malusog")
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
