package com.retro.gamestation.input

import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.retro.gamestation.core.NativeEngineBridge
import org.retrostation.core.input.GamePadKey
import org.retrostation.core.input.InputEvent
import org.retrogamestation.core.canonical.input.AxisCalibration
import org.retrogamestation.core.canonical.model.StickDeadzone

/**
 * Standardized Global Hotkey Interceptor for Retro Game Station.
 * Intercepts physical controller combos at the Activity input dispatch level
 * before they reach native emulator loops (Libretro, PPSSPP, Mupen64Plus),
 * guaranteeing 100% identical hotkey behavior across all systems.
 *
 * Combos:
 *  - Hotkey + X (Button North)       -> In-Game Quick Menu
 *  - Hotkey + R1                     -> Save State (Active Slot)
 *  - Hotkey + L1                     -> Load State (Active Slot)
 *  - Hotkey + R2 (Trigger)           -> Fast Forward Toggle
 *  - Hotkey + L2 (Trigger)           -> Rewind Toggle
 *  - Hotkey + Start                  -> Quit to Launcher
 *  - Hotkey + D-Pad Up               -> Slot Increment (0-9)
 *  - Hotkey + D-Pad Down             -> Slot Decrement (0-9)
 *  - Hotkey + D-Pad Left             -> Previous Shader
 *  - Hotkey + D-Pad Right            -> Next Shader
 *  - Hotkey + Y                      -> Pause / Resume
 *  - Hotkey + B                      -> Reset Emulation
 *  - Hotkey + R3                     -> Take Screenshot
 */
class GlobalHotkeyManager {

    companion object {
        private const val TAG = "GlobalHotkeyManager"
        private const val TRIGGER_THRESHOLD = 0.65f
        const val MAX_SLOTS = 10
    }

    interface HotkeyListener {
        fun onQuickMenuTriggered()
        fun onSaveStateTriggered(slot: Int)
        fun onLoadStateTriggered(slot: Int)
        fun onFastForwardToggled(enabled: Boolean)
        fun onRewindToggled(enabled: Boolean)
        fun onQuitToLauncherTriggered()
        fun onSlotChanged(newSlot: Int)
        fun onShaderCycle(forward: Boolean)
        fun onPauseToggled(isPaused: Boolean)
        fun onResetTriggered()
        fun onScreenshotTriggered()
    }

    var listener: HotkeyListener? = null

    // Configuration
    var hotkeyModifierKeyCode: Int = KeyEvent.KEYCODE_BUTTON_SELECT
    var consumeStandaloneModifier: Boolean = false // If false, tap-and-release passes through to game
    var isFastForwardEnabled: Boolean = false
        private set
    var isRewindEnabled: Boolean = false
        private set
    var isEmulationPaused: Boolean = false
        private set
    var currentSlot: Int = 0
        private set

    // State tracking
    private var isModifierHeld = false
    private var comboTriggeredDuringHold = false
    private var modifierDownTime: Long = 0L

    // Trigger state tracking for analog axes (L2 / R2)
    private var wasL2TriggerPressed = false
    private var wasR2TriggerPressed = false

    /**
     * Intercepts key events before the game window or native engine processes them.
     * @return true if event was consumed as part of a hotkey combination.
     */
    fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val action = event.action

        // 1. Check if the event is the hotkey modifier itself (e.g., SELECT or GUIDE)
        if (keyCode == hotkeyModifierKeyCode) {
            return handleModifierKeyEvent(event)
        }

        // 2. If the modifier is held down, intercept and match hotkey combinations
        if (isModifierHeld) {
            if (action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                val handled = processComboKeyDown(keyCode)
                if (handled) {
                    comboTriggeredDuringHold = true
                    return true // Consume so the game never sees the key!
                }
            } else if (action == KeyEvent.ACTION_UP) {
                // If a combo was triggered, consume the key release as well
                if (comboTriggeredDuringHold || isSupportedComboKey(keyCode)) {
                    return true
                }
            }
        }

        return false // Allow pass-through to game engine
    }

    /**
     * Intercepts analog trigger motion events for L2/R2 combos.
     * @return true if event was consumed as a hotkey action.
     */
    fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (!isModifierHeld) return false

        // Check if event source is a gamepad or joystick
        val isGamepad = (event.source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                        (event.source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK

        if (!isGamepad) return false

        val r2Val = maxOf(
            event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
            event.getAxisValue(MotionEvent.AXIS_GAS)
        )
        val l2Val = maxOf(
            event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
            event.getAxisValue(MotionEvent.AXIS_BRAKE)
        )

        var consumed = false

        // R2 -> Fast Forward Toggle
        if (r2Val >= TRIGGER_THRESHOLD && !wasR2TriggerPressed) {
            wasR2TriggerPressed = true
            comboTriggeredDuringHold = true
            toggleFastForward()
            consumed = true
        } else if (r2Val < TRIGGER_THRESHOLD && wasR2TriggerPressed) {
            wasR2TriggerPressed = false
            consumed = true
        }

        // L2 -> Rewind Toggle
        if (l2Val >= TRIGGER_THRESHOLD && !wasL2TriggerPressed) {
            wasL2TriggerPressed = true
            comboTriggeredDuringHold = true
            toggleRewind()
            consumed = true
        } else if (l2Val < TRIGGER_THRESHOLD && wasL2TriggerPressed) {
            wasL2TriggerPressed = false
            consumed = true
        }

        return consumed
    }

    private fun handleModifierKeyEvent(event: KeyEvent): Boolean {
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) {
                    isModifierHeld = true
                    comboTriggeredDuringHold = false
                    modifierDownTime = SystemClock.uptimeMillis()
                    Log.d(TAG, "Hotkey modifier DOWN")
                }
                return true // Always consume modifier DOWN to wait for combo
            }
            KeyEvent.ACTION_UP -> {
                val held = isModifierHeld
                val triggered = comboTriggeredDuringHold
                isModifierHeld = false
                comboTriggeredDuringHold = false
                Log.d(TAG, "Hotkey modifier UP (comboTriggered=$triggered)")

                if (triggered) {
                    // Combo was executed, consume release
                    return true
                }

                // If no combo was pressed during the hold, and pass-through is desired
                if (!consumeStandaloneModifier && held) {
                    // Pass clean press & release to the game engine for standalone Select button
                    return false
                }
                return true
            }
        }
        return false
    }

    private fun processComboKeyDown(keyCode: Int): Boolean {
        when (keyCode) {
            // Hotkey + X (Button North / X on Xbox/PS) -> Quick Menu
            KeyEvent.KEYCODE_BUTTON_X -> {
                Log.i(TAG, "Hotkey Combo: Quick Menu triggered")
                NativeEngineBridge.nativeTriggerHotkeyAction(NativeEngineBridge.ACTION_QUICK_MENU, currentSlot)
                listener?.onQuickMenuTriggered()
                return true
            }

            // Hotkey + R1 -> Save State
            KeyEvent.KEYCODE_BUTTON_R1 -> {
                Log.i(TAG, "Hotkey Combo: Save State triggered on slot $currentSlot")
                NativeEngineBridge.nativeTriggerHotkeyAction(NativeEngineBridge.ACTION_SAVE_STATE, currentSlot)
                listener?.onSaveStateTriggered(currentSlot)
                return true
            }

            // Hotkey + L1 -> Load State
            KeyEvent.KEYCODE_BUTTON_L1 -> {
                Log.i(TAG, "Hotkey Combo: Load State triggered from slot $currentSlot")
                NativeEngineBridge.nativeTriggerHotkeyAction(NativeEngineBridge.ACTION_LOAD_STATE, currentSlot)
                listener?.onLoadStateTriggered(currentSlot)
                return true
            }

            // Hotkey + R2 -> Fast Forward Toggle (Button event)
            KeyEvent.KEYCODE_BUTTON_R2 -> {
                toggleFastForward()
                return true
            }

            // Hotkey + L2 -> Rewind Toggle (Button event)
            KeyEvent.KEYCODE_BUTTON_L2 -> {
                toggleRewind()
                return true
            }

            // Hotkey + Start -> Quit to Launcher
            KeyEvent.KEYCODE_BUTTON_START -> {
                Log.i(TAG, "Hotkey Combo: Quit to Launcher triggered")
                NativeEngineBridge.nativeTriggerHotkeyAction(NativeEngineBridge.ACTION_QUIT_TO_LAUNCHER, currentSlot)
                listener?.onQuitToLauncherTriggered()
                return true
            }

            // Hotkey + D-Pad Up -> Slot Increment
            KeyEvent.KEYCODE_DPAD_UP -> {
                currentSlot = (currentSlot + 1) % MAX_SLOTS
                Log.i(TAG, "Hotkey Combo: Slot incremented to $currentSlot")
                NativeEngineBridge.nativeTriggerHotkeyAction(NativeEngineBridge.ACTION_SLOT_INCREMENT, currentSlot)
                listener?.onSlotChanged(currentSlot)
                return true
            }

            // Hotkey + D-Pad Down -> Slot Decrement
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                currentSlot = if (currentSlot - 1 < 0) MAX_SLOTS - 1 else currentSlot - 1
                Log.i(TAG, "Hotkey Combo: Slot decremented to $currentSlot")
                NativeEngineBridge.nativeTriggerHotkeyAction(NativeEngineBridge.ACTION_SLOT_DECREMENT, currentSlot)
                listener?.onSlotChanged(currentSlot)
                return true
            }

            // Hotkey + D-Pad Left -> Previous Shader
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                Log.i(TAG, "Hotkey Combo: Previous Shader")
                NativeEngineBridge.nativeTriggerHotkeyAction(NativeEngineBridge.ACTION_SHADER_PREV, 0)
                listener?.onShaderCycle(forward = false)
                return true
            }

            // Hotkey + D-Pad Right -> Next Shader
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                Log.i(TAG, "Hotkey Combo: Next Shader")
                NativeEngineBridge.nativeTriggerHotkeyAction(NativeEngineBridge.ACTION_SHADER_NEXT, 0)
                listener?.onShaderCycle(forward = true)
                return true
            }

            // Hotkey + Y -> Pause / Resume
            KeyEvent.KEYCODE_BUTTON_Y -> {
                isEmulationPaused = !isEmulationPaused
                Log.i(TAG, "Hotkey Combo: Pause toggled -> $isEmulationPaused")
                NativeEngineBridge.nativeTriggerHotkeyAction(NativeEngineBridge.ACTION_PAUSE_TOGGLE, 0)
                listener?.onPauseToggled(isEmulationPaused)
                return true
            }

            // Hotkey + B -> Reset Emulation
            KeyEvent.KEYCODE_BUTTON_B -> {
                Log.i(TAG, "Hotkey Combo: Reset Emulation")
                NativeEngineBridge.nativeTriggerHotkeyAction(NativeEngineBridge.ACTION_RESET, 0)
                listener?.onResetTriggered()
                return true
            }

            // Hotkey + R3 -> Take Screenshot
            KeyEvent.KEYCODE_BUTTON_THUMBR -> {
                Log.i(TAG, "Hotkey Combo: Screenshot")
                NativeEngineBridge.nativeTriggerHotkeyAction(NativeEngineBridge.ACTION_SCREENSHOT, 0)
                listener?.onScreenshotTriggered()
                return true
            }
        }

        return false
    }

    private fun toggleFastForward() {
        isFastForwardEnabled = !isFastForwardEnabled
        Log.i(TAG, "Hotkey Combo: Fast Forward -> $isFastForwardEnabled")
        NativeEngineBridge.nativeTriggerHotkeyAction(
            NativeEngineBridge.ACTION_FAST_FORWARD_TOGGLE,
            if (isFastForwardEnabled) 1 else 0
        )
        listener?.onFastForwardToggled(isFastForwardEnabled)
    }

    private fun toggleRewind() {
        isRewindEnabled = !isRewindEnabled
        Log.i(TAG, "Hotkey Combo: Rewind -> $isRewindEnabled")
        NativeEngineBridge.nativeTriggerHotkeyAction(
            NativeEngineBridge.ACTION_REWIND_TOGGLE,
            if (isRewindEnabled) 1 else 0
        )
        listener?.onRewindToggled(isRewindEnabled)
    }

    fun selectSlot(slot: Int) {
        if (slot in 0 until MAX_SLOTS) {
            currentSlot = slot
            listener?.onSlotChanged(currentSlot)
        }
    }

    private fun isSupportedComboKey(keyCode: Int): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_X,
            KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BUTTON_R1,
            KeyEvent.KEYCODE_BUTTON_L1,
            KeyEvent.KEYCODE_BUTTON_R2,
            KeyEvent.KEYCODE_BUTTON_L2,
            KeyEvent.KEYCODE_BUTTON_START,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_BUTTON_THUMBR -> true
            else -> false
        }
    }

    /**
     * Converts a regular non-hotkey Android KeyEvent into canonical InputEvent
     * for direct dispatching to Libretro, PPSSPP, or Mupen64Plus.
     */
    fun mapKeyEventToInputEvent(event: KeyEvent, port: Int = 0): InputEvent? {
        val key = when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> GamePadKey.BUTTON_A
            KeyEvent.KEYCODE_BUTTON_B -> GamePadKey.BUTTON_B
            KeyEvent.KEYCODE_BUTTON_X -> GamePadKey.BUTTON_X
            KeyEvent.KEYCODE_BUTTON_Y -> GamePadKey.BUTTON_Y
            KeyEvent.KEYCODE_DPAD_UP -> GamePadKey.DPAD_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> GamePadKey.DPAD_DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> GamePadKey.DPAD_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> GamePadKey.DPAD_RIGHT
            KeyEvent.KEYCODE_BUTTON_L1 -> GamePadKey.SHOULDER_L1
            KeyEvent.KEYCODE_BUTTON_R1 -> GamePadKey.SHOULDER_R1
            KeyEvent.KEYCODE_BUTTON_L2 -> GamePadKey.TRIGGER_L2
            KeyEvent.KEYCODE_BUTTON_R2 -> GamePadKey.TRIGGER_R2
            KeyEvent.KEYCODE_BUTTON_THUMBL -> GamePadKey.THUMB_L3
            KeyEvent.KEYCODE_BUTTON_THUMBR -> GamePadKey.THUMB_R3
            KeyEvent.KEYCODE_BUTTON_START -> GamePadKey.START
            KeyEvent.KEYCODE_BUTTON_SELECT -> GamePadKey.SELECT
            else -> return null
        }

        return if (event.action == KeyEvent.ACTION_DOWN) {
            InputEvent.buttonDown(key, port)
        } else {
            InputEvent.buttonUp(key, port)
        }
    }

    /**
     * Converts non-hotkey analog MotionEvents into canonical InputEvents with radial deadzone calibration.
     */
    fun mapMotionEventToInputEvents(
        event: MotionEvent,
        port: Int = 0,
        stickDeadzone: StickDeadzone = StickDeadzone(inner = 0.12f, outer = 0.95f, curve = 1.0f)
    ): List<InputEvent> {
        val events = mutableListOf<InputEvent>()

        val rawLx = event.getAxisValue(MotionEvent.AXIS_X)
        val rawLy = event.getAxisValue(MotionEvent.AXIS_Y)
        val (calLx, calLy) = AxisCalibration.calibrate2DRadial(rawLx, rawLy, stickDeadzone)

        val rawRx = event.getAxisValue(MotionEvent.AXIS_Z)
        val rawRy = event.getAxisValue(MotionEvent.AXIS_RZ)
        val (calRx, calRy) = AxisCalibration.calibrate2DRadial(rawRx, rawRy, stickDeadzone)

        events.add(InputEvent.axisMove(GamePadKey.AXIS_LEFT_X, calLx, port))
        events.add(InputEvent.axisMove(GamePadKey.AXIS_LEFT_Y, calLy, port))
        events.add(InputEvent.axisMove(GamePadKey.AXIS_RIGHT_X, calRx, port))
        events.add(InputEvent.axisMove(GamePadKey.AXIS_RIGHT_Y, calRy, port))

        val rawL2 = maxOf(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE))
        val rawR2 = maxOf(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS))
        val calL2 = AxisCalibration.calibrateTrigger(rawL2)
        val calR2 = AxisCalibration.calibrateTrigger(rawR2)

        events.add(InputEvent.axisMove(GamePadKey.TRIGGER_L2, calL2, port))
        events.add(InputEvent.axisMove(GamePadKey.TRIGGER_R2, calR2, port))

        return events
    }
}
