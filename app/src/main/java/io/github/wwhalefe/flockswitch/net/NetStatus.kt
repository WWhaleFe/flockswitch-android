package io.github.wwhalefe.flockswitch.net

import io.github.wwhalefe.flockswitch.Config
import kotlinx.serialization.Serializable

// What `NetBridge.status()` returns (netcore's `Status`), as far as the screen uses it.

@Serializable
data class NetMe(val id: String = "", val name: String = "", val port: Int = 0, val error: String? = null)

@Serializable
data class NetFound(val id: String = "", val name: String = "", val os: String = "", val addr: String = "", val paired: Boolean = false)

@Serializable
data class NetPeerState(val id: String = "", val connected: Boolean = false, val rtt_ms: Double? = null)

@Serializable
data class NetPairing(val peer_name: String = "", val code: String = "", val stage: String = "", val reason: String? = null)

@Serializable
data class NetCell(val id: String = "", val col: Int = 0, val row: Int = 0)

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
) {
    /** The state of a paired computer, if the service knows it. */
    fun peer(id: String) = peers.firstOrNull { it.id == id }

    /** The side of this screen the master is on, from the layout the master handed over
     *  ("left", "right", "top", "bottom"), or null when its cell is not next to ours. */
    fun masterSide(masterId: String): String? {
        val mine = layout.firstOrNull { it.id == me.id } ?: return null
        val master = layout.firstOrNull { it.id == masterId } ?: return null
        val dx = master.col - mine.col
        val dy = master.row - mine.row
        return when {
            dx == -1 && dy == 0 -> "left"
            dx == 1 && dy == 0 -> "right"
            dx == 0 && dy == -1 -> "top"
            dx == 0 && dy == 1 -> "bottom"
            else -> null
        }
    }

    companion object {
        /** An answer that cannot be read is the same as no service running. */
        fun parse(text: String): NetStatus =
            runCatching { Config.json.decodeFromString(serializer(), text) }.getOrDefault(NetStatus())
    }
}
