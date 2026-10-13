package org.retrostation.engine.ppsspp

import android.view.Surface
import androidx.annotation.Keep
import org.retrostation.core.engine.AudioFrameCallback
import org.retrostation.core.engine.IGameEngine
import org.retrostation.core.input.InputEvent
import org.retrostation.core.model.EmulationCore
import org.retrostation.core.model.RomDescriptor
import java.nio.ByteBuffer

@Keep
class PPSSPPEngineBridge : IGameEngine {

    override val coreType: EmulationCore = EmulationCore.PPSSPP

    private var audioCallback: AudioFrameCallback? = null
    private var isInitialized = false

    companion object {
        init {
            try {
                System.loadLibrary("retrostation_ppsspp")
            } catch (e: UnsatisfiedLinkError) {
                android.util.Log.e("PPSSPPEngineBridge", "Failed to load retrostation_ppsspp: ${e.message}")
            }
        }
    }

    override fun initialize(audioSampleRate: Int, surface: Surface?): Boolean {
        val success = nativeInit(audioSampleRate)
        if (success && surface != null) {
            nativeSetSurface(surface)
        }
        isInitialized = success
        return success
    }

    override fun setSurface(surface: Surface?): Boolean {
        return nativeSetSurface(surface)
    }

    override fun loadRom(rom: RomDescriptor): Boolean {
        return nativeLoadRom(rom.filePath)
    }

    override fun runFrame() {
        if (isInitialized) {
            nativeRunFrame()
        }
    }

    override fun pause() {
        if (isInitialized) {
            nativePause()
        }
    }

    override fun resume() {
        if (isInitialized) {
            nativeResume()
        }
    }

    override fun sendInput(event: InputEvent) {
        if (isInitialized) {
            nativeSendInput(event.port, event.key.code, event.isDown, event.axisValue)
        }
    }

    override fun setAudioCallback(callback: AudioFrameCallback) {
        this.audioCallback = callback
    }

    override fun saveState(slot: Int): ByteArray? {
        return if (isInitialized) nativeSaveState(slot) else null
    }

    override fun loadState(slot: Int, stateData: ByteArray): Boolean {
        return if (isInitialized) nativeLoadState(slot, stateData) else false
    }

    override fun destroy() {
        if (isInitialized) {
            nativeDestroy()
            isInitialized = false
        }
    }

    /**
     * JNI audio buffer callback.
     */
    @Keep
    fun onNativeAudioSamples(buffer: ByteBuffer, frames: Int) {
        audioCallback?.onAudioSamples(buffer, frames)
    }

    // Native JNI Declarations
    private external fun nativeInit(sampleRate: Int): Boolean
    private external fun nativeSetSurface(surface: Surface?): Boolean
    private external fun nativeLoadRom(romPath: String): Boolean
    private external fun nativeRunFrame()
    private external fun nativePause()
    private external fun nativeResume()
    private external fun nativeSendInput(port: Int, key: Int, isDown: Boolean, axisValue: Float)
    private external fun nativeSaveState(slot: Int): ByteArray?
    private external fun nativeLoadState(slot: Int, data: ByteArray): Boolean
    private external fun nativeDestroy()
}
