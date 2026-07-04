package com.eggplant.detector.ui.history

import androidx.compose.runtime.Composable
import com.eggplant.detector.model.ScanResult
import com.eggplant.detector.ui.result.ResultReport
import androidx.compose.ui.res.stringResource
import com.eggplant.detector.R

@Composable
fun HistoryDetailPage(result: ScanResult?, onBack: () -> Unit) {
    ResultReport(
        result = result,
        title = stringResource(R.string.history_detail),
        onBack = onBack,
    )
}
