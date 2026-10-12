package org.retrostation.core.model

/**
 * Supported emulation core types across the multi-engine Retro Game Station architecture.
 */
enum class EmulationCore(val coreId: String, val displayName: String) {
    LIBRETRO("libretro", "Libretro RetroArch Multi-Core Bridge"),
    PPSSPP("ppsspp", "PPSSPP Sony PSP Native Engine"),
    MUPEN64PLUS("mupen64plus", "Mupen64Plus Nintendo 64 Native Engine");

    companion object {
        fun fromId(id: String): EmulationCore? = entries.firstOrNull { it.coreId.equals(id, ignoreCase = true) }
    }
}
