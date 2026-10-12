package org.retrostation.core.input

/**
 * Standard input payload sent from touch/hardware controls to native engines.
 */
data class InputEvent(
    val port: Int = 0,
    val key: GamePadKey,
    val isDown: Boolean,
    val axisValue: Float = 0.0f
) {
    companion object {
        fun buttonDown(key: GamePadKey, port: Int = 0): InputEvent =
            InputEvent(port = port, key = key, isDown = true, axisValue = 1.0f)

        fun buttonUp(key: GamePadKey, port: Int = 0): InputEvent =
            InputEvent(port = port, key = key, isDown = false, axisValue = 0.0f)

        fun axisMove(key: GamePadKey, value: Float, port: Int = 0): InputEvent =
            InputEvent(port = port, key = key, isDown = value != 0.0f, axisValue = value)
    }
}
