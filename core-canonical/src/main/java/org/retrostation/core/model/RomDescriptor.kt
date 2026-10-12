package org.retrostation.core.model

import java.io.File

/**
 * Metadata descriptor for ROM payloads passed to native engines.
 */
data class RomDescriptor(
    val title: String,
    val filePath: String,
    val systemId: String,
    val suggestedCore: EmulationCore,
    val sha1Checksum: String = "",
    val saveDirectory: String = "",
    val stateDirectory: String = "",
    val coreFilePath: String = ""
) {
    val file: File get() = File(filePath)
    val exists: Boolean get() = file.exists()
}
