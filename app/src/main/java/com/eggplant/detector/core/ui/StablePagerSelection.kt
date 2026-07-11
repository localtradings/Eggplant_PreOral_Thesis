package com.eggplant.detector.core.ui

fun stablePageForId(ids: List<String>, selectedId: String?, fallbackPage: Int): Int {
    if (ids.isEmpty()) return 0
    val selectedPage = selectedId?.let(ids::indexOf)?.takeIf { it >= 0 }
    return selectedPage ?: fallbackPage.coerceIn(0, ids.lastIndex)
}
