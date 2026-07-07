package com.eggplant.detector.feature.camera

internal class ResultNavigationGate {
    private var routed = false

    fun tryRoute(route: () -> Unit): Boolean {
        if (routed) return false
        routed = true
        route()
        return true
    }

    fun reset() {
        routed = false
    }
}
