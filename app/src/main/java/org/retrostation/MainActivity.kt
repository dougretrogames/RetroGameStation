package org.retrostation

import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.appcompat.app.AppCompatActivity
import org.retrostation.core.engine.IGameEngine
import org.retrostation.core.model.EmulationCore
import org.retrostation.core.model.RomDescriptor
import org.retrostation.engine.libretro.LibretroEngineBridge
import org.retrostation.engine.mupen64plus.Mupen64PlusEngineBridge
import org.retrostation.engine.ppsspp.PPSSPPEngineBridge

class MainActivity : AppCompatActivity(), SurfaceHolder.Callback {

    private val TAG = "RetroMainActivity"

    private lateinit var surfaceView: SurfaceView
    private var activeEngine: IGameEngine? = null

    // Concrete engines available through multi-module build
    private val libretroBridge by lazy { LibretroEngineBridge() }
    private val ppssppBridge by lazy { PPSSPPEngineBridge() }
    private val mupen64PlusBridge by lazy { Mupen64PlusEngineBridge() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        surfaceView = SurfaceView(this)
        setContentView(surfaceView)
        surfaceView.holder.addCallback(this)

        Log.i(TAG, "MainActivity initialized with multi-engine support.")
    }

    fun switchEngine(core: EmulationCore) {
        activeEngine?.destroy()
        activeEngine = when (core) {
            EmulationCore.LIBRETRO -> libretroBridge
            EmulationCore.PPSSPP -> ppssppBridge
            EmulationCore.MUPEN64PLUS -> mupen64PlusBridge
        }

        val holder = surfaceView.holder
        if (holder.surface.isValid) {
            activeEngine?.initialize(44100, holder.surface)
        }
        Log.i(TAG, "Switched active engine to: ${core.displayName}")
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (activeEngine == null) {
            // Default to Libretro core bridge
            switchEngine(EmulationCore.LIBRETRO)
        } else {
            activeEngine?.initialize(44100, holder.surface)
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d(TAG, "surfaceChanged: format=$format, width=$width, height=$height")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        activeEngine?.pause()
        activeEngine?.setSurface(null)
    }

    override fun onResume() {
        super.onResume()
        activeEngine?.resume()
    }

    override fun onPause() {
        super.onPause()
        activeEngine?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        activeEngine?.destroy()
        activeEngine = null
    }
}
