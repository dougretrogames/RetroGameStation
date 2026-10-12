package org.retrogamestation.core.canonical.injector

import org.retrogamestation.core.canonical.model.CanonicalButton
import org.retrogamestation.core.canonical.model.EmulationConfig

/**
 * Mupen64Plus configuration injector.
 * Generates mupen64plus.cfg with [Core], [Video-General], and [Input-SDL-Control1] sections.
 */
class MupenCfgInjector : ConfigInjector {

    override fun generate(config: EmulationConfig): String {
        val mupen = config.mupen
        val video = config.video
        val system = config.system
        val input = config.input

        val sb = StringBuilder()
        sb.appendLine("# ===========================================")
        sb.appendLine("# RetroGameStation - Mupen64Plus Configuration")
        sb.appendLine("# Generated for: ${config.configId}")
        sb.appendLine("# ===========================================")
        sb.appendLine()

        // [Core]
        sb.appendLine("[Core]")
        sb.appendLine("Version = 1.000000")
        sb.appendLine("OnScreenDisplay = True")
        sb.appendLine("RspPlugin = \"${mupen.rspPlugin}\"")
        sb.appendLine("CountPerOp = ${mupen.counterFactor}")
        sb.appendLine("DisableExtraMem = ${if (mupen.expansionPak) "False" else "True"}")
        sb.appendLine("CurrentStateSlot = ${system.saveStateSlot}")
        sb.appendLine("AutoStateSlotInc = False")
        sb.appendLine()

        // [Video-General]
        sb.appendLine("[Video-General]")
        sb.appendLine("Fullscreen = ${if (video.fullscreen) "True" else "False"}")
        sb.appendLine("VerticalSync = ${if (video.vsync) "True" else "False"}")
        sb.appendLine("Rotate = ${video.rotationDegrees}")
        sb.appendLine()

        // [Input-SDL-Control1]
        sb.appendLine("[Input-SDL-Control1]")
        sb.appendLine("version = 2.000000")
        sb.appendLine("mode = 2")
        sb.appendLine("device = 0")
        sb.appendLine("name = \"${input.profileName}\"")
        sb.appendLine("plugged = True")
        sb.appendLine("plugin = 2")
        sb.appendLine("mouse = False")

        // Analog deadzones (normalized to 32768 max range)
        val deadzoneVal = (input.leftStickDeadzone.inner * 32768).toInt()
        val peakVal = (input.leftStickDeadzone.outer * 32768).toInt()
        sb.appendLine("AnalogDeadzone = \"$deadzoneVal,$deadzoneVal\"")
        sb.appendLine("AnalogPeak = \"$peakVal,$peakVal\"")

        // Standard N64 input mappings
        sb.appendLine("DPad R = \"hat(0 Right) key(22)\"")
        sb.appendLine("DPad L = \"hat(0 Left) key(21)\"")
        sb.appendLine("DPad D = \"hat(0 Down) key(20)\"")
        sb.appendLine("DPad U = \"hat(0 Up) key(19)\"")
        sb.appendLine("Start = \"button(108)\"")
        sb.appendLine("Z Trig = \"button(104) axis(17+)\"")
        sb.appendLine("B Button = \"button(99)\"")
        sb.appendLine("A Button = \"button(96)\"")

        // Right analog stick mapped to N64 C-Buttons
        sb.appendLine("C Button R = \"axis(11+)\"")
        sb.appendLine("C Button L = \"axis(11-)\"")
        sb.appendLine("C Button D = \"axis(14+)\"")
        sb.appendLine("C Button U = \"axis(14-)\"")

        sb.appendLine("R Trig = \"button(103)\"")
        sb.appendLine("L Trig = \"button(102)\"")
        sb.appendLine("Mempak switch = \"\"")
        sb.appendLine("Rumblepak switch = \"\"")
        sb.appendLine("X Axis = \"axis(0-,0+)\"")
        sb.appendLine("Y Axis = \"axis(1-,1+)\"")

        return sb.toString()
    }
}
