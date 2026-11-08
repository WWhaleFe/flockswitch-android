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
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

class EdgeWatcher(private val context: Context, private val shared: Shared) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val views = mutableMapOf<Edge, View>()
    /** The settings the strips on screen were made for. */
    private var shown: Edges? = null
    /** The edge the pointer rests on, and the switch waiting for the dwell time. */
    private var resting: Edge? = null
    private var pending: Runnable? = null
    /** The pointer has left the strips since the last decision, so a new rest counts. */
    private var armed = true
    private var lastSwitch = 0L
    /** While the pointer waits at an edge that will switch, the mouse pulses (if it has a motor). */
    private var haptics = Haptics()
    private var tick: Runnable? = null

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
        val bounds = screen()
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
                    MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> enter(edge, edges)
                    MotionEvent.ACTION_HOVER_EXIT -> leave(edge)
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
        pending?.let(handler::removeCallbacks)
        pending = null
        stopTicks()
        resting = null
        for (v in views.values) runCatching { wm.removeView(v) }
        views.clear()
        shown = null
    }

    private fun screen(): Rect {
        if (Build.VERSION.SDK_INT >= 30) return wm.currentWindowMetrics.bounds
        @Suppress("DEPRECATION")
        val size = Point().also { wm.defaultDisplay.getRealSize(it) }
        return Rect(0, 0, size.x, size.y)
    }

    private fun enter(edge: Edge, edges: Edges) {
        if (resting == edge) return
        leave(resting)
        resting = edge
        if (!armed) return
        val run = Runnable { fire(edge, edges) }
        pending = run
        handler.postDelayed(run, edges.dwellMs)
        val channel = edges.channel(edge)
        val s = shared.status.value
        if (haptics.onEdge && channel != 0 && s.trigger != null && s.channel != channel) {
            val t = object : Runnable {
                override fun run() {
                    shared.requestHaptic(haptics.edgeWaveform)
                    handler.postDelayed(this, HAPTIC_TICK_MS)
                }
            }
            tick = t
            handler.post(t)
        }
    }

    private fun stopTicks() {
        tick?.let(handler::removeCallbacks)
        tick = null
    }

    private fun leave(edge: Edge?) {
        if (edge == null || resting != edge) return
        pending?.let(handler::removeCallbacks)
        pending = null
        stopTicks()
        resting = null
        armed = true
    }

    /** The dwell time is up: one decision per visit to the edge. */
    private fun fire(edge: Edge, edges: Edges) {
        pending = null
        stopTicks()
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
                shared.requestSwitch(channel)
            }
        }
    }

    companion object {
        /** After one switch, the next needs the pointer to leave the edge and this long. */
        private const val COOLDOWN_MS = 2000L
        private const val HAPTIC_TICK_MS = 150L
    }
}
