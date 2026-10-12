package org.retrogamestation.core.canonical.model.subconfig

/**
 * PPSSPP-exclusive configuration parameters strictly isolated from generic configs.
 * Controls hardware transformation, vertex caching, spline/bezier quality,
 * texture scaling (xBRZ/Hybrid), and internal PSP resolution scaling (1x to 10x).
 */
data class PpssppSpecificConfig(
    // Rendering Resolution: 1x (480x272) up to 10x (4800x2720)
    val internalResolution: Int = 2,
    
    // Geometry & Acceleration hacks
    val hardwareTransform: Boolean = true,
    val softwareSkinning: Boolean = true,
    val vertexCache: Boolean = true,
    val splineBezierQuality: Int = 1, // 0 = Low, 1 = Medium, 2 = High
    
    // Texture Upscaling & Filtering
    val textureUpscaleType: Int = 0, // 0 = Off, 1 = xBRZ, 2 = Hybrid, 3 = Bicubic, 4 = Hybrid + Bicubic
    val textureUpscaleLevel: Int = 1, // 1 = Off, 2 = 2x, 3 = 3x, 4 = 4x, 5 = 5x
    val textureDeposterize: Boolean = false,
    val anisotropicFiltering: Int = 0, // 0 = Off, 2 = 2x, 4 = 4x, 8 = 8x, 16 = 16x
    val textureReplacement: Boolean = true, // Load custom textures from PSP/TEXTURES/
    
    // Frame skipping
    val frameSkipping: Int = 0, // 0 = Off, 1, 2, 3...
    val frameSkippingType: Int = 0, // 0 = Number of frames, 1 = Percent of FPS
    val autoFrameSkip: Boolean = false,
    
    // System & Emulation timing
    val fastMemory: Boolean = true,
    val separateSasThread: Boolean = true,
    val ioTimingMethod: Int = 0, // 0 = Fast, 1 = Host, 2 = Simulate UMD delays
    val pspCpuClock: Int = 0 // 0 = Auto / Default (222MHz / 333MHz unlocked)
) {
    init {
        require(internalResolution in 1..10) { "PPSSPP internal resolution must be 1x-10x (got $internalResolution)" }
        require(splineBezierQuality in 0..2) { "Spline/Bezier quality must be 0, 1, or 2" }
        require(textureUpscaleType in 0..4) { "Texture upscale type must be 0 to 4" }
        require(textureUpscaleLevel in 1..5) { "Texture upscale level must be 1 to 5" }
        require(anisotropicFiltering in listOf(0, 2, 4, 8, 16)) { "Anisotropic filtering must be 0, 2, 4, 8, or 16" }
    }

    companion object {
        fun default(): PpssppSpecificConfig = PpssppSpecificConfig()
    }
}
