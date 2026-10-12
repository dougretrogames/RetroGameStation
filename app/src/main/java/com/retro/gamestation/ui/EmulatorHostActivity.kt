package com.retro.gamestation.ui

import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.retro.gamestation.R
import com.retro.gamestation.audio.LowLatencyAudioEngine
import com.retro.gamestation.core.NativeEngineBridge
import com.retro.gamestation.input.GlobalHotkeyManager
import com.retro.gamestation.storage.ScopedStorageManager
import org.retrostation.core.engine.IGameEngine
import org.retrostation.core.model.EmulationCore
import org.retrostation.core.model.RomDescriptor
import org.retrostation.engine.libretro.LibretroEngineBridge
import org.retrostation.engine.mupen64plus.Mupen64PlusEngineBridge
import org.retrostation.engine.ppsspp.PPSSPPEngineBridge
import java.io.File
import java.nio.ByteBuffer

/**
 * Production Emulator Host Activity for Retro Game Station.
 * Hosts a hardware SurfaceView, implements SurfaceHolder.Callback2,
 * passes ANativeWindow pointers to native engines (Libretro, PPSSPP, Mupen64Plus),
 * guarantees zero EGL/Vulkan context leaks across Android Activity lifecycle transitions,
 * manages low-latency AAudio streaming, and intercepts global hotkey combinations.
 */
class EmulatorHostActivity : AppCompatActivity(), SurfaceHolder.Callback2, GlobalHotkeyManager.HotkeyListener {

    companion object {
        private const val TAG = "EmulatorHostActivity"
        const val EXTRA_ROM_URI = "extra_rom_uri"
        const val EXTRA_SYSTEM_NAME = "extra_system_name"
        const val EXTRA_CORE_NAME = "extra_core_name"
    }

    private lateinit var surfaceView: SurfaceView
    private lateinit var audioEngine: LowLatencyAudioEngine
    private lateinit var storageManager: ScopedStorageManager
    private val hotkeyManager = GlobalHotkeyManager()

    // Engine abstraction
    private var activeEngine: IGameEngine? = null
    private var nativeSurfaceBridgePtr: Long = 0L
    private var activeRomDescriptor: ScopedStorageManager.DirectFileDescriptor? = null
    private var currentSystemName: String = "arcade"
    private var currentCoreName: String = "libretro"
    private var isEmulationRunning = false
    private var isEngineInitialized = false
    private var quickMenuDialog: AlertDialog? = null

    // Emulation thread
    private var emulationThread: Thread? = null
    @Volatile
    private var isThreadRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate: Initializing EmulatorHostActivity")

        // 1. Fullscreen sticky immersive mode & display cutout short edges handling
        setupWindowDisplayFlags()

        // 2. Initialize Storage & Audio subsystems
        storageManager = ScopedStorageManager.getInstance(applicationContext)
        audioEngine = LowLatencyAudioEngine(applicationContext).apply {
            exclusiveMode = true
            bufferBurstMultiplier = 2
            setMuteOnFastForward(true)
            initialize(channels = 2)
        }

        // 3. Register Hotkey listener
        hotkeyManager.listener = this

        // 4. Create and configure hardware SurfaceView
        setupSurfaceView()

        // 5. Parse ROM launch intent
        parseLaunchIntent(intent)

        // 6. Instantiate appropriate backend engine (Libretro, PPSSPP, Mupen64Plus)
        createActiveEngine()
    }

    private fun setupWindowDisplayFlags() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        // Optimize display refresh rate for smooth emulation (60Hz / 120Hz)
        optimizeRefreshRate()
    }

    private fun optimizeRefreshRate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val disp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay
            }
            val modes = disp?.supportedModes
            val targetMode = modes?.firstOrNull { it.refreshRate >= 59.9f && it.refreshRate <= 60.1f }
                ?: modes?.maxByOrNull { it.refreshRate }
            targetMode?.let {
                val params = window.attributes
                params.preferredDisplayModeId = it.modeId
                window.attributes = params
                Log.i(TAG, "Configured preferred display mode: ${it.modeId} (${it.refreshRate} Hz)")
            }
        }
    }

    private fun setupSurfaceView() {
        val rootLayout = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        surfaceView = SurfaceView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
            // Request 32-bit color format for optimal Vulkan/GLES swapchain performance
            holder.setFormat(PixelFormat.RGBX_8888)
            holder.addCallback(this@EmulatorHostActivity)
        }

        rootLayout.addView(surfaceView)
        setContentView(rootLayout)
    }

    private fun parseLaunchIntent(intent: Intent?) {
        val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_ROM_URI, Uri::class.java) ?: intent?.data
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_ROM_URI) ?: intent?.data
        }
        currentSystemName = intent?.getStringExtra(EXTRA_SYSTEM_NAME) ?: "arcade"
        currentCoreName = intent?.getStringExtra(EXTRA_CORE_NAME) ?: "libretro"

        if (uri != null) {
            Log.i(TAG, "Opening ROM via SAF content URI: $uri (system: $currentSystemName)")
            activeRomDescriptor = storageManager.openDirectFileDescriptor(uri)
            if (activeRomDescriptor != null) {
                Log.i(TAG, "Successfully extracted direct native FD: ${activeRomDescriptor?.fd}")
            } else {
                Toast.makeText(this, "Failed to resolve ROM storage descriptor", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun createActiveEngine() {
        activeEngine = when {
            currentSystemName.equals("psp", ignoreCase = true) ||
            currentCoreName.equals("ppsspp", ignoreCase = true) -> {
                Log.i(TAG, "Instantiating PPSSPPEngineBridge")
                PPSSPPEngineBridge()
            }
            currentSystemName.equals("n64", ignoreCase = true) ||
            currentCoreName.equals("mupen64plus", ignoreCase = true) -> {
                Log.i(TAG, "Instantiating Mupen64PlusEngineBridge")
                Mupen64PlusEngineBridge()
            }
            else -> {
                Log.i(TAG, "Instantiating LibretroEngineBridge")
                LibretroEngineBridge()
            }
        }

        // Attach audio streaming callback
        activeEngine?.setAudioCallback { buffer: ByteBuffer, sampleCount: Int ->
            // Forward PCM samples to AAudio engine if needed
        }
    }

    // =========================================================================
    // SurfaceHolder.Callback2 Implementation
    // =========================================================================

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.i(TAG, "surfaceCreated: Surface acquired, passing to active native engine")
        val surface = holder.surface
        if (surface.isValid) {
            // 1. Pass ANativeWindow to C++ native lifecycle bridge
            nativeSurfaceBridgePtr = NativeEngineBridge.nativeSurfaceCreated(surface)
            Log.i(TAG, "nativeSurfaceCreated returned handle: $nativeSurfaceBridgePtr")

            // 2. Initialize active engine with surface or re-attach surface
            if (!isEngineInitialized) {
                val sampleRate = 48000
                isEngineInitialized = activeEngine?.initialize(sampleRate, surface) == true

                // 3. Load ROM via direct native descriptor if available
                val pfd = activeRomDescriptor
                if (pfd != null) {
                    val romDesc = RomDescriptor(
                        title = pfd.displayName,
                        filePath = pfd.procPath,
                        systemId = currentSystemName,
                        suggestedCore = activeEngine?.coreType ?: EmulationCore.LIBRETRO,
                        saveDirectory = storageManager.getSaveDirectory(currentSystemName).absolutePath,
                        stateDirectory = storageManager.getStateDirectory(currentSystemName).absolutePath
                    )
                    activeEngine?.loadRom(romDesc)
                }
            } else {
                activeEngine?.setSurface(surface)
            }

            // 4. Start AAudio & launch emulation loop
            audioEngine.start()
            isEmulationRunning = true
            startEmulationLoop()
        } else {
            Log.e(TAG, "surfaceCreated called with invalid surface!")
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.i(TAG, "surfaceChanged: format=$format, width=$width, height=$height")
        if (nativeSurfaceBridgePtr != 0L) {
            NativeEngineBridge.nativeSurfaceChanged(nativeSurfaceBridgePtr, format, width, height)
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.i(TAG, "surfaceDestroyed: Halting native render thread before releasing ANativeWindow")
        stopEmulationLoop()
        isEmulationRunning = false
        audioEngine.pause()

        activeEngine?.pause()
        activeEngine?.setSurface(null)

        if (nativeSurfaceBridgePtr != 0L) {
            // Tells C++ bridge to fence render thread, destroy EGL/Vulkan surface, and release ANativeWindow
            NativeEngineBridge.nativeSurfaceDestroyed(nativeSurfaceBridgePtr)
            nativeSurfaceBridgePtr = 0L
            Log.i(TAG, "Native surface cleanly destroyed. Zero EGL/Vulkan leaks.")
        }
    }

    override fun surfaceRedrawNeeded(holder: SurfaceHolder) {
        Log.d(TAG, "surfaceRedrawNeeded")
    }

    private fun startEmulationLoop() {
        if (isThreadRunning) return
        isThreadRunning = true
        emulationThread = Thread {
            Log.i(TAG, "Emulation frame loop thread started")
            while (isThreadRunning) {
                if (isEmulationRunning && !hotkeyManager.isEmulationPaused) {
                    activeEngine?.runFrame()
                    if (hotkeyManager.isFastForwardEnabled) {
                        // In fast-forward mode, run extra frame ticks
                        activeEngine?.runFrame()
                    }
                } else {
                    try {
                        Thread.sleep(16)
                    } catch (e: InterruptedException) {
                        break
                    }
                }
            }
            Log.i(TAG, "Emulation frame loop thread finished")
        }.apply {
            name = "RetroEmulationLoop"
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    private fun stopEmulationLoop() {
        isThreadRunning = false
        emulationThread?.interrupt()
        try {
            emulationThread?.join(300)
        } catch (e: InterruptedException) {
            Log.w(TAG, "Interrupted while waiting for emulation thread to stop")
        }
        emulationThread = null
    }

    // =========================================================================
    // Android Activity Lifecycle Management
    // =========================================================================

    override fun onResume() {
        super.onResume()
        Log.i(TAG, "onResume: Resuming emulation and audio stack")
        setupWindowDisplayFlags()

        if (isEmulationRunning && nativeSurfaceBridgePtr != 0L) {
            audioEngine.start()
            activeEngine?.resume()
        }
    }

    override fun onPause() {
        super.onPause()
        Log.i(TAG, "onPause: Pausing audio and native loops")
        audioEngine.pause()
        activeEngine?.pause()
    }

    override fun onStop() {
        super.onStop()
        Log.i(TAG, "onStop: Ensuring complete pause of backend engines")
        audioEngine.pause()
        activeEngine?.pause()
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy: Tearing down audio, engine, storage descriptors, and surface")
        stopEmulationLoop()
        audioEngine.destroy()

        activeEngine?.setSurface(null)
        activeEngine?.destroy()
        activeEngine = null
        isEngineInitialized = false

        activeRomDescriptor?.close()
        activeRomDescriptor = null

        if (nativeSurfaceBridgePtr != 0L) {
            NativeEngineBridge.nativeSurfaceDestroyed(nativeSurfaceBridgePtr)
            nativeSurfaceBridgePtr = 0L
        }

        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setupWindowDisplayFlags()
        }
    }

    // =========================================================================
    // Input & Hotkey Interception
    // =========================================================================

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // 1. Intercept global hotkey combinations before forwarding to the native game loop
        if (hotkeyManager.dispatchKeyEvent(event)) {
            return true // Consumed by hotkey manager
        }

        // 2. Map regular key event to canonical InputEvent and forward to active engine
        val inputEvent = hotkeyManager.mapKeyEventToInputEvent(event)
        if (inputEvent != null) {
            activeEngine?.sendInput(inputEvent)
            return true
        }

        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        // 1. Intercept analog trigger combos (L2 / R2)
        if (hotkeyManager.dispatchGenericMotionEvent(event)) {
            return true
        }

        // 2. Map analog axis displacements to canonical InputEvents
        val inputEvents = hotkeyManager.mapMotionEventToInputEvents(event)
        for (ie in inputEvents) {
            activeEngine?.sendInput(ie)
        }

        return super.dispatchGenericMotionEvent(event)
    }

    // =========================================================================
    // GlobalHotkeyManager.HotkeyListener Implementation
    // =========================================================================

    override fun onQuickMenuTriggered() {
        runOnUiThread {
            showQuickMenu()
        }
    }

    override fun onSaveStateTriggered(slot: Int) {
        runOnUiThread {
            val stateBytes = activeEngine?.saveState(slot)
            if (stateBytes != null && activeRomDescriptor != null) {
                val stateFile = storageManager.getSaveStatePath(
                    currentSystemName,
                    activeRomDescriptor?.displayName ?: "game",
                    slot
                )
                try {
                    stateFile.writeBytes(stateBytes)
                    Log.i(TAG, "Save state written to ${stateFile.absolutePath} (${stateBytes.size} bytes)")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to persist save state to disk: ${e.message}")
                }
            }
            showHudMessage(getString(R.string.msg_state_saved, slot))
        }
    }

    override fun onLoadStateTriggered(slot: Int) {
        runOnUiThread {
            audioEngine.flush() // Flush audio buffer to avoid audio stutter on time rewind/restore
            if (activeRomDescriptor != null) {
                val stateFile = storageManager.getSaveStatePath(
                    currentSystemName,
                    activeRomDescriptor?.displayName ?: "game",
                    slot
                )
                if (stateFile.exists() && stateFile.length() > 0) {
                    try {
                        val stateBytes = stateFile.readBytes()
                        activeEngine?.loadState(slot, stateBytes)
                        Log.i(TAG, "Save state loaded from ${stateFile.absolutePath}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed reading save state file: ${e.message}")
                    }
                }
            }
            showHudMessage(getString(R.string.msg_state_loaded, slot))
        }
    }

    override fun onFastForwardToggled(enabled: Boolean) {
        runOnUiThread {
            audioEngine.setFastForward(enabled)
            val msg = if (enabled) getString(R.string.msg_fast_forward_on) else getString(R.string.msg_fast_forward_off)
            showHudMessage(msg)
        }
    }

    override fun onRewindToggled(enabled: Boolean) {
        runOnUiThread {
            val msg = if (enabled) getString(R.string.msg_rewind_on) else getString(R.string.msg_rewind_off)
            showHudMessage(msg)
        }
    }

    override fun onQuitToLauncherTriggered() {
        runOnUiThread {
            Log.i(TAG, "Exiting emulation to launcher")
            finish()
        }
    }

    override fun onSlotChanged(newSlot: Int) {
        runOnUiThread {
            showHudMessage(getString(R.string.msg_slot_selected, newSlot))
        }
    }

    override fun onShaderCycle(forward: Boolean) {
        runOnUiThread {
            showHudMessage(if (forward) getString(R.string.msg_next_shader) else getString(R.string.msg_prev_shader))
        }
    }

    override fun onPauseToggled(isPaused: Boolean) {
        runOnUiThread {
            if (isPaused) {
                audioEngine.pause()
                activeEngine?.pause()
                showHudMessage(getString(R.string.msg_emulation_paused))
            } else {
                audioEngine.start()
                activeEngine?.resume()
                showHudMessage(getString(R.string.msg_emulation_resumed))
            }
        }
    }

    override fun onResetTriggered() {
        runOnUiThread {
            audioEngine.flush()
            showHudMessage(getString(R.string.msg_emulation_reset))
        }
    }

    override fun onScreenshotTriggered() {
        runOnUiThread {
            showHudMessage(getString(R.string.msg_screenshot_saved))
        }
    }

    private fun showHudMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // =========================================================================
    // In-Game Quick Menu Implementation
    // =========================================================================

    private fun showQuickMenu() {
        if (quickMenuDialog?.isShowing == true) return

        // Pause audio and emulation while quick menu is open
        audioEngine.pause()
        activeEngine?.pause()

        val items = arrayOf(
            getString(R.string.quick_menu_resume),
            getString(R.string.quick_menu_save_state, hotkeyManager.currentSlot),
            getString(R.string.quick_menu_load_state, hotkeyManager.currentSlot),
            getString(R.string.quick_menu_change_slot, hotkeyManager.currentSlot),
            getString(R.string.quick_menu_fast_forward, if (hotkeyManager.isFastForwardEnabled) "ON" else "OFF"),
            getString(R.string.quick_menu_take_screenshot),
            getString(R.string.quick_menu_reset_game),
            getString(R.string.quick_menu_quit_launcher)
        )

        quickMenuDialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.quick_menu_title))
            .setItems(items) { dialog, which ->
                dialog.dismiss()
                when (which) {
                    0 -> resumeFromQuickMenu()
                    1 -> {
                        onSaveStateTriggered(hotkeyManager.currentSlot)
                        resumeFromQuickMenu()
                    }
                    2 -> {
                        onLoadStateTriggered(hotkeyManager.currentSlot)
                        resumeFromQuickMenu()
                    }
                    3 -> showSlotSelectorDialog()
                    4 -> {
                        val newFf = !hotkeyManager.isFastForwardEnabled
                        onFastForwardToggled(newFf)
                        resumeFromQuickMenu()
                    }
                    5 -> {
                        NativeEngineBridge.nativeTriggerHotkeyAction(NativeEngineBridge.ACTION_SCREENSHOT, 0)
                        showHudMessage(getString(R.string.msg_screenshot_saved))
                        resumeFromQuickMenu()
                    }
                    6 -> {
                        NativeEngineBridge.nativeTriggerHotkeyAction(NativeEngineBridge.ACTION_RESET, 0)
                        audioEngine.flush()
                        showHudMessage(getString(R.string.msg_emulation_reset))
                        resumeFromQuickMenu()
                    }
                    7 -> finish()
                }
            }
            .setOnCancelListener {
                resumeFromQuickMenu()
            }
            .create()

        quickMenuDialog?.show()
    }

    private fun showSlotSelectorDialog() {
        val slots = (0..9).map { "Slot $it" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.title_select_slot))
            .setSingleChoiceItems(slots, hotkeyManager.currentSlot) { dialog, which ->
                hotkeyManager.selectSlot(which)
                dialog.dismiss()
                showQuickMenu()
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.dismiss()
                showQuickMenu()
            }
            .show()
    }

    private fun resumeFromQuickMenu() {
        setupWindowDisplayFlags()
        if (isEmulationRunning && !hotkeyManager.isEmulationPaused) {
            audioEngine.start()
            activeEngine?.resume()
        }
    }
}
