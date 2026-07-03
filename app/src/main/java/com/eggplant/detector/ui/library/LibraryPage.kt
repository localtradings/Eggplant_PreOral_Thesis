package com.eggplant.detector.ui.library

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eggplant.detector.components.DiseaseArtwork
import com.eggplant.detector.components.DiseaseCard
import com.eggplant.detector.components.FilterChips
import com.eggplant.detector.components.SearchBar
import com.eggplant.detector.data.DiseaseData
import com.eggplant.detector.model.Disease
import com.eggplant.detector.model.DiseaseType
import com.eggplant.detector.theme.EggplantPurple
import com.eggplant.detector.theme.LeafGreen

@Composable
fun LibraryPage(onDiseaseClick: (Disease) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf("All") }
    val selectedType = when (selectedFilter) {
        "Leaf Disease" -> DiseaseType.LEAF_DISEASE
        "Fruit Disease" -> DiseaseType.FRUIT_DISEASE
        else -> null
    }
    val filtered = DiseaseData.filter(query, selectedType)
    val leafDiseases = filtered.filter { it.type == DiseaseType.LEAF_DISEASE }
    val fruitDiseases = filtered.filter { it.type == DiseaseType.FRUIT_DISEASE }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .semantics { contentDescription = "Disease library list" },
        contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 14.dp),
    ) {
        item { LibraryHeader() }
        item { Spacer(Modifier.height(8.dp)) }
        item {
            SearchBar(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search eggplant disease...",
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
        item {
            FilterChips(
                options = listOf("All", "Leaf Disease", "Fruit Disease"),
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
                    Text("No diseases found", style = MaterialTheme.typography.titleLarge)
                    Text("Try another search or filter.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            if (leafDiseases.isNotEmpty()) {
                item { Spacer(Modifier.height(8.dp)) }
                item { SectionHeader("Leaf Disease", LeafGreen) }
                items(leafDiseases.size, key = { leafDiseases[it].id }) { index ->
                    DiseaseCard(leafDiseases[index], { onDiseaseClick(leafDiseases[index]) })
                    if (index != leafDiseases.lastIndex) Spacer(Modifier.height(2.dp))
                }
            }
            if (fruitDiseases.isNotEmpty()) {
                item { Spacer(Modifier.height(8.dp)) }
                item { SectionHeader("Fruit Disease", EggplantPurple) }
                items(fruitDiseases.size, key = { fruitDiseases[it].id }) { index ->
                    DiseaseCard(fruitDiseases[index], { onDiseaseClick(fruitDiseases[index]) })
                    if (index != fruitDiseases.lastIndex) Spacer(Modifier.height(2.dp))
                }
            }
        }
    }
}

@Composable
private fun LibraryHeader() {
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
                "Library",
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp, lineHeight = 28.sp),
            )
            Text(
                "Learn about common eggplant diseases and how to manage them.",
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 11.5.sp, lineHeight = 16.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = {}, modifier = Modifier.size(38.dp)) {
            Icon(
                Icons.AutoMirrored.Outlined.HelpOutline,
                contentDescription = "Library help display only",
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
