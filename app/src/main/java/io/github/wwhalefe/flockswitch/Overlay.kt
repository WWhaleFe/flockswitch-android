// What is drawn over the screen, in windows that take no input: the countdown near the
// pointer while it waits at a screen edge, and the highlight that shows where the pointer
// is. Every effect is one drawing function in a table, keyed by EdgeAnimStyle or
// LocateStyle: to add one, write the function, add it to the table, add the enum value
// and its name (the string array edge_anim_names, or a locate_* string).
//
// The drawing is the same as on macOS: the canvas is turned into y-up points (dp), so
// the numbers and the angles below are those of overlay_macos.m.

package io.github.wwhalefe.flockswitch

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.hardware.input.InputManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** How far into the screen the countdown window reaches (dp) at 100 %; a larger size
 *  makes the window deeper to match. */
private const val STRIP_DEPTH_DP = 240

/** An effect size from the settings (percent) as a scale factor. */
internal fun effectScale(percent: Int): Double = if (validEffectSize(percent)) percent / 100.0 else 1.0

private const val LOCATE_MS = 1300L
private const val LOCATE_FADE_S = 0.35

/** A colour from the settings, as 0xRRGGBB. */
private fun effectColor(text: String): Int = parseColor(text) ?: parseColor(DEFAULT_EFFECT_COLOR) ?: 0xFFCC00

/** Repeatable noise in 0..1, so particles and sparks stay put from frame to frame. */
private fun noise(n: Int): Double {
    val s = sin(n * 127.1) * 43758.5453
    return s - floor(s)
}

private fun smooth(from: Double, to: Double, v: Double): Double {
    val x = ((v - from) / (to - from)).coerceIn(0.0, 1.0)
    return x * x * (3 - 2 * x)
}

private data class Pt(val x: Double, val y: Double)

/** What every effect draws with, in y-up dp with the origin at the window's lower left. */
abstract class OverlayFrame internal constructor(
    val c: Canvas,
    internal val paint: Paint,
    /** The colour chosen for this effect, as 0xRRGGBB. */
    internal val rgb: Int,
)

/** One frame of a countdown. */
class EdgeFrame internal constructor(
    c: Canvas,
    val width: Double,
    val height: Double,
    /** The pointer, projected onto the screen edge. */
    internal val anchorX: Double,
    internal val anchorY: Double,
    /** Unit vector pointing into the screen. */
    internal val inX: Double,
    internal val inY: Double,
    /** Unit vector along the edge. */
    internal val alongX: Double,
    internal val alongY: Double,
    /** Share of the wait that has passed (0..1); at 1 the effect has gone. */
    val p: Double,
    /** Seconds since the wait began. */
    val t: Double,
    val direction: EdgeAnimDirection,
    val shrink: Boolean,
    /** The chosen size: the canvas is already scaled by this around the anchor, so an
     *  effect only needs it to reach a point on the window's own edge. */
    val scale: Double,
    paint: Paint,
    rgb: Int,
) : OverlayFrame(c, paint, rgb)

typealias EdgeRenderer = (EdgeFrame) -> Unit

/** One frame of the highlight. */
class LocateFrame internal constructor(
    c: Canvas,
    val width: Double,
    val height: Double,
    internal val centerX: Double,
    internal val centerY: Double,
    /** Seconds since the effect began. */
    val t: Double,
    /** 1 until the fade-out, then down to 0. */
    val fade: Double,
    /** The chosen size, as for [EdgeFrame.scale], around the centre. */
    val scale: Double,
    paint: Paint,
    rgb: Int,
) : OverlayFrame(c, paint, rgb)

typealias LocateRenderer = (LocateFrame) -> Unit

private val EdgeFrame.anchor get() = Pt(anchorX, anchorY)

/** A point [a] along the edge and [d] into the screen, from the pointer. */
private fun EdgeFrame.fromAnchor(a: Double, d: Double) =
    Pt(anchorX + alongX * a + inX * d, anchorY + alongY * a + inY * d)

/** The effect's colour with this much opacity (0..1). */
private fun OverlayFrame.accent(alpha: Double): Int = ((alpha.coerceIn(0.0, 1.0) * 255).toInt() shl 24) or rgb

private fun OverlayFrame.stroke(alpha: Double) {
    paint.style = Paint.Style.STROKE
    paint.color = accent(alpha)
}

private fun OverlayFrame.fill(alpha: Double) {
    paint.style = Paint.Style.FILL
    paint.color = accent(alpha)
}

private fun OverlayFrame.glow(blur: Double, alpha: Double) = paint.setShadowLayer(blur.toFloat(), 0f, 0f, accent(alpha))

private fun OverlayFrame.noGlow() = paint.clearShadowLayer()

private fun OverlayFrame.lineWidth(w: Double) {
    paint.strokeWidth = w.toFloat()
}

private fun OverlayFrame.circle(o: Pt, r: Double) = c.drawCircle(o.x.toFloat(), o.y.toFloat(), r.toFloat(), paint)

private fun OverlayFrame.line(a: Pt, b: Pt) = c.drawLine(a.x.toFloat(), a.y.toFloat(), b.x.toFloat(), b.y.toFloat(), paint)

private fun Path.moveTo(o: Pt) = moveTo(o.x.toFloat(), o.y.toFloat())

private fun Path.lineTo(o: Pt) = lineTo(o.x.toFloat(), o.y.toFloat())

/** An arc from angle [from] to [to] (radians, counter-clockwise from +x in y-up space,
 *  unless [clockwise]). Continues the current path when [join] is set. */
private fun Path.arc(o: Pt, r: Double, from: Double, to: Double, clockwise: Boolean, join: Boolean = false) {
    var sweep = to - from
    while (!clockwise && sweep < 0) sweep += 2 * PI
    while (clockwise && sweep > 0) sweep -= 2 * PI
    val steps = maxOf(4, ceil(abs(sweep) / (2 * PI) * 96).toInt())
    for (i in 0..steps) {
        val a = from + sweep * i / steps
        val q = Pt(o.x + cos(a) * r, o.y + sin(a) * r)
        if (i == 0 && !join) moveTo(q) else lineTo(q)
    }
}

// The effects.

private fun edgeRing(f: EdgeFrame) {
    val radius = 24.0
    val left = 1 - f.p
    if (left <= 0.002) return
    val o = f.fromAnchor(0.0, radius + 18)
    f.paint.strokeCap = Paint.Cap.ROUND
    f.lineWidth(5.0)
    if (f.shrink) {
        f.glow(12.0, 0.9)
        f.stroke(1.0)
        f.circle(o, radius * left)
        return
    }
    f.stroke(0.18)
    f.circle(o, radius)
    f.glow(12.0, 0.9)
    f.stroke(1.0)
    val top = PI / 2
    val path = Path()
    when (f.direction) {
        EdgeAnimDirection.CounterClockwise -> path.arc(o, radius, top + 2 * PI * f.p, top + 2 * PI, clockwise = false)
        EdgeAnimDirection.Both -> path.arc(o, radius, -PI / 2 - PI * left, -PI / 2 + PI * left, clockwise = false)
        EdgeAnimDirection.Clockwise -> path.arc(o, radius, top - 2 * PI * f.p, top - 2 * PI, clockwise = true)
    }
    f.c.drawPath(path, f.paint)
}

private fun edgeSemicircle(f: EdgeFrame) {
    val radius = 54.0
    val left = 1 - f.p
    if (left <= 0.002) return
    val o = f.anchor
    val mid = atan2(f.inY, f.inX)
    f.paint.strokeCap = Paint.Cap.ROUND
    if (f.shrink) {
        f.glow(14.0, 0.8)
        f.fill(0.85)
        val path = Path()
        path.moveTo(o)
        path.arc(o, radius * left, mid - PI / 2, mid + PI / 2, clockwise = false, join = true)
        path.close()
        f.c.drawPath(path, f.paint)
        return
    }
    f.lineWidth(6.0)
    f.stroke(0.18)
    val track = Path()
    track.arc(o, radius, mid - PI / 2, mid + PI / 2, clockwise = false)
    f.c.drawPath(track, f.paint)

    val from: Double
    val to: Double
    var clockwise = false
    when (f.direction) {
        EdgeAnimDirection.CounterClockwise -> {
            from = mid - PI / 2 + PI * f.p
            to = mid + PI / 2
        }
        EdgeAnimDirection.Both -> {
            from = mid - PI / 2 * left
            to = mid + PI / 2 * left
        }
        EdgeAnimDirection.Clockwise -> {
            from = mid + PI / 2 - PI * f.p
            to = mid - PI / 2
            clockwise = true
        }
    }
    f.fill(0.16)
    val wedge = Path()
    wedge.moveTo(o)
    wedge.arc(o, radius, from, to, clockwise, join = true)
    wedge.close()
    f.c.drawPath(wedge, f.paint)
    f.glow(12.0, 0.9)
    f.stroke(1.0)
    val arc = Path()
    arc.arc(o, radius, from, to, clockwise)
    f.c.drawPath(arc, f.paint)
}

private fun edgeBar(f: EdgeFrame) {
    val length = 260.0
    val inset = 5.0
    var low = -length / 2
    var high = length / 2
    // Clockwise eats the bar from the top, or from the left on a horizontal edge.
    val vertical = f.alongY != 0.0
    val fromHigh = vertical == (f.direction == EdgeAnimDirection.Clockwise)
    when {
        f.direction == EdgeAnimDirection.Both -> {
            low += length * f.p / 2
            high -= length * f.p / 2
        }
        fromHigh -> high -= length * f.p
        else -> low += length * f.p
    }
    f.paint.strokeCap = Paint.Cap.ROUND
    f.lineWidth(6.0)
    f.stroke(0.16)
    f.line(f.fromAnchor(-length / 2, inset), f.fromAnchor(length / 2, inset))
    if (high - low <= 0.5) return
    f.glow(14.0, 1.0)
    f.stroke(1.0)
    f.line(f.fromAnchor(low, inset), f.fromAnchor(high, inset))
}

private fun edgePulseWave(f: EdgeFrame) {
    // The last fifth of the wait pulls the waves in to a final ping.
    val contract = smooth(0.8, 1.0, f.p)
    val reach = 130 * (1 - contract)
    f.glow(8.0, 0.7)
    f.lineWidth(2.5)
    for (i in 0 until 3) {
        val phase = (f.t * 1.4 + i / 3.0) % 1.0
        f.stroke((1 - phase) * 0.9 * (1 - contract * 0.5))
        f.circle(f.anchor, phase * reach)
    }
    val core = (8 + 3 * sin(f.t * 10)) * (1 - contract) + 16 * sin(PI * contract) * (1 - contract)
    if (core <= 0.05) return
    f.glow(16.0, 1.0)
    f.fill(1.0)
    f.circle(f.anchor, core)
}

private fun edgeLaser(f: EdgeFrame) {
    val inset = 3.0
    val here = f.anchorX * f.alongX + f.anchorY * f.alongY
    val total = f.width * f.alongX + f.height * f.alongY
    // The beams start at the ends of the screen whatever the size.
    val ends = doubleArrayOf(-here / f.scale, (total - here) / f.scale)
    val closing = f.p * f.p * (3 - 2 * f.p)
    val fade = 1 - smooth(0.95, 1.0, f.p)
    val frame = floor(f.t * 60).toInt()
    f.paint.strokeCap = Paint.Cap.ROUND
    f.glow(12.0, 0.9)
    var side = 0
    while (side < 2 && fade > 0) {
        val head = ends[side] * (1 - closing)
        val tail = min(240.0, abs(ends[side] - head)) * (if (ends[side] < 0) -1 else 1)
        val steps = 14
        for (k in 0 until steps) {
            val a0 = head + tail * k / steps
            val a1 = head + tail * (k + 1) / steps
            val jitter = (noise(frame + k * 7 + side) - 0.5) * 2.4
            f.stroke((1 - k.toDouble() / steps).pow(1.5) * fade)
            f.lineWidth(3.5)
            f.line(f.fromAnchor(a0, inset + jitter), f.fromAnchor(a1, inset + jitter))
        }
        side++
    }
    val burst = smooth(0.84, 1.0, f.p)
    if (burst <= 0 || burst >= 1) return
    f.noGlow()
    f.glow(6.0, 1.0)
    f.lineWidth(1.6)
    val mid = atan2(f.inY, f.inX)
    f.stroke(1 - burst)
    val sparks = Path()
    for (i in 0 until 18) {
        val angle = mid + (noise(i * 3) - 0.5) * PI * 0.95
        val r0 = 8 + 70 * burst * noise(i * 5)
        val r1 = r0 + 12 + 26 * noise(i * 7)
        sparks.moveTo(Pt(f.anchorX + cos(angle) * r0, f.anchorY + sin(angle) * r0))
        sparks.lineTo(Pt(f.anchorX + cos(angle) * r1, f.anchorY + sin(angle) * r1))
    }
    f.c.drawPath(sparks, f.paint)
}

private fun edgeVortex(f: EdgeFrame) {
    val o = f.fromAnchor(0.0, 72.0)
    val closing = f.p.pow(1.6)
    val fade = 1 - smooth(0.92, 1.0, f.p)
    if (fade <= 0) return
    f.glow(6.0, 0.8)
    for (i in 0 until 56) {
        val speed = (3 + 11 * f.p) * (0.7 + 0.6 * noise(i + 100))
        val angle = noise(i) * 2 * PI + f.t * speed
        val radius = (24 + 58 * noise(i + 200)) * (1 - closing)
        val size = (1.2 + 2.4 * noise(i + 300)) * (1 - f.p * 0.5)
        f.fill((0.35 + 0.65 * noise(i + 400)) * fade)
        f.circle(Pt(o.x + cos(angle) * radius, o.y + sin(angle) * radius), size)
    }
    f.glow(18.0, 1.0)
    f.fill(fade)
    f.circle(o, (3 + 13 * f.p) * fade)
}

private fun edgeScan(f: EdgeFrame) {
    val o = f.fromAnchor(0.0, 72.0)
    val radius = 46 * (1 - f.p * 0.25)
    var alpha = 1 - smooth(0.96, 1.0, f.p)
    // The closer to the end, the faster and harder it flickers.
    val unrest = smooth(0.55, 1.0, f.p)
    if (unrest > 0 && (f.t * (18 + 60 * unrest)) % 1.0 < 0.1 + 0.4 * unrest) alpha *= 0.25
    if (alpha <= 0) return

    f.glow(8.0, 0.8)
    f.lineWidth(2.0)
    f.stroke(alpha * 0.9)
    val ticks = Path()
    for (i in 0 until 36) {
        val angle = i * PI / 18 + f.t * 0.8
        val inner = radius - (if (i % 3 == 0) 9 else 4)
        ticks.moveTo(Pt(o.x + cos(angle) * inner, o.y + sin(angle) * inner))
        ticks.lineTo(Pt(o.x + cos(angle) * radius, o.y + sin(angle) * radius))
    }
    f.c.drawPath(ticks, f.paint)

    val sweep = f.t * (4 + 18 * f.p * f.p)
    f.lineWidth(2.4)
    for (k in 0 until 12) {
        val angle = sweep - k * 0.09
        f.stroke(alpha * (1 - k / 12.0).pow(2))
        f.line(o, Pt(o.x + cos(angle) * (radius - 3), o.y + sin(angle) * (radius - 3)))
    }

    // Torn horizontal slices near the end.
    if (unrest > 0.25) {
        val frame = floor(f.t * 30).toInt()
        f.noGlow()
        f.fill(alpha * 0.8)
        for (i in 0 until 5) {
            val y = o.y + (noise(frame + i * 11) - 0.5) * radius * 2
            val x = o.x - radius + noise(frame + i * 13) * radius * 0.6 + (noise(frame + i * 17) - 0.5) * 22 * unrest
            val w = radius * (0.6 + noise(frame + i * 19))
            val h = 2 + 3 * noise(frame + i * 23)
            f.c.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), f.paint)
        }
    }
}

private fun edgeLiquid(f: EdgeFrame) {
    val radius = 58.0
    val o = f.anchor
    val mid = atan2(f.inY, f.inX)
    val level = radius * (1 - f.p)

    f.lineWidth(2.5)
    f.stroke(0.45 * (1 - smooth(0.9, 1.0, f.p)))
    val outline = Path()
    outline.arc(o, radius, mid - PI / 2, mid + PI / 2, clockwise = false)
    outline.close()
    f.c.drawPath(outline, f.paint)

    if (level <= 0.3) return
    f.c.save()
    val bowl = Path()
    bowl.moveTo(o)
    bowl.arc(o, radius - 2, mid - PI / 2, mid + PI / 2, clockwise = false, join = true)
    bowl.close()
    f.c.clipPath(bowl)
    val liquid = Path()
    liquid.moveTo(f.fromAnchor(-radius, 0.0))
    var a = -radius
    while (a <= radius) {
        val wave = sin(a * 0.13 + f.t * 7) * 3 * min(1.0, level / 8)
        liquid.lineTo(f.fromAnchor(a, level + wave))
        a += 3
    }
    liquid.lineTo(f.fromAnchor(radius, 0.0))
    liquid.close()
    f.fill(0.9)
    f.c.drawPath(liquid, f.paint)
    f.c.restore()
}

/** Every effect, by style. EdgeAnimStyle.None has no entry and draws nothing; Random is
 *  resolved to one of the others before drawing. */
val edgeRenderers: Map<EdgeAnimStyle, EdgeRenderer> = mapOf(
    EdgeAnimStyle.Ring to ::edgeRing,
    EdgeAnimStyle.Semicircle to ::edgeSemicircle,
    EdgeAnimStyle.Bar to ::edgeBar,
    EdgeAnimStyle.Pulse to ::edgePulseWave,
    EdgeAnimStyle.Laser to ::edgeLaser,
    EdgeAnimStyle.Vortex to ::edgeVortex,
    EdgeAnimStyle.Scan to ::edgeScan,
    EdgeAnimStyle.Liquid to ::edgeLiquid,
)

// The highlight.

private fun locateRing(f: LocateFrame) {
    val center = Pt(f.centerX, f.centerY)
    f.glow(14.0, f.fade)
    f.lineWidth(5.0)
    for (i in 0 until 3) {
        val phase = ((f.t - i * 0.16) / 0.85).coerceIn(0.0, 1.0)
        if (phase <= 0) continue
        val eased = 1 - (1 - phase).pow(3)
        f.stroke((1 - phase * 0.75) * f.fade)
        f.circle(center, 170 * (1 - eased) + 26)
    }
}

private fun locateRadar(f: LocateFrame) {
    val center = Pt(f.centerX, f.centerY)
    val reach = 110.0
    f.glow(8.0, 0.8 * f.fade)
    f.lineWidth(1.6)
    for (i in 1..3) {
        f.stroke(0.45 * f.fade)
        f.circle(center, reach * i / 3)
    }
    val sweep = PI / 2 - f.t * 7
    f.lineWidth(2.6)
    for (k in 0 until 26) {
        val angle = sweep + k * 0.05
        f.stroke((1 - k / 26.0).pow(2) * f.fade)
        f.line(center, Pt(center.x + cos(angle) * reach, center.y + sin(angle) * reach))
    }
    val ping = (f.t * 1.6) % 1.0
    f.stroke((1 - ping) * f.fade)
    f.lineWidth(3.5)
    f.circle(center, ping * reach)
}

private fun locateSpotlight(f: LocateFrame) {
    val center = Pt(f.centerX, f.centerY)
    val closing = 1 - (1 - (f.t / 0.4).coerceIn(0.0, 1.0)).pow(3)
    val radius = 600 - 490 * closing
    val dim = 0.5 * (f.t / 0.2).coerceIn(0.0, 1.0) * f.fade
    f.paint.style = Paint.Style.FILL
    f.paint.color = (dim.coerceIn(0.0, 1.0) * 255).toInt() shl 24
    val shade = Path()
    shade.fillType = Path.FillType.EVEN_ODD
    // The whole screen, whatever the size: the canvas is scaled around the centre.
    val s = f.scale
    shade.addRect(
        (center.x - center.x / s).toFloat(),
        (center.y - center.y / s).toFloat(),
        (center.x + (f.width - center.x) / s).toFloat(),
        (center.y + (f.height - center.y) / s).toFloat(),
        Path.Direction.CW,
    )
    shade.addCircle(center.x.toFloat(), center.y.toFloat(), radius.toFloat(), Path.Direction.CW)
    f.c.drawPath(shade, f.paint)
    f.glow(18.0, f.fade)
    f.lineWidth(4.0)
    f.stroke(f.fade)
    f.circle(center, radius)
}

/** Every highlight, by style; Random is resolved to one of the others before drawing. */
val locateRenderers: Map<LocateStyle, LocateRenderer> = mapOf(
    LocateStyle.Ring to ::locateRing,
    LocateStyle.Radar to ::locateRadar,
    LocateStyle.Spotlight to ::locateSpotlight,
)

// The windows.

@SuppressLint("ViewConstructor")
private class CountdownView(
    context: Context,
    private val edge: Edge,
    private val style: EdgeRenderer,
    private val direction: EdgeAnimDirection,
    private val shrink: Boolean,
    private val scale: Double,
    private val rgb: Int,
    private val startMs: Long,
    private val durationMs: Long,
    /** Where along the edge the pointer is, in screen pixels. */
    var along: Float,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val location = IntArray(2)

    init {
        // Before Android 9 a hardware canvas draws no glow on anything but text.
        if (Build.VERSION.SDK_INT < 28) setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width / density.toDouble()
        val h = height / density.toDouble()
        getLocationOnScreen(location)
        val local = (if (edge.vertical) along - location[1] else along - location[0]) / density.toDouble()
        val elapsed = (SystemClock.uptimeMillis() - startMs) / 1000.0
        val duration = durationMs.coerceAtLeast(50) / 1000.0
        val p = (elapsed / duration).coerceIn(0.0, 1.0)
        paint.reset()
        paint.isAntiAlias = true
        val frame = when (edge) {
            Edge.Left -> frameAt(canvas, w, h, 0.0, h - local, 1.0, 0.0, 0.0, 1.0, p, elapsed)
            Edge.Right -> frameAt(canvas, w, h, w, h - local, -1.0, 0.0, 0.0, 1.0, p, elapsed)
            Edge.Top -> frameAt(canvas, w, h, local, h, 0.0, -1.0, 1.0, 0.0, p, elapsed)
            Edge.Bottom -> frameAt(canvas, w, h, local, 0.0, 0.0, 1.0, 1.0, 0.0, p, elapsed)
        }
        val save = canvas.save()
        // y-up points, as the effects are written.
        canvas.translate(0f, height.toFloat())
        canvas.scale(density, -density)
        // The chosen size: everything grows or shrinks around the point at the edge.
        val ax = frame.anchorX.toFloat()
        val ay = frame.anchorY.toFloat()
        canvas.scale(scale.toFloat(), scale.toFloat(), ax, ay)
        style(frame)
        canvas.restoreToCount(save)
        postInvalidateOnAnimation()
    }

    private fun frameAt(
        canvas: Canvas,
        w: Double,
        h: Double,
        ax: Double,
        ay: Double,
        inX: Double,
        inY: Double,
        alongX: Double,
        alongY: Double,
        p: Double,
        t: Double,
    ) = EdgeFrame(canvas, w, h, ax, ay, inX, inY, alongX, alongY, p, t, direction, shrink, scale, paint, rgb)
}

/** The countdown on one edge of the screen. Main thread only. */
class EdgeOverlay(context: Context) {
    private val app = context.applicationContext
    private val wm = app.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var view: CountdownView? = null
    private var previewEnd: Runnable? = null

    /** Starts the countdown on [edge], [elapsedMs] into a wait of [durationMs], at [along]
     *  (screen pixels along the edge). Does nothing for EdgeAnimStyle.None. */
    fun begin(edge: Edge, edges: Edges, along: Float, elapsedMs: Long, durationMs: Long) {
        end()
        val render = edgeRenderers[edges.animStyle.resolved()] ?: return
        if (!Settings.canDrawOverlays(app)) return
        val bounds = screenBounds(wm)
        val scale = effectScale(edges.animSize)
        val depth = (STRIP_DEPTH_DP * maxOf(1.0, scale) * app.resources.displayMetrics.density).toInt()
            .coerceAtMost(minOf(bounds.width(), bounds.height()))
        val params = WindowManager.LayoutParams(
            if (edge.vertical) depth else bounds.width(),
            if (edge.vertical) bounds.height() else depth,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            @Suppress("RtlHardcoded")
            gravity = Gravity.TOP or Gravity.LEFT
            x = if (edge == Edge.Right) bounds.width() - depth else 0
            y = if (edge == Edge.Bottom) bounds.height() - depth else 0
            title = "flockswitch countdown"
            if (Build.VERSION.SDK_INT >= 30) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            // Android 12+ blocks touches and clicks that pass through another app's
            // window unless the window is at most this opaque.
            if (Build.VERSION.SDK_INT >= 31) {
                app.getSystemService(InputManager::class.java)?.let { alpha = min(alpha, it.maximumObscuringOpacityForTouch) }
            }
        }
        val v = CountdownView(
            app,
            edge,
            render,
            edges.animDirection,
            edges.animShrink,
            scale,
            effectColor(edges.animColor),
            SystemClock.uptimeMillis() - elapsedMs,
            durationMs,
            along,
        )
        try {
            wm.addView(v, params)
            view = v
        } catch (e: Exception) {
            Log.w("countdown window: ${e.message}")
        }
    }

    /** The pointer moved along the edge. */
    fun follow(along: Float) {
        view?.along = along
    }

    /** Takes the countdown away at once: finished, or called off. */
    fun end() {
        previewEnd?.let(handler::removeCallbacks)
        previewEnd = null
        val v = view ?: return
        view = null
        runCatching { wm.removeViewImmediate(v) }
    }

    /** Plays the countdown once, for the settings: on the first edge with a channel, in
     *  the middle of it, for at least a second so a short wait can still be judged.
     *  Android gives no pointer position, so it cannot be at the pointer. */
    fun preview(edges: Edges) {
        val edge = Edge.entries.firstOrNull { edges.channel(it) != 0 } ?: Edge.Right
        val bounds = screenBounds(wm)
        val along = (if (edge.vertical) bounds.height() else bounds.width()) / 2f
        val duration = edges.dwellMs.coerceAtLeast(1000)
        begin(edge, edges, along, 0, duration)
        if (view == null) return
        val end = Runnable { end() }
        previewEnd = end
        handler.postDelayed(end, duration)
    }
}

@SuppressLint("ViewConstructor")
private class LocateView(
    context: Context,
    private val render: LocateRenderer,
    private val rgb: Int,
    private val scale: Double,
    private val x: Float,
    private val y: Float,
    private val startMs: Long,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val location = IntArray(2)

    init {
        // Before Android 9 a hardware canvas draws no glow on anything but text.
        if (Build.VERSION.SDK_INT < 28) setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width / density.toDouble()
        val h = height / density.toDouble()
        getLocationOnScreen(location)
        val t = (SystemClock.uptimeMillis() - startMs) / 1000.0
        val fade = ((LOCATE_MS / 1000.0 - t) / LOCATE_FADE_S).coerceIn(0.0, 1.0)
        paint.reset()
        paint.isAntiAlias = true
        val frame = LocateFrame(canvas, w, h, (x - location[0]) / density.toDouble(), h - (y - location[1]) / density.toDouble(), t, fade, scale, paint, rgb)
        val save = canvas.save()
        // y-up points, as the effects are written.
        canvas.translate(0f, height.toFloat())
        canvas.scale(density, -density)
        // The chosen size, around the pointer. The window already covers the screen.
        canvas.scale(scale.toFloat(), scale.toFloat(), frame.centerX.toFloat(), frame.centerY.toFloat())
        render(frame)
        canvas.restoreToCount(save)
        postInvalidateOnAnimation()
    }
}

/** The highlight at a point on the screen, for a moment. [windowType] is
 *  TYPE_ACCESSIBILITY_OVERLAY from the accessibility service (whose context is needed for
 *  it), TYPE_APPLICATION_OVERLAY otherwise. Main thread only. */
class LocateOverlay(private val context: Context, private val windowType: Int) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var view: LocateView? = null
    private val finish = Runnable { end() }

    fun show(locate: Locate, x: Float, y: Float) {
        end()
        val render = locateRenderers[locate.style.resolved()] ?: return
        val bounds = screenBounds(wm)
        val params = WindowManager.LayoutParams(
            bounds.width(),
            bounds.height(),
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            @Suppress("RtlHardcoded")
            gravity = Gravity.TOP or Gravity.LEFT
            title = "flockswitch highlight"
            if (Build.VERSION.SDK_INT >= 30) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            // See EdgeOverlay; accessibility overlays are exempt.
            if (Build.VERSION.SDK_INT >= 31 && windowType == WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY) {
                context.getSystemService(InputManager::class.java)?.let { alpha = min(alpha, it.maximumObscuringOpacityForTouch) }
            }
        }
        val v = LocateView(context, render, effectColor(locate.color), effectScale(locate.size), x, y, SystemClock.uptimeMillis())
        try {
            wm.addView(v, params)
            view = v
            handler.postDelayed(finish, LOCATE_MS)
        } catch (e: Exception) {
            Log.w("highlight window: ${e.message}")
        }
    }

    fun end() {
        handler.removeCallbacks(finish)
        val v = view ?: return
        view = null
        runCatching { wm.removeViewImmediate(v) }
    }
}
