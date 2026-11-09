// Switching at the screen edge, as Logi Flow does but without a network: when the
// mouse pointer rests against an edge of the screen for a moment, the group and the
// master mouse go to the channel set for that edge. Android gives no app the pointer
// position, so four thin invisible windows along the screen's edges ("display over
// other apps") notice the pointer hovering over them. Each device watches only its own
// screen; nothing is sent between computers.

package io.github.wwhalefe.flockswitch

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

/** How often a wait at an edge is looked at, as on the desktop versions. */
private const val POLL_MS = 25L
/** After one switch, the next needs the pointer to leave the edge and this long. */
private const val COOLDOWN_MS = 2000L
/** With the rising pulses off, the mouse pulses this often while the pointer waits. */
private const val HAPTIC_TICK_MS = 150L
/** A pulse takes a moment to play; Change Host right after would cut it short. */
private const val FINAL_PULSE_MS = 150L

/** One pulse at a screen edge: the gap until the next one and its waveform. */
data class EdgePulse(val gapMs: Long, val waveform: Int)

/** How the pulses go at [progress] (0..1) of the wait: quicker and sharper as the switch
 *  nears. */
fun risingPulse(progress: Double): EdgePulse = when {
    progress < 0.40 -> EdgePulse(180, Waveform.SUBTLE_COLLISION)
    progress < 0.75 -> EdgePulse(110, Waveform.DAMP_COLLISION)
    else -> EdgePulse(60, Waveform.SHARP_COLLISION)
}

/** The pulse for this moment of a wait, as the settings ask. */
fun edgePulse(h: Haptics, progress: Double): EdgePulse =
    if (h.progressive) risingPulse(progress) else EdgePulse(HAPTIC_TICK_MS, h.edgeWaveform)

/** The rising pulses end in one last pulse; with pulses on switching, the engine's own
 *  switch pulse is that one. */
fun needsFinalPulse(h: Haptics): Boolean = h.onEdge && h.progressive && !h.onSwitch

/** Whether a pointer event says a Control key is down. Android stamps pointer events with
 *  the modifiers held on any keyboard, but only when the pointer moves. */
fun controlDown(metaState: Int): Boolean = metaState and KeyEvent.META_CTRL_ON != 0

/** The whole screen, in pixels. */
internal fun screenBounds(wm: WindowManager): Rect {
    if (Build.VERSION.SDK_INT >= 30) return wm.currentWindowMetrics.bounds
    @Suppress("DEPRECATION")
    val size = Point().also { wm.defaultDisplay.getRealSize(it) }
    return Rect(0, 0, size.x, size.y)
}

class EdgeWatcher(private val context: Context, private val shared: Shared) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val views = mutableMapOf<Edge, View>()
    /** The countdown drawn while a switch is waiting. */
    private val overlay = EdgeOverlay(context)
    /** The settings the strips on screen were made for. */
    private var shown: Edges? = null
    /** The edge the pointer rests on, since when, and the look at it that repeats. */
    private var resting: Edge? = null
    private var restStart = 0L
    private var poll: Runnable? = null
    /** The pointer has left the strips since the last decision, so a new rest counts. */
    private var armed = true
    private var lastSwitch = 0L
    /** Where along the edge the pointer last was, in screen pixels. */
    private var along = 0f
    /** The strip the pointer is over, whether or not a wait is running. */
    private var under: Edge? = null
    /** While the pointer waits at an edge that will switch, the mouse pulses (if it has a motor). */
    private var haptics = Haptics()
    private var lastTick: Long? = null
    /** The countdown is on the screen. */
    private var counting = false
    /** A switch waiting for the last pulse to play. */
    private var delayedSwitch: Runnable? = null

    /** Whether the strips may be shown: "display over other apps" is allowed. */
    fun allowed(): Boolean = Settings.canDrawOverlays(context)

    /** Shows the strips the settings ask for, or hides them. Main thread only. */
    fun sync(cfg: Config) {
        haptics = cfg.haptics
        val want = if (cfg.enabled && cfg.edges.any() && allowed()) cfg.edges else null
        if (want == shown) return
        hide()
        shown = want
        val edges = want ?: return
        shared.pointer.onControl = ::controlChanged
        val bounds = screenBounds(wm)
        val density = context.resources.displayMetrics.density
        val margin = (edges.cornerMargin * density).toInt().coerceAtMost(minOf(bounds.width(), bounds.height()) / 3)
        val thick = (2 * density).toInt().coerceAtLeast(2)
        for (edge in Edge.entries) {
            if (edges.channel(edge) == 0) continue
            val params = WindowManager.LayoutParams(
                if (edge.vertical) thick else bounds.width() - 2 * margin,
                if (edge.vertical) bounds.height() - 2 * margin else thick,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                @Suppress("RtlHardcoded")
                gravity = Gravity.TOP or Gravity.LEFT
                x = when (edge) {
                    Edge.Left -> 0
                    Edge.Right -> bounds.width() - thick
                    else -> margin
                }
                y = when (edge) {
                    Edge.Top -> 0
                    Edge.Bottom -> bounds.height() - thick
                    else -> margin
                }
            }
            val view = View(context)
            view.setOnHoverListener { _, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> hover(edge, edges, e)
                    MotionEvent.ACTION_HOVER_EXIT -> {
                        if (under == edge) under = null
                        leave(edge)
                    }
                }
                true
            }
            try {
                wm.addView(view, params)
                views[edge] = view
            } catch (e: Exception) {
                Log.w("edge window (${edge.name.lowercase()}): ${e.message}")
            }
        }
        if (views.isNotEmpty()) {
            val list = Edge.entries.filter { edges.channel(it) != 0 }.joinToString { "${it.name.lowercase()} → ${edges.channel(it)}" }
            Log.i("edges: $list after ${edges.dwellMs} ms")
        }
    }

    /** The screen turned or changed size: lay the strips out again. */
    fun relayout(cfg: Config) {
        hide()
        sync(cfg)
    }

    fun hide() {
        delayedSwitch?.let(handler::removeCallbacks)
        delayedSwitch = null
        stopRest()
        under = null
        shared.pointer.onControl = null
        for (v in views.values) runCatching { wm.removeView(v) }
        views.clear()
        shown = null
    }

    /** The pointer moved over a strip. */
    private fun hover(edge: Edge, edges: Edges, e: MotionEvent) {
        along = if (edge.vertical) e.rawY else e.rawX
        under = edge
        overlay.follow(along)
        // With "hold Control" the wait starts only with the key down; letting go calls
        // it off, and pressing again at the same edge starts over. The accessibility
        // service, when on, knows the key at once; otherwise the event says, as of now.
        if (edges.requireControl && !(shared.pointer.ctrl ?: controlDown(e.metaState))) {
            stopRest()
            return
        }
        enter(edge, edges)
    }

    /** Control went down or up, the pointer perhaps standing still. */
    private fun controlChanged(down: Boolean) {
        val edge = under ?: return
        val edges = shown ?: return
        if (!edges.requireControl) return
        if (down) enter(edge, edges) else stopRest()
    }

    private fun enter(edge: Edge, edges: Edges) {
        if (resting == edge) return
        leave(resting)
        resting = edge
        restStart = SystemClock.uptimeMillis()
        if (!armed) return
        val run = object : Runnable {
            override fun run() {
                if (look(edge, edges)) handler.postDelayed(this, POLL_MS)
            }
        }
        poll = run
        handler.post(run)
    }

    /** One look at a wait in progress; false once the wait is over. */
    private fun look(edge: Edge, edges: Edges): Boolean {
        val elapsed = SystemClock.uptimeMillis() - restStart
        val channel = edges.channel(edge)
        val s = shared.status.value
        val cooling = System.currentTimeMillis() - lastSwitch < COOLDOWN_MS
        if (channel != 0 && s.trigger != null && s.channel != channel && !cooling) {
            if (!counting) {
                overlay.begin(edge, edges, along, elapsed, edges.dwellMs)
                counting = true
            }
            // Waiting at an edge that will switch: the mouse pulses so the hand knows.
            if (haptics.onEdge) {
                val pulse = edgePulse(haptics, elapsed.toDouble() / edges.dwellMs)
                val now = SystemClock.uptimeMillis()
                val last = lastTick
                if (last == null || now - last >= pulse.gapMs) {
                    lastTick = now
                    shared.requestHaptic(pulse.waveform)
                }
            }
        } else {
            endCountdown()
        }
        if (elapsed < edges.dwellMs) return true
        fire(edge, edges)
        return false
    }

    private fun endCountdown() {
        if (counting) overlay.end()
        counting = false
    }

    /** Calls off the wait in progress, if there is one. */
    private fun stopRest() {
        poll?.let(handler::removeCallbacks)
        poll = null
        lastTick = null
        endCountdown()
        resting = null
    }

    private fun leave(edge: Edge?) {
        if (edge == null || resting != edge) return
        stopRest()
        armed = true
    }

    /** The dwell time is up: one decision per visit to the edge. */
    private fun fire(edge: Edge, edges: Edges) {
        poll = null
        lastTick = null
        endCountdown()
        armed = false
        val channel = edges.channel(edge)
        val s = shared.status.value
        val now = System.currentTimeMillis()
        val name = edge.name.lowercase()
        when {
            s.trigger == null -> Log.d("$name edge: the master mouse is not here")
            s.channel == channel -> Log.d("$name edge: channel $channel is this device")
            now - lastSwitch < COOLDOWN_MS -> {}
            else -> {
                Log.i("$name edge: channel $channel")
                lastSwitch = now
                if (needsFinalPulse(haptics)) {
                    shared.requestHaptic(Waveform.COMPLETED)
                    val go = Runnable {
                        delayedSwitch = null
                        shared.requestSwitch(channel)
                    }
                    delayedSwitch = go
                    handler.postDelayed(go, FINAL_PULSE_MS)
                } else {
                    shared.requestSwitch(channel)
                }
            }
        }
    }
}
