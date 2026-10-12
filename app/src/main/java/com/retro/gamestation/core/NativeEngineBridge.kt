package com.retro.gamestation.core

import android.util.Log
import android.view.Surface

/**
 * JNI Bridge to Retro Game Station C++ native layer.
 * Coordinates ANativeWindow lifecycle, AAudio low-latency streaming,
 * direct file descriptor passing, and hotkey actions across Libretro, PPSSPP, and Mupen64Plus.
 */
object NativeEngineBridge {
    private const val TAG = "NativeEngineBridge"

    init {
        try {
            System.loadLibrary("retro_native")
            Log.i(TAG, "Native library 'retro_native' loaded successfully.")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "CRITICAL: Failed to load 'retro_native' library: ${e.message}", e)
        }
    }

    // Hotkey Action IDs (synchronized with retro_native_bridge.h)
    const val ACTION_QUICK_MENU = 1
    const val ACTION_SAVE_STATE = 2
    const val ACTION_LOAD_STATE = 3
    const val ACTION_FAST_FORWARD_TOGGLE = 4
    const val ACTION_REWIND_TOGGLE = 5
    const val ACTION_QUIT_TO_LAUNCHER = 6
    const val ACTION_SLOT_INCREMENT = 7
    const val ACTION_SLOT_DECREMENT = 8
    const val ACTION_SHADER_PREV = 9
    const val ACTION_SHADER_NEXT = 10
    const val ACTION_PAUSE_TOGGLE = 11
    const val ACTION_RESET = 12
    const val ACTION_SCREENSHOT = 13

    // Surface & ANativeWindow JNI bindings
    external fun nativeSurfaceCreated(surface: Surface): Long
    external fun nativeSurfaceChanged(bridgePtr: Long, format: Int, width: Int, height: Int)
    external fun nativeSurfaceDestroyed(bridgePtr: Long)

    // AAudio JNI bindings
    external fun nativeAudioInit(
        sampleRate: Int,
        channels: Int,
        framesPerBurst: Int,
        bufferBursts: Int,
        exclusive: Boolean
    ): Long

    external fun nativeAudioStart(audioPtr: Long): Boolean
    external fun nativeAudioPause(audioPtr: Long): Boolean
    external fun nativeAudioFlush(audioPtr: Long): Boolean
    external fun nativeAudioDestroy(audioPtr: Long)
    external fun nativeAudioWriteSamples(audioPtr: Long, samples: FloatArray, frames: Int): Int
    external fun nativeAudioSetMute(audioPtr: Long, mute: Boolean)
    external fun nativeAudioSetVolumeGain(audioPtr: Long, gain: Float)
    external fun nativeAudioSetFastForward(audioPtr: Long, active: Boolean, muteOnFastForward: Boolean)
    external fun nativeAudioGetLatencyMs(audioPtr: Long): Double
    external fun nativeAudioGetXRuns(audioPtr: Long): Int

    // Direct ROM / SAF File Descriptor access
    external fun nativeOpenDirectRomFd(fd: Int, path: String): Boolean

    // Hotkey dispatching
    external fun nativeTriggerHotkeyAction(actionId: Int, slot: Int)
}
