package com.eggplant.detector.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eggplant.detector.core.ui.components.DiseaseArtwork
import com.eggplant.detector.domain.model.Disease
import androidx.compose.ui.res.stringResource
import com.eggplant.detector.R

@Composable
fun DiseaseDetailsScreen(disease: Disease?, onBack: () -> Unit) {
    if (disease == null) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(stringResource(R.string.disease_not_found))
            androidx.compose.material3.TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
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
            Text(stringResource(R.string.disease_detail), style = MaterialTheme.typography.titleLarge)
        }
        DiseaseArtwork(disease.id, Modifier.fillMaxWidth().height(240.dp))
        Text(disease.name, style = MaterialTheme.typography.headlineMedium)
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                stringResource(if (disease.type.name.startsWith("LEAF")) R.string.leaf_disease else R.string.fruit_disease),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        DetailSection(stringResource(R.string.about_disease, disease.name), disease.symptomPreview)
        DetailSection(stringResource(R.string.common_signs), disease.signs.joinToString("\n") { "• $it" })
        DetailSection(stringResource(R.string.recommended_action), disease.treatment)
        DetailSection(stringResource(R.string.prevention), disease.prevention)
        Text(
            stringResource(R.string.educational_notice),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DetailSection(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(body, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
