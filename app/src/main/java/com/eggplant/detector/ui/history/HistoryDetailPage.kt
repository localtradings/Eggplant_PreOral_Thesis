package com.eggplant.detector.ui.history

import androidx.compose.runtime.Composable
import com.eggplant.detector.model.ScanResult
import com.eggplant.detector.ui.result.ResultReport

@Composable
fun HistoryDetailPage(result: ScanResult?, onBack: () -> Unit) {
    ResultReport(
        result = result,
        title = "History Detail",
        onBack = onBack,
    )
}
