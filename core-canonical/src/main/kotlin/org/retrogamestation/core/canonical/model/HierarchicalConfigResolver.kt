package org.retrogamestation.core.canonical.model

import org.retrogamestation.core.canonical.model.subconfig.LibretroSpecificConfig
import org.retrogamestation.core.canonical.model.subconfig.MupenSpecificConfig
import org.retrogamestation.core.canonical.model.subconfig.PpssppSpecificConfig
import java.io.File

/**
 * Sparse patch layer representing overrides at Platform or Per-Game tier.
 * Any property set to `null` is inherited from the preceding tier in the hierarchy:
 * Global -> Platform -> Per-Game.
 */
data class ConfigOverrideLayer(
    val tier: ConfigTier = ConfigTier.GAME,
    val targetId: String = "", // platformId (e.g. "psp") or gameId (e.g. "ULES01505")
    
    // Video overrides
    val renderingApi: RenderingApi? = null,
    val aspectRatio: AspectRatioMode? = null,
    val customAspectRatio: Float? = null,
    val integerScale: Boolean? = null,
    val fullscreen: Boolean? = null,
    val rotationDegrees: Int? = null,
    val internalResolutionScale: Int? = null,
    val vsync: Boolean? = null,
    val framePacing: FramePacingMode? = null,
    val threadedVideo: Boolean? = null,
    val maxSwapchainImages: Int? = null,
    val frameDelay: Int? = null,
    val bilinearFiltering: Boolean? = null,
    val textureFiltering: TextureFilteringMode? = null,
    val shaderPresetPath: String? = null,
    val crtFilterEnabled: Boolean? = null,
    
    // Audio overrides
    val audioEnabled: Boolean? = null,
    val audioVolume: Int? = null,
    val audioLatencyMs: Int? = null,
    val syncAudio: Boolean? = null,
    
    // System overrides
    val fastForwardRatio: Float? = null,
    val rewindEnabled: Boolean? = null,
    val cheatsEnabled: Boolean? = null,
    val saveStateSlot: Int? = null,
    val showFps: Boolean? = null,
    
    // Input overrides
    val customButtonMappings: Map<CanonicalButton, PhysicalKeyBinding>? = null,
    val customAxisMappings: Map<CanonicalAxis, PhysicalAxisBinding>? = null,
    val leftStickDeadzone: StickDeadzone? = null,
    val rightStickDeadzone: StickDeadzone? = null,
    val rumbleEnabled: Boolean? = null,
    val rumbleIntensity: Float? = null,
    val turboEnabled: Boolean? = null,
    
    // Libretro overrides
    val libretroCoreOptions: Map<String, String>? = null,
    val libretroRunaheadFrames: Int? = null,
    val libretroAutoSaveInterval: Int? = null,
    
    // PPSSPP overrides
    val ppssppInternalResolution: Int? = null,
    val ppssppHardwareTransform: Boolean? = null,
    val ppssppSoftwareSkinning: Boolean? = null,
    val ppssppVertexCache: Boolean? = null,
    val ppssppTextureUpscaleType: Int? = null,
    val ppssppTextureUpscaleLevel: Int? = null,
    val ppssppFrameSkipping: Int? = null,
    val ppssppPspCpuClock: Int? = null,
    
    // Mupen64Plus overrides
    val mupenFramebufferEmulation: Boolean? = null,
    val mupenCopyDepthToRdram: Int? = null,
    val mupenCopyColorToRdram: Int? = null,
    val mupenHiResTextures: Boolean? = null,
    val mupenWidescreenHack: Boolean? = null,
    val mupenOverclockFactor: Int? = null,
    val mupenExpansionPak: Boolean? = null
)

enum class ConfigTier {
    GLOBAL,
    PLATFORM,
    GAME
}

/**
 * Three-tier cascading configuration resolver:
 *   Tier 1: Global Settings
 *   Tier 2: Platform/System Settings (e.g. SNES, N64, PSP)
 *   Tier 3: Per-Game Overrides (e.g. specific game ROM / ISO)
 *
 * Produces a deterministic, fully-resolved [EmulationConfig] ready for injection.
 */
class HierarchicalConfigResolver {

    /**
     * Resolves the final effective [EmulationConfig] by cascading:
     * Global -> Platform Layer -> Per-Game Layer.
     */
    fun resolve(
        global: EmulationConfig,
        platformLayer: ConfigOverrideLayer? = null,
        gameLayer: ConfigOverrideLayer? = null
    ): EmulationConfig {
        var resolved = global

        if (platformLayer != null) {
            resolved = applyLayer(resolved, platformLayer)
        }

        if (gameLayer != null) {
            resolved = applyLayer(resolved, gameLayer)
        }

        return resolved.copy(
            configId = gameLayer?.targetId ?: platformLayer?.targetId ?: global.configId,
            platformId = platformLayer?.targetId ?: global.platformId,
            gameId = gameLayer?.targetId ?: global.gameId
        )
    }

    /**
     * Cascades two full configs: parent is overlaid with child overrides.
     */
    fun mergeConfigs(parent: EmulationConfig, child: EmulationConfig): EmulationConfig {
        val mergedVideo = parent.video.copy(
            renderingApi = child.video.renderingApi,
            aspectRatio = child.video.aspectRatio,
            customAspectRatio = child.video.customAspectRatio ?: parent.video.customAspectRatio,
            integerScale = child.video.integerScale,
            fullscreen = child.video.fullscreen,
            rotationDegrees = child.video.rotationDegrees,
            internalResolutionScale = child.video.internalResolutionScale,
            vsync = child.video.vsync,
            framePacing = child.video.framePacing,
            threadedVideo = child.video.threadedVideo,
            maxSwapchainImages = child.video.maxSwapchainImages,
            frameDelay = child.video.frameDelay,
            bilinearFiltering = child.video.bilinearFiltering,
            textureFiltering = child.video.textureFiltering,
            shaderPresetPath = child.video.shaderPresetPath ?: parent.video.shaderPresetPath,
            crtFilterEnabled = child.video.crtFilterEnabled
        )

        val mergedInput = parent.input.copy(
            buttonMappings = parent.input.buttonMappings + child.input.buttonMappings,
            axisMappings = parent.input.axisMappings + child.input.axisMappings,
            hotkeyMappings = parent.input.hotkeyMappings + child.input.hotkeyMappings,
            leftStickDeadzone = child.input.leftStickDeadzone,
            rightStickDeadzone = child.input.rightStickDeadzone,
            rumbleEnabled = child.input.rumbleEnabled,
            rumbleIntensity = child.input.rumbleIntensity,
            turboEnabled = child.input.turboEnabled,
            turboRateHz = child.input.turboRateHz,
            turboButtons = parent.input.turboButtons + child.input.turboButtons
        )

        val mergedAudio = parent.audio.copy(
            enabled = child.audio.enabled,
            volume = child.audio.volume,
            latencyMs = child.audio.latencyMs,
            syncAudio = child.audio.syncAudio,
            audioBackend = child.audio.audioBackend
        )

        val mergedSystem = parent.system.copy(
            fastForwardRatio = child.system.fastForwardRatio,
            rewindEnabled = child.system.rewindEnabled,
            cheatsEnabled = child.system.cheatsEnabled,
            saveStateSlot = child.system.saveStateSlot,
            showFps = child.system.showFps
        )

        val mergedLibretro = parent.libretro.copy(
            coreOptions = parent.libretro.coreOptions + child.libretro.coreOptions,
            runaheadFrames = child.libretro.runaheadFrames,
            autoSaveIntervalSeconds = child.libretro.autoSaveIntervalSeconds
        )

        val mergedPpsspp = parent.ppsspp.copy(
            internalResolution = child.ppsspp.internalResolution,
            hardwareTransform = child.ppsspp.hardwareTransform,
            softwareSkinning = child.ppsspp.softwareSkinning,
            vertexCache = child.ppsspp.vertexCache,
            textureUpscaleType = child.ppsspp.textureUpscaleType,
            textureUpscaleLevel = child.ppsspp.textureUpscaleLevel,
            frameSkipping = child.ppsspp.frameSkipping,
            pspCpuClock = child.ppsspp.pspCpuClock
        )

        val mergedMupen = parent.mupen.copy(
            enableFramebufferEmulation = child.mupen.enableFramebufferEmulation,
            copyDepthToRdram = child.mupen.copyDepthToRdram,
            copyColorToRdram = child.mupen.copyColorToRdram,
            enableHiResTextures = child.mupen.enableHiResTextures,
            enableWidescreenHack = child.mupen.enableWidescreenHack,
            overclockFactor = child.mupen.overclockFactor,
            expansionPak = child.mupen.expansionPak
        )

        return child.copy(
            video = mergedVideo,
            input = mergedInput,
            audio = mergedAudio,
            system = mergedSystem,
            libretro = mergedLibretro,
            ppsspp = mergedPpsspp,
            mupen = mergedMupen
        )
    }

    private fun applyLayer(base: EmulationConfig, layer: ConfigOverrideLayer): EmulationConfig {
        val updatedVideo = base.video.copy(
            renderingApi = layer.renderingApi ?: base.video.renderingApi,
            aspectRatio = layer.aspectRatio ?: base.video.aspectRatio,
            customAspectRatio = layer.customAspectRatio ?: base.video.customAspectRatio,
            integerScale = layer.integerScale ?: base.video.integerScale,
            fullscreen = layer.fullscreen ?: base.video.fullscreen,
            rotationDegrees = layer.rotationDegrees ?: base.video.rotationDegrees,
            internalResolutionScale = layer.internalResolutionScale ?: base.video.internalResolutionScale,
            vsync = layer.vsync ?: base.video.vsync,
            framePacing = layer.framePacing ?: base.video.framePacing,
            threadedVideo = layer.threadedVideo ?: base.video.threadedVideo,
            maxSwapchainImages = layer.maxSwapchainImages ?: base.video.maxSwapchainImages,
            frameDelay = layer.frameDelay ?: base.video.frameDelay,
            bilinearFiltering = layer.bilinearFiltering ?: base.video.bilinearFiltering,
            textureFiltering = layer.textureFiltering ?: base.video.textureFiltering,
            shaderPresetPath = layer.shaderPresetPath ?: base.video.shaderPresetPath,
            crtFilterEnabled = layer.crtFilterEnabled ?: base.video.crtFilterEnabled
        )

        val updatedAudio = base.audio.copy(
            enabled = layer.audioEnabled ?: base.audio.enabled,
            volume = layer.audioVolume ?: base.audio.volume,
            latencyMs = layer.audioLatencyMs ?: base.audio.latencyMs,
            syncAudio = layer.syncAudio ?: base.audio.syncAudio
        )

        val updatedSystem = base.system.copy(
            fastForwardRatio = layer.fastForwardRatio ?: base.system.fastForwardRatio,
            rewindEnabled = layer.rewindEnabled ?: base.system.rewindEnabled,
            cheatsEnabled = layer.cheatsEnabled ?: base.system.cheatsEnabled,
            saveStateSlot = layer.saveStateSlot ?: base.system.saveStateSlot,
            showFps = layer.showFps ?: base.system.showFps
        )

        val updatedButtons = if (layer.customButtonMappings != null) {
            base.input.buttonMappings + layer.customButtonMappings
        } else {
            base.input.buttonMappings
        }

        val updatedAxes = if (layer.customAxisMappings != null) {
            base.input.axisMappings + layer.customAxisMappings
        } else {
            base.input.axisMappings
        }

        val updatedInput = base.input.copy(
            buttonMappings = updatedButtons,
            axisMappings = updatedAxes,
            leftStickDeadzone = layer.leftStickDeadzone ?: base.input.leftStickDeadzone,
            rightStickDeadzone = layer.rightStickDeadzone ?: base.input.rightStickDeadzone,
            rumbleEnabled = layer.rumbleEnabled ?: base.input.rumbleEnabled,
            rumbleIntensity = layer.rumbleIntensity ?: base.input.rumbleIntensity,
            turboEnabled = layer.turboEnabled ?: base.input.turboEnabled
        )

        val updatedLibretro = base.libretro.copy(
            coreOptions = if (layer.libretroCoreOptions != null) {
                base.libretro.coreOptions + layer.libretroCoreOptions
            } else {
                base.libretro.coreOptions
            },
            runaheadFrames = layer.libretroRunaheadFrames ?: base.libretro.runaheadFrames,
            autoSaveIntervalSeconds = layer.libretroAutoSaveInterval ?: base.libretro.autoSaveIntervalSeconds
        )

        val updatedPpsspp = base.ppsspp.copy(
            internalResolution = layer.ppssppInternalResolution ?: base.ppsspp.internalResolution,
            hardwareTransform = layer.ppssppHardwareTransform ?: base.ppsspp.hardwareTransform,
            softwareSkinning = layer.ppssppSoftwareSkinning ?: base.ppsspp.softwareSkinning,
            vertexCache = layer.ppssppVertexCache ?: base.ppsspp.vertexCache,
            textureUpscaleType = layer.ppssppTextureUpscaleType ?: base.ppsspp.textureUpscaleType,
            textureUpscaleLevel = layer.ppssppTextureUpscaleLevel ?: base.ppsspp.textureUpscaleLevel,
            frameSkipping = layer.ppssppFrameSkipping ?: base.ppsspp.frameSkipping,
            pspCpuClock = layer.ppssppPspCpuClock ?: base.ppsspp.pspCpuClock
        )

        val updatedMupen = base.mupen.copy(
            enableFramebufferEmulation = layer.mupenFramebufferEmulation ?: base.mupen.enableFramebufferEmulation,
            copyDepthToRdram = layer.mupenCopyDepthToRdram ?: base.mupen.copyDepthToRdram,
            copyColorToRdram = layer.mupenCopyColorToRdram ?: base.mupen.copyColorToRdram,
            enableHiResTextures = layer.mupenHiResTextures ?: base.mupen.enableHiResTextures,
            enableWidescreenHack = layer.mupenWidescreenHack ?: base.mupen.enableWidescreenHack,
            overclockFactor = layer.mupenOverclockFactor ?: base.mupen.overclockFactor,
            expansionPak = layer.mupenExpansionPak ?: base.mupen.expansionPak
        )

        return base.copy(
            video = updatedVideo,
            audio = updatedAudio,
            system = updatedSystem,
            input = updatedInput,
            libretro = updatedLibretro,
            ppsspp = updatedPpsspp,
            mupen = updatedMupen
        )
    }

    /**
     * Serializes an [EmulationConfig] to human-readable JSON string.
     */
    fun toJson(config: EmulationConfig): String {
        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("  \"configId\": \"${escapeJson(config.configId)}\",")
        sb.appendLine("  \"title\": \"${escapeJson(config.title)}\",")
        sb.appendLine("  \"platformId\": \"${escapeJson(config.platformId)}\",")
        sb.appendLine("  \"gameId\": ${config.gameId?.let { "\"${escapeJson(it)}\"" } ?: "null"},")
        
        // Video
        sb.appendLine("  \"video\": {")
        sb.appendLine("    \"renderingApi\": \"${config.video.renderingApi.value}\",")
        sb.appendLine("    \"aspectRatio\": \"${config.video.aspectRatio.value}\",")
        sb.appendLine("    \"customAspectRatio\": ${config.video.customAspectRatio ?: "null"},")
        sb.appendLine("    \"internalResolutionScale\": ${config.video.internalResolutionScale},")
        sb.appendLine("    \"vsync\": ${config.video.vsync},")
        sb.appendLine("    \"framePacing\": \"${config.video.framePacing.value}\",")
        sb.appendLine("    \"integerScale\": ${config.video.integerScale},")
        sb.appendLine("    \"bilinearFiltering\": ${config.video.bilinearFiltering},")
        sb.appendLine("    \"threadedVideo\": ${config.video.threadedVideo},")
        sb.appendLine("    \"maxSwapchainImages\": ${config.video.maxSwapchainImages},")
        sb.appendLine("    \"frameDelay\": ${config.video.frameDelay}")
        sb.appendLine("  },")
        
        // Audio
        sb.appendLine("  \"audio\": {")
        sb.appendLine("    \"enabled\": ${config.audio.enabled},")
        sb.appendLine("    \"volume\": ${config.audio.volume},")
        sb.appendLine("    \"latencyMs\": ${config.audio.latencyMs},")
        sb.appendLine("    \"syncAudio\": ${config.audio.syncAudio}")
        sb.appendLine("  },")
        
        // System
        sb.appendLine("  \"system\": {")
        sb.appendLine("    \"fastForwardRatio\": ${config.system.fastForwardRatio},")
        sb.appendLine("    \"rewindEnabled\": ${config.system.rewindEnabled},")
        sb.appendLine("    \"cheatsEnabled\": ${config.system.cheatsEnabled},")
        sb.appendLine("    \"saveStateSlot\": ${config.system.saveStateSlot},")
        sb.appendLine("    \"showFps\": ${config.system.showFps}")
        sb.appendLine("  },")
        
        // Input summary
        sb.appendLine("  \"input\": {")
        sb.appendLine("    \"profileName\": \"${escapeJson(config.input.profileName)}\",")
        sb.appendLine("    \"rumbleEnabled\": ${config.input.rumbleEnabled},")
        sb.appendLine("    \"rumbleIntensity\": ${config.input.rumbleIntensity},")
        sb.appendLine("    \"leftStickDeadzone\": { \"inner\": ${config.input.leftStickDeadzone.inner}, \"outer\": ${config.input.leftStickDeadzone.outer}, \"curve\": ${config.input.leftStickDeadzone.curve} },")
        sb.appendLine("    \"rightStickDeadzone\": { \"inner\": ${config.input.rightStickDeadzone.inner}, \"outer\": ${config.input.rightStickDeadzone.outer}, \"curve\": ${config.input.rightStickDeadzone.curve} }")
        sb.appendLine("  },")
        
        // Libretro
        sb.appendLine("  \"libretro\": {")
        sb.appendLine("    \"runaheadFrames\": ${config.libretro.runaheadFrames},")
        sb.appendLine("    \"autoSaveIntervalSeconds\": ${config.libretro.autoSaveIntervalSeconds},")
        sb.appendLine("    \"coreOptions\": {")
        val entries = config.libretro.coreOptions.entries.toList()
        entries.forEachIndexed { i, (k, v) ->
            val comma = if (i < entries.size - 1) "," else ""
            sb.appendLine("      \"${escapeJson(k)}\": \"${escapeJson(v)}\"$comma")
        }
        sb.appendLine("    }")
        sb.appendLine("  },")
        
        // PPSSPP
        sb.appendLine("  \"ppsspp\": {")
        sb.appendLine("    \"internalResolution\": ${config.ppsspp.internalResolution},")
        sb.appendLine("    \"hardwareTransform\": ${config.ppsspp.hardwareTransform},")
        sb.appendLine("    \"softwareSkinning\": ${config.ppsspp.softwareSkinning},")
        sb.appendLine("    \"vertexCache\": ${config.ppsspp.vertexCache},")
        sb.appendLine("    \"textureUpscaleType\": ${config.ppsspp.textureUpscaleType},")
        sb.appendLine("    \"textureUpscaleLevel\": ${config.ppsspp.textureUpscaleLevel},")
        sb.appendLine("    \"frameSkipping\": ${config.ppsspp.frameSkipping},")
        sb.appendLine("    \"pspCpuClock\": ${config.ppsspp.pspCpuClock}")
        sb.appendLine("  },")
        
        // Mupen
        sb.appendLine("  \"mupen\": {")
        sb.appendLine("    \"enableFramebufferEmulation\": ${config.mupen.enableFramebufferEmulation},")
        sb.appendLine("    \"copyDepthToRdram\": ${config.mupen.copyDepthToRdram},")
        sb.appendLine("    \"copyColorToRdram\": ${config.mupen.copyColorToRdram},")
        sb.appendLine("    \"enableHiResTextures\": ${config.mupen.enableHiResTextures},")
        sb.appendLine("    \"enableWidescreenHack\": ${config.mupen.enableWidescreenHack},")
        sb.appendLine("    \"overclockFactor\": ${config.mupen.overclockFactor},")
        sb.appendLine("    \"expansionPak\": ${config.mupen.expansionPak}")
        sb.appendLine("  }")
        
        sb.append("}")
        return sb.toString()
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
    }
}
