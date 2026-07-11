package com.eggplant.detector.core.ui.motion

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.compositionLocalOf
import com.eggplant.detector.domain.model.MotionPreference

data class EggplantMotion(
    val preference: MotionPreference,
    val fastMillis: Int,
    val standardMillis: Int,
    val emphasizedMillis: Int,
    val spatialMovement: Boolean,
) {
    val interruptibleSpring get() = spring<Float>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)

    companion object {
        fun forPreference(preference: MotionPreference) = if (preference == MotionPreference.REDUCED) {
            EggplantMotion(preference, 0, 0, 0, false)
        } else {
            EggplantMotion(preference, 120, 220, 320, true)
        }
    }
}

val LocalEggplantMotion = compositionLocalOf { EggplantMotion.forPreference(MotionPreference.SYSTEM) }
