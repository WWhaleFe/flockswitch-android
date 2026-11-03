// HID++ transport: request/response exchanges with a receiver or a directly connected
// device over any Transport (USB or Bluetooth).
//
// The design is single-threaded on purpose: every endpoint is polled from one loop,
// and reports that arrive while we wait for a response are kept in `backlog` so the
// caller can process them as notifications afterwards.

package io.github.wwhalefe.flockswitch.hidpp

import io.github.wwhalefe.flockswitch.Log
import java.io.Closeable
import java.io.IOException

const val LOGITECH_VID = 0x046D
const val REPORT_SHORT = 0x10
const val REPORT_LONG = 0x11
const val RECEIVER_INDEX = 0xFF
const val DIRECT_INDEX = 0xFF

const val SHORT_LEN = 7
const val LONG_LEN = 20

/** USB receivers (devices live in slots 1..=6). */
val RECEIVER_PIDS = setOf(
    0xC548, // Bolt
    0xC52B, 0xC532, // Unifying
    0xC52F, 0xC534, 0xC539, 0xC53A, 0xC53F, 0xC547, // nano / Lightspeed
)

enum class EndpointKind {
    /** Bolt / Unifying style receiver: devices live in slots 1..=6. */
    Receiver,

    /** Device connected directly (Bluetooth): device index 0xFF. */
    Direct,
}

/** One way to exchange HID++ reports with a receiver or a directly connected device. */
interface Transport : Closeable {
    val kind: EndpointKind
    val product: String

    /** USB/Bluetooth product ID. For a Bluetooth device this identifies the model. */
    val productId: Int

    /** Stable identity of the endpoint across re-enumerations. */
    val key: String

    /** Sends a report; `report[0]` is its report ID (0x10 short, 0x11 long). */
    @Throws(IOException::class)
    fun write(report: ByteArray)

    /** One report starting with its report ID, or null when none came in [timeoutMs].
     *  Throws when the link is gone. */
    @Throws(IOException::class)
    fun read(timeoutMs: Int): ByteArray?
}

/** Unsigned byte at [i]. */
fun ByteArray.u(i: Int): Int = this[i].toInt() and 0xFF

/** Reply to a request. */
sealed class Reply {
    class Ok(val data: ByteArray) : Reply()

    /** The device or receiver answered with an error (HID++ 1.0 0x8F or HID++ 2.0 0xFF),
     *  e.g. because the device is not linked to this receiver right now. */
    data object Error : Reply()

    /** No answer within the timeout — usually the device is asleep or on another host. */
    data object Timeout : Reply()
}

class Endpoint(private val transport: Transport) : Closeable {
    var kind: EndpointKind = transport.kind
    val product: String get() = transport.product
    val productId: Int get() = transport.productId
    val key: String get() = transport.key
    val backlog = ArrayDeque<ByteArray>()

    /** Set when a read or write fails, e.g. the receiver was unplugged; the engine then rescans. */
    var dead = false
        private set
    private var nextSwid = 0x8

    private fun write(data: ByteArray) {
        if (dead) throw IOException("$product is gone")
        try {
            transport.write(data)
        } catch (e: IOException) {
            dead = true
            throw e
        }
    }

    /** Reads one report, waiting at most [timeoutMs]. */
    fun read(timeoutMs: Int): ByteArray? {
        if (dead) {
            // Behave like an idle device so callers' loops still wait instead of spinning.
            Thread.sleep(timeoutMs.toLong())
            return null
        }
        return try {
            transport.read(timeoutMs)
        } catch (e: IOException) {
            Log.w("$product stopped responding: ${e.message}")
            dead = true
            null
        }
    }

    /** Returns a queued notification first, then polls the device. */
    fun nextReport(timeoutMs: Int): ByteArray? = backlog.removeFirstOrNull() ?: read(timeoutMs)

    private fun swid(): Int {
        // Rotate 0x8..=0xF so late replies to an earlier request are never mistaken
        // for the current one. Logitech's own software uses the low range.
        nextSwid = if (nextSwid >= 0xF) 0x8 else nextSwid + 1
        return nextSwid
    }

    /** HID++ 2.0 feature request. */
    fun request(index: Int, feature: Int, function: Int, params: ByteArray, timeoutMs: Long): Reply {
        val swid = swid()
        val pkt = ByteArray(LONG_LEN)
        pkt[0] = REPORT_LONG.toByte()
        pkt[1] = index.toByte()
        pkt[2] = feature.toByte()
        pkt[3] = ((function shl 4) or swid).toByte()
        params.copyInto(pkt, 4)
        write(pkt)

        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (System.nanoTime() < deadline && !dead) {
            val r = read(20) ?: continue
            if (r.size < SHORT_LEN || r.u(1) != index) {
                backlog.addLast(r)
                continue
            }
            // HID++ 2.0 answers are long reports; anything shorter is not our reply.
            if (r.u(0) == REPORT_LONG && r.size >= LONG_LEN && r.u(2) == feature && r[3] == pkt[3]) {
                return Reply.Ok(r.copyOfRange(4, r.size))
            }
            // HID++ 2.0 error: [11, idx, FF, feature, fn|swid, code]
            if (r.u(2) == 0xFF && r.u(3) == feature && r[4] == pkt[3]) return Reply.Error
            // HID++ 1.0 error from the receiver about this device: [10, idx, 8F, feature, fn|swid, code]
            if (r.u(0) == REPORT_SHORT && r.u(2) == 0x8F && r.u(3) == feature && r[4] == pkt[3]) return Reply.Error
            backlog.addLast(r)
        }
        return Reply.Timeout
    }

    /** HID++ 1.0 register read on the receiver (sub_id 0x81 = short, 0x83 = long). */
    fun readRegister(subId: Int, register: Int, p0: Int): Reply = register(subId, register, byteArrayOf(p0.toByte(), 0, 0))

    /** HID++ 1.0 short register write on the receiver (sub_id 0x80). */
    fun writeRegister(register: Int, value: ByteArray): Reply = register(0x80, register, value)

    private fun register(subId: Int, register: Int, params: ByteArray): Reply {
        val pkt = byteArrayOf(
            REPORT_SHORT.toByte(), RECEIVER_INDEX.toByte(), subId.toByte(), register.toByte(),
            params[0], params[1], params[2],
        )
        write(pkt)

        val wantLen = if (subId == 0x83) LONG_LEN else SHORT_LEN
        val deadline = System.nanoTime() + 1_500_000_000L
        while (System.nanoTime() < deadline && !dead) {
            val r = read(20) ?: continue
            if (r.size >= SHORT_LEN && r.u(1) == RECEIVER_INDEX) {
                // Long register replies echo the sub-page in the first parameter byte.
                val samePage = subId != 0x83 || r[4] == params[0]
                if (r.size >= wantLen && r.u(2) == subId && r.u(3) == register && samePage) {
                    return Reply.Ok(r.copyOfRange(4, r.size))
                }
                if (r.u(2) == 0x8F && r.u(3) == subId && r.u(4) == register) return Reply.Error
            }
            backlog.addLast(r)
        }
        return Reply.Timeout
    }

    override fun close() = transport.close()
}

