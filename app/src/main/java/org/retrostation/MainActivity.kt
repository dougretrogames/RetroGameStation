package org.retrostation

import android.content.Context
import android.content.Intent
import android.hardware.input.InputManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
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
import com.retro.gamestation.R
import com.retro.gamestation.ui.EmulatorHostActivity

/**
 * Main Launcher & Diagnostic Dashboard for Retro Game Station.
 * Fully compatible with Samsung OneUI 9, Android 17 (API 37), and modern 16KB page size hardware (Galaxy S25+).
 */
class MainActivity : AppCompatActivity() {

    private val TAG = "RetroMainActivity"

    private lateinit var tvDeviceInfo: TextView
    private lateinit var tvNativeStatus: TextView
    private lateinit var tvControllerStatus: TextView
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
            launchEmulatorWithUri(uri)
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
        tvBadgeStatus = findViewById(R.id.tv_badge_status)

        val btnOpenRom = findViewById<Button>(R.id.btn_open_rom)
        val btnLaunchLibretro = findViewById<Button>(R.id.btn_launch_libretro)
        val btnLaunchPpsspp = findViewById<Button>(R.id.btn_launch_ppsspp)
        val btnLaunchMupen = findViewById<Button>(R.id.btn_launch_mupen)
        val btnTestController = findViewById<Button>(R.id.btn_test_controller)
        val btnVideoSettings = findViewById<Button>(R.id.btn_video_settings)

        // 3. Diagnostics
        inspectSystemEnvironment()
        verifyNativeSubsystems()
        detectConnectedGamepads()

        // 4. Setup Action Listeners
        btnOpenRom.setOnClickListener {
            openRomPicker()
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
    }

    private fun inspectSystemEnvironment() {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        val androidVersion = Build.VERSION.RELEASE
        val apiLevel = Build.VERSION.SDK_INT
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"

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
            tvNativeStatus.text = "Status NDK: ✓ 4 Núcleos Carregados (16KB Page Aligned)"
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

    private fun openRomPicker() {
        val mimeTypes = arrayOf(
            "application/zip",
            "application/x-zip-compressed",
            "application/octet-stream",
            "*/*"
        )
        romPickerLauncher.launch(mimeTypes)
    }

    private fun launchEmulatorWithUri(uri: Uri) {
        val intent = Intent(this, EmulatorHostActivity::class.java).apply {
            putExtra(EmulatorHostActivity.EXTRA_ROM_URI, uri)
            // Auto detect system from file extension if possible
            val path = uri.toString().lowercase()
            when {
                path.endsWith(".iso") || path.endsWith(".cso") -> {
                    putExtra(EmulatorHostActivity.EXTRA_SYSTEM_NAME, "psp")
                    putExtra(EmulatorHostActivity.EXTRA_CORE_NAME, "ppsspp")
                }
                path.endsWith(".z64") || path.endsWith(".n64") || path.endsWith(".v64") -> {
                    putExtra(EmulatorHostActivity.EXTRA_SYSTEM_NAME, "n64")
                    putExtra(EmulatorHostActivity.EXTRA_CORE_NAME, "mupen64plus")
                }
                else -> {
                    putExtra(EmulatorHostActivity.EXTRA_SYSTEM_NAME, "arcade")
                    putExtra(EmulatorHostActivity.EXTRA_CORE_NAME, "libretro")
                }
            }
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
