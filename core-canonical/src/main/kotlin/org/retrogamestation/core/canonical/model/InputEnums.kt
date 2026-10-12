package org.retrogamestation.core.canonical.model

/**
 * Canonical physical controller button definitions.
 * Normalizes all vendor layouts (Xbox, PlayStation, Nintendo, Android HID)
 * into a single unified physical button representation.
 */
enum class CanonicalButton(val id: Int, val label: String) {
    DPAD_UP(0, "D-Pad Up"),
    DPAD_DOWN(1, "D-Pad Down"),
    DPAD_LEFT(2, "D-Pad Left"),
    DPAD_RIGHT(3, "D-Pad Right"),
    
    // Face buttons: Standard Xbox/Android layout (A=South, B=East, X=West, Y=North)
    A(4, "Button A / Cross / South"),
    B(5, "Button B / Circle / East"),
    X(6, "Button X / Square / West"),
    Y(7, "Button Y / Triangle / North"),
    
    // Bumpers / Shoulders
    L1(8, "Left Bumper (L1)"),
    R1(9, "Right Bumper (R1)"),
    
    // Triggers (Digital trigger button fallback or digital click)
    L2(10, "Left Trigger (L2)"),
    R2(11, "Right Trigger (R2)"),
    
    // Thumbstick clicks
    L3(12, "Left Stick Click (L3)"),
    R3(13, "Right Stick Click (R3)"),
    
    // System buttons
    START(14, "Start / Options / +"),
    SELECT(15, "Select / Share / Back / -"),
    HOME(16, "Home / Guide / PS"),
    CAPTURE(17, "Capture / Screenshot");

    companion object {
        fun fromId(id: Int): CanonicalButton? = values().firstOrNull { it.id == id }
    }
}

/**
 * Canonical analog stick and analog trigger axes.
 */
enum class CanonicalAxis(val id: Int, val label: String) {
    LEFT_STICK_X(0, "Left Stick X"),
    LEFT_STICK_Y(1, "Left Stick Y"),
    RIGHT_STICK_X(2, "Right Stick X"),
    RIGHT_STICK_Y(3, "Right Stick Y"),
    TRIGGER_LEFT(4, "Left Trigger (L2) Axis"),
    TRIGGER_RIGHT(5, "Right Trigger (R2) Axis");

    companion object {
        fun fromId(id: Int): CanonicalAxis? = values().firstOrNull { it.id == id }
    }
}

/**
 * Global hotkey actions accessible during emulation.
 */
enum class HotkeyAction(val id: String, val label: String) {
    FAST_FORWARD("fast_forward", "Fast Forward Toggle/Hold"),
    REWIND("rewind", "Rewind Active Frame"),
    SAVE_STATE("save_state", "Save State to Current Slot"),
    LOAD_STATE("load_state", "Load State from Current Slot"),
    PAUSE("pause", "Pause / Resume"),
    QUICK_MENU("quick_menu", "Open Emulator Quick Menu"),
    SCREENSHOT("screenshot", "Take Screenshot"),
    EXIT_EMULATOR("exit_emulator", "Exit Emulator cleanly"),
    NEXT_SAVE_SLOT("next_save_slot", "Next Save State Slot"),
    PREV_SAVE_SLOT("prev_save_slot", "Previous Save State Slot"),
    RESET("reset", "Soft Reset Game"),
    TOGGLE_FPS("toggle_fps", "Toggle FPS / Performance HUD"),
    MUTE("mute", "Mute / Unmute Audio");

    companion object {
        fun fromId(id: String): HotkeyAction? = values().firstOrNull { it.id.equals(id, ignoreCase = true) }
    }
}

/**
 * Unified rendering API configuration.
 */
enum class RenderingApi(val value: String) {
    VULKAN("vulkan"),
    OPENGL_ES3("gles3"),
    OPENGL_ES2("gles2"),
    SOFTWARE("software");

    companion object {
        fun fromValue(value: String): RenderingApi =
            values().firstOrNull { it.value.equals(value, ignoreCase = true) } ?: VULKAN
    }
}

/**
 * Display aspect ratio modes.
 */
enum class AspectRatioMode(val value: String) {
    ORIGINAL_4_3("4:3"),
    WIDE_16_9("16:9"),
    CORE_PROVIDED("core_provided"),
    ONE_TO_ONE("1:1"),
    INTEGER_SCALE("integer_scale"),
    STRETCH("stretch"),
    CUSTOM("custom");

    companion object {
        fun fromValue(value: String): AspectRatioMode =
            values().firstOrNull { it.value.equals(value, ignoreCase = true) } ?: CORE_PROVIDED
    }
}

/**
 * Frame pacing and synchronization strategies.
 */
enum class FramePacingMode(val value: String) {
    OFF("off"),
    VSYNC_STANDARD("vsync_standard"),
    LOW_LATENCY("low_latency"),
    THREADED_SYNC("threaded_sync");

    companion object {
        fun fromValue(value: String): FramePacingMode =
            values().firstOrNull { it.value.equals(value, ignoreCase = true) } ?: VSYNC_STANDARD
    }
}

/**
 * Texture filtering modes.
 */
enum class TextureFilteringMode(val value: String) {
    NEAREST("nearest"),
    BILINEAR("bilinear"),
    BICUBIC("bicubic"),
    AUTO("auto");

    companion object {
        fun fromValue(value: String): TextureFilteringMode =
            values().firstOrNull { it.value.equals(value, ignoreCase = true) } ?: BILINEAR
    }
}
