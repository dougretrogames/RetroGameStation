/**
 * Retro Game Station - Native C++ / JNI Interface Definitions
 */

#pragma once

#include <jni.h>
#include <cstdint>

#ifdef __cplusplus
extern "C" {
#endif

// Hotkey Actions matching Kotlin GlobalHotkeyManager
enum RetroHotkeyAction {
    HOTKEY_ACTION_QUICK_MENU = 1,
    HOTKEY_ACTION_SAVE_STATE = 2,
    HOTKEY_ACTION_LOAD_STATE = 3,
    HOTKEY_ACTION_FAST_FORWARD_TOGGLE = 4,
    HOTKEY_ACTION_REWIND_TOGGLE = 5,
    HOTKEY_ACTION_QUIT_TO_LAUNCHER = 6,
    HOTKEY_ACTION_SLOT_INCREMENT = 7,
    HOTKEY_ACTION_SLOT_DECREMENT = 8,
    HOTKEY_ACTION_SHADER_PREV = 9,
    HOTKEY_ACTION_SHADER_NEXT = 10,
    HOTKEY_ACTION_PAUSE_TOGGLE = 11,
    HOTKEY_ACTION_RESET = 12,
    HOTKEY_ACTION_SCREENSHOT = 13
};

#ifdef __cplusplus
}
#endif
