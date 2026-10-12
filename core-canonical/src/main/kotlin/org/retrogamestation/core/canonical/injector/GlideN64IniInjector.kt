package org.retrogamestation.core.canonical.injector

import org.retrogamestation.core.canonical.model.AspectRatioMode
import org.retrogamestation.core.canonical.model.EmulationConfig

/**
 * GLideN64 graphics plugin INI injector.
 * Generates GLideN64.ini with frame buffer emulation, hi-res textures,
 * aspect ratio, and widescreen hacks.
 */
class GlideN64IniInjector : ConfigInjector {

    override fun generate(config: EmulationConfig): String {
        val mupen = config.mupen
        val video = config.video

        val aspectValue = when (video.aspectRatio) {
            AspectRatioMode.ORIGINAL_4_3 -> 0
            AspectRatioMode.WIDE_16_9 -> 1
            AspectRatioMode.STRETCH -> 2
            AspectRatioMode.CORE_PROVIDED,
            AspectRatioMode.ONE_TO_ONE,
            AspectRatioMode.INTEGER_SCALE,
            AspectRatioMode.CUSTOM -> 3 // Original
        }

        val sb = StringBuilder()
        sb.appendLine("# ===========================================")
        sb.appendLine("# RetroGameStation - GLideN64 Configuration")
        sb.appendLine("# Generated for: ${config.configId}")
        sb.appendLine("# ===========================================")
        sb.appendLine()

        // [General]
        sb.appendLine("[General]")
        sb.appendLine("version = 27")
        sb.appendLine()

        // [Video]
        sb.appendLine("[Video]")
        sb.appendLine("fullscreen = ${if (video.fullscreen) 1 else 0}")
        sb.appendLine("aspect = $aspectValue")
        sb.appendLine("bilinearMode = ${if (video.bilinearFiltering) 1 else 0}")
        sb.appendLine("MaxResolution = 0")
        sb.appendLine()

        // [FrameBufferEmulation]
        sb.appendLine("[FrameBufferEmulation]")
        sb.appendLine("EnableFBEmulation = ${if (mupen.enableFramebufferEmulation) 1 else 0}")
        sb.appendLine("BufferSwapMode = ${mupen.bufferSwapMode}")
        sb.appendLine("CopyDepthToRDRAM = ${mupen.copyDepthToRdram}")
        sb.appendLine("CopyColorToRDRAM = ${mupen.copyColorToRdram}")
        sb.appendLine("N64DepthCompare = ${if (mupen.n64DepthCompare) 1 else 0}")
        sb.appendLine()

        // [Texture]
        sb.appendLine("[Texture]")
        sb.appendLine("EnableHiResTextures = ${if (mupen.enableHiResTextures) 1 else 0}")
        sb.appendLine("txFilterMode = ${mupen.txFilterMode}")
        sb.appendLine("txEnhancementMode = ${mupen.txEnhancementMode}")
        sb.appendLine("txHiresEnable = ${if (mupen.txHiresEnable) 1 else 0}")
        sb.appendLine("txCacheSize = ${mupen.txCacheSizeMb}")
        sb.appendLine()

        // [Widescreen]
        sb.appendLine("[Widescreen]")
        sb.appendLine("EnableWidescreenHack = ${if (mupen.enableWidescreenHack) 1 else 0}")
        sb.appendLine("CropMode = ${mupen.cropMode}")

        return sb.toString()
    }
}
