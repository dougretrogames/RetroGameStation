package org.retrogamestation.engine.ppsspp

import org.retrogamestation.core.canonical.injector.PpssppControlsInjector
import org.retrogamestation.core.canonical.injector.PpssppIniInjector
import org.retrogamestation.core.canonical.model.EmulationConfig
import java.io.File
import java.io.IOException

/**
 * High-level PPSSPP configuration adapter.
 * Deploys ppsspp.ini and controls.ini into the target PSP/SYSTEM directory.
 */
class PpssppConfigAdapter(
    private val iniInjector: PpssppIniInjector = PpssppIniInjector(),
    private val controlsInjector: PpssppControlsInjector = PpssppControlsInjector()
) {
    /**
     * Injects configuration files into the PSP memstick root directory.
     * Writes to: <pspMemstickDir>/PSP/SYSTEM/ppsspp.ini
     *            <pspMemstickDir>/PSP/SYSTEM/controls.ini
     */
    @Throws(IOException::class)
    fun inject(config: EmulationConfig, pspMemstickDir: File) {
        val systemDir = File(pspMemstickDir, "PSP/SYSTEM")
        if (!systemDir.exists()) {
            systemDir.mkdirs()
        }

        val iniFile = File(systemDir, "ppsspp.ini")
        iniInjector.injectToFile(config, iniFile)

        val controlsFile = File(systemDir, "controls.ini")
        controlsInjector.injectToFile(config, controlsFile)
    }
}
