// User configuration, stored as JSON in the app's private files. Same meaning as the
// desktop versions' config.toml, without what Android does differently (keyboard
// shortcuts, language, start at login).

package io.github.wwhalefe.flockswitch

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs

/** Buttons that can switch, by HID++ control ID (REPROG_CONTROLS_V4). */
const val BUTTON_WHEEL_MODE = 0x00C4
const val BUTTON_GESTURE = 0x00C3
const val BUTTON_MIDDLE = 0x0052
val BUTTONS = listOf(BUTTON_WHEEL_MODE, BUTTON_GESTURE, BUTTON_MIDDLE)

/** Names used for the buttons in the log. */
fun buttonName(cid: Int) = when (cid) {
    BUTTON_WHEEL_MODE -> "wheel_mode"
    BUTTON_GESTURE -> "gesture"
    BUTTON_MIDDLE -> "middle"
    else -> "?"
}

/** Gesture: hold the button, move the mouse, let go. */
enum class Gesture {
    /** Pressed without moving. */
    Press, Up, Down, Left, Right;

    companion object {
        /** Mouse movement (raw counts) below this while the button is held is a press. */
        private const val MIN_MOVE = 50

        /** Classifies the movement made while the button was held (y grows downwards). */
        fun fromMovement(dx: Int, dy: Int): Gesture = when {
            maxOf(abs(dx), abs(dy)) < MIN_MOVE -> Press
            abs(dx) >= abs(dy) -> if (dx > 0) Right else Left
            dy > 0 -> Down
            else -> Up
        }
    }
}

/** Where one hold-and-move gesture switches to: its own channels. */
@Serializable
data class MoveSetting(val on: Boolean = false, val channels: List<Int> = listOf(1, 2, 3))

@Serializable
data class Moves(
    val up: MoveSetting = MoveSetting(),
    val down: MoveSetting = MoveSetting(),
    val left: MoveSetting = MoveSetting(),
    val right: MoveSetting = MoveSetting(),
) {
    /** Whether any direction is switched on. */
    fun anyOn() = listOf(up, down, left, right).any { it.on }

    fun get(g: Gesture): MoveSetting? = when (g) {
        Gesture.Up -> up
        Gesture.Down -> down
        Gesture.Left -> left
        Gesture.Right -> right
        Gesture.Press -> null
    }

    fun with(g: Gesture, m: MoveSetting): Moves = when (g) {
        Gesture.Up -> copy(up = m)
        Gesture.Down -> copy(down = m)
        Gesture.Left -> copy(left = m)
        Gesture.Right -> copy(right = m)
        Gesture.Press -> this
    }
}

/** The next of [channels] after [current] (both 1-based), among the device's [count],
 *  like the Easy-Switch button under the mouse: with two channels it goes back and
 *  forth, with one it always goes there. Null when it is already there. */
fun pick(channels: List<Int>, current: Int, count: Int): Int? {
    val usable = channels.filter { it <= count }
    val i = usable.indexOf(current)
    val next = if (i >= 0) usable[(i + 1) % usable.size] else usable.firstOrNull() ?: return null
    return next.takeIf { it != current }
}

/** What one button does. */
@Serializable
data class ButtonSetting(
    /** Whether this button switches at all. */
    val on: Boolean = false,
    /** Channels (1-based) a press (without moving the mouse) steps through. */
    val channels: List<Int> = listOf(1, 2, 3),
    /** Hold-and-move gestures. While any is set, the mouse movement during a hold goes
     *  to flockswitch, so the cursor stays still. */
    val moves: Moves = Moves(),
) {
    /** The channel (1-based) a gesture leads to from the current one (0-based), given
     *  how many channels the device has; null when nothing should happen (already
     *  there, or that direction is off). */
    fun channelAfter(gesture: Gesture, current0: Int, count: Int): Int? {
        val current = current0 + 1
        val m = moves.get(gesture) ?: return pick(channels, current, count)
        return if (m.on) pick(m.channels, current, count) else null
    }

    /** Whether the button needs the mouse movement during a hold. */
    fun usesMoves() = moves.anyOn()
}

/** A side of the screen. */
enum class Edge {
    Left, Right, Top, Bottom;

    val vertical get() = this == Left || this == Right
}

/** What is drawn near the pointer while it waits at a screen edge. Each one but None is
 *  a drawing function in the table in EdgeOverlay.kt. */
@Serializable
enum class EdgeAnimStyle {
    None, Ring, Semicircle, Bar, Pulse, Laser, Vortex, Scan, Liquid;

    /** Whether the effect drains in a direction the user can choose. */
    val hasDirection get() = this == Ring || this == Semicircle || this == Bar

    /** Whether the effect can shrink away instead of draining. */
    val canShrink get() = this == Ring || this == Semicircle
}

/** Which way the ring, semicircle or bar drains. */
@Serializable
enum class EdgeAnimDirection { Clockwise, CounterClockwise, Both }

/** Switching at the screen edge: rest the pointer against an edge and the group goes to
 *  that edge's channel (0 = that edge does nothing). Each device watches its own screen. */
@Serializable
data class Edges(
    /** How long (ms) the pointer must rest against the edge first. */
    val dwellMs: Long = 500,
    val left: Int = 0,
    val right: Int = 0,
    val top: Int = 0,
    val bottom: Int = 0,
    /** Density-independent pixels at each end of an edge that do not count. */
    val cornerMargin: Int = 80,
    /** The wait runs only while Control is held. */
    val requireControl: Boolean = false,
    /** What is drawn while the pointer waits. */
    val animStyle: EdgeAnimStyle = EdgeAnimStyle.Ring,
    val animDirection: EdgeAnimDirection = EdgeAnimDirection.Clockwise,
    /** The ring or semicircle shrinks away instead of draining. */
    val animShrink: Boolean = false,
) {
    fun channel(e: Edge): Int = when (e) {
        Edge.Left -> left
        Edge.Right -> right
        Edge.Top -> top
        Edge.Bottom -> bottom
    }

    fun with(e: Edge, channel: Int): Edges = when (e) {
        Edge.Left -> copy(left = channel)
        Edge.Right -> copy(right = channel)
        Edge.Top -> copy(top = channel)
        Edge.Bottom -> copy(bottom = channel)
    }

    /** Whether any edge has a channel. */
    fun any() = Edge.entries.any { channel(it) != 0 }
}

/** Haptic waveform IDs of HID++ feature 0x19B0 (the names Logi Options+ uses). */
object Waveform {
    const val SHARP_COLLISION = 2
    const val DAMP_COLLISION = 3
    const val SUBTLE_COLLISION = 4
    const val COMPLETED = 7

    /** Every waveform the MX Master 4 offers, in the order the settings list them. */
    val ALL = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 27)
}

/** Vibration on a mouse with a haptic motor (MX Master 4): a pulse when the mouse is
 *  switched away and when it comes back, and pulses while the pointer waits at a screen
 *  edge. Waveforms are the IDs of HID++ feature 0x19B0 (0-14 and 27 on the MX Master 4:
 *  7 = completed, 3 = damp collision, 4 = subtle collision). */
@Serializable
data class Haptics(
    val onSwitch: Boolean = true,
    val onEdge: Boolean = true,
    val switchWaveform: Int = 7,
    val arriveWaveform: Int = 3,
    val edgeWaveform: Int = 4,
    /** Strength in percent (1-100), put on the mouse whenever it is here; 0 leaves the
     *  mouse as Logi Options+ set it. */
    val intensity: Int = 0,
    /** The pulses at a screen edge get faster and stronger as the switch nears, instead
     *  of repeating [edgeWaveform] evenly. */
    val progressive: Boolean = true,
)

/** How the pointer is shown. Each one is a drawing function in the table in Overlay.kt. */
@Serializable
enum class LocateStyle { Ring, Radar, Spotlight }

/** Showing where the pointer is with a short highlight around it. */
@Serializable
data class Locate(
    val on: Boolean = false,
    /** Shown after Ctrl+Alt+L. */
    val onHotkey: Boolean = true,
    /** Shown when the mouse arrives from another computer. */
    val onArrival: Boolean = true,
    val style: LocateStyle = LocateStyle.Ring,
)

/** Each button that can switch has its own setting. */
@Serializable
data class Buttons(
    /** Wheel mode-shift button (control 0x00C4). */
    val wheelMode: ButtonSetting = ButtonSetting(on = true),
    /** Gesture (thumb) button (0x00C3). */
    val gesture: ButtonSetting = ButtonSetting(),
    /** Middle button, the wheel click (0x0052). */
    val middle: ButtonSetting = ButtonSetting(),
)

@Serializable
data class Config(
    /** Master switch. When false the buttons are left alone. */
    val enabled: Boolean = true,
    /** Which device carries the trigger buttons: "auto" (first mouse that has them),
     *  a wireless product ID such as "B042", or part of the device name. */
    val triggerDevice: String = "auto",
    /** Devices that move together with the trigger device: ["all"], [] for none, or a
     *  list of wireless product IDs / name fragments. */
    val follow: List<String> = listOf("all"),
    /** Look for Logitech devices connected over Bluetooth too (experimental). */
    val bluetooth: Boolean = false,
    /** Start the background service when the phone or tablet starts. */
    val startAtBoot: Boolean = true,
    /** Look for a new version on GitHub at start and once a day. */
    val checkUpdates: Boolean = true,
    val buttons: Buttons = Buttons(),
    /** Switching at the screen edge. */
    val edges: Edges = Edges(),
    /** Vibration on a mouse with a haptic motor. */
    val haptics: Haptics = Haptics(),
    /** Showing where the pointer is. */
    val locate: Locate = Locate(),
) {
    /** The setting of a trigger button ([cid] must be one of [BUTTONS]). */
    fun button(cid: Int): ButtonSetting = when (cid) {
        BUTTON_GESTURE -> buttons.gesture
        BUTTON_MIDDLE -> buttons.middle
        else -> buttons.wheelMode
    }

    fun withButton(cid: Int, b: ButtonSetting): Config = copy(
        buttons = when (cid) {
            BUTTON_GESTURE -> buttons.copy(gesture = b)
            BUTTON_MIDDLE -> buttons.copy(middle = b)
            else -> buttons.copy(wheelMode = b)
        },
    )

    /** Control IDs of the buttons that are switched on. */
    fun triggers(): List<Int> = BUTTONS.filter { button(it).on }

    /** Problems that make the settings unusable; empty when they are fine. */
    fun problems(): List<String> {
        val out = mutableListOf<String>()
        for (cid in BUTTONS) {
            val b = button(cid)
            val name = buttonName(cid)
            if (b.channels.any { it !in 1..3 }) out += "$name: channels must be between 1 and 3"
            if (b.on && b.channels.isEmpty()) out += "$name: turn on at least one channel"
            for (m in listOf(b.moves.up, b.moves.down, b.moves.left, b.moves.right)) {
                if (m.channels.any { it !in 1..3 }) out += "$name: channels must be between 1 and 3"
                if (b.on && m.on && m.channels.isEmpty()) out += "$name: turn on at least one channel"
            }
        }
        if (triggers().isEmpty()) out += "turn on at least one button"
        if (Edge.entries.any { edges.channel(it) !in 0..3 }) out += "edges: the channel must be 1, 2, 3 or 0"
        if (edges.dwellMs !in 100..5000) out += "edges: the time at the edge must be 100 to 5000 ms"
        if (listOf(haptics.switchWaveform, haptics.arriveWaveform, haptics.edgeWaveform).any { it !in 0..31 }) out += "haptics: waveforms are numbered 0 to 31"
        if (haptics.intensity !in 0..100) out += "haptics: the strength must be 0 (as Options+ sets it) or 1-100 percent"
        return out
    }

    /** True when every device follows ("all"). */
    fun followsAll() = follow.any { it.equals("all", ignoreCase = true) }

    /** Ticks or unticks one device in the group. "all" is expanded to the given device
     *  list first, and collapsed back to "all" when every device is ticked again. */
    fun toggleFollow(devices: List<String>, key: String): Config {
        val set = (if (followsAll()) devices else follow).toMutableList()
        val i = set.indexOfFirst { it.equals(key, ignoreCase = true) }
        if (i >= 0) set.removeAt(i) else set += key
        val every = devices.isNotEmpty() && devices.all { d -> set.any { it.equals(d, ignoreCase = true) } }
        return copy(follow = if (every) listOf("all") else set)
    }

    /** Whether a device with this product ID / name should follow the trigger device. */
    fun follows(wpid: Int?, name: String): Boolean = follow.any { raw ->
        val f = raw.trim()
        f.equals("all", ignoreCase = true) ||
            (wpid != null && f.equals("%04X".format(wpid), ignoreCase = true)) ||
            (f.isNotEmpty() && name.lowercase().contains(f.lowercase()))
    }

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = true
            // A style from a newer version must not throw every setting away.
            coerceInputValues = true
        }

        /** Reads saved settings; unreadable or unusable ones give the defaults. */
        fun fromJson(text: String): Config = runCatching { json.decodeFromString(serializer(), text) }
            .getOrNull()
            ?.takeIf { it.problems().isEmpty() }
            ?: Config()
    }
}
