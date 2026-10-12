package org.retrogamestation.core.canonical.input

import android.view.InputDevice
import android.view.InputDevice.MotionRange

/**
 * Descriptor containing hardware identifiers and capabilities of an input device.
 * Decouples detection logic from the Android runtime for testing and portability.
 */
data class DeviceInfo(
    val vendorId: Int,
    val productId: Int,
    val descriptor: String,
    val name: String,
    val sources: Int,
    val supportedAxes: Set<Int> = emptySet(),
    val supportedKeys: Set<Int> = emptySet()
)

/**
 * Android Gamepad Auto-Detector.
 * Inspects Android [InputDevice] properties (vendorId, productId, descriptor, axis ranges,
 * key capabilities, and name heuristics) to identify controller hardware and synthesize
 * the optimal [GamepadProfile].
 */
class GamepadAutoDetector {

    /**
     * Inspects a live Android [InputDevice] and resolves its [GamepadProfile].
     */
    fun detectProfile(device: InputDevice): GamepadProfile {
        val axes = mutableSetOf<Int>()
        for (range in device.motionRanges) {
            axes.add(range.axis)
        }

        val keysToCheck = intArrayOf(
            GamepadProfile.KEYCODE_BUTTON_A,
            GamepadProfile.KEYCODE_BUTTON_B,
            GamepadProfile.KEYCODE_BUTTON_X,
            GamepadProfile.KEYCODE_BUTTON_Y,
            GamepadProfile.KEYCODE_BUTTON_L1,
            GamepadProfile.KEYCODE_BUTTON_R1,
            GamepadProfile.KEYCODE_BUTTON_L2,
            GamepadProfile.KEYCODE_BUTTON_R2,
            GamepadProfile.KEYCODE_BUTTON_THUMBL,
            GamepadProfile.KEYCODE_BUTTON_THUMBR,
            GamepadProfile.KEYCODE_BUTTON_START,
            GamepadProfile.KEYCODE_BUTTON_SELECT,
            GamepadProfile.KEYCODE_BUTTON_MODE,
            GamepadProfile.KEYCODE_DPAD_UP,
            GamepadProfile.KEYCODE_DPAD_DOWN,
            GamepadProfile.KEYCODE_DPAD_LEFT,
            GamepadProfile.KEYCODE_DPAD_RIGHT
        )

        val supportedKeys = mutableSetOf<Int>()
        val hasKeys = device.hasKeys(*keysToCheck)
        for (i in keysToCheck.indices) {
            if (hasKeys[i]) {
                supportedKeys.add(keysToCheck[i])
            }
        }

        val info = DeviceInfo(
            vendorId = device.vendorId,
            productId = device.productId,
            descriptor = device.descriptor ?: "",
            name = device.name ?: "Unknown Controller",
            sources = device.sources,
            supportedAxes = axes,
            supportedKeys = supportedKeys
        )

        return detectProfile(info)
    }

    /**
     * Core detection algorithm operating on [DeviceInfo].
     */
    fun detectProfile(info: DeviceInfo): GamepadProfile {
        val nameLower = info.name.lowercase()
        val vid = info.vendorId
        val pid = info.productId

        // 1. Sony PlayStation
        if (vid == VENDOR_SONY || isPlayStationName(nameLower)) {
            val profile = GamepadProfile.createPlayStationProfile(info.name)
            return refineAxisMappings(profile, info)
        }

        // 2. Nintendo Switch (Pro Controller, Joy-Con)
        if (vid == VENDOR_NINTENDO || isNintendoName(nameLower)) {
            val profile = GamepadProfile.createSwitchProProfile(info.name)
            return refineAxisMappings(profile, info)
        }

        // 3. 8BitDo
        if (vid == VENDOR_8BITDO || is8BitDoName(nameLower)) {
            val profile = GamepadProfile.createEightBitDoProfile(info.name)
            return refineAxisMappings(profile, info)
        }

        // 4. Microsoft Xbox (Xbox 360, Xbox One, Series X/S, Bluetooth)
        if (vid == VENDOR_MICROSOFT || isXboxName(nameLower)) {
            val profile = GamepadProfile.createXboxProfile(info.name)
            return refineAxisMappings(profile, info)
        }

        // 5. Generic Android HID gamepad
        val genericProfile = GamepadProfile.createGenericHidProfile(info.name)
        return refineAxisMappings(genericProfile, info)
    }

    /**
     * Inspects supported axis ranges on the device and adjusts the profile
     * to prevent dead sticks or misaligned trigger axes.
     */
    private fun refineAxisMappings(profile: GamepadProfile, info: DeviceInfo): GamepadProfile {
        val updatedAxes = profile.axisMap.toMutableMap()
        var trigL = profile.triggerLeftAxis
        var trigR = profile.triggerRightAxis

        // Right stick axis fallback: if AXIS_Z/AXIS_RZ are absent but AXIS_RX/AXIS_RY are present
        if (!info.supportedAxes.contains(GamepadProfile.AXIS_Z) && info.supportedAxes.contains(GamepadProfile.AXIS_RX)) {
            updatedAxes[GamepadProfile.AXIS_RX] = org.retrogamestation.core.canonical.model.CanonicalAxis.RIGHT_STICK_X
        }
        if (!info.supportedAxes.contains(GamepadProfile.AXIS_RZ) && info.supportedAxes.contains(GamepadProfile.AXIS_RY)) {
            updatedAxes[GamepadProfile.AXIS_RY] = org.retrogamestation.core.canonical.model.CanonicalAxis.RIGHT_STICK_Y
        }

        // Trigger axis fallback: AXIS_BRAKE / AXIS_GAS vs AXIS_LTRIGGER / AXIS_RTRIGGER
        if (info.supportedAxes.contains(GamepadProfile.AXIS_BRAKE) && !info.supportedAxes.contains(GamepadProfile.AXIS_LTRIGGER)) {
            trigL = GamepadProfile.AXIS_BRAKE
            updatedAxes[GamepadProfile.AXIS_BRAKE] = org.retrogamestation.core.canonical.model.CanonicalAxis.TRIGGER_LEFT
        }
        if (info.supportedAxes.contains(GamepadProfile.AXIS_GAS) && !info.supportedAxes.contains(GamepadProfile.AXIS_RTRIGGER)) {
            trigR = GamepadProfile.AXIS_GAS
            updatedAxes[GamepadProfile.AXIS_GAS] = org.retrogamestation.core.canonical.model.CanonicalAxis.TRIGGER_RIGHT
        }

        return profile.copy(
            axisMap = updatedAxes,
            triggerLeftAxis = trigL,
            triggerRightAxis = trigR
        )
    }

    private fun isPlayStationName(name: String): Boolean {
        return name.contains("playstation") ||
                name.contains("dualshock") ||
                name.contains("dualsense") ||
                name.contains("wireless controller") && (name.contains("sony") || name.contains("ps"))
    }

    private fun isNintendoName(name: String): Boolean {
        return name.contains("nintendo") ||
                name.contains("pro controller") ||
                name.contains("joy-con") ||
                name.contains("switch")
    }

    private fun is8BitDoName(name: String): Boolean {
        return name.contains("8bitdo") ||
                name.contains("sn30") ||
                name.contains("pro 2") ||
                name.contains("m30") ||
                name.contains("zero 2") ||
                name.contains("ultimate")
    }

    private fun isXboxName(name: String): Boolean {
        return name.contains("xbox") ||
                name.contains("x-box") ||
                name.contains("microsoft") ||
                name.contains("wireless controller")
    }

    companion object {
        const val VENDOR_MICROSOFT = 0x045E
        const val VENDOR_SONY = 0x054C
        const val VENDOR_NINTENDO = 0x057E
        const val VENDOR_8BITDO = 0x2DC8
        const val VENDOR_GOOGLE = 0x18D1
        const val VENDOR_RAZER = 0x1532
        const val VENDOR_STEELSERIES = 0x1038
    }
}
