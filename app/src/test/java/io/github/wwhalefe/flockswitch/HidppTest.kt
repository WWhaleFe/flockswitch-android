package io.github.wwhalefe.flockswitch

import io.github.wwhalefe.flockswitch.hidpp.DeviceKind
import io.github.wwhalefe.flockswitch.hidpp.Endpoint
import io.github.wwhalefe.flockswitch.hidpp.EndpointKind
import io.github.wwhalefe.flockswitch.hidpp.Pairing
import io.github.wwhalefe.flockswitch.hidpp.Reply
import io.github.wwhalefe.flockswitch.hidpp.Transport
import io.github.wwhalefe.flockswitch.hidpp.candidateSlots
import io.github.wwhalefe.flockswitch.hidpp.probeSlot
import io.github.wwhalefe.flockswitch.hidpp.receiverPairings
import io.github.wwhalefe.flockswitch.hidpp.u
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HidppTest {
    init {
        Log.sink = { _, _ -> }
    }

    /** Answers with canned reports; records what was written. */
    private class Scripted(val answer: (ByteArray) -> List<ByteArray>) : Transport {
        override val kind = EndpointKind.Receiver
        override val product = "scripted"
        override val productId = 0xC52B
        override val key = "scripted"
        val queue = ArrayDeque<ByteArray>()
        override fun write(report: ByteArray) {
            queue.addAll(answer(report))
        }

        override fun read(timeoutMs: Int): ByteArray? = queue.removeFirstOrNull()
        override fun close() {}
    }

    private fun bytes(vararg b: Int, size: Int = 20) = ByteArray(size).also { r -> b.forEachIndexed { i, v -> r[i] = v.toByte() } }

    @Test
    fun notificationsArrivingDuringARequestAreKeptForLater() {
        val link = bytes(0x10, 0x02, 0x41, 0x10, 0x42, 0x42, 0xB0, size = 7)
        val t = Scripted { req -> listOf(link, bytes(0x11, req.u(1), req.u(2), req.u(3), 0x07)) }
        val ep = Endpoint(t)
        val r = ep.request(1, 0x00, 0, byteArrayOf(0x18, 0x14), 500)
        assertTrue(r is Reply.Ok)
        assertEquals(7, (r as Reply.Ok).data.u(0))
        assertArrayEquals(link, ep.nextReport(1))
    }

    @Test
    fun aReplyToAnEarlierRequestIsNotTakenForTheCurrentOne() {
        var first = true
        val t = Scripted { req ->
            val stale = bytes(0x11, req.u(1), req.u(2), (req.u(3) and 0xF0) or 0x0, 0x99)
            val real = bytes(0x11, req.u(1), req.u(2), req.u(3), 0x01)
            if (first) {
                first = false
                listOf(stale, real)
            } else {
                listOf(real)
            }
        }
        val r = Endpoint(t).request(1, 0x02, 0, byteArrayOf(), 500) as Reply.Ok
        assertEquals(1, r.data.u(0))
    }

    @Test
    fun receiverErrorsAndTimeouts() {
        val error = Scripted { req -> listOf(bytes(0x10, req.u(1), 0x8F, req.u(2), req.u(3), 0x09, size = 7)) }
        assertEquals(Reply.Error, Endpoint(error).request(3, 0x00, 0, byteArrayOf(), 500))
        val silent = Scripted { emptyList() }
        assertEquals(Reply.Timeout, Endpoint(silent).request(3, 0x00, 0, byteArrayOf(), 100))
    }

    @Test
    fun unifyingPairingsUseTheirOwnLayout() {
        // Bolt pages (0x5n) fail; Unifying page 0x20 = slot 1 (MX Master 3, mouse),
        // 0x21 = slot 2 (MX Keys, keyboard).
        val t = Scripted { req ->
            val p0 = req.u(4)
            val table = mapOf(0x20 to intArrayOf(0x40, 0x23, 0x12, 0x4082, 0, 0, 0x02), 0x21 to intArrayOf(0x41, 0x23, 0x12, 0x408A, 0, 0, 0x01))
            val rec = table[p0]
            if (req.u(2) == 0x83 && rec != null) {
                val wpid = rec[3]
                listOf(bytes(0x11, 0xFF, 0x83, 0xB5, p0, rec[0], rec[1], wpid shr 8, wpid and 0xFF, 0, 0, rec[6]))
            } else {
                listOf(bytes(0x10, 0xFF, 0x8F, req.u(2), req.u(3), 0x03, size = 7))
            }
        }
        val ep = Endpoint(t)
        val pairings = receiverPairings(ep)
        assertEquals(
            listOf(Pairing(1, DeviceKind.Mouse, 0x4082), Pairing(2, DeviceKind.Keyboard, 0x408A)),
            pairings,
        )
        assertEquals(listOf(1, 2), candidateSlots(ep, pairings))
    }

    @Test
    fun aSlotThatDoesNotAnswerIsOffline() {
        val t = Scripted { req -> listOf(bytes(0x10, req.u(1), 0x8F, req.u(2), req.u(3), 0x09, size = 7)) }
        assertEquals(null, probeSlot(Endpoint(t), 0, 2, null))
    }

    @Test
    fun buttonAndMovementNotifications() {
        val receiver = FakeReceiver(
            mapOf(4 to FakeDevice(0xB042, 2, "MX Master 4", controls = mutableMapOf(BUTTON_WHEEL_MODE to 0))),
        )
        val d = probeSlot(Endpoint(receiver), 0, 4, Pairing(4, DeviceKind.Mouse, 0xB042))!!
        assertEquals("MX Master 4", d.name)
        assertEquals(listOf(0x00C4, 0x00C3), d.pressedControls(bytes(0x11, 4, 3, 0x00, 0x00, 0xC4, 0x00, 0xC3)))
        assertEquals(emptyList<Int>(), d.pressedControls(bytes(0x11, 4, 3, 0x00)))
        assertEquals(null, d.pressedControls(bytes(0x11, 3, 3, 0x00, 0x00, 0xC4))) // another slot
        assertEquals(Pair(-200, 10), d.rawMovement(bytes(0x11, 4, 3, 0x10, 0xFF, 0x38, 0x00, 0x0A)))
    }
}
