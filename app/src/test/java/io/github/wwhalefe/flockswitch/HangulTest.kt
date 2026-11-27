package io.github.wwhalefe.flockswitch

import io.github.wwhalefe.flockswitch.net.*
import org.junit.Assert.*
import org.junit.Test

class HangulTest {
    /** A field that behaves like an input connection: composing text is replaced by the next write. */
    private class Field : ComposingTarget {
        val text = StringBuilder()
        private var open = 0
        override fun commit(text: String) { this.text.setLength(this.text.length - open); this.text.append(text); open = 0 }
        override fun setComposing(text: String) { this.text.setLength(this.text.length - open); this.text.append(text); open = text.length }
        override fun toString() = text.toString()
    }

    private fun typer(field: Field = Field()) = HangulTyper { field }.also { it.toggle() }

    /** Lowercase letters are plain keys, uppercase ones are typed with Shift; "<" is Backspace. */
    private fun type(t: HangulTyper, keys: String) {
        for (k in keys) if (k == '<') t.backspace() else assertTrue("$k", t.letter(k.lowercaseChar(), k.isUpperCase()))
    }

    private fun typed(keys: String): String {
        val f = Field()
        val t = typer(f)
        type(t, keys)
        t.flush()
        return f.toString()
    }

    @Test fun syllablesAreComposed() {
        assertEquals("한글", typed("gksrmf"))
        assertEquals("값", typed("rkqt"))
        assertEquals("닭", typed("ekfr"))
        assertEquals("의", typed("dml"))
        assertEquals("왜", typed("dho"))
        assertEquals("원", typed("dnjs"))
        assertEquals("뷁", typed("qnpfr"))
        assertEquals("안녕하세요", typed("dkssudgktpdy"))
    }

    @Test fun aFinalMovesOnToTheNextSyllable() {
        assertEquals("하나", typed("gksk"))
        // A double final is split: 앉 + ㅏ = 안 + 자.
        assertEquals("안자", typed("dkswk"))
        // Another consonant after it keeps the double final.
        assertEquals("앉아", typed("dkswdk"))
        assertEquals("읽어", typed("dlfrdj"))
        assertEquals("값이", typed("rkqtdl"))
        assertEquals("갑시", typed("rkqtl"))
    }

    @Test fun tenseConsonantsAndTheirVowels() {
        for ((keys, jamo) in listOf("Q" to "ㅃ", "W" to "ㅉ", "E" to "ㄸ", "R" to "ㄲ", "T" to "ㅆ", "O" to "ㅒ", "P" to "ㅖ")) {
            val c = HangulComposer()
            val r = c.letter(keys[0].lowercaseChar(), true)!!
            assertEquals(jamo, r.composing)
        }
        assertEquals("빠", typed("Qk"))
        assertEquals("깨", typed("Ro"))
        assertEquals("었", typed("djT"))
        assertEquals("얘", typed("dO"))
        assertEquals("예", typed("dP"))
        // ㅃ cannot close a syllable.
        assertEquals("바ㅃ", typed("qkQ"))
        // Shift does nothing to the other letters.
        assertEquals("한", typed("GKS"))
    }

    @Test fun backspaceTakesBackOneJamoAtATime() {
        val f = Field()
        val t = typer(f)
        type(t, "rkqt")
        assertEquals("값", f.toString())
        t.backspace(); assertEquals("갑", f.toString())
        t.backspace(); assertEquals("가", f.toString())
        t.backspace(); assertEquals("ㄱ", f.toString())
        t.backspace(); assertEquals("", f.toString())
        // Nothing left to edit: the real Backspace is for the field.
        assertFalse(t.backspace())
        type(t, "dhk")
        assertEquals("와", f.toString())
        t.backspace(); assertEquals("오", f.toString())
        t.backspace(); assertEquals("ㅇ", f.toString())
        // An earlier, final syllable is not touched.
        type(t, "<")
        type(t, "gk")
        t.flush()
        type(t, "dk")
        t.backspace(); t.backspace()
        assertEquals("하", f.toString())
    }

    @Test fun backspaceAfterSplittingAFinal() {
        val f = Field()
        val t = typer(f)
        type(t, "dkswk")
        assertEquals("안자", f.toString())
        type(t, "<")
        assertEquals("안ㅈ", f.toString())
        type(t, "<")
        assertEquals("안", f.toString())
        // The syllable before it is final: the next Backspace is the field's own.
        assertFalse(t.backspace())
        assertEquals("안", f.toString())
    }

    @Test fun keysThatAreNotLettersEndTheComposition() {
        val f = Field()
        val t = typer(f)
        type(t, "gk")
        assertTrue(t.composing)
        t.flush()
        assertFalse(t.composing)
        f.commit(" ")
        type(t, "s")
        assertEquals("하 ㄴ", f.toString())
    }

    @Test fun englishModePassesLettersOn() {
        val f = Field()
        val t = HangulTyper { f }
        assertFalse(t.letter('a', false))
        assertFalse(t.korean)
        assertTrue(t.toggle())
        assertTrue(t.letter('a', false))
        // Switching back finishes the syllable first.
        assertFalse(t.toggle())
        assertEquals("ㅁ", f.toString())
        assertFalse(t.composing)
    }

    @Test fun anAbandonedCompositionIsNotCommittedAgain() {
        val f = Field()
        val t = typer(f)
        type(t, "gk")
        t.abandon()
        t.flush()
        assertEquals("하", f.toString())
        assertFalse(t.backspace())
    }

    @Test fun noTargetMeansNothingIsTaken() {
        val t = HangulTyper { null }
        t.toggle()
        assertFalse(t.letter('a', false))
        assertFalse(t.backspace())
    }

    @Test fun everyLetterKeyHasAJamo() {
        for (c in 'a'..'z') assertNotNull("$c", HangulComposer.jamoOf(c, false))
        assertNull(HangulComposer.jamoOf('1', false))
    }

    // The key that arrives for the Korean/English switch.
    private val lshift = 0xe1
    private val lctrl = 0xe0
    private val space = 0x2c

    @Test fun shiftSpaceIsTheDefaultToggle() {
        assertTrue(isHangulToggle("shift_space", space, setOf(lshift)))
        assertTrue(isHangulToggle("shift_space", space, setOf(0xe5)))
        assertFalse(isHangulToggle("shift_space", space, emptySet()))
        assertFalse(isHangulToggle("shift_space", space, setOf(lctrl)))
        assertFalse(isHangulToggle("shift_space", space, setOf(lshift, lctrl)))
        assertFalse(isHangulToggle("shift_space", space, setOf(lshift, 0xe2)))
        assertFalse(isHangulToggle("shift_space", 0x04, setOf(lshift)))
    }

    @Test fun controlSpaceWhenTheRuleSaysSo() {
        assertTrue(isHangulToggle("control_space", space, setOf(lctrl)))
        assertTrue(isHangulToggle("control_space", space, setOf(0xe4)))
        assertFalse(isHangulToggle("control_space", space, setOf(lshift)))
        assertFalse(isHangulToggle("control_space", space, setOf(lctrl, lshift)))
        assertFalse(isHangulToggle("control_space", space, setOf(lctrl, 0xe3)))
    }

    @Test fun theMastersRuleDecidesTheKey() {
        val rules = mapOf("mac>android" to DirRules(hangul_dst = "control_space"))
        assertEquals("control_space", KeyRules.toggleKeyFor("mac", rules))
        assertEquals("shift_space", KeyRules.toggleKeyFor("windows", rules))
        assertEquals("shift_space", KeyRules.toggleKeyFor(null, rules))
        // Something Android has no meaning for counts as the default.
        assertEquals("shift_space", KeyRules.toggleKeyFor("mac", mapOf("mac>android" to DirRules(hangul_dst = "lang1"))))
        assertEquals("shift_space", KeyRules.toggleKeyFor("linux", emptyMap()))
    }

    // Android 12L and older: the composition is plain text, replaced where it was written.
    private class Legacy : RemoteEditor {
        override var text: String? = ""
        override var start = 0
        override var end = 0
        override var password = false
        override val multiline = false
        override fun replace(text: String, cursor: Int): Boolean { this.text = text; start = cursor; end = cursor; return true }
        override fun select(start: Int, end: Int): Boolean { this.start = start; this.end = end; return true }
        override fun move(forward: Boolean, extend: Boolean) = true
        override fun context(action: Int) = true
    }

    @Test fun legacyTextPathComposesInPlace() {
        val field = Legacy()
        val target = LegacyComposingTarget().also { it.editor = field }
        val t = HangulTyper { target }
        t.toggle()
        type(t, "dkswk")
        assertEquals("안자", field.text)
        assertEquals(2, field.start)
        type(t, "<<")
        assertEquals("안", field.text)
        t.flush()
        assertEquals("안", field.text)
    }

    @Test fun legacyCompositionIsFinalOnceTheCursorMoved() {
        val field = Legacy()
        val target = LegacyComposingTarget().also { it.editor = field }
        val t = HangulTyper { target }
        t.toggle()
        type(t, "gk")
        assertEquals("하", field.text)
        // The user (or the master's arrow key) moved the cursor to the front: the syllable stays.
        field.start = 0; field.end = 0
        type(t, "s")
        assertEquals("ㄴ하", field.text)
    }

    @Test fun legacyRefusesPasswordFields() {
        val field = Legacy().also { it.password = true }
        val target = LegacyComposingTarget().also { it.editor = field }
        val t = HangulTyper { target }
        t.toggle()
        type(t, "gk")
        assertEquals("", field.text)
    }

    // Android 13+: no composing text, the syllable is deleted and written again.
    private class Connection : CursorText {
        var text = ""
        var cursor = 0
        var reports = true
        override fun before(n: Int): String? = if (reports) text.substring((cursor - n).coerceAtLeast(0), cursor) else null
        override fun delete(n: Int) { text = text.removeRange(cursor - n, cursor); cursor -= n }
        override fun insert(text: String) { this.text = this.text.substring(0, cursor) + text + this.text.substring(cursor); cursor += text.length }
    }

    @Test fun modernPathRewritesTheSyllableBeforeTheCursor() {
        val c = Connection()
        val t = HangulTyper(ReplacingComposingTarget { c }.let { target -> { target } })
        t.toggle()
        type(t, "dkswk")
        assertEquals("안자", c.text)
        type(t, "<<")
        assertEquals("안", c.text)
        t.flush()
        type(t, "gk")
        assertEquals("안하", c.text)
    }

    @Test fun modernPathLeavesTextAloneWhenTheCursorMovedAway() {
        val c = Connection()
        val t = HangulTyper(ReplacingComposingTarget { c }.let { target -> { target } })
        t.toggle()
        type(t, "gk")
        c.text = "X" + c.text; c.cursor = 0
        type(t, "s")
        // The syllable was no longer before the cursor: it stays, the new letter goes in at the cursor.
        assertEquals("ㄴX하", c.text)
    }

    @Test fun modernPathTrustsAFieldThatDoesNotSayWhatIsBeforeTheCursor() {
        val c = Connection().also { it.reports = false }
        val t = HangulTyper(ReplacingComposingTarget { c }.let { target -> { target } })
        t.toggle()
        type(t, "gks")
        assertEquals("한", c.text)
    }
}
