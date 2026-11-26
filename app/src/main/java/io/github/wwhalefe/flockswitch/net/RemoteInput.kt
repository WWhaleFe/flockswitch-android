package io.github.wwhalefe.flockswitch.net

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
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
import android.view.WindowManager
import org.json.JSONObject
import kotlin.math.abs

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
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.LEFT; if (Build.VERSION.SDK_INT >= 30) setFitInsetsTypes(0) }
    private var left = false
    private val buttons = java.util.ArrayDeque<Triple<Boolean, Float, Float>>()
    private var stroke: GestureDescription.StrokeDescription? = null
    private var strokeX = 0f
    private var strokeY = 0f
    private var busy = false
    private var generation = 0
    private var scrollX = 0.0
    private var scrollY = 0.0
    private val modifiers = mutableSetOf<Int>()
    private val sentKeys = mutableSetOf<Int>()
    private val characters = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
    private val pump = Runnable { gesture() }

    fun action(a: JSONObject) {
        if (a.has("cursor")) {
            val c = a.getJSONObject("cursor")
            if (!c.optBoolean("visible", true)) { reset(); return }
            x = c.optDouble("x", x.toDouble()).toFloat(); y = c.optDouble("y", y.toDouble()).toFloat()
            params.x = x.toInt(); params.y = y.toInt()
            if (!shown) { windows.addView(cursor, params); shown = true } else windows.updateViewLayout(cursor, params)
            if (left) schedule()
            return
        }
        when (a.optString("type")) {
            "Button" -> {
                val down = a.getBoolean("down")
                when (a.getString("button")) {
                    "Left" -> { buttons.add(Triple(down, x, y)); if (!busy) gesture() else schedule() }
                    "Right", "Back" -> if (down) service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    "Middle" -> if (down) service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                    "Forward" -> if (down) service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
                }
            }
            "Scroll" -> { scrollX += a.getDouble("dx"); scrollY += a.getDouble("dy"); schedule() }
            "Key" -> key(a.getInt("usage"), a.getBoolean("down"))
        }
    }
    private fun schedule() { handler.removeCallbacks(pump); handler.postDelayed(pump, 8) }
    private fun gesture() {
        if (busy) return
        val button = buttons.pollFirst()
        if (button != null) left = button.first
        val targetX = button?.second ?: x
        val targetY = button?.third ?: y
        if (left || stroke != null) {
            val path = Path().apply { moveTo(if (stroke == null) targetX else strokeX, if (stroke == null) targetY else strokeY); lineTo(targetX, targetY) }
            val next = stroke?.continueStroke(path, 0, 24, left) ?: GestureDescription.StrokeDescription(path, 0, 24, left)
            strokeX = targetX; strokeY = targetY
            stroke = if (left) next else null
            dispatch(next)
        } else if (abs(scrollX) + abs(scrollY) > 0.001) {
            val dm = screenMetrics(service)
            val endX = (x - scrollX.toFloat() * dm.heightPixels / 10).coerceIn(1f, (dm.widthPixels - 1).toFloat())
            val endY = (y - scrollY.toFloat() * dm.heightPixels / 10).coerceIn(1f, (dm.heightPixels - 1).toFloat())
            scrollX = 0.0; scrollY = 0.0
            val path = Path().apply { moveTo(x.coerceAtLeast(1f), y.coerceAtLeast(1f)); lineTo(endX, endY) }
            dispatch(GestureDescription.StrokeDescription(path, 0, 80))
        }
    }
    private fun dispatch(s: GestureDescription.StrokeDescription) {
        busy = true
        val epoch = generation
        val accepted = service.dispatchGesture(GestureDescription.Builder().addStroke(s).build(), object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription) { if (epoch == generation) { busy = false; schedule() } }
            override fun onCancelled(gestureDescription: GestureDescription) { if (epoch == generation) { busy = false; stroke = null; left = false; schedule() } }
        }, handler)
        if (!accepted) { busy = false; stroke = null; left = false }
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
        return state
    }
    private fun key(usage: Int, down: Boolean) {
        if (usage in 0xe0..0xe7) { if (down) modifiers.add(usage) else modifiers.remove(usage) }
        val code = hidKeyCode(usage) ?: return
        if (Build.VERSION.SDK_INT < 33) { fallback(code, down); return }
        val connection = service.inputMethod?.currentInputConnection
        if (connection == null) { fallback(code, down); return }
        val state = meta()
        if (!down) {
            if (sentKeys.remove(code)) connection.sendKeyEvent(event(code, false, state))
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
    private fun event(code: Int, down: Boolean, meta: Int): KeyEvent {
        val now = SystemClock.uptimeMillis()
        return KeyEvent(now, now, if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, code, 0, meta,
            KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_FROM_SYSTEM, android.view.InputDevice.SOURCE_KEYBOARD)
    }
    private fun fallback(code: Int, down: Boolean) {
        if (!down) return
        when (code) {
            KeyEvent.KEYCODE_ESCAPE -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        }
    }
    fun reset() {
        left = false
        buttons.clear()
        handler.removeCallbacks(pump)
        if (stroke != null) {
            val path = Path().apply { moveTo(strokeX, strokeY); lineTo(strokeX, strokeY) }
            val end = stroke!!.continueStroke(path, 0, 1, false)
            service.dispatchGesture(GestureDescription.Builder().addStroke(end).build(), null, null)
        }
        generation++; busy = false; stroke = null; scrollX = 0.0; scrollY = 0.0
        if (Build.VERSION.SDK_INT >= 33) {
            val c = service.inputMethod?.currentInputConnection
            for (code in sentKeys) c?.sendKeyEvent(event(code, false, 0))
        }
        sentKeys.clear(); modifiers.clear()
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
