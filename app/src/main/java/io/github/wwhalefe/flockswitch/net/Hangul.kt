package io.github.wwhalefe.flockswitch.net

// Korean typing for the keys another computer sends: a 2-set (dubeolsik) automaton. The
// accessibility service has no on-screen keyboard to switch, so flockswitch composes the
// syllables itself and hands them to the focused field.

/** What one key did: [commit] is text that is final now, [composing] the syllable still
 *  being typed (it may change with the next key). */
data class HangulResult(val commit: String = "", val composing: String = "")

/** Composes jamo into syllables. Letters go in as the QWERTY key they sit on. */
class HangulComposer {
    // The syllable being typed, as indexes into the lists below (-1: none).
    private var cho = -1
    private var jung = -1
    private var jong = 0

    /** Something is being composed (so Backspace edits it instead of the field). */
    val active: Boolean get() = cho >= 0 || jung >= 0

    /** The syllable being typed. */
    val composing: String get() = when {
        cho >= 0 && jung >= 0 -> (0xAC00 + (cho * 21 + jung) * 28 + jong).toChar().toString()
        cho >= 0 -> CHO[cho].toString()
        jung >= 0 -> JUNG[jung].toString()
        else -> ""
    }

    /** One letter key ([letter] a..z, any case); [shift] gives the tense consonants and ㅒ ㅖ.
     *  Null when it is no Korean letter key. */
    fun letter(letter: Char, shift: Boolean): HangulResult? {
        val jamo = jamoOf(letter.lowercaseChar(), shift) ?: return null
        val v = JUNG.indexOf(jamo)
        return if (v >= 0) vowel(v) else consonant(jamo)
    }

    private fun consonant(jamo: Char): HangulResult {
        val c = CHO.indexOf(jamo)
        var commit = ""
        when {
            cho >= 0 && jung >= 0 && jong == 0 -> {
                val f = JONG.indexOf(jamo)
                if (f > 0) { jong = f; return HangulResult("", composing) }
                commit = composing
            }
            cho >= 0 && jung >= 0 -> {
                val double = DOUBLE_FINAL[JONG[jong] to jamo]
                if (double != null) { jong = JONG.indexOf(double); return HangulResult("", composing) }
                commit = composing
            }
            else -> commit = composing
        }
        cho = c; jung = -1; jong = 0
        return HangulResult(commit, composing)
    }

    private fun vowel(v: Int): HangulResult {
        var commit = ""
        when {
            cho >= 0 && jung < 0 -> { jung = v; return HangulResult("", composing) }
            cho >= 0 && jong == 0 -> {
                val joined = COMPOUND_VOWEL[JUNG[jung] to JUNG[v]]
                if (joined != null) { jung = JUNG.indexOf(joined); return HangulResult("", composing) }
                commit = composing
                cho = -1; jung = v; jong = 0
            }
            cho >= 0 -> {
                // The final belongs to the new syllable now: 앉 + ㅏ = 안 + 자.
                val f = JONG[jong]
                val split = SPLIT_FINAL[f]
                val moved = split?.second ?: f
                jong = if (split != null) JONG.indexOf(split.first) else 0
                commit = composing
                cho = CHO.indexOf(moved); jung = v; jong = 0
            }
            jung >= 0 -> {
                val joined = COMPOUND_VOWEL[JUNG[jung] to JUNG[v]]
                if (joined != null) { jung = JUNG.indexOf(joined); return HangulResult("", composing) }
                commit = composing
                jung = v
            }
            else -> jung = v
        }
        return HangulResult(commit, composing)
    }

    /** Backspace while composing: takes back the last jamo. Null when nothing is being
     *  composed (the field's own Backspace is then to be used). */
    fun backspace(): HangulResult? {
        if (!active) return null
        when {
            cho >= 0 && jung >= 0 && jong != 0 -> {
                val split = SPLIT_FINAL[JONG[jong]]
                jong = if (split != null) JONG.indexOf(split.first) else 0
            }
            jung >= 0 -> {
                val back = UNJOIN_VOWEL[JUNG[jung]]
                jung = if (back != null) JUNG.indexOf(back) else -1
            }
            else -> cho = -1
        }
        return HangulResult("", composing)
    }

    /** Ends the composition: the syllable becomes final. */
    fun flush(): String {
        val text = composing
        reset()
        return text
    }

    /** Forgets the composition without producing anything. */
    fun reset() { cho = -1; jung = -1; jong = 0 }

    companion object {
        const val CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
        const val JUNG = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"
        /** Index 0 is "no final". */
        const val JONG = "\u0000ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ"

        private val KEYS = mapOf(
            'q' to 'ㅂ', 'w' to 'ㅈ', 'e' to 'ㄷ', 'r' to 'ㄱ', 't' to 'ㅅ', 'y' to 'ㅛ', 'u' to 'ㅕ', 'i' to 'ㅑ', 'o' to 'ㅐ', 'p' to 'ㅔ',
            'a' to 'ㅁ', 's' to 'ㄴ', 'd' to 'ㅇ', 'f' to 'ㄹ', 'g' to 'ㅎ', 'h' to 'ㅗ', 'j' to 'ㅓ', 'k' to 'ㅏ', 'l' to 'ㅣ',
            'z' to 'ㅋ', 'x' to 'ㅌ', 'c' to 'ㅊ', 'v' to 'ㅍ', 'b' to 'ㅠ', 'n' to 'ㅜ', 'm' to 'ㅡ',
        )
        private val SHIFTED = mapOf('q' to 'ㅃ', 'w' to 'ㅉ', 'e' to 'ㄸ', 'r' to 'ㄲ', 't' to 'ㅆ', 'o' to 'ㅒ', 'p' to 'ㅖ')

        /** The jamo on a QWERTY letter key (lowercase [key]), or null for other keys. */
        fun jamoOf(key: Char, shift: Boolean): Char? = (if (shift) SHIFTED[key] else null) ?: KEYS[key]

        /** Two finals typed in a row that make one: ㄱ+ㅅ = ㄳ. */
        private val DOUBLE_FINAL = mapOf(
            ('ㄱ' to 'ㅅ') to 'ㄳ', ('ㄴ' to 'ㅈ') to 'ㄵ', ('ㄴ' to 'ㅎ') to 'ㄶ',
            ('ㄹ' to 'ㄱ') to 'ㄺ', ('ㄹ' to 'ㅁ') to 'ㄻ', ('ㄹ' to 'ㅂ') to 'ㄼ', ('ㄹ' to 'ㅅ') to 'ㄽ',
            ('ㄹ' to 'ㅌ') to 'ㄾ', ('ㄹ' to 'ㅍ') to 'ㄿ', ('ㄹ' to 'ㅎ') to 'ㅀ', ('ㅂ' to 'ㅅ') to 'ㅄ',
        )

        /** A double final: what stays, what moves to the next syllable. */
        private val SPLIT_FINAL: Map<Char, Pair<Char, Char>> = DOUBLE_FINAL.entries.associate { (k, v) -> v to k }

        /** Two vowels in a row that make one: ㅗ+ㅏ = ㅘ. */
        private val COMPOUND_VOWEL = mapOf(
            ('ㅗ' to 'ㅏ') to 'ㅘ', ('ㅗ' to 'ㅐ') to 'ㅙ', ('ㅗ' to 'ㅣ') to 'ㅚ',
            ('ㅜ' to 'ㅓ') to 'ㅝ', ('ㅜ' to 'ㅔ') to 'ㅞ', ('ㅜ' to 'ㅣ') to 'ㅟ', ('ㅡ' to 'ㅣ') to 'ㅢ',
        )

        /** Backspace on a compound vowel leaves its first vowel. */
        private val UNJOIN_VOWEL: Map<Char, Char> = COMPOUND_VOWEL.entries.associate { (k, v) -> v to k.first }
    }
}

/** Where composed text goes. [commit] replaces the composition so far with final text;
 *  [setComposing] replaces it with a new, still open one (empty: none). */
interface ComposingTarget {
    fun commit(text: String)
    fun setComposing(text: String)

    /** The composition written last is still where it was (before the cursor). False means the
     *  field changed under it: it is final text now and the next key starts a new one. */
    fun intact(): Boolean = true
}

/** Korean mode: letters are composed, everything else ends the composition. */
class HangulTyper(private val target: () -> ComposingTarget?) {
    private val composer = HangulComposer()

    /** Typing Korean now (toggled by the Korean/English key). */
    var korean = false
        private set

    val composing: Boolean get() = composer.active

    /** The Korean/English key: ends the composition and switches. */
    fun toggle(): Boolean {
        flush()
        korean = !korean
        return korean
    }

    /** A letter key. False: not handled (not in Korean mode, or no Korean letter). */
    fun letter(letter: Char, shift: Boolean): Boolean {
        if (!korean) return false
        val t = target() ?: return false
        if (composer.active && !t.intact()) composer.reset()
        val r = composer.letter(letter, shift) ?: return false
        if (r.commit.isNotEmpty()) t.commit(r.commit)
        t.setComposing(r.composing)
        return true
    }

    /** Backspace: true when it edited the composition; false means send a real Backspace. */
    fun backspace(): Boolean {
        val t = target() ?: return false
        if (composer.active && !t.intact()) composer.reset()
        val r = composer.backspace() ?: return false
        t.setComposing(r.composing)
        return true
    }

    /** Makes the syllable being typed final. Any key that is not a Korean letter must call this first. */
    fun flush() {
        if (!composer.active) return
        val text = composer.flush()
        val t = target() ?: return
        // A syllable that is no longer before the cursor was final already.
        if (t.intact()) t.commit(text)
    }

    /** The field changed under the composition (another field, a moved cursor): forget it. */
    fun abandon() = composer.reset()

    /** Back to English with nothing composed, for a new run. */
    fun clear() {
        composer.reset()
        korean = false
    }
}
