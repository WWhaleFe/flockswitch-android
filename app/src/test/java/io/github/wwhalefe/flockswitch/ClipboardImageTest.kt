package io.github.wwhalefe.flockswitch

import io.github.wwhalefe.flockswitch.net.ClipboardImage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Images from the master: the size guard and which cached files are kept. */
class ClipboardImageTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun staged(bytes: Int = 4) = tmp.newFile().also { it.writeBytes(ByteArray(bytes) { 1 }) }

    @Test
    fun sizeGuard() {
        assertFalse(ClipboardImage.fits(0))
        assertTrue(ClipboardImage.fits(1))
        assertTrue(ClipboardImage.fits(ClipboardImage.MAX_BYTES))
        assertFalse(ClipboardImage.fits(ClipboardImage.MAX_BYTES + 1))
    }

    @Test
    fun emptyOrMissingStagedFileIsDropped() {
        val dir = File(tmp.root, "cache")
        assertNull(ClipboardImage.store(staged(0), dir, 1))
        assertNull(ClipboardImage.store(File(tmp.root, "none.png"), dir, 1))
        assertEquals(0, dir.listFiles()?.size ?: 0)
    }

    @Test
    fun storeMovesTheFileAndKeepsOnlyTheNewestTwo() {
        val dir = File(tmp.root, "cache")
        val made = (1..4).map {
            val f = assertNotNull(ClipboardImage.store(staged(), dir, it.toLong())).let { _ -> File(dir, "clip-$it.png") }
            f.setLastModified(1000L * it)
            f
        }
        val left = dir.listFiles()!!.map { it.name }.sorted()
        assertEquals(listOf("clip-3.png", "clip-4.png"), left)
        assertFalse(made[0].exists())
    }

    @Test
    fun sameTimestampDoesNotOverwrite() {
        val dir = File(tmp.root, "cache")
        val a = ClipboardImage.store(staged(), dir, 5)
        val b = ClipboardImage.store(staged(), dir, 5)
        assertNotNull(a); assertNotNull(b)
        assertTrue(a != b)
        assertEquals(2, dir.listFiles()!!.size)
    }
}
