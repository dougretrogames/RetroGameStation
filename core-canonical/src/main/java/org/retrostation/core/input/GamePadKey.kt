package org.retrostation.core.input

/**
 * Standard unified gamepad keys mapped to canonical inputs across Libretro, PPSSPP, and Mupen64Plus.
 */
enum class GamePadKey(val code: Int) {
    BUTTON_A(0),
    BUTTON_B(1),
    BUTTON_X(2),
    BUTTON_Y(3),
    DPAD_UP(4),
    DPAD_DOWN(5),
    DPAD_LEFT(6),
    DPAD_RIGHT(7),
    SHOULDER_L1(8),
    SHOULDER_R1(9),
    TRIGGER_L2(10),
    TRIGGER_R2(11),
    THUMB_L3(12),
    THUMB_R3(13),
    START(14),
    SELECT(15),
    AXIS_LEFT_X(16),
    AXIS_LEFT_Y(17),
    AXIS_RIGHT_X(18),
    AXIS_RIGHT_Y(19)
}
