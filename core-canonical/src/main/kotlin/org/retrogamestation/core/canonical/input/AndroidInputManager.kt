package org.retrogamestation.core.canonical.input

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import org.retrogamestation.core.canonical.model.CanonicalAxis
import org.retrogamestation.core.canonical.model.CanonicalButton
import org.retrogamestation.core.canonical.model.HotkeyAction
import org.retrogamestation.core.canonical.model.MasterInputConfig
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Snapshot of canonical input state for a single player.
 */
data class CanonicalInputState(
    val playerIndex: Int = 0,
    val buttons: Map<CanonicalButton, Boolean> = CanonicalButton.values().associateWith { false },
    val leftStickX: Float = 0.0f,
    val leftStickY: Float = 0.0f,
    val rightStickX: Float = 0.0f,
    val rightStickY: Float = 0.0f,
    val triggerLeft: Float = 0.0f,
    val triggerRight: Float = 0.0f
) {
    fun isButtonPressed(button: CanonicalButton): Boolean = buttons[button] == true
}

/**
 * Listener interface for canonical input events and hotkeys.
 */
interface InputEventListener {
    fun onButtonEvent(playerIndex: Int, button: CanonicalButton, isPressed: Boolean) {}
    fun onAxisEvent(playerIndex: Int, axis: CanonicalAxis, value: Float) {}
    fun onStateUpdated(playerIndex: Int, state: CanonicalInputState) {}
    fun onHotkeyTriggered(action: HotkeyAction) {}
}

/**
 * Android Input Manager.
 * Intercepts [KeyEvent] and [MotionEvent], manages auto-detected gamepad profiles,
 * applies deadzone/curve calibration, detects hotkey chords, and dispatches canonical events.
 */
class AndroidInputManager(
    private val autoDetector: GamepadAutoDetector = GamepadAutoDetector()
) {
    private val listeners = CopyOnWriteArrayList<InputEventListener>()
    
    // Multi-player tracking: deviceId -> playerIndex
    private val deviceToPlayerMap = ConcurrentHashMap<Int, Int>()
    private val playerToProfileMap = ConcurrentHashMap<Int, GamepadProfile>()
    private val playerToConfigMap = ConcurrentHashMap<Int, MasterInputConfig>()
    private val playerStates = ConcurrentHashMap<Int, CanonicalInputState>()
    
    // Hotkey tracking
    private val isModifierHeld = ConcurrentHashMap<Int, Boolean>()
    private val wasHotkeyTriggeredDuringHold = ConcurrentHashMap<Int, Boolean>()

    // Hat switch state tracking (to generate discrete button events from hat motion)
    private val lastHatX = ConcurrentHashMap<Int, Float>()
    private val lastHatY = ConcurrentHashMap<Int, Float>()

    init {
        // Initialize Player 0 default
        val defaultProfile = GamepadProfile.createGenericHidProfile()
        val defaultConfig = MasterInputConfig.createStandardAndroidDefault()
        playerToProfileMap[0] = defaultProfile
        playerToConfigMap[0] = defaultConfig
        playerStates[0] = CanonicalInputState(playerIndex = 0)
    }

    fun addListener(listener: InputEventListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: InputEventListener) {
        listeners.remove(listener)
    }

    fun setMasterConfig(playerIndex: Int, config: MasterInputConfig) {
        playerToConfigMap[playerIndex] = config
    }

    fun getCanonicalState(playerIndex: Int): CanonicalInputState {
        return playerStates[playerIndex] ?: CanonicalInputState(playerIndex = playerIndex)
    }

    /**
     * Registers an Android [InputDevice] to a player index, auto-detecting its profile.
     */
    fun registerDevice(device: InputDevice, preferredPlayerIndex: Int? = null): Int {
        val playerIndex = preferredPlayerIndex ?: allocateNextPlayerIndex()
        deviceToPlayerMap[device.id] = playerIndex
        
        val profile = autoDetector.detectProfile(device)
        playerToProfileMap[playerIndex] = profile
        
        if (!playerToConfigMap.containsKey(playerIndex)) {
            playerToConfigMap[playerIndex] = MasterInputConfig.createStandardAndroidDefault(playerIndex)
        }
        if (!playerStates.containsKey(playerIndex)) {
            playerStates[playerIndex] = CanonicalInputState(playerIndex = playerIndex)
        }
        return playerIndex
    }

    /**
     * Intercepts and processes an Android [KeyEvent].
     * Returns true if the event was consumed.
     */
    fun onKeyEvent(event: KeyEvent): Boolean {
        val playerIndex = resolvePlayerIndex(event.deviceId)
        val profile = playerToProfileMap[playerIndex] ?: GamepadProfile.createGenericHidProfile()
        val config = playerToConfigMap[playerIndex] ?: MasterInputConfig.createStandardAndroidDefault(playerIndex)

        val canonicalButton = profile.mapKeyCode(event.keyCode) ?: return false
        val isDown = event.action == KeyEvent.ACTION_DOWN

        // Hotkey chord processing
        val modifier = config.hotkeyEnableButton
        if (modifier != null && canonicalButton == modifier) {
            if (isDown) {
                isModifierHeld[playerIndex] = true
                wasHotkeyTriggeredDuringHold[playerIndex] = false
            } else {
                isModifierHeld[playerIndex] = false
                val hotkeyUsed = wasHotkeyTriggeredDuringHold[playerIndex] == true
                if (hotkeyUsed) {
                    // Suppress normal button release if it was used as a hotkey modifier
                    return true
                }
            }
        }

        // Check if modifier is held and this button triggers a hotkey
        if (isDown && isModifierHeld[playerIndex] == true && canonicalButton != modifier) {
            val matchingHotkey = config.hotkeyMappings.entries.firstOrNull {
                it.value.triggerButton == canonicalButton && it.value.modifierButton == modifier
            }
            if (matchingHotkey != null) {
                wasHotkeyTriggeredDuringHold[playerIndex] = true
                notifyHotkey(matchingHotkey.key)
                return true
            }
        }

        // Direct hotkey (without modifier)
        if (isDown && (modifier == null || isModifierHeld[playerIndex] != true)) {
            val directHotkey = config.hotkeyMappings.entries.firstOrNull {
                it.value.triggerButton == canonicalButton && it.value.modifierButton == null
            }
            if (directHotkey != null) {
                notifyHotkey(directHotkey.key)
                return true
            }
        }

        // Normal game button update
        updateButtonState(playerIndex, canonicalButton, isDown)
        return true
    }

    /**
     * Intercepts and processes an Android [MotionEvent] for analog sticks, triggers, and hats.
     * Returns true if the event was consumed.
     */
    fun onGenericMotionEvent(event: MotionEvent): Boolean {
        val playerIndex = resolvePlayerIndex(event.deviceId)
        val profile = playerToProfileMap[playerIndex] ?: GamepadProfile.createGenericHidProfile()
        val config = playerToConfigMap[playerIndex] ?: MasterInputConfig.createStandardAndroidDefault(playerIndex)

        // 1. Left Stick (Radial calibration)
        val rawLeftX = event.getAxisValue(MotionEvent.AXIS_X)
        val rawLeftY = event.getAxisValue(MotionEvent.AXIS_Y)
        val (calLeftX, calLeftY) = AxisCalibration.calibrate2DRadial(
            rawLeftX,
            rawLeftY,
            config.leftStickDeadzone,
            config.invertLeftStickX,
            config.invertLeftStickY
        )

        // 2. Right Stick (Radial calibration)
        val rawRightX = event.getAxisValue(profile.axisMap.entries.firstOrNull { it.value == CanonicalAxis.RIGHT_STICK_X }?.key ?: MotionEvent.AXIS_Z)
        val rawRightY = event.getAxisValue(profile.axisMap.entries.firstOrNull { it.value == CanonicalAxis.RIGHT_STICK_Y }?.key ?: MotionEvent.AXIS_RZ)
        val (calRightX, calRightY) = AxisCalibration.calibrate2DRadial(
            rawRightX,
            rawRightY,
            config.rightStickDeadzone,
            config.invertRightStickX,
            config.invertRightStickY
        )

        // 3. Triggers
        val rawTrigL = event.getAxisValue(profile.triggerLeftAxis)
        val rawTrigR = event.getAxisValue(profile.triggerRightAxis)
        val calTrigL = AxisCalibration.calibrateTrigger(rawTrigL, config.leftTriggerDeadzone)
        val calTrigR = AxisCalibration.calibrateTrigger(rawTrigR, config.rightTriggerDeadzone)

        // Fire digital trigger buttons if threshold crossed
        val isTrigLDown = calTrigL >= 0.5f
        val isTrigRDown = calTrigR >= 0.5f
        updateButtonState(playerIndex, CanonicalButton.L2, isTrigLDown)
        updateButtonState(playerIndex, CanonicalButton.R2, isTrigRDown)

        // 4. D-Pad Hat Switch (AXIS_HAT_X / AXIS_HAT_Y)
        val hatX = event.getAxisValue(profile.hatXAxis)
        val hatY = event.getAxisValue(profile.hatYAxis)
        processHatAxis(playerIndex, hatX, hatY)

        // Update full state
        val currentState = playerStates[playerIndex] ?: CanonicalInputState(playerIndex = playerIndex)
        val updatedState = currentState.copy(
            leftStickX = calLeftX,
            leftStickY = calLeftY,
            rightStickX = calRightX,
            rightStickY = calRightY,
            triggerLeft = calTrigL,
            triggerRight = calTrigR
        )
        playerStates[playerIndex] = updatedState

        // Notify axis events
        notifyAxis(playerIndex, CanonicalAxis.LEFT_STICK_X, calLeftX)
        notifyAxis(playerIndex, CanonicalAxis.LEFT_STICK_Y, calLeftY)
        notifyAxis(playerIndex, CanonicalAxis.RIGHT_STICK_X, calRightX)
        notifyAxis(playerIndex, CanonicalAxis.RIGHT_STICK_Y, calRightY)
        notifyAxis(playerIndex, CanonicalAxis.TRIGGER_LEFT, calTrigL)
        notifyAxis(playerIndex, CanonicalAxis.TRIGGER_RIGHT, calTrigR)
        notifyState(playerIndex, updatedState)

        return true
    }

    private fun processHatAxis(playerIndex: Int, hatX: Float, hatY: Float) {
        val prevX = lastHatX[playerIndex] ?: 0.0f
        val prevY = lastHatY[playerIndex] ?: 0.0f

        if (hatX != prevX) {
            lastHatX[playerIndex] = hatX
            val leftPressed = hatX <= -0.5f
            val rightPressed = hatX >= 0.5f
            updateButtonState(playerIndex, CanonicalButton.DPAD_LEFT, leftPressed)
            updateButtonState(playerIndex, CanonicalButton.DPAD_RIGHT, rightPressed)
        }

        if (hatY != prevY) {
            lastHatY[playerIndex] = hatY
            val upPressed = hatY <= -0.5f
            val downPressed = hatY >= 0.5f
            updateButtonState(playerIndex, CanonicalButton.DPAD_UP, upPressed)
            updateButtonState(playerIndex, CanonicalButton.DPAD_DOWN, downPressed)
        }
    }

    private fun updateButtonState(playerIndex: Int, button: CanonicalButton, isPressed: Boolean) {
        val currentState = playerStates[playerIndex] ?: CanonicalInputState(playerIndex = playerIndex)
        val wasPressed = currentState.buttons[button] == true
        if (wasPressed == isPressed) return

        val updatedButtons = currentState.buttons.toMutableMap()
        updatedButtons[button] = isPressed
        val updatedState = currentState.copy(buttons = updatedButtons)
        playerStates[playerIndex] = updatedState

        for (l in listeners) {
            l.onButtonEvent(playerIndex, button, isPressed)
            l.onStateUpdated(playerIndex, updatedState)
        }
    }

    private fun notifyAxis(playerIndex: Int, axis: CanonicalAxis, value: Float) {
        for (l in listeners) {
            l.onAxisEvent(playerIndex, axis, value)
        }
    }

    private fun notifyState(playerIndex: Int, state: CanonicalInputState) {
        for (l in listeners) {
            l.onStateUpdated(playerIndex, state)
        }
    }

    private fun notifyHotkey(action: HotkeyAction) {
        for (l in listeners) {
            l.onHotkeyTriggered(action)
        }
    }

    private fun resolvePlayerIndex(deviceId: Int): Int {
        return deviceToPlayerMap[deviceId] ?: 0
    }

    private fun allocateNextPlayerIndex(): Int {
        var idx = 0
        while (deviceToPlayerMap.values.contains(idx)) {
            idx++
        }
        return idx
    }
}
