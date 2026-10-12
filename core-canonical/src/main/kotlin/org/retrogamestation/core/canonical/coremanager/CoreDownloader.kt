package org.retrogamestation.core.canonical.coremanager

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Asynchronous Core Downloader for Retro Game Station.
 * Directly downloads and unpacks official Libretro cores (.so.zip) from the Libretro Buildbot
 * to isolated application storage (/files/cores/), making them dynamically executable via dlopen.
 */
object CoreDownloader {

    private const val TAG = "CoreDownloader"

    /**
     * Retorna o diretório local isolado onde os núcleos .so são armazenados e executados.
     */
    fun getCoresDirectory(context: Context): File {
        val dir = File(context.filesDir, "cores")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Verifica se um núcleo específico já foi baixado e está disponível para execução.
     */
    fun isCoreInstalled(context: Context, core: CoreInfo): Boolean {
        val file = File(getCoresDirectory(context), core.fileName)
        return file.exists() && file.length() > 0
    }

    /**
     * Retorna o arquivo físico do núcleo caso esteja instalado.
     */
    fun getInstalledCoreFile(context: Context, core: CoreInfo): File? {
        val file = File(getCoresDirectory(context), core.fileName)
        return if (file.exists() && file.length() > 0) file else null
    }

    /**
     * Resolve a ABI nativa primária do dispositivo para selecionar a pasta correta no Buildbot.
     */
    fun resolveDeviceAbi(): String {
        val supported = try {
            Build.SUPPORTED_ABIS
        } catch (_: Throwable) {
            null
        } ?: emptyArray()

        for (abi in supported) {
            when (abi) {
                "arm64-v8a" -> return "arm64-v8a"
                "x86_64" -> return "x86_64"
                "armeabi-v7a" -> return "armeabi-v7a"
            }
        }
        return "arm64-v8a"
    }

    /**
     * Baixa e instala um núcleo do Libretro Buildbot com relatório de progresso.
     */
    suspend fun downloadAndInstallCore(
        context: Context,
        core: CoreInfo,
        onProgress: (progress: Int, status: String) -> Unit = { _, _ -> }
    ): Result<File> = withContext(Dispatchers.IO) {
        val abi = resolveDeviceAbi()
        val downloadUrl = "${CoreCatalog.BUILDBOT_BASE_URL}/$abi/${core.zipFileName}"
        val coresDir = getCoresDirectory(context)
        val targetFile = File(coresDir, core.fileName)

        Log.i(TAG, "Iniciando download do núcleo '${core.displayName}' via: $downloadUrl")
        onProgress(5, "Conectando ao Libretro Buildbot ($abi)...")

        var connection: HttpURLConnection? = null
        try {
            val url = URL(downloadUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.instanceFollowRedirects = true
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "RetroGameStation/1.0.0 (Android; $abi)")

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val errorMsg = "Falha no servidor Libretro Buildbot (HTTP $responseCode)"
                Log.e(TAG, errorMsg)
                return@withContext Result.failure(Exception(errorMsg))
            }

            val contentLength = connection.contentLength
            val inputStream = BufferedInputStream(connection.inputStream)
            val zipInputStream = ZipInputStream(inputStream)

            var foundEntry = false
            var entry = zipInputStream.nextEntry

            while (entry != null) {
                val entryName = entry.name
                if (entryName.endsWith(".so")) {
                    foundEntry = true
                    Log.i(TAG, "Extraindo binário nativo .so: $entryName (${entry.size} bytes)")
                    onProgress(40, "Extraindo ${core.displayName}...")

                    val tempFile = File(coresDir, "${core.fileName}.tmp")
                    val outputStream = FileOutputStream(tempFile)
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (zipInputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (contentLength > 0) {
                            val percent = 40 + ((totalRead.toDouble() / contentLength.toDouble()) * 50).toInt().coerceIn(0, 50)
                            onProgress(percent, "Instalando: ${totalRead / 1024} KB...")
                        }
                    }

                    outputStream.flush()
                    outputStream.close()
                    zipInputStream.closeEntry()

                    // Replace destination atomically
                    if (targetFile.exists()) {
                        targetFile.delete()
                    }
                    if (tempFile.renameTo(targetFile)) {
                        // Grant execution and read permissions
                        targetFile.setReadable(true, false)
                        targetFile.setExecutable(true, false)
                        Log.i(TAG, "Núcleo instalado com sucesso: ${targetFile.absolutePath} (${targetFile.length()} bytes)")
                        onProgress(100, "Concluído com sucesso!")
                        return@withContext Result.success(targetFile)
                    } else {
                        return@withContext Result.failure(Exception("Falha ao renomear arquivo temporário para ${targetFile.name}"))
                    }
                }
                entry = zipInputStream.nextEntry
            }

            zipInputStream.close()

            if (!foundEntry) {
                return@withContext Result.failure(Exception("O arquivo ZIP não contém uma biblioteca ELF compartilhada (.so)"))
            }

            Result.success(targetFile)
        } catch (e: Exception) {
            Log.e(TAG, "Erro durante download do núcleo: ${e.message}", e)
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Remove um núcleo instalado.
     */
    fun uninstallCore(context: Context, core: CoreInfo): Boolean {
        val file = File(getCoresDirectory(context), core.fileName)
        return if (file.exists()) {
            file.delete()
        } else false
    }

    /**
     * Retorna o número total de núcleos atualmente instalados.
     */
    fun countInstalledCores(context: Context): Int {
        return CoreCatalog.AVAILABLE_CORES.count { isCoreInstalled(context, it) }
    }
}
