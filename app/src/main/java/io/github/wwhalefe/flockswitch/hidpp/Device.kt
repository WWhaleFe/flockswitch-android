// HID++ 2.0 devices reachable through an endpoint, and the features flockswitch uses:
// device name (0x0005), Change Host (0x1814) and Reprogrammable Controls v4 (0x1B04).

package io.github.wwhalefe.flockswitch.hidpp

import io.github.wwhalefe.flockswitch.Log

const val FEATURE_ROOT = 0x0000
const val FEATURE_NAME = 0x0005
const val FEATURE_CHANGE_HOST = 0x1814
const val FEATURE_REPROG_V4 = 0x1B04
/** Haptic feedback (the MX Master 4's motor): not in Logitech's public documents;
 *  reverse-engineered by Solaar, OpenLogi and others. Function 0 lists the waveforms
 *  the device has (bit N = waveform N; the MX Master 4 offers 0-14 and 27), 4 plays one. */
const val FEATURE_HAPTIC = 0x19B0

/** Keyboards answer slowly when they wake from sleep; 0.6 s was measured to be too short. */
const val SLOW_TIMEOUT = 2000L
private const val FAST_TIMEOUT = 600L

enum class DeviceKind {
    Keyboard, Mouse, Other;

    companion object {
        /** Kind code from a receiver pairing record (Bolt and Unifying share it). */
        fun fromReceiver(code: Int) = when (code and 0x0F) {
            1 -> Keyboard
            2 -> Mouse
            else -> Other
        }

        /** Kind code from the device itself (feature 0x0005, getDeviceType). */
        fun fromDeviceType(code: Int) = when (code) {
            0, 2 -> Keyboard // keyboard, numpad
            3, 4, 5 -> Mouse // mouse, trackpad, trackball
            else -> Other
        }
    }
}

/** A device slot as recorded by the receiver (known even when the device is offline). */
data class Pairing(val slot: Int, val kind: DeviceKind, val wpid: Int)

/** How a control reports (REPROG_CONTROLS_V4 reporting flags). */
data class Reporting(
    /** Presses come to us as notifications instead of acting on the device. */
    val diverted: Boolean,
    /** While it is held, the mouse movement comes to us instead of moving the cursor. */
    val rawXy: Boolean,
)

data class HostState(
    /** Number of host channels (Easy-Switch slots). */
    val count: Int,
    /** Current channel, 0-based. */
    val current: Int,
)

data class Device(
    val endpoint: Int,
    val index: Int,
    val name: String,
    val kind: DeviceKind?,
    val wpid: Int?,
    private val features: Map<Int, Int>,
) {
    fun label(): String = name + (wpid?.let { " [%04X]".format(it) } ?: "")

    fun has(feature: Int) = features.containsKey(feature)

    private fun feature(feature: Int) = features[feature]

    /** Reads the current Easy-Switch channel. An idle keyboard can miss the first
     *  request while its radio wakes up, so a timeout is retried once. An error reply
     *  means the device is not linked to this computer and is returned at once. */
    fun hostState(ep: Endpoint): HostState? {
        val fi = feature(FEATURE_CHANGE_HOST) ?: return null
        repeat(2) {
            when (val r = ep.request(index, fi, 0, byteArrayOf(), SLOW_TIMEOUT)) {
                is Reply.Ok -> return HostState(count = r.data.u(0), current = r.data.u(1))
                Reply.Error -> return null
                Reply.Timeout -> {}
            }
        }
        return null
    }

    /** The haptic waveforms the device offers, as a bit mask (bit N = waveform N), from
     *  feature 0x19B0 getCapabilities; null when the device has no haptics or is away. */
    fun hapticWaveforms(ep: Endpoint): Int? {
        val fi = feature(FEATURE_HAPTIC) ?: return null
        val r = ep.request(index, fi, 0, byteArrayOf(), SLOW_TIMEOUT) as? Reply.Ok ?: return null
        if (r.data.size < 8) return null
        return (r.data.u(4) shl 24) or (r.data.u(5) shl 16) or (r.data.u(6) shl 8) or r.data.u(7)
    }

    /** The haptic configuration (0x19B0 getConfiguration): flags (bit 0 = haptics on,
     *  bit 1 = power save) and the strength in percent, as Logi Options+ sets them. */
    fun hapticConfig(ep: Endpoint): Pair<Int, Int>? {
        val fi = feature(FEATURE_HAPTIC) ?: return null
        val r = ep.request(index, fi, 1, byteArrayOf(), SLOW_TIMEOUT) as? Reply.Ok ?: return null
        if (r.data.size < 2) return null
        return r.data.u(0) to r.data.u(1)
    }

    /** Sets the haptic flags and strength (0x19B0 setConfiguration). The device keeps
     *  this only until it loses power, so the engine applies it again when the mouse
     *  comes back. */
    fun setHapticConfig(ep: Endpoint, flags: Int, intensity: Int): Boolean {
        val fi = feature(FEATURE_HAPTIC) ?: return false
        return ep.request(index, fi, 2, byteArrayOf(flags.toByte(), intensity.toByte(), 0), SLOW_TIMEOUT) is Reply.Ok
    }

    /** Plays a haptic waveform (0x19B0 play). The device answers at once; the waveform
     *  itself takes a moment, so a caller about to move the mouse away should wait. */
    fun playHaptic(ep: Endpoint, waveform: Int): Boolean {
        val fi = feature(FEATURE_HAPTIC) ?: return false
        return ep.request(index, fi, 4, byteArrayOf(waveform.toByte(), 0, 0), 300) is Reply.Ok
    }

    /** The same device at another endpoint/slot (feature indices do not change). */
    fun relocated(endpoint: Int, index: Int) = copy(endpoint = endpoint, index = index)

    /** Identity used to remember a device across reconnects and rescans. */
    fun key(): String = wpid?.let { "%04X".format(it) } ?: name

    /** Moves the device to another channel (0-based). The device drops its link right
     *  away, so a missing reply is expected and not treated as an error. */
    fun setHost(ep: Endpoint, channel: Int): Boolean {
        val fi = feature(FEATURE_CHANGE_HOST) ?: return false
        ep.request(index, fi, 1, byteArrayOf(channel.toByte()), 300)
        return true
    }

    /** Reads how a control reports right now; null when the device lacks it (or is away). */
    fun controlReporting(ep: Endpoint, cid: Int): Reporting? {
        val fi = feature(FEATURE_REPROG_V4) ?: return null
        val r = ep.request(index, fi, 2, cidBytes(cid), FAST_TIMEOUT) as? Reply.Ok ?: return null
        return Reporting(diverted = r.data.u(2) and 0x01 != 0, rawXy = r.data.u(2) and 0x10 != 0)
    }

    /** Diverts (or releases) a control so its presses come to us as notifications
     *  instead of acting on the device, and with [rawXy] also the mouse movement while
     *  it is held (null leaves that as it is). Not persistent: the device forgets it
     *  when it reconnects, which is why the engine re-applies it. */
    fun setReporting(ep: Endpoint, cid: Int, divert: Boolean, rawXy: Boolean?): Boolean {
        val fi = feature(FEATURE_REPROG_V4) ?: return false
        // bit0 divert + bit1 "divert valid"; bit4 raw XY + bit5 "raw XY valid".
        var flags = 0x02 or (if (divert) 1 else 0)
        if (rawXy != null) flags = flags or 0x20 or (if (rawXy) 0x10 else 0)
        val params = cidBytes(cid) + byteArrayOf(flags.toByte(), 0, 0)
        // An idle mouse can be slow to answer; a missed reply is checked by the caller.
        return ep.request(index, fi, 3, params, SLOW_TIMEOUT) is Reply.Ok
    }

    /** If [report] is a "diverted raw XY" notification from this device (the mouse moved
     *  while a button with raw XY diverted is held), returns the movement (dx, dy). */
    fun rawMovement(report: ByteArray): Pair<Int, Int>? {
        val fi = feature(FEATURE_REPROG_V4) ?: return null
        if (report.size < 8 || report.u(1) != index || report.u(2) != fi || report.u(3) and 0xF0 != 0x10) return null
        return Pair(s16(report, 4), s16(report, 6))
    }

    /** If [report] is a "diverted buttons" notification from this device, returns the
     *  control IDs currently held down. */
    fun pressedControls(report: ByteArray): List<Int>? {
        val fi = feature(FEATURE_REPROG_V4) ?: return null
        if (report.size < 12 || report.u(1) != index || report.u(2) != fi || report.u(3) != 0x00) return null
        return (4 until 12 step 2).map { (report.u(it) shl 8) or report.u(it + 1) }.filter { it != 0 }
    }
}

private fun cidBytes(cid: Int) = byteArrayOf((cid shr 8).toByte(), cid.toByte())

private fun s16(b: ByteArray, i: Int): Int = ((b.u(i) shl 8) or b.u(i + 1)).toShort().toInt()

private fun featureIndex(ep: Endpoint, index: Int, feature: Int, timeoutMs: Long): Int? {
    val r = ep.request(index, 0x00, 0, cidBytes(feature), timeoutMs) as? Reply.Ok ?: return null
    return if (r.data.u(0) != 0 || feature == FEATURE_ROOT) r.data.u(0) else null
}

private fun readName(ep: Endpoint, index: Int, fi: Int): String {
    val c = ep.request(index, fi, 0, byteArrayOf(), SLOW_TIMEOUT) as? Reply.Ok ?: return ""
    val len = c.data.u(0)
    val name = ArrayList<Byte>()
    while (name.size < len) {
        val chunk = ep.request(index, fi, 1, byteArrayOf(name.size.toByte()), SLOW_TIMEOUT) as? Reply.Ok ?: break
        val take = minOf(len - name.size, chunk.data.size)
        name.addAll(chunk.data.take(take))
    }
    return String(name.toByteArray(), Charsets.UTF_8).trimEnd('\u0000')
}

/** Reads the receiver's pairing table. Works even when devices are asleep or away.
 *  Bolt and Unifying keep it in register 0xB5 with different sub-pages and layouts. */
fun receiverPairings(ep: Endpoint): List<Pairing> {
    val out = mutableListOf<Pairing>()
    if (ep.kind != EndpointKind.Receiver) return out
    // Bolt: sub-page 0x50 + slot -> [sub, kind, wpid_lo, wpid_hi, serial(4), ...]
    for (slot in 1..6) {
        val r = ep.readRegister(0x83, 0xB5, 0x50 + slot) as? Reply.Ok ?: continue
        if (r.data.size >= 4) {
            out += Pairing(slot, DeviceKind.fromReceiver(r.data.u(1)), r.data.u(2) or (r.data.u(3) shl 8))
        }
    }
    if (out.isNotEmpty()) return out
    // Unifying: sub-page 0x20 + (slot - 1) -> [sub, dest_id, interval, wpid_hi, wpid_lo,
    // _, _, kind]. Unlike Bolt the pages count from 0: 0x20 is slot 1 (checked on a
    // real receiver: 0x20 = MX Master 3 in slot 1, 0x21 = MX Keys in slot 2).
    for (slot in 1..6) {
        val r = ep.readRegister(0x83, 0xB5, 0x20 + slot - 1) as? Reply.Ok ?: continue
        if (r.data.size >= 8) {
            out += Pairing(slot, DeviceKind.fromReceiver(r.data.u(7)), (r.data.u(3) shl 8) or r.data.u(4))
        }
    }
    return out
}

/** Slots worth asking on an endpoint: the receiver's recorded slots when its table is
 *  readable, all six otherwise, or the single direct index for Bluetooth devices. */
fun candidateSlots(ep: Endpoint, pairings: List<Pairing>): List<Int> = when {
    ep.kind == EndpointKind.Direct -> listOf(DIRECT_INDEX)
    pairings.isNotEmpty() -> pairings.map { it.slot }
    else -> (1..6).toList()
}

/** Asks one slot for its identity and features. Returns null when nothing answers. */
fun probeSlot(ep: Endpoint, endpoint: Int, index: Int, pairing: Pairing?): Device? {
    // Root feature lookup doubles as a ping; idle keyboards need the long timeout.
    if (featureIndex(ep, index, FEATURE_ROOT, SLOW_TIMEOUT) == null) {
        Log.d("slot $index: not online")
        return null
    }
    val features = mutableMapOf<Int, Int>()
    for (f in listOf(FEATURE_NAME, FEATURE_CHANGE_HOST, FEATURE_REPROG_V4, FEATURE_HAPTIC)) {
        featureIndex(ep, index, f, SLOW_TIMEOUT)?.let { features[f] = it }
    }
    val nameFi = features[FEATURE_NAME]
    val name = nameFi?.let { readName(ep, index, it) } ?: ""
    // Receiver devices are identified by the pairing record; a Bluetooth device has no
    // record, so ask it for its type and use its Bluetooth product ID as the identity.
    val kind = when {
        pairing != null -> pairing.kind
        nameFi != null -> (ep.request(index, nameFi, 2, byteArrayOf(), SLOW_TIMEOUT) as? Reply.Ok)
            ?.let { DeviceKind.fromDeviceType(it.data.u(0)) }
        else -> null
    }
    val wpid = when {
        pairing != null -> pairing.wpid
        // 0: a Bluetooth device whose product ID could not be read; known by its name.
        ep.kind == EndpointKind.Direct -> ep.productId.takeIf { it != 0 }
        else -> null
    }
    return Device(endpoint, index, name.ifEmpty { ep.product }, kind, wpid, features)
}

/** Asks a receiver to report devices linking and unlinking (notification 0x41), which
 *  is how flockswitch notices the mouse coming back. Only the "wireless notifications"
 *  bit is added, other flags are kept. */
fun enableLinkNotifications(ep: Endpoint) {
    if (ep.kind != EndpointKind.Receiver) return
    val r = ep.readRegister(0x81, 0x00, 0) as? Reply.Ok ?: return
    val flags = byteArrayOf(r.data[0], r.data[1], r.data[2])
    if (flags.u(1) and 0x01 == 0) {
        ep.writeRegister(0x00, byteArrayOf(flags[0], (flags.u(1) or 0x01).toByte(), flags[2]))
        Log.i("turned on link notifications on ${ep.product}")
    }
}
