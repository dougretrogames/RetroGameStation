package org.retrogamestation.core.canonical.injector

import org.retrogamestation.core.canonical.model.AspectRatioMode
import org.retrogamestation.core.canonical.model.CanonicalAxis
import org.retrogamestation.core.canonical.model.CanonicalButton
import org.retrogamestation.core.canonical.model.EmulationConfig
import org.retrogamestation.core.canonical.model.RenderingApi

/**
 * Libretro Joypad & Environment injector.
 * Serializes [EmulationConfig] to retroarch.cfg and retroarch-core-options.cfg,
 * and maintains RETRO_DEVICE_JOYPAD constant tables for native C/JNI bridge polling.
 */
class LibretroConfigInjector : ConfigInjector {

    /**
     * RETRO_DEVICE_JOYPAD IDs from libretro.h
     */
    companion object {
        const val RETRO_DEVICE_ID_JOYPAD_B = 0
        const val RETRO_DEVICE_ID_JOYPAD_Y = 1
        const val RETRO_DEVICE_ID_JOYPAD_SELECT = 2
        const val RETRO_DEVICE_ID_JOYPAD_START = 3
        const val RETRO_DEVICE_ID_JOYPAD_UP = 4
        const val RETRO_DEVICE_ID_JOYPAD_DOWN = 5
        const val RETRO_DEVICE_ID_JOYPAD_LEFT = 6
        const val RETRO_DEVICE_ID_JOYPAD_RIGHT = 7
        const val RETRO_DEVICE_ID_JOYPAD_A = 8
        const val RETRO_DEVICE_ID_JOYPAD_X = 9
        const val RETRO_DEVICE_ID_JOYPAD_L = 10
        const val RETRO_DEVICE_ID_JOYPAD_R = 11
        const val RETRO_DEVICE_ID_JOYPAD_L2 = 12
        const val RETRO_DEVICE_ID_JOYPAD_R2 = 13
        const val RETRO_DEVICE_ID_JOYPAD_L3 = 14
        const val RETRO_DEVICE_ID_JOYPAD_R3 = 15
        const val RETRO_DEVICE_ID_JOYPAD_MASK = 256

        const val RETRO_DEVICE_INDEX_ANALOG_LEFT = 0
        const val RETRO_DEVICE_INDEX_ANALOG_RIGHT = 1
        const val RETRO_DEVICE_ID_ANALOG_X = 0
        const val RETRO_DEVICE_ID_ANALOG_Y = 1

        /**
         * Maps [CanonicalButton] to Libretro joypad button index.
         */
        fun toLibretroJoypadId(button: CanonicalButton): Int {
            return when (button) {
                CanonicalButton.B -> RETRO_DEVICE_ID_JOYPAD_B
                CanonicalButton.Y -> RETRO_DEVICE_ID_JOYPAD_Y
                CanonicalButton.SELECT -> RETRO_DEVICE_ID_JOYPAD_SELECT
                CanonicalButton.START -> RETRO_DEVICE_ID_JOYPAD_START
                CanonicalButton.DPAD_UP -> RETRO_DEVICE_ID_JOYPAD_UP
                CanonicalButton.DPAD_DOWN -> RETRO_DEVICE_ID_JOYPAD_DOWN
                CanonicalButton.DPAD_LEFT -> RETRO_DEVICE_ID_JOYPAD_LEFT
                CanonicalButton.DPAD_RIGHT -> RETRO_DEVICE_ID_JOYPAD_RIGHT
                CanonicalButton.A -> RETRO_DEVICE_ID_JOYPAD_A
                CanonicalButton.X -> RETRO_DEVICE_ID_JOYPAD_X
                CanonicalButton.L1 -> RETRO_DEVICE_ID_JOYPAD_L
                CanonicalButton.R1 -> RETRO_DEVICE_ID_JOYPAD_R
                CanonicalButton.L2 -> RETRO_DEVICE_ID_JOYPAD_L2
                CanonicalButton.R2 -> RETRO_DEVICE_ID_JOYPAD_R2
                CanonicalButton.L3 -> RETRO_DEVICE_ID_JOYPAD_L3
                CanonicalButton.R3 -> RETRO_DEVICE_ID_JOYPAD_R3
                CanonicalButton.HOME -> RETRO_DEVICE_ID_JOYPAD_SELECT // Fallback
                CanonicalButton.CAPTURE -> RETRO_DEVICE_ID_JOYPAD_SELECT // Fallback
            }
        }
    }

    /**
     * Generates a complete retroarch.cfg content string.
     */
    override fun generate(config: EmulationConfig): String {
        val sb = StringBuilder()
        sb.appendLine("# ===========================================")
        sb.appendLine("# RetroGameStation - Libretro Configuration")
        sb.appendLine("# Generated for: ${config.configId} (Platform: ${config.platformId})")
        sb.appendLine("# ===========================================")
        sb.appendLine()

        // 1. Video Configuration
        val video = config.video
        val videoDriver = when (video.renderingApi) {
            RenderingApi.VULKAN -> "vulkan"
            RenderingApi.OPENGL_ES3 -> "glcore"
            RenderingApi.OPENGL_ES2 -> "gl"
            RenderingApi.SOFTWARE -> "software"
        }
        sb.appendLine("video_driver = \"$videoDriver\"")
        sb.appendLine("video_vsync = \"${video.vsync}\"")
        sb.appendLine("video_threaded = \"${video.threadedVideo}\"")
        sb.appendLine("video_max_swapchain_images = \"${video.maxSwapchainImages}\"")
        sb.appendLine("video_frame_delay = \"${video.frameDelay}\"")
        sb.appendLine("video_smooth = \"${video.bilinearFiltering}\"")
        sb.appendLine("video_scale_integer = \"${video.integerScale}\"")
        sb.appendLine("video_fullscreen = \"${video.fullscreen}\"")
        sb.appendLine("video_rotation = \"${video.rotationDegrees / 90}\"")

        // Aspect ratio mapping
        val aspectIndex = when (video.aspectRatio) {
            AspectRatioMode.ORIGINAL_4_3 -> 0
            AspectRatioMode.WIDE_16_9 -> 1
            AspectRatioMode.ONE_TO_ONE -> 7
            AspectRatioMode.CORE_PROVIDED -> 21
            AspectRatioMode.CUSTOM -> 22
            AspectRatioMode.STRETCH -> 21
            AspectRatioMode.INTEGER_SCALE -> 21
        }
        sb.appendLine("aspect_ratio_index = \"$aspectIndex\"")
        if (video.customAspectRatio != null) {
            sb.appendLine("video_aspect_ratio = \"${video.customAspectRatio}\"")
        }

        if (video.shaderPresetPath != null) {
            sb.appendLine("video_shader_enable = \"true\"")
            sb.appendLine("video_shader = \"${video.shaderPresetPath}\"")
        } else {
            sb.appendLine("video_shader_enable = \"false\"")
        }
        sb.appendLine()

        // 2. Audio Configuration
        val audio = config.audio
        sb.appendLine("audio_enable = \"${audio.enabled}\"")
        sb.appendLine("audio_volume = \"${(audio.volume - 100) / 10.0f}\"") // dB gain
        sb.appendLine("audio_latency = \"${audio.latencyMs}\"")
        sb.appendLine("audio_sync = \"${audio.syncAudio}\"")
        sb.appendLine("audio_resampler_quality = \"${config.libretro.audioResamplerQuality}\"")
        sb.appendLine()

        // 3. System & Latency Configuration
        val system = config.system
        val libretro = config.libretro
        sb.appendLine("fastforward_ratio = \"${system.fastForwardRatio}\"")
        sb.appendLine("rewind_enable = \"${system.rewindEnabled}\"")
        sb.appendLine("rewind_buffer_size = \"${system.rewindBufferCapacityMb}\"")
        sb.appendLine("rewind_granularity = \"${system.rewindGranularityFrames}\"")
        sb.appendLine("pause_nonactive = \"${system.pauseOnMenu}\"")
        sb.appendLine("fps_show = \"${system.showFps}\"")
        sb.appendLine("run_ahead_frames = \"${libretro.runaheadFrames}\"")
        sb.appendLine("run_ahead_secondary_instance = \"${libretro.runaheadSecondInstance}\"")
        sb.appendLine("autosave_interval = \"${libretro.autoSaveIntervalSeconds}\"")
        sb.appendLine()

        // 4. Input Configuration (Player 1 Joypad)
        val input = config.input
        sb.appendLine("input_driver = \"android\"")
        sb.appendLine("input_player1_joypad_index = \"${input.playerIndex}\"")

        // Map button codes
        input.buttonMappings.forEach { (button, binding) ->
            val retroKey = getRetroarchKeyName(button)
            if (retroKey != null) {
                sb.appendLine("input_player1_${retroKey}_btn = \"${binding.keyCode}\"")
            }
        }

        // Stick deadzone
        sb.appendLine("input_axis_threshold = \"${input.leftStickDeadzone.inner}\"")
        sb.appendLine("input_analog_deadzone = \"${input.leftStickDeadzone.inner}\"")
        sb.appendLine("input_analog_sensitivity = \"${input.leftStickDeadzone.curve}\"")
        sb.appendLine("vibrate_on_keypress = \"${input.rumbleEnabled}\"")
        sb.appendLine("input_turbo_period = \"${60 / (input.turboRateHz.coerceAtLeast(1))}\"")

        return sb.toString()
    }

    /**
     * Generates the retroarch-core-options.cfg text for core specific options.
     */
    fun generateCoreOptions(config: EmulationConfig): String {
        val sb = StringBuilder()
        sb.appendLine("# RetroGameStation - Libretro Core Options")
        config.libretro.coreOptions.forEach { (key, value) ->
            sb.appendLine("$key = \"$value\"")
        }
        return sb.toString()
    }

    private fun getRetroarchKeyName(button: CanonicalButton): String? {
        return when (button) {
            CanonicalButton.B -> "b"
            CanonicalButton.Y -> "y"
            CanonicalButton.SELECT -> "select"
            CanonicalButton.START -> "start"
            CanonicalButton.DPAD_UP -> "up"
            CanonicalButton.DPAD_DOWN -> "down"
            CanonicalButton.DPAD_LEFT -> "left"
            CanonicalButton.DPAD_RIGHT -> "right"
            CanonicalButton.A -> "a"
            CanonicalButton.X -> "x"
            CanonicalButton.L1 -> "l"
            CanonicalButton.R1 -> "r"
            CanonicalButton.L2 -> "l2"
            CanonicalButton.R2 -> "r2"
            CanonicalButton.L3 -> "l3"
            CanonicalButton.R3 -> "r3"
            CanonicalButton.HOME -> "menu_toggle"
            CanonicalButton.CAPTURE -> "screenshot"
        }
    }
}
