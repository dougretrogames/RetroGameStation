package org.retrogamestation.core.canonical.model

/**
 * Master system and emulation runtime configuration.
 */
data class MasterSystemConfig(
    val fastForwardRatio: Float = 3.0f, // 1.0x to 10.0x speed
    val slowMotionRatio: Float = 0.5f,
    val rewindEnabled: Boolean = true,
    val rewindBufferCapacityMb: Int = 30, // Memory allocated for rewind buffer
    val rewindGranularityFrames: Int = 1,
    val pauseOnMenu: Boolean = true,
    val cheatsEnabled: Boolean = false,
    val saveStateSlot: Int = 0,
    val autoSaveStateOnExit: Boolean = true,
    val autoLoadStateOnStart: Boolean = false,
    val showFps: Boolean = false,
    val showNotifications: Boolean = true
) {
    init {
        require(fastForwardRatio in 1.0f..10.0f) { "Fast forward ratio must be between 1.0 and 10.0" }
        require(saveStateSlot in 0..99) { "Save state slot must be between 0 and 99" }
    }

    companion object {
        fun default(): MasterSystemConfig = MasterSystemConfig()
    }
}
