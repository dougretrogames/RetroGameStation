package com.retro.gamestation.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.retro.gamestation.core.NativeEngineBridge

/**
 * Production Low-Latency AAudio Engine Manager for Retro Game Station.
 * Directly interfaces with the hardware burst size, manages audio focus,
 * dynamically handles fast-forward audio mute, and prevents audio underruns (xruns).
 */
class LowLatencyAudioEngine(private val context: Context) {

    companion object {
        private const val TAG = "LowLatencyAudioEngine"
        private const val DEFAULT_SAMPLE_RATE = 48000
        private const val DEFAULT_FRAMES_PER_BURST = 192
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var nativeAudioHandle: Long = 0
    private var isPlaying = false
    private var isMuted = false
    private var muteOnFastForward = true
    private var audioFocusRequest: AudioFocusRequest? = null

    var exclusiveMode: Boolean = true
    var bufferBurstMultiplier: Int = 2 // 2 bursts = minimal latency, 3 = safe
    var masterVolumeGain: Float = 1.0f

    /**
     * Initializes the AAudio backend using Android hardware-native optimal parameters.
     */
    fun initialize(channels: Int = 2): Boolean {
        if (nativeAudioHandle != 0L) {
            Log.w(TAG, "Audio engine already initialized")
            return true
        }

        // Query device hardware optimal audio configuration
        val hardwareSampleRate = audioManager?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull()
            ?: DEFAULT_SAMPLE_RATE
        val hardwareFramesPerBurst = audioManager?.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull()
            ?: DEFAULT_FRAMES_PER_BURST

        Log.i(TAG, "Initializing AAudio: hardwareSampleRate=$hardwareSampleRate, burst=$hardwareFramesPerBurst, exclusive=$exclusiveMode")

        nativeAudioHandle = NativeEngineBridge.nativeAudioInit(
            sampleRate = hardwareSampleRate,
            channels = channels,
            framesPerBurst = hardwareFramesPerBurst,
            bufferBursts = bufferBurstMultiplier,
            exclusive = exclusiveMode
        )

        if (nativeAudioHandle == 0L) {
            Log.e(TAG, "Failed to initialize native AAudio stream!")
            return false
        }

        Log.i(TAG, "AAudio stream initialized successfully (handle: $nativeAudioHandle)")
        return true
    }

    /**
     * Requests system Audio Focus and starts playback.
     */
    fun start(): Boolean {
        if (nativeAudioHandle == 0L) return false
        if (isPlaying) return true

        if (!requestAudioFocus()) {
            Log.w(TAG, "Failed to acquire audio focus, starting stream anyway...")
        }

        val success = NativeEngineBridge.nativeAudioStart(nativeAudioHandle)
        if (success) {
            isPlaying = true
            Log.i(TAG, "AAudio playback started")
        } else {
            Log.e(TAG, "Failed to start native AAudio stream")
        }
        return success
    }

    /**
     * Pauses the audio stream and releases transient audio focus.
     */
    fun pause(): Boolean {
        if (nativeAudioHandle == 0L || !isPlaying) return true

        val success = NativeEngineBridge.nativeAudioPause(nativeAudioHandle)
        if (success) {
            isPlaying = false
            Log.i(TAG, "AAudio playback paused")
        }
        return success
    }

    /**
     * Flushes the audio ring buffer (clears pending samples upon state restore or reset).
     */
    fun flush() {
        if (nativeAudioHandle != 0L) {
            NativeEngineBridge.nativeAudioFlush(nativeAudioHandle)
        }
    }

    /**
     * Tears down the AAudio stream and releases all resources.
     */
    fun destroy() {
        abandonAudioFocus()
        if (nativeAudioHandle != 0L) {
            NativeEngineBridge.nativeAudioDestroy(nativeAudioHandle)
            nativeAudioHandle = 0L
            isPlaying = false
            Log.i(TAG, "AAudio stream destroyed")
        }
    }

    /**
     * Sets fast-forward audio state.
     * When fast-forwarding, audio can be muted to eliminate screeching/audio distortion.
     */
    fun setFastForward(active: Boolean) {
        if (nativeAudioHandle != 0L) {
            NativeEngineBridge.nativeAudioSetFastForward(nativeAudioHandle, active, muteOnFastForward)
        }
    }

    fun setMuteOnFastForward(mute: Boolean) {
        this.muteOnFastForward = mute
    }

    fun setMute(muted: Boolean) {
        this.isMuted = muted
        if (nativeAudioHandle != 0L) {
            NativeEngineBridge.nativeAudioSetMute(nativeAudioHandle, muted)
        }
    }

    fun setVolume(gain: Float) {
        this.masterVolumeGain = gain.coerceIn(0.0f, 2.0f)
        if (nativeAudioHandle != 0L) {
            NativeEngineBridge.nativeAudioSetVolumeGain(nativeAudioHandle, this.masterVolumeGain)
        }
    }

    fun getEstimatedLatencyMillis(): Double {
        return if (nativeAudioHandle != 0L) {
            NativeEngineBridge.nativeAudioGetLatencyMs(nativeAudioHandle)
        } else 0.0
    }

    fun getUnderrunCount(): Int {
        return if (nativeAudioHandle != 0L) {
            NativeEngineBridge.nativeAudioGetXRuns(nativeAudioHandle)
        } else 0
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.w(TAG, "Audio focus permanently lost")
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.i(TAG, "Audio focus transiently lost (phone call / notification)")
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.i(TAG, "Audio focus ducking requested")
                setVolume(0.2f)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.i(TAG, "Audio focus gained/restored")
                setVolume(masterVolumeGain)
                if (!isPlaying) {
                    start()
                }
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        if (audioManager == null) return false

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()

            audioFocusRequest = request
            audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (audioManager == null) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
    }
}
