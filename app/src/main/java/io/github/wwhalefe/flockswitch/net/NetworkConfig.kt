package io.github.wwhalefe.flockswitch.net

import io.github.wwhalefe.flockswitch.Config
import kotlinx.serialization.Serializable

@Serializable
data class NetworkPeer(
    val id: String = "", val name: String = "", val os: String = "mac",
    val fingerprint: String = "", val side: String = "off",
    val logitech_channel: Int = 0, val last_addr: String = "",
)

@Serializable
data class NetworkConfig(
    val on: Boolean = false, val name: String = "", val port: Int = 47160,
    val notice_accepted: Boolean = false,
    val swap_cmd_ctrl: Boolean = true, val lang1_on_mac: String = "capslock",
    val cross_delay_ms: Long = 0,
    /** Put text copied on the master on this device's clipboard (Android receives only). */
    val share_clipboard: Boolean = true,
    val peers: List<NetworkPeer> = emptyList(),
) {
    /** A late change must not restore an unpaired peer or replace its trusted identity. */
    fun withChangedPeer(peer: NetworkPeer): NetworkConfig = copy(peers = peers.map {
        if (it.id == peer.id && it.fingerprint == peer.fingerprint) peer else it
    })

    fun toCoreJson(deviceName: String = ""): String = Config.json.encodeToString(serializer(),
        if (name.isBlank()) copy(name = deviceName) else this)
    fun problems(): List<String> = buildList {
        if (port !in 1..65535) add("network: port must be 1 to 65535")
        if (cross_delay_ms !in 0..60000) add("network: invalid cross delay")
        if (lang1_on_mac !in listOf("capslock", "off")) add("network: invalid language key option")
        if (peers.map { it.id }.distinct().size != peers.size) add("network: duplicate peers")
        for (p in peers) {
            if (p.id.isBlank() || p.os !in listOf("mac", "windows", "linux", "android") ||
                p.side !in listOf("off", "left", "right", "top", "bottom") || p.logitech_channel !in 0..3)
                add("network: invalid peer")
        }
    }
}
