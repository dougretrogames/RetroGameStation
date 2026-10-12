package org.retrogamestation.core.canonical.model

/**
 * Master canonical video configuration across all supported emulation engines.
 * Normalizes Vulkan/OpenGL backends, aspect ratios, integer scaling, frame pacing,
 * internal rendering resolution multipliers, and shaders.
 */
data class MasterVideoConfig(
    // Graphics backend
    val renderingApi: RenderingApi = RenderingApi.VULKAN,
    
    // Aspect ratio and scaling
    val aspectRatio: AspectRatioMode = AspectRatioMode.CORE_PROVIDED,
    val customAspectRatio: Float? = null, // e.g. 1.3333f for 4:3 or 1.7778f for 16:9
    val integerScale: Boolean = false,
    val fullscreen: Boolean = true,
    val rotationDegrees: Int = 0, // 0, 90, 180, 270
    
    // Internal rendering resolution multiplier: 1 = 1x native, 2 = 2x, 3 = 3x, etc. (up to 10x)
    val internalResolutionScale: Int = 1,
    
    // Frame pacing and synchronization
    val vsync: Boolean = true,
    val framePacing: FramePacingMode = FramePacingMode.VSYNC_STANDARD,
    val threadedVideo: Boolean = true,
    val maxSwapchainImages: Int = 3,
    val frameDelay: Int = 0, // In milliseconds (0-15), reduces input latency at cost of performance
    
    // Filtering and Shaders
    val bilinearFiltering: Boolean = true,
    val textureFiltering: TextureFilteringMode = TextureFilteringMode.BILINEAR,
    val shaderPresetPath: String? = null, // Path to GLSL/Slang shader preset (e.g., crt-geom, scanlines)
    val crtFilterEnabled: Boolean = false
) {
    init {
        require(internalResolutionScale in 1..10) {
            "Internal resolution scale must be between 1x and 10x (got $internalResolutionScale)"
        }
        require(rotationDegrees in listOf(0, 90, 180, 270)) {
            "Rotation must be 0, 90, 180, or 270 degrees (got $rotationDegrees)"
        }
        require(maxSwapchainImages in 2..5) {
            "Max swapchain images must be between 2 and 5 (got $maxSwapchainImages)"
        }
        require(frameDelay in 0..15) {
            "Frame delay must be between 0 and 15 ms (got $frameDelay)"
        }
    }

    /**
     * Resolves the effective aspect ratio as a float (width / height).
     */
    fun resolveAspectRatioValue(): Float {
        return when (aspectRatio) {
            AspectRatioMode.ORIGINAL_4_3 -> 4.0f / 3.0f
            AspectRatioMode.WIDE_16_9 -> 16.0f / 9.0f
            AspectRatioMode.ONE_TO_ONE -> 1.0f
            AspectRatioMode.CUSTOM -> customAspectRatio ?: (4.0f / 3.0f)
            AspectRatioMode.CORE_PROVIDED,
            AspectRatioMode.INTEGER_SCALE,
            AspectRatioMode.STRETCH -> 0.0f // Let core / driver manage native or full-stretch ratio
        }
    }

    companion object {
        fun default(): MasterVideoConfig = MasterVideoConfig()
    }
}
