package org.retrogamestation.core.canonical.injector

import org.retrogamestation.core.canonical.model.CanonicalButton
import org.retrogamestation.core.canonical.model.EmulationConfig
import org.retrogamestation.core.canonical.model.HotkeyAction

/**
 * PPSSPP controls.ini injector.
 * Generates the [ControlMapping] section with exact PPSSPP syntax and NKCODE mappings.
 * Format: <action> = <deviceId>-<keyCode>[, <deviceId>-<keyCode>...]
 * Device ID 1 is the primary Android gamepad.
 */
class PpssppControlsInjector : ConfigInjector {

    companion object {
        const val DEVICE_ID_GAMEPAD = 1
        const val DEVICE_ID_AXIS = 4001

        // PPSSPP Virtual Axis Codes
        const val JOYSTICK_AXIS_X_MIN = 4003 // Left
        const val JOYSTICK_AXIS_X_MAX = 4004 // Right
        const val JOYSTICK_AXIS_Y_MIN = 4001 // Up
        const val JOYSTICK_AXIS_Y_MAX = 4002 // Down
    }

    override fun generate(config: EmulationConfig): String {
        val input = config.input
        val sb = StringBuilder()
        sb.appendLine("# ===========================================")
        sb.appendLine("# RetroGameStation - PPSSPP Control Mappings")
        sb.appendLine("# Generated for: ${config.configId}")
        sb.appendLine("# ===========================================")
        sb.appendLine()
        sb.appendLine("[ControlMapping]")

        // Helper to get mapped Android KeyCode from CanonicalButton
        fun getCode(btn: CanonicalButton, defaultCode: Int): Int {
            return input.buttonMappings[btn]?.keyCode ?: defaultCode
        }

        // Face buttons (Cross=A, Circle=B, Square=X, Triangle=Y)
        val crossCode = getCode(CanonicalButton.A, 96)
        val circleCode = getCode(CanonicalButton.B, 97)
        val squareCode = getCode(CanonicalButton.X, 99)
        val triangleCode = getCode(CanonicalButton.Y, 100)

        sb.appendLine("pad1.cross = $DEVICE_ID_GAMEPAD-$crossCode")
        sb.appendLine("pad1.circle = $DEVICE_ID_GAMEPAD-$circleCode")
        sb.appendLine("pad1.square = $DEVICE_ID_GAMEPAD-$squareCode")
        sb.appendLine("pad1.triangle = $DEVICE_ID_GAMEPAD-$triangleCode")

        // D-Pad
        val upCode = getCode(CanonicalButton.DPAD_UP, 19)
        val downCode = getCode(CanonicalButton.DPAD_DOWN, 20)
        val leftCode = getCode(CanonicalButton.DPAD_LEFT, 21)
        val rightCode = getCode(CanonicalButton.DPAD_RIGHT, 22)

        sb.appendLine("pad1.up = $DEVICE_ID_GAMEPAD-$upCode")
        sb.appendLine("pad1.down = $DEVICE_ID_GAMEPAD-$downCode")
        sb.appendLine("pad1.left = $DEVICE_ID_GAMEPAD-$leftCode")
        sb.appendLine("pad1.right = $DEVICE_ID_GAMEPAD-$rightCode")

        // Shoulders & System
        val lCode = getCode(CanonicalButton.L1, 102)
        val rCode = getCode(CanonicalButton.R1, 103)
        val startCode = getCode(CanonicalButton.START, 108)
        val selectCode = getCode(CanonicalButton.SELECT, 109)

        sb.appendLine("pad1.l = $DEVICE_ID_GAMEPAD-$lCode")
        sb.appendLine("pad1.r = $DEVICE_ID_GAMEPAD-$rCode")
        sb.appendLine("pad1.start = $DEVICE_ID_GAMEPAD-$startCode")
        sb.appendLine("pad1.select = $DEVICE_ID_GAMEPAD-$selectCode")

        // Analog stick directions
        sb.appendLine("pad1.an.up = $DEVICE_ID_AXIS-$JOYSTICK_AXIS_Y_MIN")
        sb.appendLine("pad1.an.down = $DEVICE_ID_AXIS-$JOYSTICK_AXIS_Y_MAX")
        sb.appendLine("pad1.an.left = $DEVICE_ID_AXIS-$JOYSTICK_AXIS_X_MIN")
        sb.appendLine("pad1.an.right = $DEVICE_ID_AXIS-$JOYSTICK_AXIS_X_MAX")

        // Hotkeys mapped to PPSSPP actions
        input.hotkeyMappings.forEach { (action, binding) ->
            val ppssppAction = getPpssppActionName(action)
            if (ppssppAction != null) {
                val triggerCode = getCode(binding.triggerButton, 0)
                val modifierCode = binding.modifierButton?.let { getCode(it, 0) }
                if (modifierCode != null && modifierCode != 0) {
                    sb.appendLine("$ppssppAction = $DEVICE_ID_GAMEPAD-$modifierCode, $DEVICE_ID_GAMEPAD-$triggerCode")
                } else if (triggerCode != 0) {
                    sb.appendLine("$ppssppAction = $DEVICE_ID_GAMEPAD-$triggerCode")
                }
            }
        }

        return sb.toString()
    }

    private fun getPpssppActionName(action: HotkeyAction): String? {
        return when (action) {
            HotkeyAction.PAUSE -> "pause"
            HotkeyAction.FAST_FORWARD -> "fastforward"
            HotkeyAction.REWIND -> "rewind"
            HotkeyAction.SAVE_STATE -> "statesave"
            HotkeyAction.LOAD_STATE -> "stateload"
            HotkeyAction.SCREENSHOT -> "screenshot"
            HotkeyAction.NEXT_SAVE_SLOT -> "nextslot"
            HotkeyAction.TOGGLE_FPS -> "togglefps"
            else -> null
        }
    }
}
