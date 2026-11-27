package io.github.wwhalefe.flockswitch.net

import kotlin.math.abs
import kotlin.math.hypot

// The mouse of the computer that is sharing, turned into touch. Pure logic, no Android
// classes: [RemoteInput] carries the commands out through the accessibility service.
//
// - A click is one short tap where the pointer was when the button went down: moving the
//   mouse by a hair between down and up never moves the finger.
// - Pressing and moving past the touch slop is one drag: the finger goes down where the
//   button went down and follows the pointer until the button is released.
// - A button held for a moment without moving puts the finger down and keeps it there (a
//   real long press, or the start of a drag); a quick click never does.
// - A second click within the double-tap time lands on the first one's spot.

/** The numbers the planner needs from the system (View configuration), in pixels and ms. */
data class TouchTuning(
    /** Movement below this is no drag. */
    val slopPx: Float = 24f,
    /** A button held this long without moving puts the finger down. */
    val holdMs: Long = 120,
    /** Two clicks closer than this (up to up) are a double click. */
    val doubleTapMs: Long = 300,
    /** How long a right-button long press keeps the finger down. */
    val longPressMs: Long = 700,
)

/** What the right and the middle button do. */
data class TouchSettings(val rightLongPress: Boolean = false, val middleHome: Boolean = true) {
    companion object {
        fun of(network: NetworkConfig) = TouchSettings(
            rightLongPress = network.right_button == NetworkConfig.RIGHT_LONG_PRESS,
            middleHome = network.middle_button != NetworkConfig.MIDDLE_NONE,
        )
    }
}

enum class GlobalKey { Back, Home, Recents }

sealed interface TouchCommand {
    /** A short tap. [followsTap]: it is the second click of a double click. */
    data class Tap(val x: Float, val y: Float, val followsTap: Boolean = false) : TouchCommand

    /** The finger goes down and stays. */
    data class Press(val x: Float, val y: Float) : TouchCommand

    /** The held finger moves to here. */
    data class Drag(val x: Float, val y: Float) : TouchCommand

    /** The held finger lifts at here. */
    data class Release(val x: Float, val y: Float) : TouchCommand

    /** A finger down for [TouchTuning.longPressMs] and up again. */
    data class LongPress(val x: Float, val y: Float) : TouchCommand

    /** Wheel movement in wheel units (what netcore sends), at the pointer. */
    data class Scroll(val x: Float, val y: Float, val dx: Double, val dy: Double) : TouchCommand

    data class Global(val key: GlobalKey) : TouchCommand
}

/** Decides what the mouse does. Every call gives the commands to carry out, in order. [now]
 *  is any millisecond clock that does not go backwards. */
class TouchPlanner(private val tuning: TouchTuning = TouchTuning(), private val settings: () -> TouchSettings = { TouchSettings() }) {
    private enum class State { Idle, Down, Held, Drag }

    private var state = State.Idle
    private var downX = 0f
    private var downY = 0f
    private var downAt = 0L
    private var x = 0f
    private var y = 0f
    private var lastTapAt = Long.MIN_VALUE
    private var lastTapX = 0f
    private var lastTapY = 0f

    /** The finger is down (a press or a drag is in progress). */
    val pressed: Boolean get() = state == State.Held || state == State.Drag

    /** When [tick] has something to do, or null. */
    val wakeAt: Long? get() = if (state == State.Down) downAt + tuning.holdMs else null

    /** The pointer is at (x, y) now. */
    fun move(x: Float, y: Float, now: Long): List<TouchCommand> {
        this.x = x
        this.y = y
        return when (state) {
            State.Idle -> emptyList()
            State.Down -> if (far(x, y)) {
                state = State.Drag
                listOf(TouchCommand.Press(downX, downY), TouchCommand.Drag(x, y))
            } else emptyList()
            State.Held -> if (far(x, y)) {
                state = State.Drag
                listOf(TouchCommand.Drag(x, y))
            } else emptyList()
            State.Drag -> listOf(TouchCommand.Drag(x, y))
        }
    }

    fun leftDown(now: Long): List<TouchCommand> {
        if (state != State.Idle) return emptyList()
        state = State.Down
        downX = x; downY = y; downAt = now
        return emptyList()
    }

    fun leftUp(now: Long): List<TouchCommand> {
        val was = state
        state = State.Idle
        return when (was) {
            State.Idle -> emptyList()
            State.Down -> {
                val follows = lastTapAt != Long.MIN_VALUE && now - lastTapAt <= tuning.doubleTapMs &&
                    hypot(downX - lastTapX, downY - lastTapY) <= tuning.slopPx * 2
                // A second click lands where the first did: a double tap needs the same spot.
                val (tx, ty) = if (follows) lastTapX to lastTapY else downX to downY
                lastTapAt = now; lastTapX = tx; lastTapY = ty
                listOf(TouchCommand.Tap(tx, ty, follows))
            }
            State.Held -> listOf(TouchCommand.Release(downX, downY))
            State.Drag -> listOf(TouchCommand.Release(x, y))
        }
    }

    /** Call at [wakeAt]: a button held long enough without moving puts the finger down. */
    fun tick(now: Long): List<TouchCommand> {
        if (state != State.Down || now - downAt < tuning.holdMs) return emptyList()
        state = State.Held
        return listOf(TouchCommand.Press(downX, downY))
    }

    fun rightDown(): List<TouchCommand> =
        if (settings().rightLongPress) listOf(TouchCommand.LongPress(x, y)) else listOf(TouchCommand.Global(GlobalKey.Back))

    fun middleDown(): List<TouchCommand> =
        if (settings().middleHome) listOf(TouchCommand.Global(GlobalKey.Home)) else emptyList()

    fun back(): List<TouchCommand> = listOf(TouchCommand.Global(GlobalKey.Back))
    fun forward(): List<TouchCommand> = listOf(TouchCommand.Global(GlobalKey.Recents))

    /** A wheel turn. Ignored while a finger is down (a drag is not scrolling). */
    fun scroll(dx: Double, dy: Double): List<TouchCommand> =
        if (state != State.Idle || abs(dx) + abs(dy) < 0.001) emptyList() else listOf(TouchCommand.Scroll(x, y, dx, dy))

    /** The gesture of a press was cancelled by the system: the finger is up already. */
    fun cancelled() {
        if (state == State.Held || state == State.Drag) state = State.Idle
    }

    /** The pointer left, or the receiver stopped: lifts a finger that is down. */
    fun reset(): List<TouchCommand> {
        val was = state
        state = State.Idle
        return if (was == State.Held || was == State.Drag) listOf(TouchCommand.Release(if (was == State.Drag) x else downX, if (was == State.Drag) y else downY)) else emptyList()
    }

    private fun far(x: Float, y: Float) = hypot(x - downX, y - downY) > tuning.slopPx
}

/** Commands waiting for the gesture API, which takes one gesture at a time. A newer drag
 *  replaces an older one that was not sent yet, and wheel turns add up: a fast mouse must
 *  not leave a long queue behind. */
class GestureQueue {
    private val items = ArrayDeque<TouchCommand>()

    val isEmpty: Boolean get() = items.isEmpty()

    fun push(c: TouchCommand) {
        val last = items.lastOrNull()
        when {
            c is TouchCommand.Drag && last is TouchCommand.Drag -> items[items.lastIndex] = c
            c is TouchCommand.Scroll && last is TouchCommand.Scroll ->
                items[items.lastIndex] = TouchCommand.Scroll(c.x, c.y, last.dx + c.dx, last.dy + c.dy)
            else -> items.addLast(c)
        }
    }

    fun poll(): TouchCommand? = items.removeFirstOrNull()

    /** Drops what has no finger to act on (after the system cancelled the press): drags and
     *  releases up to the next command that starts something. */
    fun dropStale() {
        while (true) {
            val first = items.firstOrNull() ?: return
            if (first is TouchCommand.Drag || first is TouchCommand.Release) items.removeFirst() else return
        }
    }

    fun clear() = items.clear()
}

/** The two ends of a wheel swipe at the pointer. A finger moves the opposite way to the
 *  content: wheel down (dy > 0) swipes up. [unitPx] is one wheel unit in pixels; one gesture
 *  covers at most [maxFraction] of the screen. The path is moved inside the screen (by
 *  [margin]) instead of cut short, so the distance stays proportional. */
fun scrollPath(x: Float, y: Float, dx: Double, dy: Double, width: Int, height: Int, unitPx: Float, maxFraction: Float = 0.6f, margin: Float = 8f): FloatArray {
    fun axis(at: Float, units: Double, size: Int): Pair<Float, Float> {
        val room = (size - 2 * margin).coerceAtLeast(1f)
        val dist = (-units * unitPx).toFloat().coerceIn(-room * maxFraction, room * maxFraction)
        var start = at.coerceIn(margin, size - margin)
        var end = start + dist
        if (end < margin) { start += margin - end; end = margin }
        if (end > size - margin) { start -= end - (size - margin); end = size - margin }
        return start.coerceIn(margin, size - margin) to end
    }
    val (sx, ex) = axis(x, dx, width)
    val (sy, ey) = axis(y, dy, height)
    return floatArrayOf(sx, sy, ex, ey)
}

/** How long a swipe of [distance] px takes: slow enough to scroll smoothly and not fling. */
fun scrollDurationMs(distance: Float): Long = (distance / 1.1f).toLong().coerceIn(60L, 450L)

/** How long a drag segment of [distance] px takes. */
fun dragDurationMs(distance: Float): Long = (distance / 2f).toLong().coerceIn(16L, 100L)
