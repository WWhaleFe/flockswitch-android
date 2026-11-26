package io.github.wwhalefe.flockswitch

import io.github.wwhalefe.flockswitch.net.DirRules
import io.github.wwhalefe.flockswitch.net.KeyRules
import io.github.wwhalefe.flockswitch.net.NetStatus
import io.github.wwhalefe.flockswitch.net.ReconnectGate
import io.github.wwhalefe.flockswitch.net.KeyRuleEdits
import io.github.wwhalefe.flockswitch.net.keyRulesPollMs
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class KeyRulesTest {
    @Test fun idlePollingBacksOffAndKeepsConnectedAndPendingEditsResponsive() {
        assertEquals(1000L, keyRulesPollMs(connected = false, pending = false))
        assertEquals(200L, keyRulesPollMs(connected = true, pending = false))
        assertEquals(200L, keyRulesPollMs(connected = false, pending = true))
        assertEquals(200L, keyRulesPollMs(connected = true, pending = true))
    }

    @Test fun anEditWakesIdlePollingAndStaysPendingUntilAcknowledged() = runBlocking {
        val queue = KeyRuleEdits()
        val base = KeyRules.defaultFor("mac")
        val changed = base.copy(capture = false)
        queue.observe(mapOf("mac>android" to base))
        assertFalse(queue.hasPending)
        val waiting = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(1000) { queue.awaitChange(5000) }
        }
        queue.edit("mac>android", changed)
        waiting.await()
        assertTrue(queue.hasPending)
        val sent = queue.next()!!
        assertTrue(queue.hasPending)
        queue.observe(sent)
        assertFalse(queue.hasPending)
        assertEquals(changed, queue.rules.value["mac>android"])
    }

    @Test fun configurationAndReconnectCanWakeIdlePolling() = runBlocking {
        val queue = KeyRuleEdits()
        val waiting = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(1000) { queue.awaitChange(5000) }
        }
        queue.wake()
        waiting.await()
        assertFalse(queue.hasPending)
    }

    @Test fun rapidEditsWaitForAcknowledgementAndPreserveOtherDirections() {
        val base = KeyRules.defaultFor("mac")
        val first = base.copy(mod_mode = "position")
        val second = first.copy(hangul_dst = "control_space")
        val other = DirRules(capture = false)
        val queue = KeyRuleEdits()
        queue.observe(mapOf("mac>android" to base, "windows>mac" to other))
        queue.edit("mac>android", first)
        assertEquals(first, queue.next()!!["mac>android"])
        queue.edit("mac>android", second)
        queue.edit("linux>android", base)
        assertNull(queue.next())
        queue.observe(mapOf("mac>android" to first, "windows>mac" to other.copy(hanja = false)))
        assertEquals(second, queue.rules.value["mac>android"])
        val next = queue.next()!!
        assertEquals(second, next["mac>android"])
        assertEquals(base, next["linux>android"])
        assertEquals(other.copy(hanja = false), next["windows>mac"])
        queue.observe(next)
        assertNull(queue.next())
    }

    @Test fun reconnectRetainsUnconfirmedEdits() {
        val queue = KeyRuleEdits()
        val desired = KeyRules.defaultFor("mac").copy(capture = false)
        queue.edit("mac>android", desired)
        queue.next()
        queue.disconnected()
        queue.observe(mapOf("windows>linux" to DirRules()))
        assertEquals(desired, queue.next()!!["mac>android"])
    }
    private val mac = "mac"

    @Test fun modeTablesMatchNetcore() {
        // Mac to PC by function: Command is Ctrl, Control is Windows, Option is Alt.
        assertEquals(KeyRules.CTRL, KeyRules.modeTarget(mac, "android", "function", KeyRules.GUI))
        assertEquals(KeyRules.GUI, KeyRules.modeTarget(mac, "android", "function", KeyRules.CTRL))
        assertEquals(KeyRules.ALT, KeyRules.modeTarget(mac, "android", "function", KeyRules.ALT))
        // By position: Control is Ctrl, Option is Windows, Command is Alt.
        assertEquals(KeyRules.CTRL, KeyRules.modeTarget(mac, "android", "position", KeyRules.CTRL))
        assertEquals(KeyRules.GUI, KeyRules.modeTarget(mac, "android", "position", KeyRules.ALT))
        assertEquals(KeyRules.ALT, KeyRules.modeTarget(mac, "android", "position", KeyRules.GUI))
        // Off, and PC to Android, change nothing.
        for (kind in listOf(KeyRules.CTRL, KeyRules.ALT, KeyRules.GUI)) {
            assertEquals(kind, KeyRules.modeTarget(mac, "android", "off", kind))
            assertEquals(kind, KeyRules.modeTarget("windows", "android", "function", kind))
            assertEquals(kind, KeyRules.modeTarget("linux", "android", "position", kind))
        }
    }

    @Test fun modifierOverrideTouchesLeftAndRight() {
        val base = KeyRules.defaultFor(mac)
        // Control normally becomes Meta (by function); make it Alt.
        val edited = KeyRules.withModTarget(mac, "android", base, KeyRules.CTRL, KeyRules.ALT)
        assertEquals(mapOf("224" to 0xE2, "228" to 0xE6), edited.mods)
        assertEquals(KeyRules.ALT, KeyRules.modTarget(mac, "android", edited, KeyRules.CTRL))
        // Asking for what the mode gives anyway removes the override.
        val back = KeyRules.withModTarget(mac, "android", edited, KeyRules.CTRL, KeyRules.GUI)
        assertTrue(back.mods.isEmpty())
        // A new mode starts from its own table.
        assertTrue(KeyRules.withModMode(edited, "position").mods.isEmpty())
        assertEquals("position", KeyRules.withModMode(edited, "position").mod_mode)
    }

    @Test fun hangulSourceKeys() {
        var rules = KeyRules.defaultFor(mac)
        assertEquals(listOf("caps_lock", "shift_space"), rules.hangul_src)
        assertEquals("shift_space", rules.hangul_dst)
        rules = KeyRules.withHangulSource(mac, rules, "control_space", true)
        // Kept in the order they are offered.
        assertEquals(listOf("caps_lock", "control_space", "shift_space"), rules.hangul_src)
        rules = KeyRules.withHangulSource(mac, rules, "caps_lock", false)
        assertEquals(listOf("control_space", "shift_space"), rules.hangul_src)
        // A key this system does not offer cannot be added.
        assertEquals(listOf("lang1"), KeyRules.withHangulSource("windows", KeyRules.defaultFor("windows"), "caps_lock", true).hangul_src)
        assertEquals("control_space", KeyRules.withHangulTarget(rules, "control_space").hangul_dst)
    }

    @Test fun defaultsReproduceTwoPointZero() {
        val windows = KeyRules.defaultFor("windows")
        assertEquals(listOf("lang1"), windows.hangul_src)
        assertEquals("shift_space", windows.hangul_dst)
        assertTrue(windows.hanja)
        assertFalse(KeyRules.defaultFor(mac).hanja)
        assertEquals(listOf("shift_space", "control_space"), KeyRules.ANDROID_HANGUL)
        assertEquals(setOf("mac>android", "windows>android", "linux>android"), KeyRules.forAndroid(emptyMap()).keys)
    }

    @Test fun jsonMatchesNetcoreShape() {
        val all = mapOf(
            "mac>android" to KeyRules.withModTarget(mac, "android", KeyRules.defaultFor(mac), KeyRules.CTRL, KeyRules.ALT),
            "windows>mac" to DirRules(hangul_src = emptyList(), hangul_dst = "caps_lock"),
        )
        val root = Config.json.parseToJsonElement(KeyRules.toJson(all)).jsonObject
        val first = root.getValue("mac>android").jsonObject
        assertEquals("function", first.getValue("mod_mode").jsonPrimitive.content)
        assertEquals(0xE2, first.getValue("mods").jsonObject.getValue("224").jsonPrimitive.int)
        assertEquals(listOf("caps_lock", "shift_space"), first.getValue("hangul_src").jsonArray.map { it.jsonPrimitive.content })
        assertEquals("shift_space", first.getValue("hangul_dst").jsonPrimitive.content)
        assertTrue(first.getValue("caps_long_press").jsonPrimitive.boolean)
        assertEquals(4, first.getValue("nav").jsonArray.size)
        assertTrue(root.getValue("windows>mac") is JsonObject)
        // What netcore writes reads back, directions Android does not edit included.
        val status = NetStatus.parse("""{"running":true,"keys":${KeyRules.toJson(all)}}""")
        assertEquals(all, status.keys)
        assertEquals(all, KeyRules.merged(status.keys, emptyMap()))
        val edited = all.getValue("mac>android").copy(capture = false)
        assertEquals(edited, KeyRules.merged(status.keys, mapOf("mac>android" to edited)).getValue("mac>android"))
        assertEquals(all.getValue("windows>mac"), KeyRules.merged(status.keys, mapOf("mac>android" to edited)).getValue("windows>mac"))
    }

    @Test fun statusReadsWhatNetcoreWrites() {
        val text = """{"running":true,"me":{"id":"me1","name":"Phone","port":47160,"error":null},
            "discovered":[{"id":"a","name":"Mac mini","os":"mac","addr":"192.168.0.2:47160","paired":false,"extra":1}],
            "peers":[{"id":"m","connected":true,"rtt_ms":4.2,"can_send":true,"via_master":false}],
            "pairing":{"peer_name":"Mac mini","code":"123456","stage":"compare","reason":null},
            "layout":[{"id":"m","col":2,"row":1},{"id":"me1","col":3,"row":1}],
            "reconnecting":true,"state":{"mode":"idle","peer":null}}"""
        val s = NetStatus.parse(text)
        assertTrue(s.running && s.reconnecting)
        assertEquals("Phone", s.me.name)
        assertEquals("192.168.0.2:47160", s.discovered.single().addr)
        assertEquals(4.2, s.peer("m")!!.rtt_ms!!, 0.0)
        assertEquals("123456", s.pairing!!.code)
        // The master's cell is to the left of this device's.
        assertEquals("left", s.masterSide("m"))
        assertNull(s.masterSide("unknown"))
        assertEquals(NetStatus(), NetStatus.parse("not json"))
        assertFalse(NetStatus.parse("""{"running":false}""").running)
    }

    @Test fun masterSideNeedsNeighbours() {
        fun at(col: Int, row: Int) = NetStatus(
            me = io.github.wwhalefe.flockswitch.net.NetMe(id = "me"),
            layout = listOf(io.github.wwhalefe.flockswitch.net.NetCell("me", 2, 1), io.github.wwhalefe.flockswitch.net.NetCell("m", col, row)),
        ).masterSide("m")
        assertEquals("right", at(3, 1))
        assertEquals("top", at(2, 0))
        assertEquals("bottom", at(2, 2))
        assertNull(at(4, 1))
        assertNull(at(3, 2))
    }

    @Test fun reconnectsAreNotRepeatedAtOnce() {
        val gate = ReconnectGate(1500)
        assertTrue(gate.allow(10_000))
        // Unlock, screen on and the network coming back together: one redial.
        assertFalse(gate.allow(10_300))
        assertFalse(gate.allow(11_400))
        assertTrue(gate.allow(11_600))
        // A clock set back is not stuck behind an old time.
        assertTrue(gate.allow(100))
    }
}
