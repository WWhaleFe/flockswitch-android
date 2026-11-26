package io.github.wwhalefe.flockswitch.net

import io.github.wwhalefe.flockswitch.Config
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull

/** Main-thread queue. Only one snapshot may be awaiting the master's status echo. */
class KeyRuleEdits {
    private var known = emptyMap<String, DirRules>()
    private var pending = emptyMap<String, DirRules>()
    private var sent: Map<String, DirRules>? = null
    private var sentAt = 0L
    /** False until the master's settings arrived: [known] would be the factory defaults,
     *  and sending them back would reset the master's rules for every computer. */
    private var ready = true
    private val changes = Channel<Unit>(Channel.CONFLATED)
    private val display = MutableStateFlow(emptyMap<String, DirRules>())
    val rules = display.asStateFlow()
    private val allowed = MutableStateFlow(true)
    /** False while the followed master is a 2.0 one that cannot take key rule edits. */
    val editable = allowed.asStateFlow()
    internal val hasPending: Boolean get() = sent != null || pending.any { (key, value) -> known[key] != value }

    internal fun wake() { changes.trySend(Unit) }

    internal suspend fun awaitChange(timeoutMs: Long) {
        withTimeoutOrNull(timeoutMs) { changes.receive() }
    }

    /** [keys]: the status' rules. [following]: the master's settings have arrived, so
     *  [keys] are the master's and not defaults. A sent batch the master did not echo
     *  within [ECHO_TIMEOUT_MS] (an old master without key rules, or another computer
     *  changed the same direction) is given up and the master's values show again. */
    fun observe(keys: Map<String, DirRules>, following: Boolean = true, now: Long = nowMs(), masterTakes: Boolean = true) {
        allowed.value = masterTakes
        if (!masterTakes) {
            // A 2.0 master never echoes: drop the queue now instead of waiting out the timeout.
            pending = emptyMap()
            sent = null
        }
        ready = following
        if (following) known = keys
        sent?.let { batch ->
            if (following && batch.all { (key, value) -> keys[key] == value }) {
                pending = pending.filterNot { (key, value) -> batch[key] == value }
                sent = null
            } else if (now - sentAt > ECHO_TIMEOUT_MS) {
                pending = pending.filterNot { (key, value) -> batch[key] == value }
                sent = null
            }
        }
        display.value = known + pending
    }

    fun edit(key: String, rules: DirRules) {
        if (!allowed.value) return
        pending = pending + (key to rules)
        display.value = known + pending
        wake()
    }

    fun next(now: Long = nowMs()): Map<String, DirRules>? {
        if (!ready || sent != null) return null
        pending = pending.filterNot { (key, value) -> known[key] == value }
        if (pending.isEmpty()) return null
        sent = pending.toMap()
        sentAt = now
        return known + pending
    }

    fun disconnected() { sent = null }

    /** Forgets everything: another master (or none) is now the one the edits are for. */
    fun reset() {
        known = emptyMap()
        pending = emptyMap()
        sent = null
        ready = false
        allowed.value = true
        display.value = emptyMap()
    }

    companion object {
        const val ECHO_TIMEOUT_MS = 5000L
        private fun nowMs() = System.nanoTime() / 1_000_000
    }
}

/** Keep the existing acknowledgement cadence while connected or editing. */
internal fun keyRulesPollMs(connected: Boolean, pending: Boolean): Long =
    if (connected || pending) 200L else 1000L

/** Key rules of one direction ("mac>android"), in the shape netcore's `DirRules` has in
 *  JSON. Android only edits the directions that end here; the master saves them and hands
 *  them to every computer, so the whole map is always sent back. */
@Serializable
data class DirRules(
    /** "function", "position" or "off". */
    val mod_mode: String = "function",
    /** Overrides on top of the mode: modifier usage (as text) to usage. */
    val mods: Map<String, Int> = emptyMap(),
    val hangul_src: List<String> = listOf("lang1"),
    val hangul_dst: String = "lang1",
    val caps_long_press: Boolean = true,
    val hanja: Boolean = true,
    val nav: List<Boolean> = listOf(true, true, true, true),
    val capture: Boolean = true,
)

/** Reading, changing and writing the key rules the master's settings carry. */
object KeyRules {
    /** The computers that can send to this one. */
    val SOURCES = listOf("mac", "windows", "linux")

    // Modifier kinds, as a left key's offset from Left Control (0xE0); the right key is 4 more.
    const val CTRL = 0
    const val ALT = 2
    const val GUI = 3
    const val LEFT_CTRL = 0xE0
    private const val RIGHT_OFFSET = 4

    /** The modifiers a table row is shown for, in the order of the source keyboard's bottom row. */
    fun modifiers(src: String): List<Int> = if (src == "mac") listOf(CTRL, ALT, GUI) else listOf(CTRL, GUI, ALT)

    /** Where a Korean/English source key is offered, per source system. */
    fun hangulSources(src: String): List<String> = when (src) {
        "mac" -> listOf("caps_lock", "control_space", "shift_space")
        "windows" -> listOf("lang1", "right_alt", "shift_space")
        else -> listOf("lang1", "shift_space", "super_space")
    }

    /** What Android understands as the Korean/English switch. */
    val ANDROID_HANGUL = listOf("shift_space", "control_space")

    fun key(src: String, dst: String = "android") = "$src>$dst"

    /** The rules of a direction when nothing was ever set: what netcore's `default_for` gives. */
    fun defaultFor(src: String, dst: String = "android"): DirRules {
        val (source, target) = when {
            src == "mac" && dst == "android" -> listOf("caps_lock", "shift_space") to "shift_space"
            src == "mac" -> listOf("caps_lock", "shift_space") to "lang1"
            dst == "mac" -> listOf("lang1") to "caps_lock"
            dst == "android" -> listOf("lang1") to "shift_space"
            else -> listOf("lang1") to "lang1"
        }
        return DirRules(hangul_src = source, hangul_dst = target, hanja = src != "mac")
    }

    /** The kind of modifier a source modifier becomes by the mode alone (netcore's `map_modifier`). */
    fun modeTarget(src: String, dst: String, mode: String, kind: Int): Int {
        if (mode == "off" || (src == "mac") == (dst == "mac")) return kind
        return if (src == "mac") when {
            mode == "function" && kind == GUI -> CTRL
            mode == "function" && kind == CTRL -> GUI
            mode == "position" && kind == ALT -> GUI
            mode == "position" && kind == GUI -> ALT
            else -> kind
        } else when {
            mode == "function" && kind == CTRL -> GUI
            mode == "function" && kind == GUI -> CTRL
            mode == "position" && kind == GUI -> ALT
            mode == "position" && kind == ALT -> GUI
            else -> kind
        }
    }

    /** What a source modifier becomes now: the override if there is one, else the mode's table. */
    fun modTarget(src: String, dst: String, rules: DirRules, kind: Int): Int {
        val usage = rules.mods[(LEFT_CTRL + kind).toString()]
        if (usage != null && usage in LEFT_CTRL..(LEFT_CTRL + 7)) return (usage - LEFT_CTRL) % 4
        return modeTarget(src, dst, rules.mod_mode, kind)
    }

    /** Makes a source modifier become [target] (both its left and right key). A choice the
     *  mode already gives needs no override. */
    fun withModTarget(src: String, dst: String, rules: DirRules, kind: Int, target: Int): DirRules {
        val left = (LEFT_CTRL + kind).toString()
        val right = (LEFT_CTRL + RIGHT_OFFSET + kind).toString()
        val mods = rules.mods.toMutableMap()
        if (target == modeTarget(src, dst, rules.mod_mode, kind)) {
            mods.remove(left)
            mods.remove(right)
        } else {
            mods[left] = LEFT_CTRL + target
            mods[right] = LEFT_CTRL + RIGHT_OFFSET + target
        }
        return rules.copy(mods = mods)
    }

    /** A new mode starts from its own table: the overrides of the old one are dropped. */
    fun withModMode(rules: DirRules, mode: String) = rules.copy(mod_mode = mode, mods = emptyMap())

    /** Turns one Korean/English source key on or off. */
    fun withHangulSource(src: String, rules: DirRules, key: String, on: Boolean): DirRules {
        val options = hangulSources(src)
        val chosen = options.filter { if (it == key) on else it in rules.hangul_src }
        return rules.copy(hangul_src = chosen)
    }

    fun withHangulTarget(rules: DirRules, key: String) = rules.copy(hangul_dst = key)

    /** The rules of the directions that end on Android, from the status' map; a direction
     *  that is missing (not connected yet) shows its defaults. */
    fun forAndroid(known: Map<String, DirRules>): Map<String, DirRules> =
        SOURCES.associate { key(it) to (known[key(it)] ?: defaultFor(it)) }

    /** The whole map as netcore takes it: the directions Android does not edit stay as the
     *  master has them. */
    fun toJson(all: Map<String, DirRules>): String =
        Config.json.encodeToString(MapSerializer(String.serializer(), DirRules.serializer()), all)

    /** [known] with the edited directions put over it. */
    fun merged(known: Map<String, DirRules>, edited: Map<String, DirRules>): Map<String, DirRules> = known + edited
}
