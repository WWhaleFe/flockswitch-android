package io.github.wwhalefe.flockswitch.net

import io.github.wwhalefe.flockswitch.Config
import kotlinx.serialization.Serializable

@Serializable
data class NetworkPeer(
    val id: String = "", val name: String = "", val os: String = "mac",
    val fingerprint: String = "", val side: String = "off",
    val logitech_channel: Int = 0, val last_addr: String = "",
    /** This device pauses the connection to the computer: not dialled, links refused, still paired. */
    val paused: Boolean = false,
)

@Serializable
data class NetworkConfig(
    val on: Boolean = false, val name: String = "", val port: Int = 47160,
    val notice_accepted: Boolean = false,
    val swap_cmd_ctrl: Boolean = true, val lang1_on_mac: String = "capslock",
    val cross_delay_ms: Long = 0,
    /** Put text copied on the master on this device's clipboard (Android receives only). */
    val share_clipboard: Boolean = true,
    /** The one-time default-key migration netcore ran (`NetConfig::apply_hangul_defaults`);
     *  the service hands the number back and the app saves it. */
    val hangul_defaults: Int = 0,
    /** What the mouse's right button does as touch: "back" (default) or "long_press".
     *  Only the app reads these two; netcore ignores them. */
    val right_button: String = RIGHT_BACK,
    /** The middle button: "home" (default) or "none". */
    val middle_button: String = MIDDLE_HOME,
    val peers: List<NetworkPeer> = emptyList(),
) {
    /** A late change must not restore an unpaired peer or replace its trusted identity. */
    fun withChangedPeer(peer: NetworkPeer): NetworkConfig = copy(peers = peers.map {
        if (it.id == peer.id && it.fingerprint == peer.fingerprint) peer else it
    })

    /** What may travel to another install (a settings file, a restored backup): the
     *  consent, the switch and the paired computers belong to this install's own identity
     *  key, so a copy starts with all three off and empty. */
    fun withoutTrust(): NetworkConfig = copy(on = false, notice_accepted = false, peers = emptyList())

    fun toCoreJson(deviceName: String = ""): String = Config.json.encodeToString(serializer(),
        if (name.isBlank()) copy(name = deviceName) else this)
    fun problems(): List<String> = buildList {
        if (!validPort(port)) add("network: port must be 1024 to 65535, not 5353, 47851 or 47852")
        if (cross_delay_ms !in 0..60000) add("network: invalid cross delay")
        if (lang1_on_mac !in listOf("capslock", "off")) add("network: invalid language key option")
        if (right_button !in listOf(RIGHT_BACK, RIGHT_LONG_PRESS)) add("network: invalid right button option")
        if (middle_button !in listOf(MIDDLE_HOME, MIDDLE_NONE)) add("network: invalid middle button option")
        if (peers.map { it.id }.distinct().size != peers.size) add("network: duplicate peers")
        for (p in peers) {
            if (p.id.isBlank() || p.os !in listOf("mac", "windows", "linux", "android") ||
                p.side !in listOf("off", "left", "right", "top", "bottom") || p.logitech_channel !in 0..3)
                add("network: invalid peer")
        }
    }

    companion object {
        const val DEFAULT_PORT = 47160
        const val RIGHT_BACK = "back"
        const val RIGHT_LONG_PRESS = "long_press"
        const val MIDDLE_HOME = "home"
        const val MIDDLE_NONE = "none"

        /** The same range netcore's `check_port` accepts (ports below 1024 need privileges;
         *  5353 is mDNS and the other two belong to the computer versions). */
        fun validPort(port: Int) = port in 1024..65535 && port !in listOf(5353, 47851, 47852)
    }
}
