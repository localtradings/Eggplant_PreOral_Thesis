package com.eggplant.detector.feature.camera

import com.eggplant.detector.detection.api.EngineState
import org.junit.Assert.assertEquals
import org.junit.Test

class ShutterActionCoordinatorTest {
    @Test
    fun `tap requests capture when the engine is ready`() {
        val coordinator = ShutterActionCoordinator()

        assertEquals(
            ShutterAction.CAPTURE,
            coordinator.onTap(
                processing = false,
                engineState = EngineState.READY,
            ),
        )
    }

    @Test
    fun `tap is ignored while processing or when the engine is unavailable`() {
        val coordinator = ShutterActionCoordinator()

        assertEquals(
            ShutterAction.NONE,
            coordinator.onTap(
                processing = true,
                engineState = EngineState.READY,
            ),
        )
        assertEquals(
            ShutterAction.NONE,
            coordinator.onTap(
                processing = false,
                engineState = EngineState.FAILED,
            ),
        )
    }

    @Test
    fun `long press starts live preview and release stops it`() {
        val coordinator = ShutterActionCoordinator()

        assertEquals(
            ShutterAction.START_LIVE_PREVIEW,
            coordinator.onLongPress(
                processing = false,
                engineState = EngineState.READY,
            ),
        )
        assertEquals(ShutterAction.STOP_LIVE_PREVIEW, coordinator.onPressedChanged(isPressed = false))
    }

    @Test
    fun `release without an active long press does nothing`() {
        val coordinator = ShutterActionCoordinator()

        assertEquals(ShutterAction.NONE, coordinator.onPressedChanged(isPressed = false))
    }
}
