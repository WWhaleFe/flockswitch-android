// The only surface Android gives an app for a keyboard's keys and the mouse pointer of
// the whole system: an accessibility service, turned on by the user.
//
// Keys: while a setting needs them, every key passes through onKeyEvent and is let go on
// unchanged, except the find-the-cursor shortcut. Only Control's state is kept.
//
// The pointer: Android 14+ can send a service the mouse's events (setMotionEventSources),
// but they are then taken away from the apps, clicks included; watching them without
// taking them (setObservedMotionEventSources) is @hide/@TestApi. So the mouse is asked for
// only from a trigger until its first event, which gives the position.

package io.github.wwhalefe.flockswitch

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** How long a trigger waits for the mouse to move before giving up. */
private const val WAIT_FOR_MOVE_MS = 5000L

/** Whether Android can tell this app where the pointer is at all. */
val pointerReadable: Boolean get() = Build.VERSION.SDK_INT >= 34

/** Whether this key event is the find-the-cursor shortcut, Ctrl+Alt+L. */
fun isLocateShortcut(e: KeyEvent): Boolean =
    e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0 && e.keyCode == KeyEvent.KEYCODE_L &&
        e.isCtrlPressed && e.isAltPressed && !e.isShiftPressed && !e.isMetaPressed

class PointerService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private val overlay by lazy { LocateOverlay(this, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY) }
    private var wantKeys = false
    /** A trigger is waiting for the mouse's next event. */
    private var waiting = false
    private val giveUp = Runnable { stopWaiting() }

    override fun onServiceConnected() {
        instance = this
        shared.setStatus { it.copy(pointerService = true) }
        scope.launch { shared.config.collect(::follow) }
    }

    private fun follow(cfg: Config) {
        wantKeys = (cfg.edges.requireControl && cfg.edges.any()) || (cfg.locate.on && cfg.locate.onHotkey)
        shared.pointer.setControl(if (wantKeys) shared.pointer.ctrl ?: false else null)
        if (!cfg.locate.on) stopWaiting()
        request()
    }

    /** Asks the system for exactly what is needed right now. */
    private fun request() {
        val info = serviceInfo ?: return
        val filter = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        info.flags = if (wantKeys) info.flags or filter else info.flags and filter.inv()
        if (Build.VERSION.SDK_INT >= 34) info.motionEventSources = if (waiting) InputDevice.SOURCE_MOUSE else 0
        serviceInfo = info
    }

    /** Shows where the pointer is as soon as the mouse moves. */
    fun locateOnNextMove() {
        if (!pointerReadable || !shared.config.value.locate.on) return
        waiting = true
        request()
        handler.removeCallbacks(giveUp)
        handler.postDelayed(giveUp, WAIT_FOR_MOVE_MS)
    }

    private fun stopWaiting() {
        handler.removeCallbacks(giveUp)
        if (!waiting) return
        waiting = false
        request()
    }

    override fun onMotionEvent(event: MotionEvent) {
        if (!waiting || !event.isFromSource(InputDevice.SOURCE_MOUSE)) return
        stopWaiting()
        overlay.show(shared.config.value.locate, event.rawX, event.rawY)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (shared.pointer.ctrl != null) shared.pointer.setControl(event.isCtrlPressed)
        val locate = shared.config.value.locate
        if (locate.on && locate.onHotkey && isLocateShortcut(event)) {
            locateOnNextMove()
            return true
        }
        return false
    }

    /** Plays the highlight once, for the settings. */
    fun preview(locate: Locate, x: Float, y: Float) = overlay.show(locate, x, y)

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        handler.removeCallbacks(giveUp)
        scope.cancel()
        overlay.end()
        shared.pointer.setControl(null)
        shared.setStatus { it.copy(pointerService = false) }
        super.onDestroy()
    }

    companion object {
        /** The running service, while the user has it turned on. Main thread only. */
        var instance: PointerService? = null
            private set
    }
}
