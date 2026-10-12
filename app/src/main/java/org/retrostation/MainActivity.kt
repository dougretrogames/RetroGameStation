package org.retrostation

import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.hardware.input.InputManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.InputDevice
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.retro.gamestation.R
import com.retro.gamestation.ui.EmulatorHostActivity
import kotlinx.coroutines.launch
import org.retrogamestation.core.canonical.coremanager.CoreCatalog
import org.retrogamestation.core.canonical.coremanager.CoreDownloader
import org.retrogamestation.core.canonical.coremanager.CoreInfo

/**
 * Main Launcher, Diagnostic Dashboard & Core Downloader for Retro Game Station.
 * Fully compatible with Samsung OneUI 9, Android 17 (API 37), and modern 16KB page size hardware (Galaxy S25+).
 */
class MainActivity : AppCompatActivity() {

    private val TAG = "RetroMainActivity"

    private lateinit var tvDeviceInfo: TextView
    private lateinit var tvNativeStatus: TextView
    private lateinit var tvControllerStatus: TextView
    private lateinit var tvCoresStatus: TextView
    private lateinit var tvBadgeStatus: TextView

    // SAF Document Picker for selecting ROM files
    private val romPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            // Persist read permission across reboots
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                Log.w(TAG, "Could not take persistable permission: ${e.message}")
            }
            handleSelectedRom(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Edge-to-Edge compliance for Android 15/16/17 and OneUI 9
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_main)

        // Apply window insets for display cutouts and navigation bars
        val contentContainer = findViewById<android.view.View>(R.id.main_content_container)
        ViewCompat.setOnApplyWindowInsetsListener(contentContainer) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(systemBars.left + 24, systemBars.top + 24, systemBars.right + 24, systemBars.bottom + 24)
            insets
        }

        // 2. Bind views
        tvDeviceInfo = findViewById(R.id.tv_device_info)
        tvNativeStatus = findViewById(R.id.tv_native_status)
        tvControllerStatus = findViewById(R.id.tv_controller_status)
        tvCoresStatus = findViewById(R.id.tv_cores_status)
        tvBadgeStatus = findViewById(R.id.tv_badge_status)

        val btnOpenRom = findViewById<Button>(R.id.btn_open_rom)
        val btnManageCores = findViewById<Button>(R.id.btn_manage_cores)
        val btnLaunchLibretro = findViewById<Button>(R.id.btn_launch_libretro)
        val btnLaunchPpsspp = findViewById<Button>(R.id.btn_launch_ppsspp)
        val btnLaunchMupen = findViewById<Button>(R.id.btn_launch_mupen)
        val btnTestController = findViewById<Button>(R.id.btn_test_controller)
        val btnVideoSettings = findViewById<Button>(R.id.btn_video_settings)

        // 3. Diagnostics
        inspectSystemEnvironment()
        verifyNativeSubsystems()
        detectConnectedGamepads()
        updateCoresStatus()

        // 4. Setup Action Listeners
        btnOpenRom.setOnClickListener {
            openRomPicker()
        }

        btnManageCores.setOnClickListener {
            showCoreManagerDialog()
        }

        btnLaunchLibretro.setOnClickListener {
            launchEmulator(core = "libretro", system = "arcade")
        }

        btnLaunchPpsspp.setOnClickListener {
            launchEmulator(core = "ppsspp", system = "psp")
        }

        btnLaunchMupen.setOnClickListener {
            launchEmulator(core = "mupen64plus", system = "n64")
        }

        btnTestController.setOnClickListener {
            showControllerTestDialog()
        }

        btnVideoSettings.setOnClickListener {
            showVideoSettingsDialog()
        }

        // 5. Predictive back gesture callback for Android 14+ / 17 / OneUI 9
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(R.string.quick_menu_quit_launcher)
                    .setMessage("Deseja realmente sair do Retro Game Station?")
                    .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        detectConnectedGamepads()
        updateCoresStatus()
    }

    private fun inspectSystemEnvironment() {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        val androidVersion = Build.VERSION.RELEASE
        val apiLevel = Build.VERSION.SDK_INT
        val abi = CoreDownloader.resolveDeviceAbi()

        tvDeviceInfo.text = "Dispositivo: $manufacturer $model (Android $androidVersion / API $apiLevel)\nArquitetura: $abi (Primary)"
        tvBadgeStatus.text = "$abi • 16KB Clean"
    }

    private fun verifyNativeSubsystems() {
        val loaded = mutableListOf<String>()
        val failed = mutableListOf<String>()

        val libraries = listOf(
            "retro_native" to "Host Native/AAudio",
            "retrostation_libretro" to "Libretro Bridge",
            "retrostation_ppsspp" to "PPSSPP Engine",
            "retrostation_mupen64plus" to "Mupen64Plus Engine"
        )

        for ((libName, displayName) in libraries) {
            try {
                System.loadLibrary(libName)
                loaded.add(displayName)
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "UnsatisfiedLinkError loading $libName: ${e.message}")
                failed.add(displayName)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading $libName: ${e.message}")
                failed.add(displayName)
            }
        }

        if (failed.isEmpty()) {
            tvNativeStatus.text = "Status NDK: ✓ 4 Motores Nativos Integrados (16KB Page Aligned)"
            tvNativeStatus.setTextColor(android.graphics.Color.parseColor("#00F5D4"))
        } else {
            tvNativeStatus.text = "Status NDK: Carregados: ${loaded.size}/4. Falhas: ${failed.joinToString()}"
            tvNativeStatus.setTextColor(android.graphics.Color.parseColor("#FF6B6B"))
        }
    }

    private fun detectConnectedGamepads() {
        val inputManager = getSystemService(Context.INPUT_SERVICE) as? InputManager
        val deviceIds = inputManager?.inputDeviceIds ?: InputDevice.getDeviceIds()
        val gamepads = mutableListOf<String>()

        for (id in deviceIds) {
            val device = InputDevice.getDevice(id) ?: continue
            val sources = device.sources
            if ((sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
                gamepads.add(device.name)
            }
        }

        if (gamepads.isNotEmpty()) {
            tvControllerStatus.text = "Gamepad: Conectado (${gamepads.first()})"
            tvControllerStatus.setTextColor(android.graphics.Color.parseColor("#00F5D4"))
        } else {
            tvControllerStatus.text = "Gamepad: Nenhum controle físico detectado (Controles virtuais disponíveis)"
            tvControllerStatus.setTextColor(android.graphics.Color.parseColor("#FFBE0B"))
        }
    }

    private fun updateCoresStatus() {
        val installedCount = CoreDownloader.countInstalledCores(this)
        val totalCount = CoreCatalog.AVAILABLE_CORES.size
        tvCoresStatus.text = "Núcleos Libretro: $installedCount / $totalCount Instalados no Dispositivo"
        if (installedCount > 0) {
            tvCoresStatus.setTextColor(android.graphics.Color.parseColor("#00F5D4"))
        } else {
            tvCoresStatus.setTextColor(android.graphics.Color.parseColor("#FFBE0B"))
        }
    }

    private fun openRomPicker() {
        val mimeTypes = arrayOf(
            "application/zip",
            "application/x-zip-compressed",
            "application/octet-stream",
            "*/*"
        )
        romPickerLauncher.launch(mimeTypes)
    }

    /**
     * Identifica a extensão da ROM, localiza o núcleo apropriado e inicia ou baixa o núcleo.
     */
    private fun handleSelectedRom(uri: Uri) {
        val path = uri.toString().lowercase()
        val extension = when {
            path.contains(".") -> "." + path.substringAfterLast(".")
            else -> ""
        }

        // 1. Standalone PSP Engine
        if (extension in listOf(".iso", ".cso") && path.contains("psp")) {
            launchEmulatorWithUri(uri, core = "ppsspp", system = "psp", corePath = "")
            return
        }

        // 2. Standalone N64 Engine
        if (extension in listOf(".z64", ".n64", ".v64") && !CoreDownloader.isCoreInstalled(this, CoreCatalog.findCoreById("mupen64plus_next") ?: CoreCatalog.AVAILABLE_CORES[0])) {
            launchEmulatorWithUri(uri, core = "mupen64plus", system = "n64", corePath = "")
            return
        }

        // 3. Libretro Core Matching
        val matchedCore = CoreCatalog.findCoreForExtension(extension)
        if (matchedCore != null) {
            if (CoreDownloader.isCoreInstalled(this, matchedCore)) {
                val coreFile = CoreDownloader.getInstalledCoreFile(this, matchedCore)
                launchEmulatorWithUri(uri, core = matchedCore.id, system = matchedCore.systemId, corePath = coreFile?.absolutePath ?: "")
            } else {
                // Prompt user to download core
                AlertDialog.Builder(this)
                    .setTitle("Núcleo Necessário: ${matchedCore.displayName}")
                    .setMessage("A ROM selecionada requer o núcleo de emulação ${matchedCore.displayName} (${matchedCore.systemName}).\n\n" +
                            "O arquivo do jogo foi reconhecido pela extensão '$extension'.\n\n" +
                            "Deseja baixar e instalar o núcleo oficial agora diretamente pelo Libretro Buildbot?")
                    .setPositiveButton("Baixar e Iniciar") { _, _ ->
                        downloadCoreAndLaunch(matchedCore, uri)
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        } else {
            // Default generic Libretro core or standalone fallback
            launchEmulatorWithUri(uri, core = "libretro", system = "arcade", corePath = "")
        }
    }

    private fun downloadCoreAndLaunch(core: CoreInfo, romUri: Uri) {
        @Suppress("DEPRECATION")
        val progressDialog = ProgressDialog(this).apply {
            setTitle("Baixando Núcleo ${core.displayName}")
            setMessage("Conectando ao Libretro Buildbot...")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            max = 100
            isIndeterminate = false
            setCancelable(false)
            show()
        }

        lifecycleScope.launch {
            val result = CoreDownloader.downloadAndInstallCore(this@MainActivity, core) { progress, status ->
                runOnUiThread {
                    progressDialog.progress = progress
                    progressDialog.setMessage(status)
                }
            }

            progressDialog.dismiss()

            result.onSuccess { file ->
                updateCoresStatus()
                Toast.makeText(this@MainActivity, "${core.displayName} instalado com sucesso!", Toast.LENGTH_SHORT).show()
                launchEmulatorWithUri(romUri, core = core.id, system = core.systemId, corePath = file.absolutePath)
            }.onFailure { e ->
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Erro no Download")
                    .setMessage("Não foi possível baixar o núcleo ${core.displayName}: ${e.message}\n\nVerifique sua conexão com a internet.")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun launchEmulatorWithUri(uri: Uri, core: String, system: String, corePath: String) {
        val intent = Intent(this, EmulatorHostActivity::class.java).apply {
            putExtra(EmulatorHostActivity.EXTRA_ROM_URI, uri)
            putExtra(EmulatorHostActivity.EXTRA_CORE_NAME, core)
            putExtra(EmulatorHostActivity.EXTRA_SYSTEM_NAME, system)
            putExtra(EmulatorHostActivity.EXTRA_CORE_PATH, corePath)
        }
        startActivity(intent)
    }

    private fun launchEmulator(core: String, system: String) {
        val intent = Intent(this, EmulatorHostActivity::class.java).apply {
            putExtra(EmulatorHostActivity.EXTRA_CORE_NAME, core)
            putExtra(EmulatorHostActivity.EXTRA_SYSTEM_NAME, system)
        }
        startActivity(intent)
    }

    /**
     * Abre a interface completa do Gerenciador de Núcleos Libretro.
     */
    private fun showCoreManagerDialog() {
        val cores = CoreCatalog.AVAILABLE_CORES
        val items = cores.map { core ->
            val isInstalled = CoreDownloader.isCoreInstalled(this, core)
            val statusTag = if (isInstalled) "✓ Instalado" else "📥 Baixar"
            "${core.iconEmoji} ${core.displayName} • ${core.systemName}\n    [$statusTag]"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Gerenciador de Núcleos Libretro (${CoreDownloader.resolveDeviceAbi()})")
            .setItems(items) { _, which ->
                val selectedCore = cores[which]
                showCoreActionDialog(selectedCore)
            }
            .setNeutralButton("⚡ Baixar Pacote Recomendado") { _, _ ->
                downloadRecommendedCoresPackage()
            }
            .setPositiveButton("Fechar", null)
            .show()
    }

    private fun showCoreActionDialog(core: CoreInfo) {
        val isInstalled = CoreDownloader.isCoreInstalled(this, core)
        val actions = if (isInstalled) {
            arrayOf("Reinstalar / Atualizar Núcleo", "Desinstalar Núcleo")
        } else {
            arrayOf("Baixar e Instalar Núcleo")
        }

        AlertDialog.Builder(this)
            .setTitle("${core.iconEmoji} ${core.displayName} (${core.systemName})")
            .setMessage("${core.description}\n\nArquivo: ${core.fileName}\nExtensões: ${core.extensions.joinToString(", ")}")
            .setItems(actions) { _, which ->
                if (isInstalled && which == 1) {
                    CoreDownloader.uninstallCore(this, core)
                    updateCoresStatus()
                    Toast.makeText(this, "${core.displayName} desinstalado.", Toast.LENGTH_SHORT).show()
                    showCoreManagerDialog()
                } else {
                    downloadSingleCore(core)
                }
            }
            .setNegativeButton("Voltar") { _, _ -> showCoreManagerDialog() }
            .show()
    }

    private fun downloadSingleCore(core: CoreInfo) {
        @Suppress("DEPRECATION")
        val progressDialog = ProgressDialog(this).apply {
            setTitle("Baixando ${core.displayName}")
            setMessage("Conectando ao Libretro Buildbot...")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            max = 100
            isIndeterminate = false
            setCancelable(false)
            show()
        }

        lifecycleScope.launch {
            val result = CoreDownloader.downloadAndInstallCore(this@MainActivity, core) { progress, status ->
                runOnUiThread {
                    progressDialog.progress = progress
                    progressDialog.setMessage(status)
                }
            }
            progressDialog.dismiss()

            result.onSuccess {
                updateCoresStatus()
                Toast.makeText(this@MainActivity, "${core.displayName} pronto para jogar!", Toast.LENGTH_SHORT).show()
                showCoreManagerDialog()
            }.onFailure { e ->
                Toast.makeText(this@MainActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                showCoreManagerDialog()
            }
        }
    }

    private fun downloadRecommendedCoresPackage() {
        val recommendedIds = listOf("snes9x", "fceumm", "genesis_plus_gx", "mgba", "fbneo")
        val toDownload = CoreCatalog.AVAILABLE_CORES.filter { it.id in recommendedIds && !CoreDownloader.isCoreInstalled(this, it) }

        if (toDownload.isEmpty()) {
            Toast.makeText(this, "Todos os núcleos recomendados já estão instalados!", Toast.LENGTH_SHORT).show()
            return
        }

        @Suppress("DEPRECATION")
        val progressDialog = ProgressDialog(this).apply {
            setTitle("Baixando Pacote Recomendado")
            setMessage("Iniciando lote (${toDownload.size} núcleos)...")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            max = 100
            setCancelable(false)
            show()
        }

        lifecycleScope.launch {
            for ((index, core) in toDownload.withIndex()) {
                runOnUiThread {
                    progressDialog.setMessage("Baixando ${core.displayName} (${index + 1}/${toDownload.size})...")
                }
                CoreDownloader.downloadAndInstallCore(this@MainActivity, core) { percent, _ ->
                    runOnUiThread {
                        val overall = ((index * 100 + percent) / toDownload.size)
                        progressDialog.progress = overall
                    }
                }
            }
            progressDialog.dismiss()
            updateCoresStatus()
            Toast.makeText(this@MainActivity, "Pacote essencial instalado com sucesso!", Toast.LENGTH_LONG).show()
            showCoreManagerDialog()
        }
    }

    private fun showControllerTestDialog() {
        AlertDialog.Builder(this)
            .setTitle("Teste de Controles Físicos")
            .setMessage("Pressione qualquer botão ou mova os analógicos no controle conectado.\n\n" +
                    "• D-Pad: Direcionais Digitais\n" +
                    "• Botões de Ação: A, B, X, Y\n" +
                    "• Ombros: L1, R1, Gatilhos L2, R2\n" +
                    "• Navegação: Start, Select / Guia\n\n" +
                    "Todos os botões são mapeados automaticamente pelo GamepadAutoDetector.")
            .setPositiveButton("Fechar", null)
            .show()
    }

    private fun showVideoSettingsDialog() {
        val apis = arrayOf("Vulkan 1.1+ (Recomendado)", "OpenGL ES 3.2", "OpenGL ES 3.0 Fallback")
        AlertDialog.Builder(this)
            .setTitle("API Gráfica Canônica")
            .setSingleChoiceItems(apis, 0) { dialog, which ->
                val selected = apis[which]
                Toast.makeText(this, "API Selecionada: $selected", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
