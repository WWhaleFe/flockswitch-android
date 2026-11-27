package io.github.wwhalefe.flockswitch.net

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.InputMethod
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import io.github.wwhalefe.flockswitch.R
import io.github.wwhalefe.flockswitch.shared
import org.json.JSONObject
import kotlin.math.hypot

private const val TAP_MS = 40L
private const val PRESS_MS = 20L
/** Two taps of a double click start this far apart (a gap of 60 ms: above the system's
 *  minimum double-tap time, far below its timeout). */
private const val DOUBLE_TAP_START_GAP_MS = 100L
/** One wheel unit (a notch) scrolls this much. */
private const val SCROLL_UNIT_DP = 64f

/** What the master's rules make arrive as the Korean/English switch on this device: set by
 *  the network service from the master's status. */
object HangulSettings {
    @Volatile var toggleKey: String = "shift_space"
}

/** All methods run on the accessibility service's main thread. */
class RemoteInput(private val service: AccessibilityService) {
    private val handler = Handler(Looper.getMainLooper())
    private val windows = service.getSystemService(WindowManager::class.java)
    private var x = 0f
    private var y = 0f
    private var shown = false
    private val cursor = object : View(service) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            val scale = width / 24f
            canvas.save(); canvas.scale(scale, scale)
            val path = Path().apply { moveTo(2f, 1f); lineTo(3f, 21f); lineTo(8f, 16f); lineTo(12f, 23f); lineTo(16f, 21f); lineTo(12f, 14f); lineTo(20f, 13f); close() }
            paint.color = Color.WHITE; paint.style = Paint.Style.FILL; canvas.drawPath(path, paint)
            paint.color = Color.BLACK; paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.3f; canvas.drawPath(path, paint)
            canvas.restore()
        }
    }
    private val params = WindowManager.LayoutParams(
        (24 * service.resources.displayMetrics.density).toInt(), (24 * service.resources.displayMetrics.density).toInt(),
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        // The arrow is only drawn: it never takes a touch (the gestures below go to the app under it).
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.LEFT
        if (Build.VERSION.SDK_INT >= 30) setFitInsetsTypes(0)
        // Clicks use the whole physical screen (cutout included), so the arrow must be laid
        // out from the same origin; otherwise a landscape notch pushes it sideways.
        if (Build.VERSION.SDK_INT >= 30) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        else if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }
    private val density = service.resources.displayMetrics.density
    private val tuning = ViewConfiguration.get(service).let {
        TouchTuning(
            slopPx = it.scaledTouchSlop.toFloat(),
            doubleTapMs = ViewConfiguration.getDoubleTapTimeout().toLong(),
            longPressMs = ViewConfiguration.getLongPressTimeout() + 300L,
        )
    }
    private val planner = TouchPlanner(tuning) { TouchSettings.of(service.shared.config.value.network) }
    private val queue = GestureQueue()
    /** The stroke of a finger that is down (press or drag), to be continued. */
    private var held: GestureDescription.StrokeDescription? = null
    private var heldX = 0f
    private var heldY = 0f
    private var lastTapStart = Long.MIN_VALUE
    private var busy = false
    private var generation = 0
    private val modifiers = mutableSetOf<Int>()
    private val sentKeys = mutableSetOf<Int>()
    private var capsLock = false
    private var lastKeyboardHint = Long.MIN_VALUE
    private val characters = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
    private val wake = Runnable { run(planner.tick(SystemClock.uptimeMillis())) }
    /** The Korean/English toggle's Space is eaten on both edges. */
    private var swallowSpace = false
    /** The accessibility input connection the composition belongs to (API 33+; kept as Any so older Androids never load the class). */
    private var connection: Any? = null
    private val legacyTarget = LegacyComposingTarget()
    private val modernTarget = ReplacingComposingTarget {
        if (Build.VERSION.SDK_INT < 33) null else (connection as? InputMethod.AccessibilityInputConnection)?.let { c ->
            object : CursorText {
                override fun before(n: Int) = c.getSurroundingText(n, 0, 0)?.text?.toString()
                override fun delete(n: Int) { c.deleteSurroundingText(n, 0) }
                override fun insert(text: String) { c.commitText(text, 1, null) }
            }
        }
    }
    private val typer = HangulTyper { if (Build.VERSION.SDK_INT >= 33) (if (connection != null) modernTarget else null) else legacyTarget }

    fun action(a: JSONObject) {
        if (a.has("cursor")) {
            val c = a.getJSONObject("cursor")
            if (!c.optBoolean("visible", true)) { reset(); return }
            x = c.optDouble("x", x.toDouble()).toFloat(); y = c.optDouble("y", y.toDouble()).toFloat()
            params.x = x.toInt(); params.y = y.toInt()
            if (!shown) { windows.addView(cursor, params); shown = true } else windows.updateViewLayout(cursor, params)
            run(planner.move(x, y, SystemClock.uptimeMillis()))
            return
        }
        when (a.optString("type")) {
            "Button" -> {
                val down = a.getBoolean("down")
                val now = SystemClock.uptimeMillis()
                when (a.getString("button")) {
                    "Left" -> run(if (down) planner.leftDown(now) else planner.leftUp(now))
                    "Right" -> if (down) run(planner.rightDown())
                    "Back" -> if (down) run(planner.back())
                    "Middle" -> if (down) run(planner.middleDown())
                    "Forward" -> if (down) run(planner.forward())
                }
            }
            "Scroll" -> run(planner.scroll(a.getDouble("dx"), a.getDouble("dy")))
            "Key" -> key(a.getInt("usage"), a.getBoolean("down"))
        }
    }

    /** Carries out what the planner decided: global actions at once, finger gestures one at a time. */
    private fun run(commands: List<TouchCommand>) {
        for (c in commands) {
            if (c is TouchCommand.Global) service.performGlobalAction(when (c.key) {
                GlobalKey.Back -> AccessibilityService.GLOBAL_ACTION_BACK
                GlobalKey.Home -> AccessibilityService.GLOBAL_ACTION_HOME
                GlobalKey.Recents -> AccessibilityService.GLOBAL_ACTION_RECENTS
            }) else queue.push(c)
        }
        pump()
        handler.removeCallbacks(wake)
        planner.wakeAt?.let { handler.postDelayed(wake, (it - SystemClock.uptimeMillis()).coerceAtLeast(0)) }
    }

    private fun pump() {
        while (!busy) {
            val c = queue.poll() ?: return
            val dm = screenMetrics(service)
            val maxX = (dm.widthPixels - 1).toFloat()
            val maxY = (dm.heightPixels - 1).toFloat()
            fun px(v: Float) = v.coerceIn(0f, maxX)
            fun py(v: Float) = v.coerceIn(0f, maxY)
            when (c) {
                is TouchCommand.Tap -> {
                    val stroke = GestureDescription.StrokeDescription(Path().apply { moveTo(px(c.x), py(c.y)) }, 0, TAP_MS)
                    // Two taps of a double click need a gap the system reads as a double tap.
                    val wait = if (c.followsTap && lastTapStart != Long.MIN_VALUE) lastTapStart + DOUBLE_TAP_START_GAP_MS - SystemClock.uptimeMillis() else 0L
                    send(stroke, wait.coerceAtLeast(0), tap = true)
                }
                is TouchCommand.Press -> {
                    val stroke = GestureDescription.StrokeDescription(Path().apply { moveTo(px(c.x), py(c.y)) }, 0, PRESS_MS, true)
                    held = stroke; heldX = px(c.x); heldY = py(c.y)
                    send(stroke)
                }
                is TouchCommand.Drag -> {
                    val from = held ?: continue
                    val tx = px(c.x); val ty = py(c.y)
                    val dist = hypot(tx - heldX, ty - heldY)
                    if (dist < 1f) continue
                    val next = from.continueStroke(Path().apply { moveTo(heldX, heldY); lineTo(tx, ty) }, 0, dragDurationMs(dist), true)
                    held = next; heldX = tx; heldY = ty
                    send(next)
                }
                is TouchCommand.Release -> {
                    val from = held ?: continue
                    val tx = px(c.x); val ty = py(c.y)
                    val end = from.continueStroke(Path().apply { moveTo(heldX, heldY); lineTo(tx, ty) }, 0, PRESS_MS, false)
                    held = null
                    send(end)
                }
                is TouchCommand.LongPress ->
                    send(GestureDescription.StrokeDescription(Path().apply { moveTo(px(c.x), py(c.y)) }, 0, tuning.longPressMs))
                is TouchCommand.Scroll -> {
                    val p = scrollPath(c.x, c.y, c.dx, c.dy, dm.widthPixels, dm.heightPixels, SCROLL_UNIT_DP * density)
                    val dist = hypot(p[2] - p[0], p[3] - p[1])
                    if (dist < 1f) continue
                    send(GestureDescription.StrokeDescription(Path().apply { moveTo(p[0], p[1]); lineTo(p[2], p[3]) }, 0, scrollDurationMs(dist)))
                }
                is TouchCommand.Global -> {}
            }
        }
    }

    private fun send(stroke: GestureDescription.StrokeDescription, delayMs: Long = 0, tap: Boolean = false) {
        busy = true
        val epoch = generation
        val go = Runnable {
            if (epoch != generation) return@Runnable
            if (tap) lastTapStart = SystemClock.uptimeMillis()
            val accepted = service.dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) { if (epoch == generation) { busy = false; pump() } }
                override fun onCancelled(gestureDescription: GestureDescription) { if (epoch == generation) { busy = false; lost(); pump() } }
            }, handler)
            if (!accepted) { busy = false; lost(); if (delayMs > 0) pump() }
        }
        if (delayMs > 0) handler.postDelayed(go, delayMs) else go.run()
    }

    /** The system dropped a gesture: a finger that was down is up now, so what follows for it is moot. */
    private fun lost() {
        if (held != null) {
            held = null
            planner.cancelled()
            queue.dropStale()
        }
    }
    private fun meta(): Int {
        var state = 0
        for (u in modifiers) state = state or when (u) {
            0xe0, 0xe4 -> KeyEvent.META_CTRL_ON
            0xe1, 0xe5 -> KeyEvent.META_SHIFT_ON
            0xe2, 0xe6 -> KeyEvent.META_ALT_ON
            0xe3, 0xe7 -> KeyEvent.META_META_ON
            else -> 0
        }
        return if (capsLock) state or KeyEvent.META_CAPS_LOCK_ON else state
    }
    /** Android 13+: keys go through the accessibility service's own input connection
     *  ([AccessibilityService.InputMethod]), letters as typed on a US keyboard. There is no
     *  on-screen keyboard to switch, so the Korean/English key (Shift+Space or Control+Space,
     *  per the master's rule for this device) is taken here: it flips flockswitch's own Korean
     *  mode, in which letters are composed by [HangulComposer]. The connection has no
     *  composing text, so the syllable being typed is written, deleted and written again
     *  ([ReplacingComposingTarget]). Other chords such as Shift+Space still go on as keys when they are not the toggle. */
    private fun key(usage: Int, down: Boolean) {
        if (usage in 0xe0..0xe7) { if (down) modifiers.add(usage) else modifiers.remove(usage) }
        if (usage == 0x2c) {
            if (down && isHangulToggle(HangulSettings.toggleKey, usage, modifiers)) { toggleKorean(); swallowSpace = true; return }
            if (!down && swallowSpace) { swallowSpace = false; return }
        }
        val code = hidKeyCode(usage) ?: return
        if (down && code == KeyEvent.KEYCODE_CAPS_LOCK) capsLock = !capsLock
        if (Build.VERSION.SDK_INT < 33) { fallback(code, down); return }
        val input = service.inputMethod?.currentInputConnection
        if (input !== connection) { typer.abandon(); connection = input }
        val connection = input
        if (connection == null) { fallback(code, down); return }
        val state = meta()
        if (!down) {
            if (sentKeys.remove(code)) connection.sendKeyEvent(event(code, false, state))
            return
        }
        if (typer.korean && koreanKey(code, state)) return
        // Modifier events have already been sent. Send both Space edges as keys too.
        if (remoteImeChord(code, state)) {
            connection.sendKeyEvent(event(code, true, state)); sentKeys.add(code)
            return
        }
        if (state and KeyEvent.META_CTRL_ON != 0) {
            val action = when (code) {
                KeyEvent.KEYCODE_C -> android.R.id.copy
                KeyEvent.KEYCODE_V -> android.R.id.paste
                KeyEvent.KEYCODE_X -> android.R.id.cut
                KeyEvent.KEYCODE_A -> android.R.id.selectAll
                else -> null
            }
            if (action != null) { connection.performContextMenuAction(action); return }
        }
        val char = characters.get(code, state)
        if (char > 0 && char and KeyCharacterMap.COMBINING_ACCENT == 0 && !Character.isISOControl(char) &&
            state and (KeyEvent.META_CTRL_ON or KeyEvent.META_META_ON or KeyEvent.META_ALT_ON) == 0) {
            connection.commitText(String(Character.toChars(char)), 1, null)
        } else {
            connection.sendKeyEvent(event(code, true, state)); sentKeys.add(code)
        }
    }
    private fun toggleKorean() {
        if (Build.VERSION.SDK_INT >= 33) {
            val input = service.inputMethod?.currentInputConnection
            if (input !== connection) { typer.abandon(); connection = input }
        }
        val korean = typer.toggle()
        service.shared.setStatus { it.copy(hangul = korean) }
    }
    /** Korean mode, a key goes down. True when the typer took it (a letter, or Backspace that
     *  edited the composition); any other key first makes the composition final. */
    private fun koreanKey(code: Int, state: Int): Boolean {
        if (hidUsage(code) in 0xe0..0xe7) return false
        if (state and (KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON or KeyEvent.META_META_ON) == 0) {
            if (code in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z) {
                if (typer.letter('a' + (code - KeyEvent.KEYCODE_A), state and KeyEvent.META_SHIFT_ON != 0)) return true
            } else if (code == KeyEvent.KEYCODE_DEL) {
                return typer.backspace()
            }
        }
        typer.flush()
        return false
    }
    private fun hidUsage(code: Int): Int = when (code) {
        KeyEvent.KEYCODE_CTRL_LEFT -> 0xe0; KeyEvent.KEYCODE_SHIFT_LEFT -> 0xe1; KeyEvent.KEYCODE_ALT_LEFT -> 0xe2
        KeyEvent.KEYCODE_META_LEFT -> 0xe3; KeyEvent.KEYCODE_CTRL_RIGHT -> 0xe4; KeyEvent.KEYCODE_SHIFT_RIGHT -> 0xe5
        KeyEvent.KEYCODE_ALT_RIGHT -> 0xe6; KeyEvent.KEYCODE_META_RIGHT -> 0xe7
        else -> 0
    }
    private fun event(code: Int, down: Boolean, meta: Int): KeyEvent {
        val now = SystemClock.uptimeMillis()
        return KeyEvent(now, now, if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, code, 0, meta,
            KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_FROM_SYSTEM, android.view.InputDevice.SOURCE_KEYBOARD)
    }
    @Suppress("DEPRECATION")
    private fun fallback(code: Int, down: Boolean) {
        if (!down) return
        when (code) {
            KeyEvent.KEYCODE_ESCAPE -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT,
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT,
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT, KeyEvent.KEYCODE_CAPS_LOCK -> Unit
            else -> {
                if (Build.VERSION.SDK_INT >= 33) { keyboardHint(); return }
                val node = service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                val ok = try {
                    if (node == null || !node.isEditable || !node.isFocused) { typer.abandon(); false }
                    else {
                        val editor = AccessibilityEditor(node)
                        legacyTarget.editor = editor
                        if (typer.korean && koreanKey(code, meta())) true
                        else {
                            val char = characters.get(code, meta())
                            val text = if (char > 0 && char and KeyCharacterMap.COMBINING_ACCENT == 0)
                                String(Character.toChars(char)) else null
                            editRemoteKey(editor, code, meta(), text)
                        }
                    }
                } finally { legacyTarget.editor = null; node?.recycle() }
                if (!ok) keyboardHint()
            }
        }
    }
    private fun keyboardHint() {
        val now = SystemClock.uptimeMillis()
        if (lastKeyboardHint != Long.MIN_VALUE && now - lastKeyboardHint < 5000) return
        lastKeyboardHint = now
        Toast.makeText(service, R.string.net_keyboard_unavailable, Toast.LENGTH_SHORT).show()
    }
    fun reset() {
        handler.removeCallbacks(wake)
        val release = planner.reset()
        queue.clear()
        generation++; busy = false
        val from = held
        held = null
        if (from != null) {
            // The finger that is down lifts where it was last moved to.
            val end = release.filterIsInstance<TouchCommand.Release>().firstOrNull()
            val tx = end?.x ?: heldX
            val ty = end?.y ?: heldY
            val stroke = from.continueStroke(Path().apply { moveTo(heldX, heldY); lineTo(tx, ty) }, 0, PRESS_MS, false)
            service.dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            val c = service.inputMethod?.currentInputConnection
            // The syllable being typed stays in the field as final text.
            if (c === connection) typer.flush() else typer.abandon()
            for (code in sentKeys) c?.sendKeyEvent(event(code, false, 0))
        } else typer.abandon()
        sentKeys.clear(); modifiers.clear(); capsLock = false; swallowSpace = false
        if (shown) windows.removeView(cursor)
        shown = false
    }
}

/** USB HID keyboard page 0x07 to Android key codes. */
fun hidKeyCode(usage: Int): Int? = when (usage) {
    in 0x04..0x1d -> KeyEvent.KEYCODE_A + usage - 0x04
    in 0x1e..0x26 -> KeyEvent.KEYCODE_1 + usage - 0x1e
    0x27 -> KeyEvent.KEYCODE_0
    in 0x3a..0x45 -> KeyEvent.KEYCODE_F1 + usage - 0x3a
    in 0x59..0x61 -> KeyEvent.KEYCODE_NUMPAD_1 + usage - 0x59
    else -> mapOf(
        0x28 to KeyEvent.KEYCODE_ENTER, 0x29 to KeyEvent.KEYCODE_ESCAPE, 0x2a to KeyEvent.KEYCODE_DEL,
        0x2b to KeyEvent.KEYCODE_TAB, 0x2c to KeyEvent.KEYCODE_SPACE, 0x2d to KeyEvent.KEYCODE_MINUS,
        0x2e to KeyEvent.KEYCODE_EQUALS, 0x2f to KeyEvent.KEYCODE_LEFT_BRACKET, 0x30 to KeyEvent.KEYCODE_RIGHT_BRACKET,
        0x31 to KeyEvent.KEYCODE_BACKSLASH, 0x32 to KeyEvent.KEYCODE_POUND, 0x33 to KeyEvent.KEYCODE_SEMICOLON,
        0x34 to KeyEvent.KEYCODE_APOSTROPHE, 0x35 to KeyEvent.KEYCODE_GRAVE, 0x36 to KeyEvent.KEYCODE_COMMA,
        0x37 to KeyEvent.KEYCODE_PERIOD, 0x38 to KeyEvent.KEYCODE_SLASH, 0x39 to KeyEvent.KEYCODE_CAPS_LOCK,
        0x46 to KeyEvent.KEYCODE_SYSRQ, 0x47 to KeyEvent.KEYCODE_SCROLL_LOCK, 0x48 to KeyEvent.KEYCODE_BREAK,
        0x49 to KeyEvent.KEYCODE_INSERT, 0x4a to KeyEvent.KEYCODE_MOVE_HOME, 0x4b to KeyEvent.KEYCODE_PAGE_UP,
        0x4c to KeyEvent.KEYCODE_FORWARD_DEL, 0x4d to KeyEvent.KEYCODE_MOVE_END, 0x4e to KeyEvent.KEYCODE_PAGE_DOWN,
        0x4f to KeyEvent.KEYCODE_DPAD_RIGHT, 0x50 to KeyEvent.KEYCODE_DPAD_LEFT, 0x51 to KeyEvent.KEYCODE_DPAD_DOWN,
        0x52 to KeyEvent.KEYCODE_DPAD_UP, 0x53 to KeyEvent.KEYCODE_NUM_LOCK, 0x54 to KeyEvent.KEYCODE_NUMPAD_DIVIDE,
        0x55 to KeyEvent.KEYCODE_NUMPAD_MULTIPLY, 0x56 to KeyEvent.KEYCODE_NUMPAD_SUBTRACT, 0x57 to KeyEvent.KEYCODE_NUMPAD_ADD,
        0x58 to KeyEvent.KEYCODE_NUMPAD_ENTER, 0x62 to KeyEvent.KEYCODE_NUMPAD_0, 0x63 to KeyEvent.KEYCODE_NUMPAD_DOT,
        0x64 to KeyEvent.KEYCODE_BACKSLASH, 0x65 to KeyEvent.KEYCODE_MENU, 0x67 to KeyEvent.KEYCODE_NUMPAD_EQUALS,
        0x90 to KeyEvent.KEYCODE_LANGUAGE_SWITCH, 0x91 to KeyEvent.KEYCODE_HENKAN,
        0xe0 to KeyEvent.KEYCODE_CTRL_LEFT, 0xe1 to KeyEvent.KEYCODE_SHIFT_LEFT, 0xe2 to KeyEvent.KEYCODE_ALT_LEFT,
        0xe3 to KeyEvent.KEYCODE_META_LEFT, 0xe4 to KeyEvent.KEYCODE_CTRL_RIGHT, 0xe5 to KeyEvent.KEYCODE_SHIFT_RIGHT,
        0xe6 to KeyEvent.KEYCODE_ALT_RIGHT, 0xe7 to KeyEvent.KEYCODE_META_RIGHT,
    )[usage]
}
