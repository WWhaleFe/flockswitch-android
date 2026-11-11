// User configuration, stored as JSON in the app's private files. Same meaning as the
// desktop versions' config.toml, without what Android does differently (keyboard
// shortcuts, language, start at login).

package io.github.wwhalefe.flockswitch

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
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

/** The colour of the on-screen effects until another is chosen. */
const val DEFAULT_EFFECT_COLOR = "#FFCC00"

/** The ready-made colours of a colour row; any other comes from the picker beside them. */
val COLOR_PRESETS = listOf(0xFFCC00, 0xFF7A1A, 0xFF3B30, 0xFF2D92, 0x0A84FF, 0x34C759, 0xFFFFFF)

/** Red, green and blue (0xRRGGBB) of a colour written as "#RRGGBB"; null if it is not one. */
fun parseColor(text: String): Int? {
    val t = text.trim()
    if (t.length != 7 || t[0] != '#') return null
    val hex = t.substring(1)
    if (!hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
    return hex.toInt(16)
}

/** The sizes the settings offer for an effect, in percent (100 = as designed). Saved
 *  settings may hold any size from 50 to 200, as the desktop versions' command line does. */
val EFFECT_SIZES = listOf(75, 100, 125, 150)

/** Whether [percent] is a size an effect can be drawn at. */
fun validEffectSize(percent: Int) = percent in EFFECT_SIZES || percent in 50..200

/** A colour (0xRRGGBB) as the settings write it. */
fun formatColor(rgb: Int): String = "#%06X".format(rgb and 0xFFFFFF)

/** A number below [n], different each time. Good enough to choose an effect. */
private fun randomBelow(n: Int) = kotlin.random.Random.nextInt(n)

/** What is drawn near the pointer while it waits at a screen edge. Each one but None and
 *  Random is a drawing function in the table in Overlay.kt. */
@Serializable
enum class EdgeAnimStyle {
    None, Ring, Semicircle, Bar, Pulse, Laser, Vortex, Scan, Liquid,

    /** A different effect each time, with random options too; see [Edges.countdown]. Has
     *  no drawing function or settings of its own. */
    Random;

    /** Whether the effect drains in a direction the user can choose. */
    val hasDirection get() = this == Ring || this == Semicircle || this == Bar

    /** Whether the effect can shrink away instead of draining. */
    val canShrink get() = this == Ring || this == Semicircle
}

/** Which way the ring, semicircle or bar drains. */
@Serializable
enum class EdgeAnimDirection { Clockwise, CounterClockwise, Both }

/** The effects that are drawn (every style but None and Random), in the settings' order. */
val DRAWN_EFFECTS: List<EdgeAnimStyle> = EdgeAnimStyle.entries.filter { it != EdgeAnimStyle.None && it != EdgeAnimStyle.Random }

/** An effect's name in config.json's `edges.effects`: "ring", "semicircle", … */
val EdgeAnimStyle.key: String get() = name.lowercase()

/** Every effect's settings until the user changes them: the shared fields' defaults. */
private val DEFAULT_EFFECTS: Map<String, EffectEntry> = DRAWN_EFFECTS.associate {
    it.key to EffectEntry(
        DEFAULT_EFFECT_COLOR,
        100,
        if (it.hasDirection) EdgeAnimDirection.Clockwise else null,
        if (it.canShrink) false else null,
    )
}

/** One effect's own settings as config.json keeps them. A field left out comes from the
 *  older shared fields (animColor, animSize, animDirection, animShrink) when the settings
 *  are read; [Edges.filled] does that. [direction] is only for effects with one, [shrink]
 *  only for those that can shrink. */
@Serializable
data class EffectEntry(
    /** "#RRGGBB". */
    val color: String? = null,
    /** Percent. */
    val size: Int? = null,
    val direction: EdgeAnimDirection? = null,
    val shrink: Boolean? = null,
)

/** How one effect is drawn: its colour, size, and (where it has them) direction and
 *  shrinking. */
data class EffectLook(
    val color: String = DEFAULT_EFFECT_COLOR,
    val size: Int = 100,
    val direction: EdgeAnimDirection = EdgeAnimDirection.Clockwise,
    val shrink: Boolean = false,
)

/** What one countdown draws: the effect and how. */
data class Countdown(val style: EdgeAnimStyle, val look: EffectLook)

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
    /** The next four were shared by every effect before 0.9.0. Each effect now has its
     *  own in [effects]; these mirror the chosen effect's (so an older version reading
     *  the settings keeps its look), and fill in what [effects] leaves out. */
    val animDirection: EdgeAnimDirection = EdgeAnimDirection.Clockwise,
    /** The ring or semicircle shrinks away instead of draining. */
    val animShrink: Boolean = false,
    /** "#RRGGBB". */
    val animColor: String = DEFAULT_EFFECT_COLOR,
    /** Size of the countdown in percent. */
    val animSize: Int = 100,
    /** Each effect's own settings, by [EdgeAnimStyle.key]. Settings saved before 0.9.0
     *  have none: [Config.decode] then fills them in from the shared fields. For an Edges
     *  made in code with only the shared fields set, `copy(effects = emptyMap()).filled()`
     *  gives every effect those. */
    val effects: Map<String, EffectEntry> = DEFAULT_EFFECTS,
) {
    /** The look the shared fields describe. */
    private fun legacyLook() = EffectLook(animColor, animSize, animDirection, animShrink)

    /** How [style] is drawn: its own settings, what it lacks from the shared fields. For
     *  None and Random (no drawing of their own), the shared fields. */
    fun look(style: EdgeAnimStyle): EffectLook {
        val base = legacyLook()
        val e = effects[style.key] ?: return base
        return EffectLook(
            e.color ?: base.color,
            e.size ?: base.size,
            if (style.hasDirection) e.direction ?: base.direction else base.direction,
            if (style.canShrink) e.shrink ?: base.shrink else base.shrink,
        )
    }

    /** Every drawn effect with all its settings written out, and nothing else in
     *  [effects]: what the settings hold from now on, so changing one effect (or the
     *  shared fields it mirrors) leaves the others as they are. */
    fun filled(): Edges = copy(effects = DRAWN_EFFECTS.associate { it.key to entry(it, look(it)) })

    /** [style] drawn with [look]; the shared fields follow when it is the chosen effect. */
    fun withLook(style: EdgeAnimStyle, look: EffectLook): Edges {
        if (style !in DRAWN_EFFECTS) return this
        val f = filled()
        val out = f.copy(effects = f.effects + (style.key to entry(style, look)))
        return if (style == animStyle) out.mirror(style) else out
    }

    /** [style] chosen; the shared fields take its settings. */
    fun withStyle(style: EdgeAnimStyle): Edges = filled().copy(animStyle = style).mirror(style)

    /** The shared fields set to [style]'s settings (for None and Random left as they are). */
    private fun mirror(style: EdgeAnimStyle): Edges {
        if (style !in DRAWN_EFFECTS) return this
        val l = look(style)
        return copy(
            animColor = l.color,
            animSize = l.size,
            animDirection = if (style.hasDirection) l.direction else animDirection,
            animShrink = if (style.canShrink) l.shrink else animShrink,
        )
    }

    /** What the next countdown draws; null for None. A chosen effect is drawn with its
     *  own settings. Random picks an effect with [pick] (a number below its argument),
     *  drawn in that effect's own colour and size, and picks a direction and whether to
     *  shrink away too where the effect has them. */
    fun countdown(pick: (Int) -> Int = ::randomBelow): Countdown? = when (animStyle) {
        EdgeAnimStyle.None -> null
        EdgeAnimStyle.Random -> {
            val style = DRAWN_EFFECTS[pick(DRAWN_EFFECTS.size).coerceIn(DRAWN_EFFECTS.indices)]
            var l = look(style)
            if (style.hasDirection) {
                val dirs = EdgeAnimDirection.entries
                l = l.copy(direction = dirs[pick(dirs.size).coerceIn(dirs.indices)])
            }
            if (style.canShrink) l = l.copy(shrink = pick(2) == 1)
            Countdown(style, l)
        }
        else -> Countdown(animStyle, look(animStyle))
    }

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

/** [look] as [style]'s entry: direction and shrink only where the effect has them. */
private fun entry(style: EdgeAnimStyle, look: EffectLook) = EffectEntry(
    look.color,
    look.size,
    look.direction.takeIf { style.hasDirection },
    look.shrink.takeIf { style.canShrink },
)

/** Haptic waveform IDs of HID++ feature 0x19B0 (the names Logi Options+ uses). */
object Waveform {
    const val SHARP_COLLISION = 2
    const val DAMP_COLLISION = 3
    const val SUBTLE_COLLISION = 4
    const val COMPLETED = 7

    /** Not a waveform: a different one each time, among those the mouse offers. */
    const val RANDOM = 255

    /** Every waveform the MX Master 4 offers, in the order the settings list them. */
    val ALL = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 27)

    /** The waveform to play for [waveform] on a mouse offering [mask] (bit N = waveform N):
     *  itself, or for [RANDOM] one of those offered, chosen by [pick] (a number below its
     *  argument). Null when there is nothing to play. */
    fun resolve(waveform: Int, mask: Int, pick: (Int) -> Int = ::randomBelow): Int? {
        if (waveform != RANDOM) return waveform.takeIf { it in 0..31 && mask and (1 shl it) != 0 }
        val offered = (0..31).filter { mask and (1 shl it) != 0 }
        return if (offered.isEmpty()) null else offered[pick(offered.size)]
    }
}

/** Vibration on a mouse with a haptic motor (MX Master 4): a pulse when the mouse is
 *  switched away and when it comes back, and pulses while the pointer waits at a screen
 *  edge. Waveforms are the IDs of HID++ feature 0x19B0 (0-14 and 27 on the MX Master 4:
 *  7 = completed, 3 = damp collision, 4 = subtle collision), or [Waveform.RANDOM]. */
@Serializable
data class Haptics(
    /** A pulse as the mouse is switched away. */
    val onSwitch: Boolean = true,
    /** A pulse when the mouse is back. Settings from before 0.6.0 had this under
     *  [onSwitch]; [Config.fromJson] carries it over. */
    val onArrive: Boolean = true,
    /** Pulses while the pointer rests at a screen edge, waiting to switch. */
    val onEdge: Boolean = true,
    val switchWaveform: Int = 7,
    val arriveWaveform: Int = 3,
    val edgeWaveform: Int = 4,
    /** Strength in percent (1-100), put on the mouse whenever it is here; 0 leaves the
     *  mouse as Logi Options+ set it. */
    val intensity: Int = 0,
    /** The pulses at a screen edge get faster and stronger as the switch nears ("Rising"),
     *  instead of repeating [edgeWaveform] evenly ("Even"). */
    val progressive: Boolean = true,
) {
    /** Whether any of the three is on. */
    fun anyOn() = onSwitch || onArrive || onEdge
}

/** How the pointer is shown. Each one is a drawing function in the table in Overlay.kt. */
@Serializable
enum class LocateStyle {
    Ring, Radar, Spotlight,

    /** A different effect each time; see [resolved]. Has no drawing function of its own. */
    Random;

    /** The effect to draw now: for Random one of the others. */
    fun resolved(): LocateStyle = if (this == Random) entries.filter { it != Random }.let { it[randomBelow(it.size)] } else this
}

/** Showing where the pointer is with a short highlight around it. */
@Serializable
data class Locate(
    val on: Boolean = false,
    /** Shown after Ctrl+Alt+L. */
    val onHotkey: Boolean = true,
    /** Shown when the mouse arrives from another computer. */
    val onArrival: Boolean = true,
    val style: LocateStyle = LocateStyle.Ring,
    /** "#RRGGBB". */
    val color: String = DEFAULT_EFFECT_COLOR,
    /** Size of the highlight in percent. */
    val size: Int = 100,
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
    /** Look for Logitech devices connected over Bluetooth too. */
    val bluetooth: Boolean = false,
    /** Start the background service when the phone or tablet starts. */
    val startAtBoot: Boolean = true,
    /** Look for a new version on GitHub at start and once a day. */
    val checkUpdates: Boolean = true,
    /** When a new version is found, download it, check it against the release's
     *  SHA256SUMS.txt and install it (GitHub build only; Android may still ask). */
    val autoUpdate: Boolean = true,
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
        if (parseColor(edges.animColor) == null) out += "edges: the colour must be like #FFCC00"
        if (parseColor(locate.color) == null) out += "locate: the colour must be like #FFCC00"
        if (!validEffectSize(edges.animSize)) out += "edges: the size must be 50 to 200 percent"
        for (style in DRAWN_EFFECTS) {
            val e = edges.effects[style.key] ?: continue
            if (e.color != null && parseColor(e.color) == null) out += "edges: ${style.key}: the colour must be like #FFCC00"
            if (e.size != null && !validEffectSize(e.size)) out += "edges: ${style.key}: the size must be 50 to 200 percent"
        }
        if (!validEffectSize(locate.size)) out += "locate: the size must be 50 to 200 percent"
        if (listOf(haptics.switchWaveform, haptics.arriveWaveform, haptics.edgeWaveform).any { it !in 0..31 && it != Waveform.RANDOM }) {
            out += "haptics: waveforms are numbered 0 to 31, or random"
        }
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
            // An effect's entry writes only the fields it has (no "direction": null).
            explicitNulls = false
        }

        /** Reads saved settings; unreadable or unusable ones give the defaults. */
        fun fromJson(text: String): Config = runCatching { decode(json.parseToJsonElement(text)) }
            .getOrNull()
            ?.takeIf { it.problems().isEmpty() }
            ?: Config()

        /** The settings in [root], brought up to date; throws if they cannot be read. Says
         *  nothing about whether they are usable: see [problems]. */
        fun decode(root: JsonElement): Config = json.decodeFromJsonElement(serializer(), migrate(root)).let {
            // Before 0.9.0 every effect shared one colour, size, direction and shrink:
            // each effect starts from those, so older settings keep their look.
            it.copy(edges = it.edges.filled())
        }

        /** Settings from older versions, brought up to date. Before 0.6.0 one switch
         *  (`onSwitch`) covered the pulse on leaving and the one on arriving. An effect
         *  from a newer version in `edges.effects`, or an entry that is not an object, is
         *  left out. */
        private fun migrate(root: JsonElement): JsonElement {
            var obj = root as? JsonObject ?: return root
            val edges = obj["edges"] as? JsonObject
            val effects = edges?.get("effects")
            // Before 0.9.0: none of their own, so each takes the shared fields (decode).
            if (edges != null && effects == null) {
                obj = JsonObject(obj + ("edges" to JsonObject(edges + ("effects" to JsonObject(emptyMap())))))
            }
            if (effects != null) {
                val known = DRAWN_EFFECTS.map { it.key }.toSet()
                val kept = (effects as? JsonObject)
                    ?.filter { (k, v) -> k in known && v is JsonObject }
                    ?.mapValues { (_, v) -> cleanEntry(v as JsonObject) }
                    .orEmpty()
                obj = JsonObject(obj + ("edges" to JsonObject(edges + ("effects" to JsonObject(kept)))))
            }
            val h = obj["haptics"] as? JsonObject ?: return obj
            if ("onArrive" in h) return obj
            val leaving = (h["onSwitch"] as? JsonPrimitive)?.booleanOrNull ?: true
            return JsonObject(obj + ("haptics" to JsonObject(h + ("onArrive" to JsonPrimitive(leaving)))))
        }

        /** An effect's entry with only the fields of the right kind, so one odd value
         *  falls back to the shared field instead of costing every setting. */
        private fun cleanEntry(o: JsonObject): JsonObject = JsonObject(
            o.filter { (k, v) ->
                val p = v as? JsonPrimitive
                when (k) {
                    "color", "direction" -> p?.isString == true
                    "size" -> p != null && !p.isString && p.intOrNull != null
                    "shrink" -> p != null && !p.isString && p.booleanOrNull != null
                    else -> false
                }
            },
        )
    }
}
