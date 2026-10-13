package com.retro.gamestation.storage

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import com.retro.gamestation.core.NativeEngineBridge
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.CRC32

/**
 * Scoped Storage & Persistence Pipeline for Retro Game Station.
 * Full Android 11+ (API 30+) Storage Access Framework (SAF) compliance.
 *
 * Resolves content:// URIs, extracts native file descriptors via ParcelFileDescriptor
 * for zero-copy native consumption without high-overhead ROM duplication,
 * and organizes persistent app-isolated directories for saves, states, shaders, and BIOS.
 */
class ScopedStorageManager(private val context: Context) {

    companion object {
        private const val TAG = "ScopedStorageManager"

        const val DIR_SAVES = "saves"
        const val DIR_STATES = "states"
        const val DIR_SHADERS = "shaders"
        const val DIR_BIOS = "bios"
        const val DIR_SCREENSHOTS = "screenshots"
        const val DIR_CHEATS = "cheats"

        @Volatile
        private var instance: ScopedStorageManager? = null

        fun getInstance(context: Context): ScopedStorageManager {
            return instance ?: synchronized(this) {
                instance ?: ScopedStorageManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val rootFilesDir: File = context.filesDir

    init {
        ensureDirectoryStructure()
        extractAssetsIfNeeded(context)
    }

    /**
     * Initializes the standard app-isolated directory hierarchy.
     */
    fun ensureDirectoryStructure() {
        val directories = listOf(
            File(rootFilesDir, DIR_SAVES),
            File(rootFilesDir, DIR_STATES),
            File(rootFilesDir, DIR_SHADERS),
            File(rootFilesDir, DIR_BIOS),
            File(rootFilesDir, DIR_SCREENSHOTS),
            File(rootFilesDir, DIR_CHEATS)
        )

        for (dir in directories) {
            if (!dir.exists()) {
                val created = dir.mkdirs()
                Log.d(TAG, "Directory ${dir.name} created: $created (${dir.absolutePath})")
            }
        }
    }

    /**
     * Extrai BIOS, remaps e configurações empacotadas na pasta assets/ para o armazenamento interno.
     */
    fun extractAssetsIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences("rgs_storage_prefs", Context.MODE_PRIVATE)
        val extractedVersion = prefs.getInt("assets_extracted_version", 0)
        val currentVersion = 1

        if (extractedVersion < currentVersion) {
            try {
                val assetManager = context.assets
                copyAssetFolder(assetManager, "system", getBiosDirectory())
                copyAssetFolder(assetManager, "remaps", File(rootFilesDir, "remaps"))
                prefs.edit().putInt("assets_extracted_version", currentVersion).apply()
                Log.i(TAG, "Assets (BIOS, System, Remaps) extraídos com sucesso para o armazenamento interno.")
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao extrair assets: ${e.message}", e)
            }
        }
    }

    private fun copyAssetFolder(assetManager: android.content.res.AssetManager, assetPath: String, targetDir: File) {
        val files = try {
            assetManager.list(assetPath)
        } catch (_: Exception) {
            null
        } ?: return

        if (!targetDir.exists()) targetDir.mkdirs()

        for (file in files) {
            val subAssetPath = if (assetPath.isEmpty()) file else "$assetPath/$file"
            val subFiles = try {
                assetManager.list(subAssetPath)
            } catch (_: Exception) {
                null
            }

            if (subFiles != null && subFiles.isNotEmpty()) {
                copyAssetFolder(assetManager, subAssetPath, File(targetDir, file))
            } else {
                val targetFile = File(targetDir, file)
                if (!targetFile.exists() || targetFile.length() == 0L) {
                    try {
                        assetManager.open(subAssetPath).use { input ->
                            FileOutputStream(targetFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Não foi possível extrair arquivo de asset $subAssetPath: ${e.message}")
                    }
                }
            }
        }
    }

    // =========================================================================
    // System-Specific Directory Resolvers
    // =========================================================================

    /**
     * Returns persistent battery/SRAM saves directory for a specific console system.
     * Path: /files/saves/{system}/
     */
    fun getSaveDirectory(system: String): File {
        val sanitized = sanitizeSystemName(system)
        val dir = File(rootFilesDir, "$DIR_SAVES/$sanitized")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Returns persistent save state directory for a specific console system.
     * Path: /files/states/{system}/
     */
    fun getStateDirectory(system: String): File {
        val sanitized = sanitizeSystemName(system)
        val dir = File(rootFilesDir, "$DIR_STATES/$sanitized")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Returns persistent shaders directory.
     * Path: /files/shaders/
     */
    fun getShadersDirectory(): File {
        val dir = File(rootFilesDir, DIR_SHADERS)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Returns persistent BIOS directory.
     * Path: /files/bios/
     */
    fun getBiosDirectory(): File {
        val dir = File(rootFilesDir, DIR_BIOS)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Returns persistent screenshots directory.
     * Path: /files/screenshots/
     */
    fun getScreenshotsDirectory(): File {
        val dir = File(rootFilesDir, DIR_SCREENSHOTS)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Returns persistent cheats directory for a specific console system.
     * Path: /files/cheats/{system}/
     */
    fun getCheatsDirectory(system: String): File {
        val sanitized = sanitizeSystemName(system)
        val dir = File(rootFilesDir, "$DIR_CHEATS/$sanitized")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    // =========================================================================
    // Save State File Management (Slots 0..9)
    // =========================================================================

    data class SaveStateInfo(
        val slot: Int,
        val stateFile: File,
        val screenshotFile: File?,
        val exists: Boolean,
        val timestampMillis: Long,
        val sizeBytes: Long
    )

    fun getSaveStatePath(system: String, romBaseName: String, slot: Int): File {
        val stateDir = getStateDirectory(system)
        val suffix = if (slot == 0) ".state" else ".state$slot"
        return File(stateDir, "$romBaseName$suffix")
    }

    fun getSaveStateScreenshotPath(system: String, romBaseName: String, slot: Int): File {
        val stateDir = getStateDirectory(system)
        val suffix = if (slot == 0) ".state.png" else ".state$slot.png"
        return File(stateDir, "$romBaseName$suffix")
    }

    fun getSaveStateInfo(system: String, romBaseName: String, slot: Int): SaveStateInfo {
        val file = getSaveStatePath(system, romBaseName, slot)
        val ssFile = getSaveStateScreenshotPath(system, romBaseName, slot)
        val exists = file.exists() && file.length() > 0
        return SaveStateInfo(
            slot = slot,
            stateFile = file,
            screenshotFile = if (ssFile.exists()) ssFile else null,
            exists = exists,
            timestampMillis = if (exists) file.lastModified() else 0L,
            sizeBytes = if (exists) file.length() else 0L
        )
    }

    fun listAllSaveStates(system: String, romBaseName: String): List<SaveStateInfo> {
        return (0..9).map { slot -> getSaveStateInfo(system, romBaseName, slot) }
    }

    // =========================================================================
    // Storage Access Framework (SAF) & Direct Native FD Extraction
    // =========================================================================

    /**
     * Persists URI permissions across system reboots for user-selected ROM trees or documents.
     */
    fun takePersistablePermissions(uri: Uri): Boolean {
        return try {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            Log.i(TAG, "Persistable SAF permission granted for URI: $uri")
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Failed to persist URI permission (might be read-only): ${e.message}")
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                true
            } catch (ex: Exception) {
                Log.e(TAG, "Could not acquire persistable read permission: ${ex.message}")
                false
            }
        }
    }

    data class DirectFileDescriptor(
        val pfd: ParcelFileDescriptor,
        val fd: Int,
        val procPath: String,
        val displayName: String,
        val sizeBytes: Long
    ) : AutoCloseable {
        override fun close() {
            try {
                pfd.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing ParcelFileDescriptor: ${e.message}")
            }
        }
    }

    /**
     * Opens a direct native file descriptor from a content:// or file:// URI.
     * This avoids multi-gigabyte file copying, allowing native C/C++ core engines
     * (Libretro, PPSSPP, Mupen64Plus) to access the file directly via /proc/self/fd/<fd>.
     */
    fun openDirectFileDescriptor(uri: Uri, mode: String = "r"): DirectFileDescriptor? {
        takePersistablePermissions(uri)

        val pfd = try {
            context.contentResolver.openFileDescriptor(uri, mode)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open ParcelFileDescriptor for $uri: ${e.message}")
            return null
        } ?: return null

        val rawFd = pfd.fd
        val procPath = "/proc/self/fd/$rawFd"
        val displayName = queryDocumentDisplayName(uri) ?: "unknown_rom"
        val size = queryDocumentSize(uri)

        Log.i(TAG, "Acquired direct native FD: fd=$rawFd, path=$procPath, name=$displayName, size=$size bytes")

        // Test direct access through native C++ JNI bridge
        val nativeValid = NativeEngineBridge.nativeOpenDirectRomFd(rawFd, procPath)
        if (!nativeValid) {
            Log.w(TAG, "Native FD validation failed or non-seekable. Direct streaming may be needed.")
        }

        return DirectFileDescriptor(
            pfd = pfd,
            fd = rawFd,
            procPath = procPath,
            displayName = displayName,
            sizeBytes = size
        )
    }

    fun queryDocumentDisplayName(uri: Uri): String? {
        if (uri.scheme == "file") {
            return uri.lastPathSegment
        }
        var name: String? = null
        try {
            val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        name = it.getString(nameIndex)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not resolve display name for $uri: ${e.message}")
        }
        return name ?: uri.lastPathSegment
    }

    fun queryDocumentSize(uri: Uri): Long {
        if (uri.scheme == "file") {
            val file = uri.path?.let { File(it) }
            return file?.length() ?: -1L
        }
        var size: Long = -1L
        try {
            val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1 && !it.isNull(sizeIndex)) {
                        size = it.getLong(sizeIndex)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not resolve size for $uri: ${e.message}")
        }
        return size
    }

    // =========================================================================
    // BIOS Management & Checksum Verification
    // =========================================================================

    data class BiosFileStatus(
        val fileName: String,
        val exists: Boolean,
        val expectedSha1: String?,
        val actualSha1: String?,
        val expectedMd5: String?,
        val actualMd5: String?,
        val isChecksumMatching: Boolean,
        val filePath: String
    )

    fun copyBiosFromUri(sourceUri: Uri, targetFileName: String): Boolean {
        val biosDir = getBiosDirectory()
        val targetFile = File(biosDir, targetFileName)

        return try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.i(TAG, "Copied BIOS file $targetFileName successfully to ${targetFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed copying BIOS from $sourceUri: ${e.message}", e)
            false
        }
    }

    fun verifyBiosFile(
        fileName: String,
        expectedSha1: String? = null,
        expectedMd5: String? = null
    ): BiosFileStatus {
        val file = File(getBiosDirectory(), fileName)
        if (!file.exists() || file.length() == 0L) {
            return BiosFileStatus(
                fileName = fileName,
                exists = false,
                expectedSha1 = expectedSha1,
                actualSha1 = null,
                expectedMd5 = expectedMd5,
                actualMd5 = null,
                isChecksumMatching = false,
                filePath = file.absolutePath
            )
        }

        val actualSha1 = calculateDigest(file, "SHA-1")
        val actualMd5 = calculateDigest(file, "MD5")

        val sha1Match = expectedSha1 == null || expectedSha1.equals(actualSha1, ignoreCase = true)
        val md5Match = expectedMd5 == null || expectedMd5.equals(actualMd5, ignoreCase = true)

        return BiosFileStatus(
            fileName = fileName,
            exists = true,
            expectedSha1 = expectedSha1,
            actualSha1 = actualSha1,
            expectedMd5 = expectedMd5,
            actualMd5 = actualMd5,
            isChecksumMatching = sha1Match && md5Match,
            filePath = file.absolutePath
        )
    }

    // =========================================================================
    // Saves Export / Import (Cloud Sync & Backup Pipeline)
    // =========================================================================

    /**
     * Exports all saves for a system to an external SAF directory (e.g. SD card or Cloud drive).
     */
    fun exportSystemSaves(system: String, targetFolderUri: Uri): Int {
        val saveDir = getSaveDirectory(system)
        val saveFiles = saveDir.listFiles() ?: return 0
        var exportedCount = 0

        for (file in saveFiles) {
            if (file.isFile) {
                try {
                    val docUri = DocumentsContract.createDocument(
                        context.contentResolver,
                        targetFolderUri,
                        "application/octet-stream",
                        file.name
                    ) ?: continue

                    context.contentResolver.openOutputStream(docUri)?.use { out ->
                        FileInputStream(file).use { input ->
                            input.copyTo(out)
                        }
                    }
                    exportedCount++
                } catch (e: Exception) {
                    Log.e(TAG, "Failed exporting ${file.name}: ${e.message}")
                }
            }
        }
        Log.i(TAG, "Exported $exportedCount saves for system $system to $targetFolderUri")
        return exportedCount
    }

    /**
     * Imports saves from an external SAF document into the system saves folder.
     */
    fun importSaveFile(system: String, sourceUri: Uri, destinationFileName: String): Boolean {
        val targetFile = File(getSaveDirectory(system), destinationFileName)
        return try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.i(TAG, "Imported save $destinationFileName successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed importing save from $sourceUri: ${e.message}", e)
            false
        }
    }

    // =========================================================================
    // Helper Utilities
    // =========================================================================

    private fun sanitizeSystemName(system: String): String {
        return system.lowercase().replace("[^a-z0-9_-]".toRegex(), "")
    }

    private fun calculateDigest(file: File, algorithm: String): String {
        return try {
            val digest = MessageDigest.getInstance(algorithm)
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var read = input.read(buffer)
                while (read != -1) {
                    digest.update(buffer, 0, read)
                    read = input.read(buffer)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating $algorithm for ${file.name}: ${e.message}")
            ""
        }
    }

    fun calculateCRC32(file: File): Long {
        return try {
            val crc = CRC32()
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var read = input.read(buffer)
                while (read != -1) {
                    crc.update(buffer, 0, read)
                    read = input.read(buffer)
                }
            }
            crc.value
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating CRC32 for ${file.name}: ${e.message}")
            0L
        }
    }
}
