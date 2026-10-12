package org.retrogamestation.core.canonical.model

/**
 * Master audio configuration.
 */
data class MasterAudioConfig(
    val enabled: Boolean = true,
    val volume: Int = 100, // 0 to 100
    val latencyMs: Int = 64, // Target audio latency in milliseconds
    val syncAudio: Boolean = true,
    val audioBackend: String = "oboe", // "oboe", "opensles", "audiotrack"
    val sampleRate: Int = 44100
) {
    init {
        require(volume in 0..100) { "Volume must be between 0 and 100 (got $volume)" }
        require(latencyMs in 16..256) { "Latency must be between 16ms and 256ms (got $latencyMs)" }
    }

    companion object {
        fun default(): MasterAudioConfig = MasterAudioConfig()
    }
}
