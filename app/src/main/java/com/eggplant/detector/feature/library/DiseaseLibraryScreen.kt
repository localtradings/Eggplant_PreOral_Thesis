package com.eggplant.detector.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eggplant.detector.core.ui.components.DiseaseArtwork
import com.eggplant.detector.core.ui.components.DiseaseCard
import com.eggplant.detector.core.ui.components.FilterChips
import com.eggplant.detector.core.ui.components.SearchBar
import com.eggplant.detector.R
import com.eggplant.detector.data.catalog.DiseaseCatalog
import com.eggplant.detector.domain.model.Disease
import com.eggplant.detector.domain.model.DiseaseType
import com.eggplant.detector.core.ui.theme.EggplantPurple
import com.eggplant.detector.core.ui.theme.LeafGreen

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun DiseaseLibraryScreen(
    diseases: List<Disease>,
    onDiseaseClick: (Disease) -> Unit,
    onHelp: () -> Unit,
) {
    val allLabel = stringResource(R.string.all)
    val leafLabel = stringResource(R.string.leaf_disease)
    val fruitLabel = stringResource(R.string.fruit_disease)
    val listDescription = stringResource(R.string.disease_library_list)
    var query by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable(allLabel) { mutableStateOf(allLabel) }
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }
    val selectedType = when (selectedFilter) {
        leafLabel -> DiseaseType.LEAF_DISEASE
        fruitLabel -> DiseaseType.FRUIT_DISEASE
        else -> null
    }
    val filtered = DiseaseCatalog.filter(diseases, query, selectedType)
    val leafDiseases = filtered.filter { it.type == DiseaseType.LEAF_DISEASE }
    val fruitDiseases = filtered.filter { it.type == DiseaseType.FRUIT_DISEASE }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .semantics { contentDescription = listDescription },
        contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 14.dp),
    ) {
        item { LibraryHeader(onHelp) }
        item { Spacer(Modifier.height(8.dp)) }
        item {
            SearchBar(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.library_search),
                onFilterClick = { showFilterSheet = true },
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
        item {
            FilterChips(
                options = listOf(allLabel, leafLabel, fruitLabel),
                selected = selectedFilter,
                onSelected = { selectedFilter = it },
            )
        }

        if (filtered.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 70.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DiseaseArtwork("empty", Modifier.fillMaxWidth(.48f).height(120.dp))
                    Text(stringResource(R.string.no_diseases), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.try_search), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            if (leafDiseases.isNotEmpty()) {
                item { Spacer(Modifier.height(8.dp)) }
                item { SectionHeader(leafLabel, LeafGreen) }
                items(leafDiseases.size, key = { leafDiseases[it].id }) { index ->
                    DiseaseCard(leafDiseases[index], { onDiseaseClick(leafDiseases[index]) })
                    if (index != leafDiseases.lastIndex) Spacer(Modifier.height(2.dp))
                }
            }
            if (fruitDiseases.isNotEmpty()) {
                item { Spacer(Modifier.height(8.dp)) }
                item { SectionHeader(fruitLabel, EggplantPurple) }
                items(fruitDiseases.size, key = { fruitDiseases[it].id }) { index ->
                    DiseaseCard(fruitDiseases[index], { onDiseaseClick(fruitDiseases[index]) })
                    if (index != fruitDiseases.lastIndex) Spacer(Modifier.height(2.dp))
                }
            }
        }
    }

    if (showFilterSheet) {
        ModalBottomSheet(onDismissRequest = { showFilterSheet = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(stringResource(R.string.filter_diseases), style = MaterialTheme.typography.titleLarge)
                listOf(allLabel, leafLabel, fruitLabel).forEach { option ->
                    TextButton(
                        onClick = {
                            selectedFilter = option
                            showFilterSheet = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (selectedFilter == option) "✓  $option" else option)
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryHeader(onHelp: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(54.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.MenuBook,
            contentDescription = null,
            modifier = Modifier.size(42.dp).padding(end = 10.dp),
            tint = EggplantPurple,
        )
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.library_title),
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp, lineHeight = 28.sp),
            )
            Text(
                stringResource(R.string.library_subtitle),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 11.5.sp, lineHeight = 16.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onHelp, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.AutoMirrored.Outlined.HelpOutline,
                contentDescription = stringResource(R.string.open_help),
                tint = EggplantPurple,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, tint: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().height(30.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Outlined.Spa, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 13.5.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold),
        )
    }
}
