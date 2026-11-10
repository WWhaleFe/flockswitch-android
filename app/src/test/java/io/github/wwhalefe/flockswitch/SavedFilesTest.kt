package io.github.wwhalefe.flockswitch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The list of saved settings files: the limit, the order, renaming. */
class SavedFilesTest {
    private fun file(n: Int, at: Long = n.toLong()) = SavedFile("content://x/document/$n", "flockswitch-settings-$n.json", at)

    @Test
    fun atMostTenAreSaved() {
        assertEquals(10, SettingsFile.MAX_SAVED)
        assertTrue(SettingsFile.canSave(0))
        assertTrue(SettingsFile.canSave(9))
        assertFalse(SettingsFile.canSave(10))
        assertFalse(SettingsFile.canSave(11))
        var list = emptyList<SavedFile>()
        for (n in 1..10) {
            assertTrue(SettingsFile.canSave(list.size))
            list = SettingsFile.added(list, file(n))
        }
        assertEquals(10, list.size)
        assertFalse(SettingsFile.canSave(list.size))
        // Saved over one of them: still ten, that one now the newest.
        val again = SettingsFile.added(list, file(3, at = 99))
        assertEquals(10, again.size)
        assertEquals(file(3, at = 99), SettingsFile.sorted(again).first())
    }

    @Test
    fun newestFirstAndKept() {
        val list = listOf(file(1, 100), file(2, 300), file(3, 200))
        assertEquals(listOf(2, 3, 1), SettingsFile.sorted(list).map { it.uri.substringAfterLast('/').toInt() })
        assertEquals(list, SettingsFile.decodeList(SettingsFile.encodeList(list)))
        assertEquals(emptyList<SavedFile>(), SettingsFile.decodeList(null))
        assertEquals(emptyList<SavedFile>(), SettingsFile.decodeList("not json"))
        // The same document twice, or one without a URI, is dropped.
        val messy = SettingsFile.encodeList(listOf(file(1), file(1), SavedFile("", "x.json", 5)))
        assertEquals(listOf(file(1)), SettingsFile.decodeList(messy))
    }

    @Test
    fun shownWithoutJson() {
        assertEquals("flockswitch-settings-1", SettingsFile.shownName("flockswitch-settings-1.json"))
        assertEquals("work", SettingsFile.shownName("work.JSON"))
        assertEquals("notes.txt", SettingsFile.shownName("notes.txt"))
        assertEquals(".json", SettingsFile.shownName(".json"))
    }

    @Test
    fun renameChecks() {
        assertNull(SettingsFile.renameProblem("Office"))
        assertNull(SettingsFile.renameProblem("  집 노트북 설정  "))
        assertNull(SettingsFile.renameProblem("work.json"))
        assertNull(SettingsFile.renameProblem("a".repeat(60)))
        assertNull(SettingsFile.renameProblem("a".repeat(60) + ".json"))
        assertEquals(RenameProblem.Empty, SettingsFile.renameProblem(""))
        assertEquals(RenameProblem.Empty, SettingsFile.renameProblem("   "))
        assertEquals(RenameProblem.Empty, SettingsFile.renameProblem(".json"))
        assertEquals(RenameProblem.Empty, SettingsFile.renameProblem(".."))
        assertEquals(RenameProblem.TooLong, SettingsFile.renameProblem("a".repeat(61)))
        for (c in listOf('/', '\\', ':', '*', '?', '"', '<', '>', '|', '\n', '\u0000')) {
            assertEquals("$c", RenameProblem.BadCharacter, SettingsFile.renameProblem("a${c}b"))
        }
        // Not the name of another saved file, whatever the case.
        val others = listOf("Office.json", "home.json")
        assertEquals(RenameProblem.Taken, SettingsFile.renameProblem("office", others))
        assertEquals(RenameProblem.Taken, SettingsFile.renameProblem("HOME.json", others))
        assertNull(SettingsFile.renameProblem("Office 2", others))
    }

    @Test
    fun renamedKeepsJson() {
        assertEquals("Office.json", SettingsFile.renamedFileName("Office"))
        assertEquals("Office.json", SettingsFile.renamedFileName("  Office  "))
        assertEquals("work.json", SettingsFile.renamedFileName("work.json"))
        assertEquals("work.json", SettingsFile.renamedFileName("work.JSON"))
        assertEquals("v1.2.json", SettingsFile.renamedFileName("v1.2"))
    }
}
