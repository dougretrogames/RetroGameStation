package org.retrogamestation.engine.mupen64plus

import org.retrogamestation.core.canonical.injector.GlideN64IniInjector
import org.retrogamestation.core.canonical.injector.MupenCfgInjector
import org.retrogamestation.core.canonical.model.EmulationConfig
import java.io.File
import java.io.IOException

/**
 * High-level Mupen64Plus configuration adapter.
 * Deploys mupen64plus.cfg and GLideN64.ini into the emulator config directory.
 */
class MupenConfigAdapter(
    private val cfgInjector: MupenCfgInjector = MupenCfgInjector(),
    private val glideInjector: GlideN64IniInjector = GlideN64IniInjector()
) {
    /**
     * Injects configuration files into the Mupen64Plus user data directory.
     * Writes to: <mupenDataDir>/mupen64plus.cfg
     *            <mupenDataDir>/GLideN64.ini
     */
    @Throws(IOException::class)
    fun inject(config: EmulationConfig, mupenDataDir: File) {
        if (!mupenDataDir.exists()) {
            mupenDataDir.mkdirs()
        }

        val cfgFile = File(mupenDataDir, "mupen64plus.cfg")
        cfgInjector.injectToFile(config, cfgFile)

        val glideFile = File(mupenDataDir, "GLideN64.ini")
        glideInjector.injectToFile(config, glideFile)
    }
}
