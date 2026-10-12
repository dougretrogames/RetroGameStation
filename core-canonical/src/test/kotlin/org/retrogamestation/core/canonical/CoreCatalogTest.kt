package org.retrogamestation.core.canonical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.retrogamestation.core.canonical.coremanager.CoreCatalog
import org.retrogamestation.core.canonical.coremanager.CoreDownloader

class CoreCatalogTest {

    @Test
    fun testCoreCatalogCompleteness() {
        val cores = CoreCatalog.AVAILABLE_CORES
        assertTrue("Catalog must contain at least 10 major retro gaming cores", cores.size >= 10)

        for (core in cores) {
            assertTrue("Core id must not be empty", core.id.isNotEmpty())
            assertTrue("Core displayName must not be empty", core.displayName.isNotEmpty())
            assertTrue("Core systemName must not be empty", core.systemName.isNotEmpty())
            assertTrue("Core fileName must end with .so", core.fileName.endsWith(".so"))
            assertTrue("Core zipFileName must end with .so.zip", core.zipFileName.endsWith(".so.zip"))
            assertTrue("Core extensions must not be empty", core.extensions.isNotEmpty())
            assertTrue("Each extension must start with .", core.extensions.all { it.startsWith(".") })
        }
    }

    @Test
    fun testCoreExtensionMatching() {
        // SNES
        val snesCore = CoreCatalog.findCoreForExtension(".sfc")
        assertNotNull(snesCore)
        assertEquals("snes9x", snesCore?.id)

        // NES
        val nesCore = CoreCatalog.findCoreForExtension(".nes")
        assertNotNull(nesCore)
        assertEquals("fceumm", nesCore?.id)

        // Genesis
        val mdCore = CoreCatalog.findCoreForExtension(".md")
        assertNotNull(mdCore)
        assertEquals("genesis_plus_gx", mdCore?.id)

        // GBA
        val gbaCore = CoreCatalog.findCoreForExtension(".gba")
        assertNotNull(gbaCore)
        assertEquals("mgba", gbaCore?.id)

        // GB
        val gbCore = CoreCatalog.findCoreForExtension(".gb")
        assertNotNull(gbCore)
        assertEquals("gambatte", gbCore?.id)

        // Arcade
        val arcadeCore = CoreCatalog.findCoreForExtension(".zip")
        assertNotNull(arcadeCore)

        // Atari 2600
        val atariCore = CoreCatalog.findCoreForExtension(".a26")
        assertNotNull(atariCore)
        assertEquals("stella", atariCore?.id)

        // PCE
        val pceCore = CoreCatalog.findCoreForExtension(".pce")
        assertNotNull(pceCore)
        assertEquals("mednafen_pce_fast", pceCore?.id)

        // NDS
        val ndsCore = CoreCatalog.findCoreForExtension(".nds")
        assertNotNull(ndsCore)
        assertEquals("melonds", ndsCore?.id)
    }

    @Test
    fun testFindCoreById() {
        val core = CoreCatalog.findCoreById("pcsx_rearmed")
        assertNotNull(core)
        assertEquals("PCSX ReARMed", core?.displayName)
        assertEquals("psx", core?.systemId)
    }

    @Test
    fun testResolveDeviceAbi() {
        val abi = CoreDownloader.resolveDeviceAbi()
        assertTrue("ABI must be one of arm64-v8a, x86_64, or armeabi-v7a",
            abi in listOf("arm64-v8a", "x86_64", "armeabi-v7a"))
    }
}
