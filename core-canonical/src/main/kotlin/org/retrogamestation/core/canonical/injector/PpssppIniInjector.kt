package org.retrogamestation.core.canonical.injector

import org.retrogamestation.core.canonical.model.EmulationConfig
import org.retrogamestation.core.canonical.model.RenderingApi

/**
 * PPSSPP INI configuration injector.
 * Generates ppsspp.ini with exact PPSSPP section syntax: [Graphics], [System], [Audio], [Control].
 */
class PpssppIniInjector : ConfigInjector {

    override fun generate(config: EmulationConfig): String {
        val video = config.video
        val ppsspp = config.ppsspp
        val audio = config.audio
        val system = config.system

        val backendId = when (video.renderingApi) {
            RenderingApi.VULKAN -> 3 // PPSSPP GPU_BACKEND_VULKAN = 3
            RenderingApi.OPENGL_ES3,
            RenderingApi.OPENGL_ES2 -> 0 // PPSSPP GPU_BACKEND_OPENGL = 0
            RenderingApi.SOFTWARE -> 4 // PPSSPP GPU_BACKEND_DIRECT3D11 / software fallback
        }

        // Internal resolution: prefer core-specific if explicitly set, else MasterVideoConfig
        val resolution = if (ppsspp.internalResolution != 2) {
            ppsspp.internalResolution
        } else {
            video.internalResolutionScale
        }

        val sb = StringBuilder()
        sb.appendLine("# ===========================================")
        sb.appendLine("# RetroGameStation - PPSSPP Configuration")
        sb.appendLine("# Generated for: ${config.configId}")
        sb.appendLine("# ===========================================")
        sb.appendLine()

        // [General]
        sb.appendLine("[General]")
        sb.appendLine("FirstRun = False")
        sb.appendLine("AutoLoadSaveState = ${if (system.autoLoadStateOnStart) 1 else 0}")
        sb.appendLine("EnableCheats = ${if (system.cheatsEnabled) "True" else "False"}")
        sb.appendLine("CurrentStateSlot = ${system.saveStateSlot}")
        sb.appendLine("ShowFPSCounter = ${if (system.showFps) 1 else 0}")
        sb.appendLine()

        // [Graphics]
        sb.appendLine("[Graphics]")
        sb.appendLine("RenderingBackend = $backendId")
        sb.appendLine("InternalResolution = $resolution")
        sb.appendLine("FullScreen = ${if (video.fullscreen) "True" else "False"}")
        sb.appendLine("VSync = ${if (video.vsync) "True" else "False"}")
        sb.appendLine("DisplayAspectRatio = 1.000000")
        sb.appendLine("BufferGraphics = True")
        sb.appendLine("HardwareTransform = ${if (ppsspp.hardwareTransform) "True" else "False"}")
        sb.appendLine("SoftwareSkinning = ${if (ppsspp.softwareSkinning) "True" else "False"}")
        sb.appendLine("VertexCache = ${if (ppsspp.vertexCache) "True" else "False"}")
        sb.appendLine("SplineBezierQuality = ${ppsspp.splineBezierQuality}")
        sb.appendLine("TexFiltering = ${if (video.bilinearFiltering) 2 else 1}") // 1=Off, 2=Linear
        sb.appendLine("TexDeposterize = ${if (ppsspp.textureDeposterize) "True" else "False"}")
        sb.appendLine("TexScalingType = ${ppsspp.textureUpscaleType}")
        sb.appendLine("TexScalingLevel = ${ppsspp.textureUpscaleLevel}")
        sb.appendLine("AnisotropyLevel = ${ppsspp.anisotropicFiltering}")
        sb.appendLine("TextureReplacement = ${if (ppsspp.textureReplacement) "True" else "False"}")
        sb.appendLine("FrameSkipping = ${ppsspp.frameSkipping}")
        sb.appendLine("FrameSkippingType = ${ppsspp.frameSkippingType}")
        sb.appendLine("AutoFrameSkip = ${if (ppsspp.autoFrameSkip) "True" else "False"}")
        sb.appendLine("TextureBackoffCache = True")
        sb.appendLine()

        // [System]
        sb.appendLine("[System]")
        sb.appendLine("FastMemory = ${if (ppsspp.fastMemory) "True" else "False"}")
        sb.appendLine("SeparateSASThread = ${if (ppsspp.separateSasThread) "True" else "False"}")
        sb.appendLine("CPUSpeed = ${ppsspp.pspCpuClock}")
        sb.appendLine("IOTimingMethod = ${ppsspp.ioTimingMethod}")
        sb.appendLine("RewindFlip = ${if (system.rewindEnabled) 1 else 0}")
        sb.appendLine()

        // [Audio]
        sb.appendLine("[Audio]")
        sb.appendLine("Enable = ${if (audio.enabled) "True" else "False"}")
        sb.appendLine("AudioBackend = 0") // 0 = Auto / Oboe
        sb.appendLine("Volume = ${(audio.volume / 10).coerceIn(0, 10)}")
        sb.appendLine("LowLatencyAudio = True")
        sb.appendLine()

        // [Control]
        sb.appendLine("[Control]")
        sb.appendLine("ShowTouchControls = False")
        sb.appendLine("HapticFeedback = ${if (config.input.rumbleEnabled) "True" else "False"}")

        return sb.toString()
    }
}
