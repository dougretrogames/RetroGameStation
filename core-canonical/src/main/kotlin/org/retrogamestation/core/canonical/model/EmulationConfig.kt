package org.retrogamestation.core.canonical.model

import org.retrogamestation.core.canonical.model.subconfig.LibretroSpecificConfig
import org.retrogamestation.core.canonical.model.subconfig.MupenSpecificConfig
import org.retrogamestation.core.canonical.model.subconfig.PpssppSpecificConfig

/**
 * Root canonical emulation configuration.
 * Aggregates all standardized sub-domains:
 * - Physical Input & Gamepad mapping
 * - Master Video display & backend
 * - Master Audio pipeline
 * - Master System execution parameters
 * - Isolated Core-Specific configurations (Libretro, PPSSPP, Mupen64Plus)
 */
data class EmulationConfig(
    val configId: String = "default",
    val title: String = "Default Configuration",
    val platformId: String = "global",
    val gameId: String? = null,
    
    // Core standardized subsystems
    val input: MasterInputConfig = MasterInputConfig.createStandardAndroidDefault(),
    val video: MasterVideoConfig = MasterVideoConfig.default(),
    val audio: MasterAudioConfig = MasterAudioConfig.default(),
    val system: MasterSystemConfig = MasterSystemConfig.default(),
    
    // Cleanly isolated sub-configs for engine/core-specific parameters
    val libretro: LibretroSpecificConfig = LibretroSpecificConfig.default(),
    val ppsspp: PpssppSpecificConfig = PpssppSpecificConfig.default(),
    val mupen: MupenSpecificConfig = MupenSpecificConfig.default()
) {
    companion object {
        fun default(): EmulationConfig = EmulationConfig()
    }
}
