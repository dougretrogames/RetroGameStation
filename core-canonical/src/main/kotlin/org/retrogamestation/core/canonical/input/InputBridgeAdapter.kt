package org.retrogamestation.core.canonical.input

import org.retrogamestation.core.canonical.model.CanonicalAxis
import org.retrogamestation.core.canonical.model.CanonicalButton
import org.retrogamestation.core.canonical.model.HotkeyAction
import org.retrostation.core.engine.IGameEngine
import org.retrostation.core.input.GamePadKey
import org.retrostation.core.input.InputEvent

/**
 * Bridges [AndroidInputManager] events directly to [IGameEngine] native core instances.
 * Translates canonical buttons and analog axes into [InputEvent] with appropriate [GamePadKey] codes.
 */
class InputBridgeAdapter(
    private val engine: IGameEngine? = null,
    private val onHotkey: ((HotkeyAction) -> Unit)? = null
) : InputEventListener {

    override fun onButtonEvent(playerIndex: Int, button: CanonicalButton, isPressed: Boolean) {
        val key = mapButtonToGamePadKey(button) ?: return
        val event = if (isPressed) {
            InputEvent.buttonDown(key, port = playerIndex)
        } else {
            InputEvent.buttonUp(key, port = playerIndex)
        }
        engine?.sendInput(event)
    }

    override fun onAxisEvent(playerIndex: Int, axis: CanonicalAxis, value: Float) {
        val key = mapAxisToGamePadKey(axis) ?: return
        val event = InputEvent.axisMove(key, value, port = playerIndex)
        engine?.sendInput(event)
    }

    override fun onHotkeyTriggered(action: HotkeyAction) {
        onHotkey?.invoke(action)
    }

    companion object {
        fun mapButtonToGamePadKey(button: CanonicalButton): GamePadKey? {
            return when (button) {
                CanonicalButton.A -> GamePadKey.BUTTON_A
                CanonicalButton.B -> GamePadKey.BUTTON_B
                CanonicalButton.X -> GamePadKey.BUTTON_X
                CanonicalButton.Y -> GamePadKey.BUTTON_Y
                CanonicalButton.DPAD_UP -> GamePadKey.DPAD_UP
                CanonicalButton.DPAD_DOWN -> GamePadKey.DPAD_DOWN
                CanonicalButton.DPAD_LEFT -> GamePadKey.DPAD_LEFT
                CanonicalButton.DPAD_RIGHT -> GamePadKey.DPAD_RIGHT
                CanonicalButton.L1 -> GamePadKey.SHOULDER_L1
                CanonicalButton.R1 -> GamePadKey.SHOULDER_R1
                CanonicalButton.L2 -> GamePadKey.TRIGGER_L2
                CanonicalButton.R2 -> GamePadKey.TRIGGER_R2
                CanonicalButton.L3 -> GamePadKey.THUMB_L3
                CanonicalButton.R3 -> GamePadKey.THUMB_R3
                CanonicalButton.START -> GamePadKey.START
                CanonicalButton.SELECT -> GamePadKey.SELECT
                CanonicalButton.HOME -> null
                CanonicalButton.CAPTURE -> null
            }
        }

        fun mapAxisToGamePadKey(axis: CanonicalAxis): GamePadKey? {
            return when (axis) {
                CanonicalAxis.LEFT_STICK_X -> GamePadKey.AXIS_LEFT_X
                CanonicalAxis.LEFT_STICK_Y -> GamePadKey.AXIS_LEFT_Y
                CanonicalAxis.RIGHT_STICK_X -> GamePadKey.AXIS_RIGHT_X
                CanonicalAxis.RIGHT_STICK_Y -> GamePadKey.AXIS_RIGHT_Y
                CanonicalAxis.TRIGGER_LEFT -> GamePadKey.TRIGGER_L2
                CanonicalAxis.TRIGGER_RIGHT -> GamePadKey.TRIGGER_R2
            }
        }
    }
}
