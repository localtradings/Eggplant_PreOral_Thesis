package com.eggplant.detector.navigation

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val CAMERA = "camera"
    const val RESULT = "result"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val DISEASE_DETAIL = "disease/{diseaseId}"
    const val HISTORY_DETAIL = "history/{resultId}"

    fun diseaseDetail(id: String): String = "disease/$id"
    fun historyDetail(id: String): String = "history/$id"
}
