package io.github.wwhalefe.flockswitch

import io.github.wwhalefe.flockswitch.hidpp.EndpointKind
import io.github.wwhalefe.flockswitch.hidpp.Transport
import io.github.wwhalefe.flockswitch.hidpp.u

/** A device paired to [FakeReceiver]. Feature indices: root 0, name 1, change host 2,
 *  reprogrammable controls 3. */
class FakeDevice(
    val wpid: Int,
    /** Receiver pairing kind code: 1 keyboard, 2 mouse. */
    val kindCode: Int,
    val name: String,
    var online: Boolean = true,
    val hostCount: Int = 3,
    /** Current channel, 0-based. */
    var current: Int = 1,
    /** Control ID -> reporting flags (bit0 diverted, bit4 raw XY). Empty for a keyboard. */
    val controls: MutableMap<Int, Int> = mutableMapOf(),
    /** Has a haptic motor (feature 0x19B0, index 4), offering waveforms 0-14 and 27. */
    val haptics: Boolean = false,
    var hapticStrength: Int = 60,
)

/** A Bolt receiver answering HID++ the way the real one was seen to. */
class FakeReceiver(val slots: Map<Int, FakeDevice>) : Transport {
    override val kind = EndpointKind.Receiver
    override val product = "USB Receiver"
    override val productId = 0xC548
    override val key = "fake-bolt"

    private val out = ArrayDeque<ByteArray>()

    /** What was done to the devices, in order, e.g. "B366 -> 2". */
    val events = mutableListOf<String>()

    /** Queues a report from a device (a notification), as if it came over the air. */
    fun notify(vararg bytes: Int) {
        val r = ByteArray(maxOf(bytes.size, 20))
        bytes.forEachIndexed { i, b -> r[i] = b.toByte() }
        synchronized(out) { out.addLast(r) }
    }

    /** A "diverted buttons" notification from [slot] with these controls held. */
    fun buttons(slot: Int, vararg cids: Int) {
        val bytes = mutableListOf(0x11, slot, 3, 0x00)
        cids.forEach { bytes += it shr 8; bytes += it and 0xFF }
        notify(*bytes.toIntArray())
    }

    override fun read(timeoutMs: Int): ByteArray? {
        synchronized(out) { out.removeFirstOrNull() }?.let { return it }
        Thread.sleep(1)
        return null
    }

    override fun close() {}

    private fun reply(vararg bytes: Int) {
        val r = ByteArray(if (bytes[0] == 0x10) 7 else 20)
        bytes.forEachIndexed { i, b -> r[i] = b.toByte() }
        synchronized(out) { out.addLast(r) }
    }

    override fun write(report: ByteArray) {
        val idx = report.u(1)
        if (report.u(0) == 0x10) return register(report)
        val feature = report.u(2)
        val fnSw = report.u(3)
        val dev = slots[idx]
        if (dev == null || !dev.online) {
            // HID++ 1.0 error from the receiver: the device is not linked.
            reply(0x10, idx, 0x8F, feature, fnSw, 0x09)
            return
        }
        val fn = fnSw shr 4
        val head = intArrayOf(0x11, idx, feature, fnSw)
        when (feature) {
            0 -> { // root: getFeature
                val id = (report.u(4) shl 8) or report.u(5)
                val index = when (id) {
                    0x0000 -> 0
                    0x0005 -> 1
                    0x1814 -> 2
                    0x1B04 -> if (dev.controls.isEmpty()) 0 else 3
                    0x19B0 -> if (dev.haptics) 4 else 0
                    else -> 0
                }
                reply(*head, index)
            }
            1 -> when (fn) { // name
                0 -> reply(*head, dev.name.length)
                1 -> {
                    val from = report.u(4)
                    val chunk = dev.name.drop(from).take(16).map { it.code }.toIntArray()
                    reply(*head, *chunk)
                }
                else -> reply(*head, if (dev.kindCode == 1) 0 else 3)
            }
            2 -> when (fn) { // change host
                0 -> reply(*head, dev.hostCount, dev.current)
                else -> {
                    // The device leaves at once, without answering.
                    events += "%04X -> %d".format(dev.wpid, report.u(4) + 1)
                    dev.current = report.u(4)
                    dev.online = false
                }
            }
            3 -> { // reprogrammable controls v4
                val cid = (report.u(4) shl 8) or report.u(5)
                val flags = dev.controls[cid]
                if (flags == null) {
                    reply(0x11, idx, 0xFF, feature, fnSw, 0x02)
                    return
                }
                if (fn == 3) {
                    val set = report.u(6)
                    var f = flags
                    if (set and 0x02 != 0) f = (f and 0x01.inv()) or (set and 0x01)
                    if (set and 0x20 != 0) f = (f and 0x10.inv()) or (set and 0x10)
                    dev.controls[cid] = f
                    events += "%04X %04X divert=%b raw=%b".format(dev.wpid, cid, f and 1 != 0, f and 0x10 != 0)
                }
                reply(*head, cid shr 8, cid and 0xFF, dev.controls[cid]!!)
            }
            4 -> when (fn) { // haptics
                0 -> reply(*head, 0, 0, 0, 0, 0x08, 0x00, 0x7F, 0xFF)
                1 -> reply(*head, 0x01, dev.hapticStrength)
                2 -> {
                    dev.hapticStrength = report.u(5)
                    events += "%04X strength %d".format(dev.wpid, dev.hapticStrength)
                    reply(*head)
                }
                else -> {
                    events += "%04X play %d".format(dev.wpid, report.u(4))
                    reply(*head)
                }
            }
            else -> reply(0x11, idx, 0xFF, feature, fnSw, 0x02)
        }
    }

    private fun register(report: ByteArray) {
        val sub = report.u(2)
        val reg = report.u(3)
        val p0 = report.u(4)
        when {
            sub == 0x83 && reg == 0xB5 && p0 in 0x51..0x56 -> {
                val d = slots[p0 - 0x50] ?: return reply(0x10, 0xFF, 0x8F, sub, reg, 0x03)
                val r = intArrayOf(0x11, 0xFF, sub, reg, p0, d.kindCode, d.wpid and 0xFF, d.wpid shr 8)
                reply(*r)
            }
            sub == 0x81 && reg == 0x00 -> reply(0x10, 0xFF, sub, reg, 0, 0, 0)
            sub == 0x80 -> reply(0x10, 0xFF, sub, reg, 0, 0, 0)
            else -> reply(0x10, 0xFF, 0x8F, sub, reg, 0x03)
        }
    }
}

class FakeSource(private val transports: List<Transport>) : EndpointSource {
    override fun keys() = transports.map { it.key }
    override fun open() = transports
}
