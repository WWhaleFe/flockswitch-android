package io.github.wwhalefe.flockswitch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EngineTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val mouseSlot = 4
    private val keyboardSlot = 3

    private fun keyboard() = FakeDevice(wpid = 0xB366, kindCode = 1, name = "MX Mechanical")
    private fun mouse() = FakeDevice(
        wpid = 0xB042, kindCode = 2, name = "MX Master 4",
        controls = mutableMapOf(BUTTON_WHEEL_MODE to 0, BUTTON_GESTURE to 0x01, BUTTON_MIDDLE to 0),
    )

    private fun setup(kb: FakeDevice = keyboard(), m: FakeDevice = mouse()): Triple<Engine, FakeReceiver, Shared> {
        Log.sink = { _, _ -> }
        val receiver = FakeReceiver(mapOf(keyboardSlot to kb, mouseSlot to m))
        val shared = Shared(tmp.root)
        val engine = Engine(shared, FakeSource(listOf(receiver)))
        engine.scan()
        engine.ensureDiverted()
        return Triple(engine, receiver, shared)
    }

    @Test
    fun scanFindsTheMouseAsMasterAndDivertsItsButton() {
        val (engine, receiver, shared) = setup()
        assertEquals(2, engine.devices.size)
        assertEquals("B042", engine.trigger?.key())
        assertEquals(listOf("B042 00C4 divert=true raw=false"), receiver.events)
        val s = shared.status.value
        assertEquals("MX Master 4", s.trigger)
        assertEquals(2, s.channel) // current = 1 (0-based)
        assertEquals(setOf("B042", "B366"), s.devices.map { it.key }.toSet())
        assertTrue(s.devices.first { it.key == "B042" }.master)
        assertFalse(s.devices.first { it.key == "B366" }.master)
    }

    @Test
    fun aPressMovesTheKeyboardFirstAndTheMouseLast() {
        val (engine, receiver, shared) = setup()
        receiver.events.clear()
        // Channels [1, 2, 3], on 2: a press goes to 3.
        engine.handle(0, byteArrayOf(0x11, mouseSlot.toByte(), 3, 0, 0x00, 0xC4.toByte()) + ByteArray(14))
        assertEquals(listOf("B366 -> 3", "B042 -> 3"), receiver.events)
        assertEquals("2 → 3", shared.status.value.lastSwitch)
    }

    @Test
    fun nothingMovesWhilePaused() {
        val (engine, receiver, shared) = setup()
        shared.updateConfig { it.copy(enabled = false) }
        val method = Engine::class.java.getDeclaredMethod("reloadConfig").apply { isAccessible = true }
        method.invoke(engine)
        // Pausing hands the button back.
        assertEquals("B042 00C4 divert=false raw=false", receiver.events.last())
        receiver.events.clear()
        engine.handle(0, byteArrayOf(0x11, mouseSlot.toByte(), 3, 0, 0x00, 0xC4.toByte()) + ByteArray(14))
        assertTrue(receiver.events.isEmpty())
    }

    @Test
    fun aKeyboardOnAnotherComputerDoesNotKeepTheMouseHere() {
        val kb = keyboard().apply { online = false }
        val (engine, receiver, _) = setup(kb = kb)
        receiver.events.clear()
        engine.switchTo(1)
        assertEquals(listOf("B042 -> 1"), receiver.events)
    }

    @Test
    fun aDeviceLeftOutOfTheGroupStays() {
        val (engine, receiver, shared) = setup()
        shared.updateConfig { it.copy(follow = emptyList()) }
        // The engine picks the change up in its loop; apply it the same way.
        val method = Engine::class.java.getDeclaredMethod("reloadConfig").apply { isAccessible = true }
        method.invoke(engine)
        receiver.events.clear()
        engine.switchTo(3)
        assertEquals(listOf("B042 -> 3"), receiver.events)
    }

    @Test
    fun releaseGivesTheButtonBackAsItWas() {
        val (engine, receiver, shared) = setup()
        assertTrue(shared.savedOriginalDivert("B042", BUTTON_WHEEL_MODE) != null)
        receiver.events.clear()
        engine.release()
        assertEquals(listOf("B042 00C4 divert=false raw=false"), receiver.events)
        assertNull(shared.savedOriginalDivert("B042", BUTTON_WHEEL_MODE))
    }

    @Test
    fun aButtonAnotherAppDivertedIsHandedBackDiverted() {
        // The gesture button is already diverted (by another app) before flockswitch
        // takes it: on release it must stay diverted for that app.
        val (engine, receiver, shared) = setup()
        shared.updateConfig { it.withButton(BUTTON_GESTURE, ButtonSetting(on = true, channels = listOf(1))) }
        val method = Engine::class.java.getDeclaredMethod("reloadConfig").apply { isAccessible = true }
        method.invoke(engine)
        receiver.events.clear()
        engine.release()
        assertTrue("B042 00C3 divert=true raw=false" in receiver.events)
        assertTrue("B042 00C4 divert=false raw=false" in receiver.events)
    }

    @Test
    fun gesturesDecideOnRelease() {
        val (engine, receiver, shared) = setup()
        val moves = Moves(left = MoveSetting(on = true, channels = listOf(1)))
        shared.updateConfig {
            it.withButton(BUTTON_GESTURE, ButtonSetting(on = true, channels = listOf(3), moves = moves))
        }
        val method = Engine::class.java.getDeclaredMethod("reloadConfig").apply { isAccessible = true }
        method.invoke(engine)
        assertTrue("B042 00C3 divert=true raw=true" in receiver.events)
        receiver.events.clear()
        val slot = mouseSlot.toByte()
        engine.handle(0, byteArrayOf(0x11, slot, 3, 0, 0x00, 0xC3.toByte()) + ByteArray(14))
        assertTrue(receiver.events.isEmpty()) // waits for the release
        // Raw XY: dx = -200, dy = 10.
        engine.handle(0, byteArrayOf(0x11, slot, 3, 0x10, 0xFF.toByte(), 0x38, 0x00, 0x0A) + ByteArray(12))
        engine.handle(0, byteArrayOf(0x11, slot, 3, 0) + ByteArray(16))
        assertEquals(listOf("B366 -> 1", "B042 -> 1"), receiver.events)
    }

    @Test
    fun linkNotificationsUpdateWhoIsHere() {
        val (engine, receiver, shared) = setup()
        // Keyboard link down: [10, slot, 41, protocol, info | 0x40, wpid_lo, wpid_hi]
        engine.handle(0, byteArrayOf(0x10, keyboardSlot.toByte(), 0x41, 0x10, 0x41, 0x66, 0xB3.toByte()))
        assertFalse(shared.status.value.devices.first { it.key == "B366" }.online)
        assertEquals(2, engine.devices.size) // still remembered
        assertEquals(emptyList<String>(), receiver.events.drop(1))
    }

    private fun reload(engine: Engine) =
        Engine::class.java.getDeclaredMethod("reloadConfig").apply { isAccessible = true }.invoke(engine)

    private fun hapticMouse() = mouse().let { FakeDevice(it.wpid, it.kindCode, it.name, controls = it.controls, haptics = true) }

    @Test
    fun aWaveformJustChosenPlaysOnce() {
        val (engine, receiver, shared) = setup(m = hapticMouse())
        receiver.events.clear()
        shared.updateConfig { it.copy(haptics = it.haptics.copy(arriveWaveform = 12)) }
        reload(engine)
        assertEquals(listOf("B042 play 12"), receiver.events)
        // Waveform 27 is outside the run 0-14 but the MX Master 4 has it.
        receiver.events.clear()
        shared.updateConfig { it.copy(haptics = it.haptics.copy(switchWaveform = 27)) }
        reload(engine)
        assertEquals(listOf("B042 play 27"), receiver.events)
    }

    @Test
    fun aStrengthJustChosenGoesToTheMouseWithAPulse() {
        val (engine, receiver, shared) = setup(m = hapticMouse())
        receiver.events.clear()
        shared.updateConfig { it.copy(haptics = it.haptics.copy(intensity = 50)) }
        reload(engine)
        assertEquals(listOf("B042 strength 50", "B042 play 7"), receiver.events)
    }

    @Test
    fun theTileGoesOnlyWhereTheMouseCanGo() {
        // A mouse with two channels, on 2: like its button, the tile goes to 1 (not 3,
        // which would leave the mouse behind while the keyboard went).
        val m = mouse()
        val twoChannels = FakeDevice(m.wpid, m.kindCode, m.name, hostCount = 2, current = 1, controls = m.controls)
        val (engine, receiver, _) = setup(m = twoChannels)
        receiver.events.clear()
        Engine::class.java.getDeclaredMethod("onPressRequest").apply { isAccessible = true }.invoke(engine)
        assertEquals(listOf("B366 -> 1", "B042 -> 1"), receiver.events)
    }
}
