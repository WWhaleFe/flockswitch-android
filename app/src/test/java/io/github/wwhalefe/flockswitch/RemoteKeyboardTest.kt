package io.github.wwhalefe.flockswitch

import android.view.KeyEvent
import io.github.wwhalefe.flockswitch.net.*
import org.junit.Assert.*
import org.junit.Test

class RemoteKeyboardTest {
    private class Editor : RemoteEditor {
        override var text: String? = "ab😀cd"
        override var start = 4
        override var end = 4
        override var password = false
        override var multiline = true
        var action = 0
        override fun replace(text: String, cursor: Int): Boolean {
            this.text = text; start = cursor; end = cursor; return true
        }
        override fun select(start: Int, end: Int): Boolean { this.start = start; this.end = end; return true }
        override fun move(forward: Boolean, extend: Boolean): Boolean { action = if (forward) 1 else -1; return true }
        override fun context(action: Int): Boolean { this.action = action; return true }
    }

    @Test fun legacyTextReplacesSelectionAndDeletesWholeCodePoints() {
        val e = Editor()
        assertTrue(editRemoteKey(e, KeyEvent.KEYCODE_DEL, 0, null))
        assertEquals("abcd", e.text)
        assertEquals(2, e.start)
        e.end = 4
        assertTrue(editRemoteKey(e, KeyEvent.KEYCODE_A, 0, "A"))
        assertEquals("abA", e.text)
        assertEquals(3, e.start)
        assertTrue(editRemoteKey(e, KeyEvent.KEYCODE_1, 0, "1"))
        assertEquals("abA1", e.text)
    }

    @Test fun legacyNavigationSelectionAndClipboardActions() {
        val e = Editor()
        assertTrue(editRemoteKey(e, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.META_SHIFT_ON, null))
        assertEquals(4, e.start)
        assertEquals(2, e.end)
        assertTrue(editRemoteKey(e, KeyEvent.KEYCODE_DPAD_RIGHT, 0, null))
        assertEquals(4, e.start)
        assertEquals(4, e.end)
        assertTrue(editRemoteKey(e, KeyEvent.KEYCODE_DPAD_DOWN, 0, null))
        assertEquals(1, e.action)
        assertTrue(editRemoteKey(e, KeyEvent.KEYCODE_C, KeyEvent.META_CTRL_ON, null))
        assertEquals(android.view.accessibility.AccessibilityNodeInfo.ACTION_COPY, e.action)
    }

    @Test fun languageChordsNeverBecomeTextOrModifyLegacyEditor() {
        val e = Editor()
        for (meta in listOf(KeyEvent.META_SHIFT_ON, KeyEvent.META_CTRL_ON)) {
            assertTrue(remoteImeChord(KeyEvent.KEYCODE_SPACE, meta))
            assertFalse(editRemoteKey(e, KeyEvent.KEYCODE_SPACE, meta, " "))
            assertEquals("ab😀cd", e.text)
        }
        assertFalse(remoteImeChord(KeyEvent.KEYCODE_SPACE, 0))
        assertTrue(editRemoteKey(e, KeyEvent.KEYCODE_SPACE, 0, " "))
    }

    @Test fun hiddenTextUnknownSelectionAndUnsupportedShortcutsAreNotRewritten() {
        val e = Editor()
        e.password = true
        assertFalse(editRemoteKey(e, KeyEvent.KEYCODE_A, 0, "a"))
        e.password = false
        e.start = -1
        assertFalse(editRemoteKey(e, KeyEvent.KEYCODE_DEL, 0, null))
        e.start = 0
        assertFalse(editRemoteKey(e, KeyEvent.KEYCODE_A, KeyEvent.META_ALT_ON, "a"))
        assertFalse(editRemoteKey(e, KeyEvent.KEYCODE_TAB, 0, null))
        e.multiline = false
        assertFalse(editRemoteKey(e, KeyEvent.KEYCODE_ENTER, 0, "\n"))
        assertEquals("ab😀cd", e.text)
    }

    @Test fun emptyEditorAndMultilineEnter() {
        val e = Editor()
        e.text = null; e.start = 0; e.end = 0
        assertTrue(editRemoteKey(e, KeyEvent.KEYCODE_A, 0, "a"))
        assertTrue(editRemoteKey(e, KeyEvent.KEYCODE_ENTER, 0, null))
        assertEquals("a\n", e.text)
        assertTrue(editRemoteKey(e, KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.META_CTRL_ON, null))
        assertEquals(0, e.end)
        assertTrue(editRemoteKey(e, KeyEvent.KEYCODE_FORWARD_DEL, 0, null))
        assertEquals("\n", e.text)
    }
}
