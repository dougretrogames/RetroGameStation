package org.retrogamestation.core.canonical.model.subconfig

/**
 * Libretro-specific parameters strictly isolated from generic configs.
 * Encapsulates core options dictionary, runahead latency reduction,
 * and Libretro environment variables.
 */
data class LibretroSpecificConfig(
    // Core options key-values (e.g., "mame2003-plus_tate_mode" -> "enabled")
    val coreOptions: Map<String, String> = emptyMap(),
    
    // Latency and performance optimizations
    val runaheadFrames: Int = 0, // 0 = off, 1-5 frames
    val runaheadSecondInstance: Boolean = false,
    val latencyMode: String = "normal", // "normal", "low_latency", "ultra"
    
    // Libretro audio resampler quality
    val audioResamplerQuality: Int = 2, // 0=lowest, 2=normal/sinc, 4=higher
    
    // Save RAM auto-save interval in seconds (0 = disabled/on exit only)
    val autoSaveIntervalSeconds: Int = 10,
    
    // Libretro video threaded mode
    val threadedVideo: Boolean = true
) {
    init {
        require(runaheadFrames in 0..5) { "Runahead frames must be between 0 and 5" }
        require(autoSaveIntervalSeconds >= 0) { "Auto-save interval must be >= 0" }
    }

    /**
     * Merge this config with another, overlaying the given core options.
     */
    fun withCoreOption(key: String, value: String): LibretroSpecificConfig {
        val updated = coreOptions.toMutableMap()
        updated[key] = value
        return copy(coreOptions = updated)
    }

    companion object {
        fun default(): LibretroSpecificConfig = LibretroSpecificConfig()
    }
}
