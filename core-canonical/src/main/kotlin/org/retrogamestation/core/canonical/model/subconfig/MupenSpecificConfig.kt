package org.retrogamestation.core.canonical.model.subconfig

/**
 * Mupen64Plus and GLideN64 exclusive configuration parameters strictly isolated from generic configs.
 * Controls N64 Framebuffer emulation, RDRAM depth/color copies, VI refresh rate,
 * overclocking, expansion pak, and texture enhancements.
 */
data class MupenSpecificConfig(
    // Framebuffer Emulation (GLideN64)
    val enableFramebufferEmulation: Boolean = true,
    val bufferSwapMode: Int = 0, // 0 = Old, 1 = New, 2 = Hybrid
    val copyDepthToRdram: Int = 2, // 0 = Never, 1 = Sync, 2 = Async
    val copyColorToRdram: Int = 0, // 0 = Never, 1 = Sync, 2 = Async
    val n64DepthCompare: Boolean = true,
    
    // Texture enhancements
    val enableHiResTextures: Boolean = true,
    val txFilterMode: Int = 0, // 0 = None, 1 = Smooth, 2 = Sharp
    val txEnhancementMode: Int = 0, // 0 = None, 1 = xBRZ, 2 = Scale2x, 3 = HQ4x
    val txHiresEnable: Boolean = true,
    val txCacheSizeMb: Int = 128,
    
    // Hacks and widescreen
    val enableWidescreenHack: Boolean = false,
    val cropMode: Int = 0, // 0 = Disable, 1 = Auto, 2 = Manual
    
    // Core parameters
    val overclockFactor: Int = 1, // 1 = Default 62.5MHz, 2 = 2x overclock (reduces in-game lag)
    val expansionPak: Boolean = true, // 8MB RDRAM expansion
    val viRefreshRate: Int = 1500, // Vertical Interrupt refresh rate (1500 = default)
    val counterFactor: Int = 2, // Emulation timing factor (1 or 2)
    val rspPlugin: String = "mupen64plus-rsp-hle",
    val videoPlugin: String = "mupen64plus-video-GLideN64"
) {
    init {
        require(copyDepthToRdram in 0..2) { "copyDepthToRdram must be 0, 1, or 2" }
        require(copyColorToRdram in 0..2) { "copyColorToRdram must be 0, 1, or 2" }
        require(overclockFactor in 1..4) { "overclockFactor must be between 1 and 4" }
        require(counterFactor in 1..4) { "counterFactor must be between 1 and 4" }
    }

    companion object {
        fun default(): MupenSpecificConfig = MupenSpecificConfig()
    }
}
