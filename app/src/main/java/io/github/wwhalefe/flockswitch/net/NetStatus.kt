package io.github.wwhalefe.flockswitch.net

import io.github.wwhalefe.flockswitch.Config
import kotlinx.serialization.Serializable

// What `NetBridge.status()` returns (netcore's `Status`), as far as the screen uses it.

@Serializable
data class NetMe(
    val id: String = "", val name: String = "", val port: Int = 0, val error: String? = null, val can_send: Boolean = true,
    /** This device's monitors (always one on Android). */
    val displays: List<NetRect> = emptyList(),
)

@Serializable
data class NetRect(val x: Double = 0.0, val y: Double = 0.0, val width: Double = 0.0, val height: Double = 0.0)

@Serializable
data class NetFound(val id: String = "", val name: String = "", val os: String = "", val addr: String = "", val paired: Boolean = false)

@Serializable
data class NetPeerState(
    val id: String = "", val connected: Boolean = false, val rtt_ms: Double? = null,
    val name: String = "", val os: String = "",
    /** The peer takes key rule edits (a 2.1 master); false for a 2.0 master. */
    val keyrules: Boolean = false,
    /** This device pauses the connection to the computer. */
    val paused: Boolean = false,
    /** The computer's monitors as it told us (empty: not known, treat as one). */
    val displays: List<NetRect> = emptyList(),
)

@Serializable
data class NetPairing(val peer_name: String = "", val code: String = "", val stage: String = "", val reason: String? = null)

@Serializable
data class NetCell(val id: String = "", val col: Int = 0, val row: Int = 0, val display: Int = 0)

@Serializable
data class NetStatus(
    val running: Boolean = false,
    val me: NetMe = NetMe(),
    val discovered: List<NetFound> = emptyList(),
    val peers: List<NetPeerState> = emptyList(),
    val pairing: NetPairing? = null,
    val layout: List<NetCell> = emptyList(),
    val reconnecting: Boolean = false,
    val keys: Map<String, DirRules> = emptyMap(),
    /** The master's name once its settings arrived (null before, and while not connected). */
    val following: String? = null,
) {
    /** Whether the connected master among [ids] takes key rule edits; true while none is
     *  connected (nothing is known yet, so nothing is given up). */
    fun masterTakesKeyRules(ids: Set<String>): Boolean =
        peers.firstOrNull { it.connected && it.id in ids }?.keyrules ?: true

    /** The state of a paired computer, if the service knows it. */
    fun peer(id: String) = peers.firstOrNull { it.id == id }

    /** The side of this screen the master is on, from the layout the master handed over
     *  ("left", "right", "top", "bottom"), or null when its cell is not next to ours. */
    fun masterSide(masterId: String): String? {
        val mine = layout.filter { it.id == me.id }
        val master = layout.filter { it.id == masterId }
        // A computer has one cell per monitor: the first of the master's cells that touches ours.
        for (m in master) for (c in mine) {
            val dx = m.col - c.col
            val dy = m.row - c.row
            val side = when {
                dx == -1 && dy == 0 -> "left"
                dx == 1 && dy == 0 -> "right"
                dx == 0 && dy == -1 -> "top"
                dx == 0 && dy == 1 -> "bottom"
                else -> null
            }
            if (side != null) return side
        }
        return null
    }

    /** The key the master's rules make arrive as the Korean/English toggle: "shift_space"
     *  or "control_space". [masterOs] is the system of the connected master, null when none
     *  is connected (Shift+Space, the default of every sender). */
    fun hangulToggleKey(masterOs: String?): String = KeyRules.toggleKeyFor(masterOs, keys)

    companion object {
        /** An answer that cannot be read is the same as no service running. */
        fun parse(text: String): NetStatus =
            runCatching { Config.json.decodeFromString(serializer(), text) }.getOrDefault(NetStatus())
    }
}
