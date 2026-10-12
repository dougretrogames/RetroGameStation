package org.retrogamestation.core.canonical

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Validates Samsung OneUI 9, Android 17, and modern 16KB page size compliance.
 */
class ModernAndroidCompatibilityTest {

    private val projectRoot = File(System.getProperty("user.dir") ?: ".").parentFile ?: File(".")

    @Test
    fun testAdaptiveIconResourcesExistAndAreValid() {
        val resDir = File(projectRoot, "app/src/main/res")
        assertTrue("res directory must exist", resDir.exists())

        // 1. Vector components
        val bgXml = File(resDir, "drawable/ic_launcher_background.xml")
        val fgXml = File(resDir, "drawable/ic_launcher_foreground.xml")
        val monoXml = File(resDir, "drawable/ic_launcher_monochrome.xml")
        val bannerXml = File(resDir, "drawable/banner.xml")

        assertTrue("ic_launcher_background.xml must exist", bgXml.exists() && bgXml.length() > 0)
        assertTrue("ic_launcher_foreground.xml must exist", fgXml.exists() && fgXml.length() > 0)
        assertTrue("ic_launcher_monochrome.xml must exist", monoXml.exists() && monoXml.length() > 0)
        assertTrue("banner.xml must exist", bannerXml.exists() && bannerXml.length() > 0)

        // 2. Adaptive icon definitions
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

        // 3. Fallback raster icons
        val densities = listOf("mipmap-mdpi", "mipmap-hdpi", "mipmap-xhdpi", "mipmap-xxhdpi", "mipmap-xxxhdpi")
        for (d in densities) {
            val dDir = File(resDir, d)
            assertTrue("Density directory $d must exist", dDir.exists())
            val png = File(dDir, "ic_launcher.png")
            val pngRound = File(dDir, "ic_launcher_round.png")
            assertTrue("$d/ic_launcher.png must exist", png.exists() && png.length() > 0)
            assertTrue("$d/ic_launcher_round.png must exist", pngRound.exists() && pngRound.length() > 0)
        }
    }

    @Test
    fun testAndroidManifestIconAndOneUiDeclarations() {
        val manifest = File(projectRoot, "app/src/main/AndroidManifest.xml")
        assertTrue("AndroidManifest.xml must exist", manifest.exists())

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
        assertTrue("libs.versions.toml must exist", catalog.exists())

        val text = catalog.readText()
        assertTrue("compileSdk must be configured", text.contains("compileSdk = \"35\""))
        assertTrue("targetSdk must be configured", text.contains("targetSdk = \"35\""))
        assertTrue("minSdk must be 26+", text.contains("minSdk = \"26\""))
        assertTrue("ndk must be r26+", text.contains("ndk = \"26.3.11579264\""))
    }
}
