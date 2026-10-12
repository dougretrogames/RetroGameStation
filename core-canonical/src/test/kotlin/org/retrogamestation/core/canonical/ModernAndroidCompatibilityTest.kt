package org.retrogamestation.core.canonical

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Validates Samsung OneUI 9, Android 17, minSdk 24, and modern 16KB page size compliance.
 */
class ModernAndroidCompatibilityTest {

    private val projectRoot: File by lazy {
        var current: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (current != null && !File(current, "settings.gradle.kts").exists()) {
            current = current.parentFile
        }
        current ?: File(".")
    }

    @Test
    fun testAdaptiveIconResourcesExistAndAreValid() {
        val resDir = File(projectRoot, "app/src/main/res")
        assertTrue("res directory must exist at ${resDir.absolutePath}", resDir.exists())

        // 1. Adaptive icon definitions in mipmap-anydpi-v26
        val anyDpiDir = File(resDir, "mipmap-anydpi-v26")
        val icLauncher = File(anyDpiDir, "ic_launcher.xml")
        val icLauncherRound = File(anyDpiDir, "ic_launcher_round.xml")

        assertTrue("mipmap-anydpi-v26/ic_launcher.xml must exist", icLauncher.exists() && icLauncher.length() > 0)
        assertTrue("mipmap-anydpi-v26/ic_launcher_round.xml must exist", icLauncherRound.exists() && icLauncherRound.length() > 0)

        // Verify adaptive icon contents point to foreground, background and monochrome
        val icContent = icLauncher.readText()
        assertTrue(icContent.contains("@drawable/ic_launcher_background"))
        assertTrue(icContent.contains("@drawable/ic_launcher_foreground"))
        assertTrue(icContent.contains("@drawable/ic_launcher_monochrome"))

        // 2. Banner
        val bannerXml = File(resDir, "drawable/banner.xml")
        assertTrue("banner.xml must exist", bannerXml.exists() && bannerXml.length() > 0)

        // 3. Official NeoStation Drawables (foreground, background, monochrome)
        val testDensity = File(resDir, "drawable-xxhdpi")
        assertTrue("drawable-xxhdpi must exist", testDensity.exists())
        val bgPng = File(testDensity, "ic_launcher_background.png")
        val fgPng = File(testDensity, "ic_launcher_foreground.png")
        val monoPng = File(testDensity, "ic_launcher_monochrome.png")

        assertTrue("ic_launcher_background.png must exist in drawable-xxhdpi", bgPng.exists() && bgPng.length() > 0)
        assertTrue("ic_launcher_foreground.png must exist in drawable-xxhdpi", fgPng.exists() && fgPng.length() > 0)
        assertTrue("ic_launcher_monochrome.png must exist in drawable-xxhdpi", monoPng.exists() && monoPng.length() > 0)

        // 4. Fallback raster icons across all standard densities
        val densities = listOf("mipmap-mdpi", "mipmap-hdpi", "mipmap-xhdpi", "mipmap-xxhdpi", "mipmap-xxxhdpi")
        for (d in densities) {
            val dDir = File(resDir, d)
            assertTrue("Density directory $d must exist", dDir.exists())
            val png = File(dDir, "ic_launcher.png")
            assertTrue("$d/ic_launcher.png must exist", png.exists() && png.length() > 0)
        }
    }

    @Test
    fun testAndroidManifestIconAndOneUiDeclarations() {
        val manifest = File(projectRoot, "app/src/main/AndroidManifest.xml")
        assertTrue("AndroidManifest.xml must exist at ${manifest.absolutePath}", manifest.exists())

        val content = manifest.readText()
        assertTrue("Manifest must declare @mipmap/ic_launcher", content.contains("android:icon=\"@mipmap/ic_launcher\""))
        assertTrue("Manifest must declare @mipmap/ic_launcher_round", content.contains("android:roundIcon=\"@mipmap/ic_launcher_round\""))
        assertTrue("Manifest must declare @drawable/banner", content.contains("android:banner=\"@drawable/banner\""))
        assertTrue("Manifest must enable predictive back callback for OneUI 9", content.contains("android:enableOnBackInvokedCallback=\"true\""))
        assertTrue("Manifest must enable largeHeap for high-DPI emulators", content.contains("android:largeHeap=\"true\""))
    }

    @Test
    fun testCMakeLists16KbPageAlignmentConfigured() {
        val cmakePaths = listOf(
            "app/src/main/cpp/CMakeLists.txt",
            "engine-libretro/CMakeLists.txt",
            "engine-ppsspp/CMakeLists.txt",
            "engine-mupen64plus/CMakeLists.txt"
        )

        for (path in cmakePaths) {
            val cmakeFile = File(projectRoot, path)
            assertTrue("CMakeLists at $path must exist", cmakeFile.exists())
            val text = cmakeFile.readText()
            assertTrue(
                "CMakeLists at $path must include -Wl,-z,max-page-size=16384 for Android 15/16/17 (Galaxy S25+) 16KB page support",
                text.contains("-Wl,-z,max-page-size=16384")
            )
        }
    }

    @Test
    fun testTargetAndCompileSdkInVersionCatalog() {
        val catalog = File(projectRoot, "gradle/libs.versions.toml")
        assertTrue("libs.versions.toml must exist at ${catalog.absolutePath}", catalog.exists())

        val text = catalog.readText()
        assertTrue("compileSdk must be configured", text.contains("compileSdk = \"35\""))
        assertTrue("targetSdk must be configured", text.contains("targetSdk = \"35\""))
        assertTrue("minSdk must be 24", text.contains("minSdk = \"24\""))
        assertTrue("ndk must be r26+", text.contains("ndk = \"26.3.11579264\""))
    }
}
