package org.retrogamestation.core.canonical.injector

import org.retrogamestation.core.canonical.model.EmulationConfig
import java.io.File
import java.io.IOException

/**
 * Base contract for native configuration serializers and injectors.
 */
interface ConfigInjector {

    /**
     * Serializes an [EmulationConfig] into the native emulator configuration text.
     */
    fun generate(config: EmulationConfig): String

    /**
     * Injects the serialized configuration directly into the target configuration file.
     * Creates parent directories if necessary.
     */
    @Throws(IOException::class)
    fun injectToFile(config: EmulationConfig, targetFile: File) {
        val parent = targetFile.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        targetFile.writeText(generate(config), Charsets.UTF_8)
    }
}
