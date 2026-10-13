package org.retrogamestation.core.canonical.coremanager

/**
 * Metadata definition for a downloadable Libretro emulation core.
 */
data class CoreInfo(
    val id: String,
    val displayName: String,
    val systemName: String,
    val systemId: String,
    val fileName: String,
    val zipFileName: String,
    val extensions: List<String>,
    val description: String,
    val iconEmoji: String = "🎮"
)

/**
 * Curated catalog of official Libretro emulation cores hosted on the Libretro Buildbot.
 */
object CoreCatalog {

    const val BUILDBOT_BASE_URL = "https://buildbot.libretro.com/nightly/android/latest"

    val AVAILABLE_CORES: List<CoreInfo> = listOf(
        CoreInfo(
            id = "snes9x",
            displayName = "Snes9x",
            systemName = "Super Nintendo (SNES)",
            systemId = "snes",
            fileName = "snes9x_libretro_android.so",
            zipFileName = "snes9x_libretro_android.so.zip",
            extensions = listOf(".sfc", ".smc", ".fig", ".swc"),
            description = "O emulador de Super Nintendo mais compatível e maduro.",
            iconEmoji = "🍄"
        ),
        CoreInfo(
            id = "fceumm",
            displayName = "FCEUmm",
            systemName = "Nintendo Entertainment System (NES)",
            systemId = "nes",
            fileName = "fceumm_libretro_android.so",
            zipFileName = "fceumm_libretro_android.so.zip",
            extensions = listOf(".nes", ".fds", ".unf"),
            description = "Núcleo NES/Famicom de alta performance com suporte amplo a mappers.",
            iconEmoji = "⭐"
        ),
        CoreInfo(
            id = "genesis_plus_gx",
            displayName = "Genesis Plus GX",
            systemName = "Sega Genesis / Mega Drive / Master System",
            systemId = "genesis",
            fileName = "genesis_plus_gx_libretro_android.so",
            zipFileName = "genesis_plus_gx_libretro_android.so.zip",
            extensions = listOf(".md", ".smd", ".gen", ".bin", ".sms", ".gg", ".sg"),
            description = "Emulação precisa de consoles Sega de 8 e 16 bits.",
            iconEmoji = "🦔"
        ),
        CoreInfo(
            id = "mgba",
            displayName = "mGBA",
            systemName = "Game Boy Advance (GBA)",
            systemId = "gba",
            fileName = "mgba_libretro_android.so",
            zipFileName = "mgba_libretro_android.so.zip",
            extensions = listOf(".gba"),
            description = "Núcleo GBA moderno, veloz e focado em alta fidelidade.",
            iconEmoji = "⚡"
        ),
        CoreInfo(
            id = "gambatte",
            displayName = "Gambatte",
            systemName = "Game Boy / Game Boy Color (GB/GBC)",
            systemId = "gb",
            fileName = "gambatte_libretro_android.so",
            zipFileName = "gambatte_libretro_android.so.zip",
            extensions = listOf(".gb", ".gbc"),
            description = "Emulador cycle-accurate de Game Boy com paletas de cores customizadas.",
            iconEmoji = "🔋"
        ),
        CoreInfo(
            id = "pcsx_rearmed",
            displayName = "PCSX ReARMed",
            systemName = "Sony PlayStation (PS1 / PSX)",
            systemId = "psx",
            fileName = "pcsx_rearmed_libretro_android.so",
            zipFileName = "pcsx_rearmed_libretro_android.so.zip",
            extensions = listOf(".cue", ".chd", ".pbp", ".iso", ".bin"),
            description = "Altamente otimizado para ARM com compilador dinâmico JIT e alta velocidade.",
            iconEmoji = "💿"
        ),
        CoreInfo(
            id = "swanstation",
            displayName = "SwanStation",
            systemName = "Sony PlayStation (PS1 / PSX Advanced)",
            systemId = "psx",
            fileName = "swanstation_libretro_android.so",
            zipFileName = "swanstation_libretro_android.so.zip",
            extensions = listOf(".cue", ".chd", ".pbp", ".iso", ".bin"),
            description = "Núcleo PSX moderno baseado no DuckStation com renderização HD.",
            iconEmoji = "🦆"
        ),
        CoreInfo(
            id = "fbneo",
            displayName = "FinalBurn Neo",
            systemName = "Arcade / Neo Geo / CPS1 / CPS2 / CPS3",
            systemId = "arcade",
            fileName = "fbneo_libretro_android.so",
            zipFileName = "fbneo_libretro_android.so.zip",
            extensions = listOf(".zip", ".7z"),
            description = "O melhor núcleo arcade para clássicos da Capcom, SNK Neo-Geo e fliperamas.",
            iconEmoji = "🕹️"
        ),
        CoreInfo(
            id = "mame2003_plus",
            displayName = "MAME 2003-Plus",
            systemName = "Arcade Clássico (MAME)",
            systemId = "mame",
            fileName = "mame2003_plus_libretro_android.so",
            zipFileName = "mame2003_plus_libretro_android.so.zip",
            extensions = listOf(".zip"),
            description = "Focado em clássicos arcade dos anos 80 e 90 com baixo consumo de CPU.",
            iconEmoji = "👾"
        ),
        CoreInfo(
            id = "ppsspp_libretro",
            displayName = "PPSSPP (Libretro Core)",
            systemName = "PlayStation Portable (PSP)",
            systemId = "psp",
            fileName = "ppsspp_libretro_android.so",
            zipFileName = "ppsspp_libretro_android.so.zip",
            extensions = listOf(".iso", ".cso", ".pbp"),
            description = "Versão Libretro do aclamado emulador de Sony PSP.",
            iconEmoji = "🕹️"
        ),
        CoreInfo(
            id = "melonds",
            displayName = "melonDS",
            systemName = "Nintendo DS (NDS)",
            systemId = "nds",
            fileName = "melonds_libretro_android.so",
            zipFileName = "melonds_libretro_android.so.zip",
            extensions = listOf(".nds"),
            description = "Emulação veloz de telas duplas do Nintendo DS com renderizador OpenGL/Software.",
            iconEmoji = "📱"
        ),
        CoreInfo(
            id = "desmume",
            displayName = "DeSmuME",
            systemName = "Nintendo DS (DeSmuME)",
            systemId = "nds",
            fileName = "desmume_libretro_android.so",
            zipFileName = "desmume_libretro_android.so.zip",
            extensions = listOf(".nds"),
            description = "Núcleo clássico e altamente preciso para Nintendo DS.",
            iconEmoji = "📱"
        ),
        CoreInfo(
            id = "stella",
            displayName = "Stella",
            systemName = "Atari 2600",
            systemId = "atari2600",
            fileName = "stella_libretro_android.so",
            zipFileName = "stella_libretro_android.so.zip",
            extensions = listOf(".a26"),
            description = "Emulador oficial clássico do pioneiro console Atari 2600.",
            iconEmoji = "🕹️"
        ),
        CoreInfo(
            id = "mednafen_pce_fast",
            displayName = "Beetle PCE Fast",
            systemName = "PC Engine / TurboGrafx-16",
            systemId = "pce",
            fileName = "mednafen_pce_fast_libretro_android.so",
            zipFileName = "mednafen_pce_fast_libretro_android.so.zip",
            extensions = listOf(".pce", ".cue", ".chd"),
            description = "Núcleo rápido para a biblioteca de jogos da NEC / Hudson Soft.",
            iconEmoji = "🚀"
        ),
        CoreInfo(
            id = "mupen64plus_next",
            displayName = "Mupen64Plus-Next",
            systemName = "Nintendo 64 (Libretro Core)",
            systemId = "n64",
            fileName = "mupen64plus_next_libretro_android.so",
            zipFileName = "mupen64plus_next_libretro_android.so.zip",
            extensions = listOf(".z64", ".n64", ".v64"),
            description = "Port Libretro moderno do Mupen64Plus com plugin GLideN64 integrado.",
            iconEmoji = "🏰"
        ),
        CoreInfo(
            id = "flycast",
            displayName = "Flycast",
            systemName = "Sega Dreamcast / Naomi",
            systemId = "dreamcast",
            fileName = "flycast_libretro_android.so",
            zipFileName = "flycast_libretro_android.so.zip",
            extensions = listOf(".cdi", ".gdi", ".chd"),
            description = "Núcleo Sega Dreamcast com suporte gráfico a Vulkan e GLES.",
            iconEmoji = "🌀"
        ),
        CoreInfo(
            id = "picodrive",
            displayName = "PicoDrive",
            systemName = "Sega 32X / Sega CD / Mega Drive",
            systemId = "genesis",
            fileName = "picodrive_libretro_android.so",
            zipFileName = "picodrive_libretro_android.so.zip",
            extensions = listOf(".32x", ".cue", ".iso", ".md"),
            description = "Excelente suporte para add-ons Sega 32X e Sega CD.",
            iconEmoji = "🦔"
        ),
        CoreInfo(
            id = "sameboy",
            displayName = "SameBoy",
            systemName = "Game Boy / Game Boy Color (SameBoy)",
            systemId = "gb",
            fileName = "sameboy_libretro_android.so",
            zipFileName = "sameboy_libretro_android.so.zip",
            extensions = listOf(".gb", ".gbc"),
            description = "Emulador extremamente preciso para Game Boy e Game Boy Color.",
            iconEmoji = "🔋"
        ),
        CoreInfo(
            id = "gearsystem",
            displayName = "Gearsystem",
            systemName = "Sega Master System / Game Gear",
            systemId = "sms",
            fileName = "gearsystem_libretro_android.so",
            zipFileName = "gearsystem_libretro_android.so.zip",
            extensions = listOf(".sms", ".gg"),
            description = "Emulador preciso de 8-bits da Sega.",
            iconEmoji = "🎮"
        )
    )

    /**
     * Encontra o núcleo ideal correspondente a uma extensão de arquivo de ROM.
     */
    fun findCoreForExtension(extension: String): CoreInfo? {
        val cleanExt = if (extension.startsWith(".")) extension.lowercase() else ".$extension".lowercase()
        return AVAILABLE_CORES.firstOrNull { core ->
            core.extensions.any { it.equals(cleanExt, ignoreCase = true) }
        }
    }

    /**
     * Encontra um núcleo pelo seu identificador único.
     */
    fun findCoreById(id: String): CoreInfo? {
        return AVAILABLE_CORES.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }

    /**
     * Cria dinamicamente uma definição de CoreInfo para QUALQUER núcleo do Libretro Buildbot.
     */
    fun createCustomCore(coreNameInput: String): CoreInfo {
        val cleanName = coreNameInput.trim()
            .removeSuffix(".so.zip")
            .removeSuffix(".so")
            .removeSuffix("_libretro_android")
        val fileName = "${cleanName}_libretro_android.so"
        val zipFileName = "${fileName}.zip"
        return CoreInfo(
            id = cleanName,
            displayName = cleanName.replace("_", " ").split(" ").joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } },
            systemName = "Núcleo Libretro Custom ($cleanName)",
            systemId = cleanName,
            fileName = fileName,
            zipFileName = zipFileName,
            extensions = emptyList(),
            description = "Núcleo Libretro do Buildbot oficial.",
            iconEmoji = "📦"
        )
    }
}
