package org.retrogamestation.core.canonical.input

import org.retrogamestation.core.canonical.model.CanonicalAxis
import org.retrogamestation.core.canonical.model.CanonicalButton
import org.retrogamestation.core.canonical.model.PhysicalAxisBinding
import org.retrogamestation.core.canonical.model.PhysicalKeyBinding

/**
 * Family profile category of physical gamepads.
 */
enum class GamepadProfileType {
    XBOX,
    PLAYSTATION,
    SWITCH_PRO,
    EIGHTBITDO,
    ANDROID_GENERIC
}

/**
 * Input profile representing the hardware mapping of a physical gamepad model to canonical controls.
 */
data class GamepadProfile(
    val profileType: GamepadProfileType,
    val name: String,
    val vendorId: Int? = null,
    val productId: Int? = null,
    val buttonMap: Map<Int, CanonicalButton>, // Android KeyCode -> CanonicalButton
    val axisMap: Map<Int, CanonicalAxis>,     // Android MotionEvent Axis -> CanonicalAxis
    val hatXAxis: Int = 15,                   // MotionEvent.AXIS_HAT_X = 15
    val hatYAxis: Int = 16,                   // MotionEvent.AXIS_HAT_Y = 16
    val triggerLeftAxis: Int = 17,            // MotionEvent.AXIS_LTRIGGER = 17 / AXIS_BRAKE = 23
    val triggerRightAxis: Int = 18,           // MotionEvent.AXIS_RTRIGGER = 18 / AXIS_GAS = 22
    val swapABForNintendo: Boolean = false,
    val swapXYForNintendo: Boolean = false
) {
    /**
     * Resolves the canonical button for a given Android KeyEvent keyCode.
     */
    fun mapKeyCode(keyCode: Int): CanonicalButton? {
        val canonical = buttonMap[keyCode] ?: return null
        if (swapABForNintendo) {
            when (canonical) {
                CanonicalButton.A -> return CanonicalButton.B
                CanonicalButton.B -> return CanonicalButton.A
                else -> {}
            }
        }
        if (swapXYForNintendo) {
            when (canonical) {
                CanonicalButton.X -> return CanonicalButton.Y
                CanonicalButton.Y -> return CanonicalButton.X
                else -> {}
            }
        }
        return canonical
    }

    /**
     * Resolves the canonical axis for a given Android MotionEvent axis index.
     */
    fun mapAxis(axis: Int): CanonicalAxis? = axisMap[axis]

    companion object {
        // Standard Android KeyCodes
        const val KEYCODE_DPAD_UP = 19
        const val KEYCODE_DPAD_DOWN = 20
        const val KEYCODE_DPAD_LEFT = 21
        const val KEYCODE_DPAD_RIGHT = 22
        const val KEYCODE_BUTTON_A = 96
        const val KEYCODE_BUTTON_B = 97
        const val KEYCODE_BUTTON_C = 98
        const val KEYCODE_BUTTON_X = 99
        const val KEYCODE_BUTTON_Y = 100
        const val KEYCODE_BUTTON_Z = 101
        const val KEYCODE_BUTTON_L1 = 102
        const val KEYCODE_BUTTON_R1 = 103
        const val KEYCODE_BUTTON_L2 = 104
        const val KEYCODE_BUTTON_R2 = 105
        const val KEYCODE_BUTTON_THUMBL = 106
        const val KEYCODE_BUTTON_THUMBR = 107
        const val KEYCODE_BUTTON_START = 108
        const val KEYCODE_BUTTON_SELECT = 109
        const val KEYCODE_BUTTON_MODE = 110

        // Standard Android MotionEvent Axes
        const val AXIS_X = 0
        const val AXIS_Y = 1
        const val AXIS_Z = 11
        const val AXIS_RZ = 14
        const val AXIS_HAT_X = 15
        const val AXIS_HAT_Y = 16
        const val AXIS_LTRIGGER = 17
        const val AXIS_RTRIGGER = 18
        const val AXIS_GAS = 22
        const val AXIS_BRAKE = 23
        const val AXIS_RX = 12
        const val AXIS_RY = 13

        /**
         * Standard Xbox Controller Profile (Xbox 360, Xbox One, Series X/S).
         */
        fun createXboxProfile(name: String = "Xbox Controller"): GamepadProfile {
            val buttons = mapOf(
                KEYCODE_DPAD_UP to CanonicalButton.DPAD_UP,
                KEYCODE_DPAD_DOWN to CanonicalButton.DPAD_DOWN,
                KEYCODE_DPAD_LEFT to CanonicalButton.DPAD_LEFT,
                KEYCODE_DPAD_RIGHT to CanonicalButton.DPAD_RIGHT,
                KEYCODE_BUTTON_A to CanonicalButton.A,
                KEYCODE_BUTTON_B to CanonicalButton.B,
                KEYCODE_BUTTON_X to CanonicalButton.X,
                KEYCODE_BUTTON_Y to CanonicalButton.Y,
                KEYCODE_BUTTON_L1 to CanonicalButton.L1,
                KEYCODE_BUTTON_R1 to CanonicalButton.R1,
                KEYCODE_BUTTON_L2 to CanonicalButton.L2,
                KEYCODE_BUTTON_R2 to CanonicalButton.R2,
                KEYCODE_BUTTON_THUMBL to CanonicalButton.L3,
                KEYCODE_BUTTON_THUMBR to CanonicalButton.R3,
                KEYCODE_BUTTON_START to CanonicalButton.START,
                KEYCODE_BUTTON_SELECT to CanonicalButton.SELECT,
                KEYCODE_BUTTON_MODE to CanonicalButton.HOME
            )
            val axes = mapOf(
                AXIS_X to CanonicalAxis.LEFT_STICK_X,
                AXIS_Y to CanonicalAxis.LEFT_STICK_Y,
                AXIS_Z to CanonicalAxis.RIGHT_STICK_X,
                AXIS_RZ to CanonicalAxis.RIGHT_STICK_Y,
                AXIS_LTRIGGER to CanonicalAxis.TRIGGER_LEFT,
                AXIS_RTRIGGER to CanonicalAxis.TRIGGER_RIGHT,
                AXIS_BRAKE to CanonicalAxis.TRIGGER_LEFT,
                AXIS_GAS to CanonicalAxis.TRIGGER_RIGHT
            )
            return GamepadProfile(
                profileType = GamepadProfileType.XBOX,
                name = name,
                vendorId = 0x045E,
                buttonMap = buttons,
                axisMap = axes,
                triggerLeftAxis = AXIS_LTRIGGER,
                triggerRightAxis = AXIS_RTRIGGER
            )
        }

        /**
         * Sony PlayStation Profile (DualShock 4, DualSense PS5).
         */
        fun createPlayStationProfile(name: String = "PlayStation Controller"): GamepadProfile {
            val buttons = mapOf(
                KEYCODE_DPAD_UP to CanonicalButton.DPAD_UP,
                KEYCODE_DPAD_DOWN to CanonicalButton.DPAD_DOWN,
                KEYCODE_DPAD_LEFT to CanonicalButton.DPAD_LEFT,
                KEYCODE_DPAD_RIGHT to CanonicalButton.DPAD_RIGHT,
                KEYCODE_BUTTON_A to CanonicalButton.A, // Cross
                KEYCODE_BUTTON_B to CanonicalButton.B, // Circle
                KEYCODE_BUTTON_X to CanonicalButton.X, // Square
                KEYCODE_BUTTON_Y to CanonicalButton.Y, // Triangle
                KEYCODE_BUTTON_L1 to CanonicalButton.L1,
                KEYCODE_BUTTON_R1 to CanonicalButton.R1,
                KEYCODE_BUTTON_L2 to CanonicalButton.L2,
                KEYCODE_BUTTON_R2 to CanonicalButton.R2,
                KEYCODE_BUTTON_THUMBL to CanonicalButton.L3,
                KEYCODE_BUTTON_THUMBR to CanonicalButton.R3,
                KEYCODE_BUTTON_START to CanonicalButton.START,   // Options
                KEYCODE_BUTTON_SELECT to CanonicalButton.SELECT, // Share / Create
                KEYCODE_BUTTON_MODE to CanonicalButton.HOME     // PS Button
            )
            val axes = mapOf(
                AXIS_X to CanonicalAxis.LEFT_STICK_X,
                AXIS_Y to CanonicalAxis.LEFT_STICK_Y,
                AXIS_Z to CanonicalAxis.RIGHT_STICK_X,
                AXIS_RZ to CanonicalAxis.RIGHT_STICK_Y,
                AXIS_LTRIGGER to CanonicalAxis.TRIGGER_LEFT,
                AXIS_RTRIGGER to CanonicalAxis.TRIGGER_RIGHT,
                AXIS_BRAKE to CanonicalAxis.TRIGGER_LEFT,
                AXIS_GAS to CanonicalAxis.TRIGGER_RIGHT
            )
            return GamepadProfile(
                profileType = GamepadProfileType.PLAYSTATION,
                name = name,
                vendorId = 0x054C,
                buttonMap = buttons,
                axisMap = axes,
                triggerLeftAxis = AXIS_LTRIGGER,
                triggerRightAxis = AXIS_RTRIGGER
            )
        }

        /**
         * Nintendo Switch Pro Controller profile.
         * Optionally swaps A/B and X/Y to match Nintendo physical button labels.
         */
        fun createSwitchProProfile(name: String = "Nintendo Switch Pro Controller", matchNintendoLabels: Boolean = true): GamepadProfile {
            val buttons = mapOf(
                KEYCODE_DPAD_UP to CanonicalButton.DPAD_UP,
                KEYCODE_DPAD_DOWN to CanonicalButton.DPAD_DOWN,
                KEYCODE_DPAD_LEFT to CanonicalButton.DPAD_LEFT,
                KEYCODE_DPAD_RIGHT to CanonicalButton.DPAD_RIGHT,
                KEYCODE_BUTTON_A to CanonicalButton.A,
                KEYCODE_BUTTON_B to CanonicalButton.B,
                KEYCODE_BUTTON_X to CanonicalButton.X,
                KEYCODE_BUTTON_Y to CanonicalButton.Y,
                KEYCODE_BUTTON_L1 to CanonicalButton.L1,
                KEYCODE_BUTTON_R1 to CanonicalButton.R1,
                KEYCODE_BUTTON_L2 to CanonicalButton.L2,
                KEYCODE_BUTTON_R2 to CanonicalButton.R2,
                KEYCODE_BUTTON_THUMBL to CanonicalButton.L3,
                KEYCODE_BUTTON_THUMBR to CanonicalButton.R3,
                KEYCODE_BUTTON_START to CanonicalButton.START,
                KEYCODE_BUTTON_SELECT to CanonicalButton.SELECT,
                KEYCODE_BUTTON_MODE to CanonicalButton.HOME
            )
            val axes = mapOf(
                AXIS_X to CanonicalAxis.LEFT_STICK_X,
                AXIS_Y to CanonicalAxis.LEFT_STICK_Y,
                AXIS_Z to CanonicalAxis.RIGHT_STICK_X,
                AXIS_RZ to CanonicalAxis.RIGHT_STICK_Y
            )
            return GamepadProfile(
                profileType = GamepadProfileType.SWITCH_PRO,
                name = name,
                vendorId = 0x057E,
                buttonMap = buttons,
                axisMap = axes,
                swapABForNintendo = matchNintendoLabels,
                swapXYForNintendo = matchNintendoLabels
            )
        }

        /**
         * 8BitDo Controller Profile (SN30 Pro, Pro 2, Ultimate).
         */
        fun createEightBitDoProfile(name: String = "8BitDo Gamepad"): GamepadProfile {
            val buttons = mapOf(
                KEYCODE_DPAD_UP to CanonicalButton.DPAD_UP,
                KEYCODE_DPAD_DOWN to CanonicalButton.DPAD_DOWN,
                KEYCODE_DPAD_LEFT to CanonicalButton.DPAD_LEFT,
                KEYCODE_DPAD_RIGHT to CanonicalButton.DPAD_RIGHT,
                KEYCODE_BUTTON_A to CanonicalButton.A,
                KEYCODE_BUTTON_B to CanonicalButton.B,
                KEYCODE_BUTTON_X to CanonicalButton.X,
                KEYCODE_BUTTON_Y to CanonicalButton.Y,
                KEYCODE_BUTTON_L1 to CanonicalButton.L1,
                KEYCODE_BUTTON_R1 to CanonicalButton.R1,
                KEYCODE_BUTTON_L2 to CanonicalButton.L2,
                KEYCODE_BUTTON_R2 to CanonicalButton.R2,
                KEYCODE_BUTTON_THUMBL to CanonicalButton.L3,
                KEYCODE_BUTTON_THUMBR to CanonicalButton.R3,
                KEYCODE_BUTTON_START to CanonicalButton.START,
                KEYCODE_BUTTON_SELECT to CanonicalButton.SELECT,
                KEYCODE_BUTTON_MODE to CanonicalButton.HOME
            )
            val axes = mapOf(
                AXIS_X to CanonicalAxis.LEFT_STICK_X,
                AXIS_Y to CanonicalAxis.LEFT_STICK_Y,
                AXIS_Z to CanonicalAxis.RIGHT_STICK_X,
                AXIS_RZ to CanonicalAxis.RIGHT_STICK_Y,
                AXIS_LTRIGGER to CanonicalAxis.TRIGGER_LEFT,
                AXIS_RTRIGGER to CanonicalAxis.TRIGGER_RIGHT
            )
            return GamepadProfile(
                profileType = GamepadProfileType.EIGHTBITDO,
                name = name,
                vendorId = 0x2DC8,
                buttonMap = buttons,
                axisMap = axes
            )
        }

        /**
         * Generic Android HID Profile fallback.
         */
        fun createGenericHidProfile(name: String = "Generic Android HID Gamepad"): GamepadProfile {
            val buttons = mapOf(
                KEYCODE_DPAD_UP to CanonicalButton.DPAD_UP,
                KEYCODE_DPAD_DOWN to CanonicalButton.DPAD_DOWN,
                KEYCODE_DPAD_LEFT to CanonicalButton.DPAD_LEFT,
                KEYCODE_DPAD_RIGHT to CanonicalButton.DPAD_RIGHT,
                KEYCODE_BUTTON_A to CanonicalButton.A,
                KEYCODE_BUTTON_B to CanonicalButton.B,
                KEYCODE_BUTTON_X to CanonicalButton.X,
                KEYCODE_BUTTON_Y to CanonicalButton.Y,
                KEYCODE_BUTTON_L1 to CanonicalButton.L1,
                KEYCODE_BUTTON_R1 to CanonicalButton.R1,
                KEYCODE_BUTTON_L2 to CanonicalButton.L2,
                KEYCODE_BUTTON_R2 to CanonicalButton.R2,
                KEYCODE_BUTTON_THUMBL to CanonicalButton.L3,
                KEYCODE_BUTTON_THUMBR to CanonicalButton.R3,
                KEYCODE_BUTTON_START to CanonicalButton.START,
                KEYCODE_BUTTON_SELECT to CanonicalButton.SELECT,
                KEYCODE_BUTTON_MODE to CanonicalButton.HOME
            )
            val axes = mapOf(
                AXIS_X to CanonicalAxis.LEFT_STICK_X,
                AXIS_Y to CanonicalAxis.LEFT_STICK_Y,
                AXIS_Z to CanonicalAxis.RIGHT_STICK_X,
                AXIS_RZ to CanonicalAxis.RIGHT_STICK_Y,
                AXIS_RX to CanonicalAxis.RIGHT_STICK_X,
                AXIS_RY to CanonicalAxis.RIGHT_STICK_Y,
                AXIS_LTRIGGER to CanonicalAxis.TRIGGER_LEFT,
                AXIS_RTRIGGER to CanonicalAxis.TRIGGER_RIGHT,
                AXIS_BRAKE to CanonicalAxis.TRIGGER_LEFT,
                AXIS_GAS to CanonicalAxis.TRIGGER_RIGHT
            )
            return GamepadProfile(
                profileType = GamepadProfileType.ANDROID_GENERIC,
                name = name,
                buttonMap = buttons,
                axisMap = axes
            )
        }
    }
}
