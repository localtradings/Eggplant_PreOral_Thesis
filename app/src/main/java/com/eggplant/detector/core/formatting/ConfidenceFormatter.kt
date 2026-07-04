package com.eggplant.detector.core.formatting

object ConfidenceFormatter {
    fun format(confidence: Int): String {
        require(confidence in 0..100) { "Confidence must be between 0 and 100." }
        return "$confidence%"
    }
}
