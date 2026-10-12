package org.retrogamestation.core.canonical.model

/**
 * Representation of a physical key or button binding on an Android input device.
 */
data class PhysicalKeyBinding(
    val keyCode: Int,
    val description: String = "",
    val isAxisButton: Boolean = false,
    val sourceAxis: Int? = null,
    val axisDirection: Float = 0.0f // +1.0f or -1.0f for hat or stick directions mapped as buttons
)

/**
 * Representation of an analog axis binding on an Android input device.
 */
data class PhysicalAxisBinding(
    val axis: Int,
    val isInverted: Boolean = false,
    val deadzoneInner: Float = 0.12f,
    val deadzoneOuter: Float = 0.95f,
    val scale: Float = 1.0f
)

/**
 * Deadzone and sensitivity parameters for dual analog sticks.
 */
data class StickDeadzone(
    val inner: Float = 0.12f,
    val outer: Float = 0.95f,
    val curve: Float = 1.0f // 1.0 = linear, >1.0 = exponential/finer control near center
) {
    init {
        require(inner in 0.0f..0.9f) { "Inner deadzone must be in [0.0, 0.9]" }
        require(outer in (inner + 0.05f)..1.0f) { "Outer deadzone must be greater than inner" }
        require(curve in 0.5f..3.0f) { "Sensitivity curve must be in [0.5, 3.0]" }
    }
}

/**
 * Hotkey combination binding.
 * Can be triggered by a chord: hold [modifierButton] and press [triggerButton],
 * or directly with [triggerButton] if modifierButton is null.
 */
data class HotkeyBinding(
    val triggerButton: CanonicalButton,
    val modifierButton: CanonicalButton? = CanonicalButton.SELECT,
    val description: String = ""
)

/**
 * Master canonical physical controller configuration.
 * Contains all button and axis bindings, deadzones, rumble, turbo, and hotkey pipelines.
 */
data class MasterInputConfig(
    val profileName: String = "Default Gamepad",
    val playerIndex: Int = 0,
    val buttonMappings: Map<CanonicalButton, PhysicalKeyBinding> = emptyMap(),
    val axisMappings: Map<CanonicalAxis, PhysicalAxisBinding> = emptyMap(),
    val hotkeyMappings: Map<HotkeyAction, HotkeyBinding> = emptyMap(),
    
    // Deadzone and calibration
    val leftStickDeadzone: StickDeadzone = StickDeadzone(inner = 0.12f, outer = 0.95f, curve = 1.0f),
    val rightStickDeadzone: StickDeadzone = StickDeadzone(inner = 0.12f, outer = 0.95f, curve = 1.0f),
    val leftTriggerDeadzone: Float = 0.05f,
    val rightTriggerDeadzone: Float = 0.05f,
    
    // Invert analog axes
    val invertLeftStickX: Boolean = false,
    val invertLeftStickY: Boolean = false,
    val invertRightStickX: Boolean = false,
    val invertRightStickY: Boolean = false,
    
    // Vibration / Haptics
    val rumbleEnabled: Boolean = true,
    val rumbleIntensity: Float = 1.0f,
    
    // Turbo settings
    val turboEnabled: Boolean = false,
    val turboRateHz: Int = 15,
    val turboButtons: Set<CanonicalButton> = emptySet(),
    
    // Hotkey chord modifier (default: SELECT button)
    val hotkeyEnableButton: CanonicalButton? = CanonicalButton.SELECT
) {
    fun findButtonForKeyCode(keyCode: Int): CanonicalButton? {
        return buttonMappings.entries.firstOrNull { it.value.keyCode == keyCode }?.key
    }

    fun findAxisForMotionEventAxis(axis: Int): CanonicalAxis? {
        return axisMappings.entries.firstOrNull { it.value.axis == axis }?.key
    }

    companion object {
        /**
         * Standard default Android HID controller mapping (Standard Android Gamepad spec).
         * Uses Android KEYCODE constants (96=A, 97=B, 99=X, 100=Y, etc.) and MotionEvent axes.
         */
        fun createStandardAndroidDefault(playerIndex: Int = 0): MasterInputConfig {
            val buttons = mapOf(
                CanonicalButton.DPAD_UP to PhysicalKeyBinding(19, "DPAD_UP"),
                CanonicalButton.DPAD_DOWN to PhysicalKeyBinding(20, "DPAD_DOWN"),
                CanonicalButton.DPAD_LEFT to PhysicalKeyBinding(21, "DPAD_LEFT"),
                CanonicalButton.DPAD_RIGHT to PhysicalKeyBinding(22, "DPAD_RIGHT"),
                
                CanonicalButton.A to PhysicalKeyBinding(96, "BUTTON_A"),
                CanonicalButton.B to PhysicalKeyBinding(97, "BUTTON_B"),
                CanonicalButton.X to PhysicalKeyBinding(99, "BUTTON_X"),
                CanonicalButton.Y to PhysicalKeyBinding(100, "BUTTON_Y"),
                
                CanonicalButton.L1 to PhysicalKeyBinding(102, "BUTTON_L1"),
                CanonicalButton.R1 to PhysicalKeyBinding(103, "BUTTON_R1"),
                CanonicalButton.L2 to PhysicalKeyBinding(104, "BUTTON_L2"),
                CanonicalButton.R2 to PhysicalKeyBinding(105, "BUTTON_R2"),
                
                CanonicalButton.L3 to PhysicalKeyBinding(106, "BUTTON_THUMBL"),
                CanonicalButton.R3 to PhysicalKeyBinding(107, "BUTTON_THUMBR"),
                
                CanonicalButton.START to PhysicalKeyBinding(108, "BUTTON_START"),
                CanonicalButton.SELECT to PhysicalKeyBinding(109, "BUTTON_SELECT"),
                CanonicalButton.HOME to PhysicalKeyBinding(110, "BUTTON_MODE")
            )

            val axes = mapOf(
                // MotionEvent.AXIS_X = 0, AXIS_Y = 1, AXIS_Z = 11, AXIS_RZ = 14, AXIS_LTRIGGER = 17, AXIS_RTRIGGER = 18
                CanonicalAxis.LEFT_STICK_X to PhysicalAxisBinding(0, isInverted = false),
                CanonicalAxis.LEFT_STICK_Y to PhysicalAxisBinding(1, isInverted = false),
                CanonicalAxis.RIGHT_STICK_X to PhysicalAxisBinding(11, isInverted = false),
                CanonicalAxis.RIGHT_STICK_Y to PhysicalAxisBinding(14, isInverted = false),
                CanonicalAxis.TRIGGER_LEFT to PhysicalAxisBinding(17, isInverted = false),
                CanonicalAxis.TRIGGER_RIGHT to PhysicalAxisBinding(18, isInverted = false)
            )

            val hotkeys = mapOf(
                HotkeyAction.EXIT_EMULATOR to HotkeyBinding(CanonicalButton.START, CanonicalButton.SELECT, "Select + Start: Exit"),
                HotkeyAction.QUICK_MENU to HotkeyBinding(CanonicalButton.X, CanonicalButton.SELECT, "Select + X: Quick Menu"),
                HotkeyAction.SAVE_STATE to HotkeyBinding(CanonicalButton.R1, CanonicalButton.SELECT, "Select + R1: Save State"),
                HotkeyAction.LOAD_STATE to HotkeyBinding(CanonicalButton.L1, CanonicalButton.SELECT, "Select + L1: Load State"),
                HotkeyAction.FAST_FORWARD to HotkeyBinding(CanonicalButton.R2, CanonicalButton.SELECT, "Select + R2: Fast Forward"),
                HotkeyAction.REWIND to HotkeyBinding(CanonicalButton.L2, CanonicalButton.SELECT, "Select + L2: Rewind"),
                HotkeyAction.PAUSE to HotkeyBinding(CanonicalButton.A, CanonicalButton.SELECT, "Select + A: Pause / Resume"),
                HotkeyAction.SCREENSHOT to HotkeyBinding(CanonicalButton.Y, CanonicalButton.SELECT, "Select + Y: Screenshot")
            )

            return MasterInputConfig(
                profileName = "Standard Android Gamepad",
                playerIndex = playerIndex,
                buttonMappings = buttons,
                axisMappings = axes,
                hotkeyMappings = hotkeys,
                leftStickDeadzone = StickDeadzone(0.12f, 0.95f, 1.0f),
                rightStickDeadzone = StickDeadzone(0.12f, 0.95f, 1.0f),
                rumbleEnabled = true,
                rumbleIntensity = 1.0f
            )
        }
    }
}
