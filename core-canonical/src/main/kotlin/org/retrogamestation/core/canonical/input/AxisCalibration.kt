package org.retrogamestation.core.canonical.input

import org.retrogamestation.core.canonical.model.StickDeadzone
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * High-performance axis calibration and deadzone processor.
 * Supports axial and radial (circular) deadzones, outer clamp, sensitivity curves,
 * and trigger normalization.
 */
object AxisCalibration {

    /**
     * Calibrates a single 1D axis value.
     *
     * @param rawValue Raw axis value in [-1.0f, 1.0f]
     * @param deadzone Deadzone configuration containing inner threshold, outer threshold, and curve
     * @param inverted True if the axis direction should be inverted
     * @return Calibrated value in [-1.0f, 1.0f]
     */
    fun calibrate1D(rawValue: Float, deadzone: StickDeadzone, inverted: Boolean = false): Float {
        val sign = sign(rawValue)
        val absVal = abs(rawValue)

        if (absVal <= deadzone.inner) {
            return 0.0f
        }

        // Clamp to outer threshold
        val clamped = min(absVal, deadzone.outer)

        // Rescale from [inner, outer] to [0.0, 1.0]
        val range = deadzone.outer - deadzone.inner
        val normalized = if (range > 0.0001f) {
            (clamped - deadzone.inner) / range
        } else {
            0.0f
        }

        // Apply non-linear sensitivity curve
        val curved = if (deadzone.curve != 1.0f) {
            normalized.pow(deadzone.curve)
        } else {
            normalized
        }

        val result = sign * curved
        return if (inverted) -result else result
    }

    /**
     * Calibrates a 2D stick pair (X, Y) using a radial (circular) deadzone.
     * Preserves stick angle while smoothing magnitude.
     */
    fun calibrate2DRadial(
        rawX: Float,
        rawY: Float,
        deadzone: StickDeadzone,
        invertX: Boolean = false,
        invertY: Boolean = false
    ): Pair<Float, Float> {
        val magnitude = sqrt((rawX * rawX + rawY * rawY).toDouble()).toFloat()

        if (magnitude <= deadzone.inner) {
            return Pair(0.0f, 0.0f)
        }

        val clampedMag = min(magnitude, deadzone.outer)
        val range = deadzone.outer - deadzone.inner
        val normalizedMag = if (range > 0.0001f) {
            (clampedMag - deadzone.inner) / range
        } else {
            0.0f
        }

        val curvedMag = if (deadzone.curve != 1.0f) {
            normalizedMag.pow(deadzone.curve)
        } else {
            normalizedMag
        }

        // Unit vector * curved magnitude
        var outX = (rawX / magnitude) * curvedMag
        var outY = (rawY / magnitude) * curvedMag

        if (invertX) outX = -outX
        if (invertY) outY = -outY

        return Pair(outX.coerceIn(-1.0f, 1.0f), outY.coerceIn(-1.0f, 1.0f))
    }

    /**
     * Calibrates analog trigger axis.
     * Handles drivers where rest position is 0.0f or -1.0f.
     */
    fun calibrateTrigger(rawValue: Float, deadzone: Float = 0.05f): Float {
        // Some drivers report -1.0 at rest and 1.0 when fully pressed
        val adjusted = if (rawValue < -0.1f) {
            (rawValue + 1.0f) / 2.0f
        } else {
            rawValue
        }

        val clamped = adjusted.coerceIn(0.0f, 1.0f)
        if (clamped <= deadzone) {
            return 0.0f
        }

        val range = 1.0f - deadzone
        val normalized = (clamped - deadzone) / range
        return normalized.coerceIn(0.0f, 1.0f)
    }
}
