package org.retrogamestation.engine.libretro

import org.retrogamestation.core.canonical.input.CanonicalInputState
import org.retrogamestation.core.canonical.injector.LibretroConfigInjector
import org.retrogamestation.core.canonical.model.CanonicalAxis
import org.retrogamestation.core.canonical.model.CanonicalButton
import org.retrogamestation.core.canonical.model.EmulationConfig
import java.io.File
import java.io.IOException

/**
 * Libretro native bridge data structures and JNI state provider.
 * Connects CanonicalInputState directly to Libretro retro_input_state_t polling callbacks.
 */
class LibretroNativeBridge {

    /**
     * Translates the active [CanonicalInputState] to Libretro joypad digital button state.
     * Return 1 if pressed, 0 otherwise.
     */
    fun pollJoypad(state: CanonicalInputState, retroJoypadId: Int): Short {
        val isPressed = when (retroJoypadId) {
            LibretroConfigInjector.RETRO_DEVICE_ID_JOYPAD_B -> state.isButtonPressed(CanonicalButton.B)
            LibretroConfigInjector.RETRO_DEVICE_ID_JOYPAD_Y -> state.isButtonPressed(CanonicalButton.Y)
            LibretroConfigInjector.RETRO_DEVICE_ID_JOYPAD_SELECT -> state.isButtonPressed(CanonicalButton.SELECT)
            LibretroConfigInjector.RETRO_DEVICE_ID_JOYPAD_START -> state.isButtonPressed(CanonicalButton.START)
            LibretroConfigInjector.RETRO_DEVICE_ID_JOYPAD_UP -> state.isButtonPressed(CanonicalButton.DPAD_UP)
            LibretroConfigInjector.RETRO_DEVICE_ID_JOYPAD_DOWN -> state.isButtonPressed(CanonicalButton.DPAD_DOWN)
            LibretroConfigInjector.RETRO_DEVICE_ID_JOYPAD_LEFT -> state.isButtonPressed(CanonicalButton.DPAD_LEFT)
            LibretroConfigInjector.RETRO_DEVICE_ID_JOYPAD_RIGHT -> state.isButtonPressed(CanonicalButton.DPAD_RIGHT)
            LibretroConfigInjector.RETRO_DEVICE_ID_JOYPAD_A -> state.isButtonPressed(CanonicalButton.A)
            LibretroConfigInjector.RETRO_DEVICE_ID_JOYPAD_X -> state.isButtonPressed(CanonicalButton.X)
            LibretroConfigInjector.RETRO_DEVICE_ID_JOYPAD_L -> state.isButtonPressed(CanonicalButton.L1)
            LibretroConfigInjector.RETRO_DEVICE_ID_JOYPAD_R -> state.isButtonPressed(CanonicalButton.R1)
            LibretroConfigInjector.RETRO_DEVICE_ID_JOYPAD_L2 -> state.isButtonPressed(CanonicalButton.L2) || state.triggerLeft >= 0.5f
            LibretroConfigInjector.RETRO_DEVICE_ID_JOYPAD_R2 -> state.isButtonPressed(CanonicalButton.R2) || state.triggerRight >= 0.5f
            LibretroConfigInjector.RETRO_DEVICE_ID_JOYPAD_L3 -> state.isButtonPressed(CanonicalButton.L3)
            LibretroConfigInjector.RETRO_DEVICE_ID_JOYPAD_R3 -> state.isButtonPressed(CanonicalButton.R3)
            else -> false
        }
        return if (isPressed) 1 else 0
    }

    /**
     * Translates analog stick axes to Libretro native range [-0x7fff, 0x7fff].
     */
    fun pollAnalog(state: CanonicalInputState, index: Int, id: Int): Short {
        val normalized = when (index) {
            LibretroConfigInjector.RETRO_DEVICE_INDEX_ANALOG_LEFT -> {
                if (id == LibretroConfigInjector.RETRO_DEVICE_ID_ANALOG_X) state.leftStickX else state.leftStickY
            }
            LibretroConfigInjector.RETRO_DEVICE_INDEX_ANALOG_RIGHT -> {
                if (id == LibretroConfigInjector.RETRO_DEVICE_ID_ANALOG_X) state.rightStickX else state.rightStickY
            }
            else -> 0.0f
        }
        val clamped = normalized.coerceIn(-1.0f, 1.0f)
        return (clamped * 32767.0f).toInt().toShort()
    }
}

/**
 * High-level Libretro Engine configuration adapter.
 */
class LibretroConfigAdapter(
    private val injector: LibretroConfigInjector = LibretroConfigInjector()
) {
    @Throws(IOException::class)
    fun inject(config: EmulationConfig, retroArchDir: File) {
        val cfgFile = File(retroArchDir, "retroarch.cfg")
        injector.injectToFile(config, cfgFile)

        if (config.libretro.coreOptions.isNotEmpty()) {
            val coreOptionsFile = File(retroArchDir, "retroarch-core-options.cfg")
            val coreOptionsContent = injector.generateCoreOptions(config)
            coreOptionsFile.writeText(coreOptionsContent, Charsets.UTF_8)
        }
    }
}
