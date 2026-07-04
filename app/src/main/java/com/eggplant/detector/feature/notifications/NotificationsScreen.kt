package com.eggplant.detector.feature.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eggplant.detector.app.EggplantAppViewModel
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.eggplant.detector.R

private data class LocalNotice(val key: String, @param:StringRes val title: Int, @param:StringRes val body: Int)

private val notices = listOf(
    LocalNotice("welcome", R.string.notice_ready_title, R.string.notice_ready_body),
    LocalNotice("model", R.string.notice_model_title, R.string.notice_model_body),
    LocalNotice("tip", R.string.notice_tip_title, R.string.notice_tip_body),
    LocalNotice("privacy", R.string.notice_privacy_title, R.string.notice_privacy_body),
)

@Composable
fun NotificationsScreen(viewModel: EggplantAppViewModel, onBack: () -> Unit) {
    val readKeys by viewModel.readNotificationKeys.collectAsState()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
                Text(stringResource(R.string.notifications), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { viewModel.markAllNotificationsRead(notices.map { it.key }) }) {
                    Text(stringResource(R.string.mark_all_read))
                }
            }
        }
        items(notices.size, key = { notices[it].key }) { index ->
            val notice = notices[index]
            val isRead = notice.key in readKeys
            Card(
                onClick = { viewModel.markNotificationRead(notice.key) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isRead) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(stringResource(notice.title), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(notice.body), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(if (isRead) R.string.read else R.string.new_notice), color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
