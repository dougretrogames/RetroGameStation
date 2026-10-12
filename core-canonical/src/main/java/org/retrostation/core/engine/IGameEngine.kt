package org.retrostation.core.engine

import android.view.Surface
import org.retrostation.core.input.InputEvent
import org.retrostation.core.model.EmulationCore
import org.retrostation.core.model.RomDescriptor

/**
 * Universal interface for emulation backend engines.
 * Implemented by Libretro, PPSSPP, and Mupen64Plus bridges.
 */
interface IGameEngine {
    val coreType: EmulationCore

    /**
     * Initializes engine subsystems, audio buffer sizes, and native rendering targets.
     */
    fun initialize(audioSampleRate: Int, surface: Surface?): Boolean

    /**
     * Attaches or detaches the native hardware rendering Surface.
     */
    fun setSurface(surface: Surface?): Boolean

    /**
     * Loads a game ROM descriptor into the engine.
     */
    fun loadRom(rom: RomDescriptor): Boolean

    /**
     * Advances emulation by exactly one frame (CPU step + video frame emission).
     */
    fun runFrame()

    /**
     * Pauses emulation execution.
     */
    fun pause()

    /**
     * Resumes paused emulation.
     */
    fun resume()

    /**
     * Sends input event (key down/up or analog axis displacement) to native core.
     */
    fun sendInput(event: InputEvent)

    /**
     * Attaches audio callback for streaming direct PCM audio.
     */
    fun setAudioCallback(callback: AudioFrameCallback)

    /**
     * Serializes core state into a byte array.
     */
    fun saveState(slot: Int): ByteArray?

    /**
     * Deserializes core state from a byte array.
     */
    fun loadState(slot: Int, stateData: ByteArray): Boolean

    /**
     * Destroys engine instance and releases native memory, textures, and audio ring buffers.
     */
    fun destroy()
}
