package io.github.wwhalefe.flockswitch.net

import android.os.Bundle
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo

/** The focused editor only. No screen tree or text is retained between key presses. */
internal interface RemoteEditor {
    val text: String?
    val start: Int
    val end: Int
    val password: Boolean
    val multiline: Boolean
    fun replace(text: String, cursor: Int): Boolean
    fun select(start: Int, end: Int): Boolean
    fun move(forward: Boolean, extend: Boolean): Boolean
    fun context(action: Int): Boolean
}

internal class AccessibilityEditor(private val node: AccessibilityNodeInfo) : RemoteEditor {
    override val text get() = node.text?.toString()
    override val start get() = node.textSelectionStart
    override val end get() = node.textSelectionEnd
    override val password get() = node.isPassword
    override val multiline get() = node.isMultiLine
    override fun replace(text: String, cursor: Int): Boolean {
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        })
        if (!ok) return false
        return select(cursor, cursor)
    }
    override fun select(start: Int, end: Int) = node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, Bundle().apply {
        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, start)
        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, end)
    })
    override fun move(forward: Boolean, extend: Boolean) = node.performAction(
        if (forward) AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY else AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY,
        Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT, AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE)
            putBoolean(AccessibilityNodeInfo.ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN, extend)
        },
    )
    override fun context(action: Int) = node.performAction(action)
}

/** Language combinations must reach the IME as keys, never as a literal space. */
internal fun remoteImeChord(code: Int, meta: Int): Boolean =
    code == KeyEvent.KEYCODE_SPACE && meta and (KeyEvent.META_SHIFT_ON or KeyEvent.META_CTRL_ON) != 0

/** Accessibility actions supported since before API 26. False means the app must explain
 *  the limit, rather than replacing hidden text or silently inserting a language chord. */
internal fun editRemoteKey(editor: RemoteEditor, code: Int, meta: Int, character: String?): Boolean {
    if (remoteImeChord(code, meta)) return false
    if (meta and (KeyEvent.META_ALT_ON or KeyEvent.META_META_ON) != 0) return false
    val ctrl = meta and KeyEvent.META_CTRL_ON != 0
    val shift = meta and KeyEvent.META_SHIFT_ON != 0
    if (editor.password) return false
    if (ctrl) {
        val action = when (code) {
            KeyEvent.KEYCODE_C -> AccessibilityNodeInfo.ACTION_COPY
            KeyEvent.KEYCODE_V -> AccessibilityNodeInfo.ACTION_PASTE
            KeyEvent.KEYCODE_X -> AccessibilityNodeInfo.ACTION_CUT
            else -> null
        }
        if (action != null) return editor.context(action)
    }
    val start = editor.start
    val end = editor.end
    val text = editor.text ?: if (start == 0 && end == 0) "" else return false
    if (start !in 0..text.length || end !in 0..text.length) return false
    val low = minOf(start, end)
    val high = maxOf(start, end)
    if (ctrl && code == KeyEvent.KEYCODE_A) return editor.select(0, text.length)
    if (ctrl && code !in listOf(KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.KEYCODE_MOVE_END)) return false
    fun previous(at: Int) = if (at == 0) 0 else text.offsetByCodePoints(at, -1)
    fun next(at: Int) = if (at == text.length) at else text.offsetByCodePoints(at, 1)
    fun select(to: Int) = editor.select(if (shift) start else to, to)
    when (code) {
        KeyEvent.KEYCODE_DPAD_LEFT -> return select(if (!shift && low != high) low else previous(end))
        KeyEvent.KEYCODE_DPAD_RIGHT -> return select(if (!shift && low != high) high else next(end))
        KeyEvent.KEYCODE_DPAD_UP -> return editor.move(false, shift)
        KeyEvent.KEYCODE_DPAD_DOWN -> return editor.move(true, shift)
        KeyEvent.KEYCODE_MOVE_HOME -> return select(if (ctrl) 0 else text.lastIndexOf('\n', (end - 1).coerceAtLeast(-1)) + 1)
        KeyEvent.KEYCODE_MOVE_END -> return select(if (ctrl) text.length else text.indexOf('\n', end).let { if (it < 0) text.length else it })
        KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL -> {
            val from = if (low == high && code == KeyEvent.KEYCODE_DEL) previous(low) else low
            val to = if (low == high && code == KeyEvent.KEYCODE_FORWARD_DEL) next(high) else high
            return editor.replace(text.removeRange(from, to), from)
        }
    }
    val insert = when (code) {
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> if (editor.multiline) "\n" else return false
        else -> character?.takeIf { it.isNotEmpty() && it.none(Character::isISOControl) } ?: return false
    }
    return editor.replace(text.replaceRange(low, high, insert), low + insert.length)
}
