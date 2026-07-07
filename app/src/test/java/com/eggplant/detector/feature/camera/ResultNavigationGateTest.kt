package com.eggplant.detector.feature.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultNavigationGateTest {
    @Test
    fun `result route can be opened once until reset`() {
        val gate = ResultNavigationGate()
        var routeCount = 0

        assertTrue(gate.tryRoute { routeCount++ })
        assertFalse(gate.tryRoute { routeCount++ })
        gate.reset()
        assertTrue(gate.tryRoute { routeCount++ })

        assertEquals(2, routeCount)
    }
}
