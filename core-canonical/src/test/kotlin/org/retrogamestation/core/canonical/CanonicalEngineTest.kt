package org.retrogamestation.core.canonical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.retrogamestation.core.canonical.injector.GlideN64IniInjector
import org.retrogamestation.core.canonical.injector.LibretroConfigInjector
import org.retrogamestation.core.canonical.injector.MupenCfgInjector
import org.retrogamestation.core.canonical.injector.PpssppControlsInjector
import org.retrogamestation.core.canonical.injector.PpssppIniInjector
import org.retrogamestation.core.canonical.input.AxisCalibration
import org.retrogamestation.core.canonical.input.DeviceInfo
import org.retrogamestation.core.canonical.input.GamepadAutoDetector
import org.retrogamestation.core.canonical.input.GamepadProfile
import org.retrogamestation.core.canonical.input.GamepadProfileType
import org.retrogamestation.core.canonical.model.AspectRatioMode
import org.retrogamestation.core.canonical.model.CanonicalAxis
import org.retrogamestation.core.canonical.model.CanonicalButton
import org.retrogamestation.core.canonical.model.ConfigOverrideLayer
import org.retrogamestation.core.canonical.model.ConfigTier
import org.retrogamestation.core.canonical.model.EmulationConfig
import org.retrogamestation.core.canonical.model.HierarchicalConfigResolver
import org.retrogamestation.core.canonical.model.HotkeyAction
import org.retrogamestation.core.canonical.model.MasterInputConfig
import org.retrogamestation.core.canonical.model.MasterVideoConfig
import org.retrogamestation.core.canonical.model.RenderingApi
import org.retrogamestation.core.canonical.model.StickDeadzone

class CanonicalEngineTest {

    // -------------------------------------------------------------
    // 1. Canonical Models & Validation
    // -------------------------------------------------------------

    @Test
    fun testMasterVideoConfigDefaultsAndValidation() {
        val config = MasterVideoConfig.default()
        assertEquals(RenderingApi.VULKAN, config.renderingApi)
        assertEquals(AspectRatioMode.CORE_PROVIDED, config.aspectRatio)
        assertEquals(1, config.internalResolutionScale)
        assertTrue(config.vsync)

        // Valid scales: 1 to 10
        val scaled = config.copy(internalResolutionScale = 4)
        assertEquals(4, scaled.internalResolutionScale)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testMasterVideoConfigInvalidResolutionScale() {
        MasterVideoConfig(internalResolutionScale = 11)
    }

    @Test
    fun testMasterInputConfigStandardAndroidDefault() {
        val input = MasterInputConfig.createStandardAndroidDefault()
        assertEquals(96, input.buttonMappings[CanonicalButton.A]?.keyCode)
        assertEquals(97, input.buttonMappings[CanonicalButton.B]?.keyCode)
        assertEquals(99, input.buttonMappings[CanonicalButton.X]?.keyCode)
        assertEquals(100, input.buttonMappings[CanonicalButton.Y]?.keyCode)

        // Hotkey mappings
        assertTrue(input.hotkeyMappings.containsKey(HotkeyAction.EXIT_EMULATOR))
        assertEquals(CanonicalButton.START, input.hotkeyMappings[HotkeyAction.EXIT_EMULATOR]?.triggerButton)
        assertEquals(CanonicalButton.SELECT, input.hotkeyMappings[HotkeyAction.EXIT_EMULATOR]?.modifierButton)
    }

    // -------------------------------------------------------------
    // 2. Hierarchical Config Resolver (Three-tier cascading)
    // -------------------------------------------------------------

    @Test
    fun testThreeTierHierarchicalResolution() {
        val resolver = HierarchicalConfigResolver()

        // Tier 1: Global
        val global = EmulationConfig(
            configId = "global",
            video = MasterVideoConfig(
                renderingApi = RenderingApi.VULKAN,
                internalResolutionScale = 1,
                vsync = true
            )
        )

        // Tier 2: Platform (PSP Platform specifies 2x resolution and GLES3)
        val platformLayer = ConfigOverrideLayer(
            tier = ConfigTier.PLATFORM,
            targetId = "psp",
            renderingApi = RenderingApi.OPENGL_ES3,
            internalResolutionScale = 2,
            ppssppHardwareTransform = true
        )

        // Tier 3: Per-Game Override (God of War needs 4x resolution, 333MHz CPU clock, but inherits GLES3 & Vsync)
        val gameLayer = ConfigOverrideLayer(
            tier = ConfigTier.GAME,
            targetId = "god_of_war_chains_of_olympus",
            internalResolutionScale = 4,
            ppssppPspCpuClock = 333
        )

        val resolved = resolver.resolve(global, platformLayer, gameLayer)

        // Verifications
        assertEquals("god_of_war_chains_of_olympus", resolved.configId)
        assertEquals("psp", resolved.platformId)
        assertEquals("god_of_war_chains_of_olympus", resolved.gameId)

        // Cascaded properties:
        // RenderingApi was overridden at Platform to GLES3, not overridden at Game -> should be GLES3
        assertEquals(RenderingApi.OPENGL_ES3, resolved.video.renderingApi)
        // InternalResolutionScale was overridden at Game to 4 -> should be 4
        assertEquals(4, resolved.video.internalResolutionScale)
        // VSync was defined at Global as true, not overridden -> should be true
        assertTrue(resolved.video.vsync)
        // PPSSPP CPU clock was set at Game -> should be 333
        assertEquals(333, resolved.ppsspp.pspCpuClock)
        // PPSSPP Hardware Transform was set at Platform -> should be true
        assertTrue(resolved.ppsspp.hardwareTransform)

        // JSON Serialization check
        val json = resolver.toJson(resolved)
        assertTrue(json.contains("\"configId\": \"god_of_war_chains_of_olympus\""))
        assertTrue(json.contains("\"internalResolutionScale\": 4"))
        assertTrue(json.contains("\"renderingApi\": \"gles3\""))
    }

    // -------------------------------------------------------------
    // 3. Gamepad Auto-Detection
    // -------------------------------------------------------------

    @Test
    fun testAutoDetectXboxController() {
        val detector = GamepadAutoDetector()
        val info = DeviceInfo(
            vendorId = GamepadAutoDetector.VENDOR_MICROSOFT,
            productId = 0x02EA,
            descriptor = "xbox_one_controller",
            name = "Xbox Wireless Controller",
            sources = 0,
            supportedAxes = setOf(0, 1, 11, 14, 17, 18)
        )

        val profile = detector.detectProfile(info)
        assertEquals(GamepadProfileType.XBOX, profile.profileType)
        assertEquals(CanonicalButton.A, profile.mapKeyCode(GamepadProfile.KEYCODE_BUTTON_A))
        assertEquals(CanonicalButton.B, profile.mapKeyCode(GamepadProfile.KEYCODE_BUTTON_B))
    }

    @Test
    fun testAutoDetectPlayStationController() {
        val detector = GamepadAutoDetector()
        val info = DeviceInfo(
            vendorId = GamepadAutoDetector.VENDOR_SONY,
            productId = 0x0CE6,
            descriptor = "dualsense_ps5",
            name = "Wireless Controller",
            sources = 0,
            supportedAxes = setOf(0, 1, 11, 14, 17, 18)
        )

        val profile = detector.detectProfile(info)
        assertEquals(GamepadProfileType.PLAYSTATION, profile.profileType)
        assertEquals(CanonicalButton.A, profile.mapKeyCode(GamepadProfile.KEYCODE_BUTTON_A)) // Cross
    }

    @Test
    fun testAutoDetectSwitchProWithNintendoSwaps() {
        val detector = GamepadAutoDetector()
        val info = DeviceInfo(
            vendorId = GamepadAutoDetector.VENDOR_NINTENDO,
            productId = 0x2009,
            descriptor = "switch_pro",
            name = "Nintendo Switch Pro Controller",
            sources = 0,
            supportedAxes = setOf(0, 1, 11, 14)
        )

        val profile = detector.detectProfile(info)
        assertEquals(GamepadProfileType.SWITCH_PRO, profile.profileType)
        assertTrue(profile.swapABForNintendo)
        assertTrue(profile.swapXYForNintendo)
        // With Nintendo label swapping active: KEYCODE_BUTTON_A (physical South) maps to CanonicalButton.B
        assertEquals(CanonicalButton.B, profile.mapKeyCode(GamepadProfile.KEYCODE_BUTTON_A))
        assertEquals(CanonicalButton.A, profile.mapKeyCode(GamepadProfile.KEYCODE_BUTTON_B))
    }

    @Test
    fun testAutoDetect8BitDo() {
        val detector = GamepadAutoDetector()
        val info = DeviceInfo(
            vendorId = GamepadAutoDetector.VENDOR_8BITDO,
            productId = 0x2882,
            descriptor = "8bitdo_pro2",
            name = "8BitDo Pro 2",
            sources = 0,
            supportedAxes = setOf(0, 1, 11, 14, 17, 18)
        )

        val profile = detector.detectProfile(info)
        assertEquals(GamepadProfileType.EIGHTBITDO, profile.profileType)
    }

    // -------------------------------------------------------------
    // 4. Axis Calibration & Deadzones
    // -------------------------------------------------------------

    @Test
    fun testAxisCalibrationDeadzoneAndClamping() {
        val deadzone = StickDeadzone(inner = 0.15f, outer = 0.90f, curve = 1.0f)

        // Inside inner deadzone -> must be 0.0
        val belowInner = AxisCalibration.calibrate1D(0.10f, deadzone)
        assertEquals(0.0f, belowInner, 0.001f)

        val belowInnerNeg = AxisCalibration.calibrate1D(-0.14f, deadzone)
        assertEquals(0.0f, belowInnerNeg, 0.001f)

        // Beyond outer deadzone -> must be clamped to 1.0 or -1.0
        val beyondOuter = AxisCalibration.calibrate1D(0.95f, deadzone)
        assertEquals(1.0f, beyondOuter, 0.001f)

        val beyondOuterNeg = AxisCalibration.calibrate1D(-0.95f, deadzone)
        assertEquals(-1.0f, beyondOuterNeg, 0.001f)

        // Midpoint check: (0.525 - 0.15) / (0.90 - 0.15) = 0.375 / 0.75 = 0.5
        val mid = AxisCalibration.calibrate1D(0.525f, deadzone)
        assertEquals(0.5f, mid, 0.001f)
    }

    @Test
    fun testRadialDeadzonePreservesAngle() {
        val deadzone = StickDeadzone(inner = 0.20f, outer = 0.90f, curve = 1.0f)
        // 45 degree angle: X = 0.5, Y = 0.5
        val (outX, outY) = AxisCalibration.calibrate2DRadial(0.5f, 0.5f, deadzone)
        assertTrue(outX > 0.0f)
        assertTrue(outY > 0.0f)
        assertEquals(outX, outY, 0.0001f) // Symmetry preserved
    }

    // -------------------------------------------------------------
    // 5. Injector Serializations
    // -------------------------------------------------------------

    @Test
    fun testLibretroConfigInjector() {
        val injector = LibretroConfigInjector()
        val config = EmulationConfig(
            configId = "snes_test",
            video = MasterVideoConfig(
                renderingApi = RenderingApi.VULKAN,
                aspectRatio = AspectRatioMode.ORIGINAL_4_3,
                vsync = true
            )
        )
        val retroCfg = injector.generate(config)
        assertTrue(retroCfg.contains("video_driver = \"vulkan\""))
        assertTrue(retroCfg.contains("aspect_ratio_index = \"0\""))
        assertTrue(retroCfg.contains("input_player1_a_btn = \"96\""))
    }

    @Test
    fun testPpssppInjectors() {
        val iniInjector = PpssppIniInjector()
        val controlsInjector = PpssppControlsInjector()

        val config = EmulationConfig(
            configId = "psp_test",
            video = MasterVideoConfig(
                renderingApi = RenderingApi.VULKAN,
                internalResolutionScale = 3
            )
        )

        val ini = iniInjector.generate(config)
        assertTrue(ini.contains("[Graphics]"))
        assertTrue(ini.contains("RenderingBackend = 3"))
        assertTrue(ini.contains("InternalResolution = 3"))
        assertTrue(ini.contains("[System]"))
        assertTrue(ini.contains("FastMemory = True"))

        val controls = controlsInjector.generate(config)
        assertTrue(controls.contains("[ControlMapping]"))
        assertTrue(controls.contains("pad1.cross = 1-96"))
        assertTrue(controls.contains("pad1.an.up = 4001-4001"))
    }

    @Test
    fun testMupenInjectors() {
        val cfgInjector = MupenCfgInjector()
        val glideInjector = GlideN64IniInjector()

        val config = EmulationConfig(
            configId = "n64_test",
            video = MasterVideoConfig(
                aspectRatio = AspectRatioMode.WIDE_16_9,
                bilinearFiltering = true
            )
        )

        val cfg = cfgInjector.generate(config)
        assertTrue(cfg.contains("[Input-SDL-Control1]"))
        assertTrue(cfg.contains("AnalogDeadzone"))
        assertTrue(cfg.contains("C Button R = \"axis(11+)\""))

        val glide = glideInjector.generate(config)
        assertTrue(glide.contains("[FrameBufferEmulation]"))
        assertTrue(glide.contains("EnableFBEmulation = 1"))
        assertTrue(glide.contains("aspect = 1"))
    }
}
