package io.github.wwhalefe.flockswitch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigTest {
    @Test
    fun edges() {
        val c = Config().copy(edges = Edges(right = 2, dwellMs = 800))
        assertTrue(c.edges.any())
        assertEquals(2, c.edges.channel(Edge.Right))
        assertEquals(0, c.edges.with(Edge.Right, 0).channel(Edge.Right))
        assertFalse(c.edges.with(Edge.Right, 0).any())
        assertTrue(c.problems().isEmpty())
        assertFalse(Config().copy(edges = Edges(left = 4)).problems().isEmpty())
        assertFalse(Config().copy(edges = Edges(dwellMs = 50)).problems().isEmpty())
        assertEquals(c.edges, Config.fromJson(c.toJson()).edges)
        // Settings from before edges existed have every edge off.
        assertEquals(Edges(), Config.fromJson("{\"enabled\": true}").edges)
    }

    @Test
    fun edgeCountdownSettings() {
        assertFalse(Edges().requireControl)
        assertEquals(EdgeAnimStyle.Ring, Edges().animStyle)
        val e = Edges(requireControl = true, animStyle = EdgeAnimStyle.Vortex, animDirection = EdgeAnimDirection.Both, animShrink = true)
        val c = Config().copy(edges = e)
        assertEquals(e, Config.fromJson(c.toJson()).edges)
        // A style from a newer version falls back to the default, keeping everything else.
        val newer = c.toJson().replace("\"Vortex\"", "\"Sparkle\"")
        assertEquals(EdgeAnimStyle.Ring, Config.fromJson(newer).edges.animStyle)
        assertTrue(Config.fromJson(newer).edges.requireControl)
        assertTrue(EdgeAnimStyle.Bar.hasDirection && !EdgeAnimStyle.Bar.canShrink)
        assertFalse(EdgeAnimStyle.None.hasDirection || EdgeAnimStyle.Vortex.canShrink)
    }

    @Test
    fun colours() {
        assertEquals(0x0A84FF, parseColor("#0a84ff"))
        assertEquals(0xFFCC00, parseColor(" #FFCC00 "))
        assertEquals(0, parseColor("#000000"))
        assertEquals("#0A84FF", formatColor(0x0A84FF))
        assertEquals("#000000", formatColor(0))
        for (bad in listOf("red", "#12345", "#1234567", "#GGGGGG", "0A84FF", "#+12345", "#-12345", "#한글한글한글", "")) {
            assertNull(bad, parseColor(bad))
        }
        assertEquals(7, COLOR_PRESETS.size)
        assertEquals(DEFAULT_EFFECT_COLOR, formatColor(COLOR_PRESETS.first()))
        assertTrue(COLOR_PRESETS.all { parseColor(formatColor(it)) == it })
    }

    @Test
    fun effectColoursAreSavedAndChecked() {
        assertEquals("#FFCC00", Edges().animColor)
        assertEquals("#FFCC00", Locate().color)
        val c = Config().copy(edges = Edges(animColor = "#0A84FF", animStyle = EdgeAnimStyle.Random), locate = Locate(color = "#34C759", style = LocateStyle.Random))
        assertTrue(c.problems().isEmpty())
        val back = Config.fromJson(c.toJson())
        assertEquals(c.edges, back.edges)
        assertEquals(c.locate, back.locate)
        // Settings from before colours existed.
        assertEquals("#FFCC00", Config.fromJson("{\"enabled\": true}").edges.animColor)
        assertFalse(Config().copy(edges = Edges(animColor = "yellow")).problems().isEmpty())
        assertFalse(Config().copy(locate = Locate(color = "#12")).problems().isEmpty())
    }

    @Test
    fun locateSettings() {
        assertFalse(Locate().on)
        val l = Locate(on = true, onHotkey = false, style = LocateStyle.Spotlight)
        assertEquals(l, Config.fromJson(Config().copy(locate = l).toJson()).locate)
        assertEquals(Locate(), Config.fromJson("{\"enabled\": true}").locate)
    }

    @Test
    fun hapticsSettings() {
        assertTrue(Haptics().progressive)
        val h = Haptics(progressive = false, switchWaveform = 27)
        assertEquals(h, Config.fromJson(Config().copy(haptics = h).toJson()).haptics)
        // Settings from before these existed.
        assertEquals(Haptics(), Config.fromJson("{\"enabled\": true}").haptics)
        assertEquals(16, Waveform.ALL.size)
        assertEquals(27, Waveform.ALL.last())
    }

    private fun button(vararg channels: Int) = ButtonSetting(on = true, channels = channels.toList())

    private fun press(b: ButtonSetting, current0: Int, count: Int) = b.channelAfter(Gesture.Press, current0, count)

    @Test
    fun cycleWrapsLikeTheHardwareButton() {
        val b = button(1, 2, 3)
        assertEquals(2, press(b, 0, 3))
        assertEquals(3, press(b, 1, 3))
        assertEquals(1, press(b, 2, 3))
    }

    @Test
    fun cycleSkipsChannelsTheDeviceLacks() = assertEquals(1, press(button(1, 2, 3), 1, 2))

    @Test
    fun cycleFromUnlistedChannelGoesToFirst() = assertEquals(1, press(button(1, 2), 2, 3))

    @Test
    fun oneChannelAlwaysGoesThere() {
        val b = button(2)
        assertNull(press(b, 1, 3))
        assertEquals(2, press(b, 0, 3))
        assertEquals(2, press(b, 2, 3))
        assertNull(press(button(3), 0, 2)) // the device has no channel 3
    }

    @Test
    fun gestures() {
        assertEquals(Gesture.Press, Gesture.fromMovement(10, -20))
        assertEquals(Gesture.Right, Gesture.fromMovement(300, 40))
        assertEquals(Gesture.Left, Gesture.fromMovement(-300, 100))
        assertEquals(Gesture.Up, Gesture.fromMovement(20, -200))
        assertEquals(Gesture.Down, Gesture.fromMovement(-30, 200))
        val b = button(1, 2).copy(
            moves = Moves(
                left = MoveSetting(true, listOf(1)),
                right = MoveSetting(true, listOf(3)),
                up = MoveSetting(true, listOf(1, 3)),
                down = MoveSetting(false, listOf(2)), // set but off
            ),
        )
        assertEquals(2, b.channelAfter(Gesture.Press, 0, 3))
        assertEquals(3, b.channelAfter(Gesture.Right, 0, 3))
        assertNull(b.channelAfter(Gesture.Left, 0, 3)) // already on 1
        assertEquals(3, b.channelAfter(Gesture.Up, 0, 3))
        assertEquals(1, b.channelAfter(Gesture.Up, 2, 3))
        assertNull(b.channelAfter(Gesture.Down, 0, 3)) // off
        assertTrue(b.usesMoves())
        assertFalse(button(1).usesMoves())
    }

    @Test
    fun jsonRoundTrip() {
        val c = Config(follow = listOf("B366"), bluetooth = true)
            .withButton(BUTTON_GESTURE, button(3).copy(moves = Moves(left = MoveSetting(true, listOf(2)))))
        assertEquals(c, Config.fromJson(c.toJson()))
    }

    @Test
    fun unreadableOrUnusableSettingsGiveTheDefaults() {
        assertEquals(Config(), Config.fromJson("not json"))
        val noButton = Config().withButton(BUTTON_WHEEL_MODE, ButtonSetting(on = false))
        assertEquals(Config(), Config.fromJson(noButton.toJson()))
        // Unknown keys (from a newer version) are ignored.
        assertEquals(Config(enabled = false), Config.fromJson("""{"enabled": false, "future": 1}"""))
    }

    @Test
    fun problems() {
        assertTrue(Config().problems().isEmpty())
        assertFalse(Config().withButton(BUTTON_WHEEL_MODE, button(4)).problems().isEmpty())
        assertFalse(Config().withButton(BUTTON_WHEEL_MODE, button()).problems().isEmpty())
        assertFalse(Config().withButton(BUTTON_WHEEL_MODE, ButtonSetting(on = false)).problems().isEmpty())
    }

    @Test
    fun untickOneDeviceFromAll() {
        val c = Config().toggleFollow(listOf("B366", "B377"), "B366")
        assertEquals(listOf("B377"), c.follow)
    }

    @Test
    fun tickingTheLastOneCollapsesToAll() {
        val c = Config(follow = listOf("B377")).toggleFollow(listOf("B366", "B377"), "B366")
        assertEquals(listOf("all"), c.follow)
    }

    @Test
    fun followMatching() {
        assertTrue(Config().follows(0xB366, "MX Mechanical"))
        assertTrue(Config(follow = listOf("b366")).follows(0xB366, "x"))
        assertFalse(Config(follow = listOf("b366")).follows(0xB042, "MX Master 4"))
        assertTrue(Config(follow = listOf("keys")).follows(null, "MX Keys S"))
        assertFalse(Config(follow = emptyList()).follows(0xB366, "MX Mechanical"))
    }

    @Test
    fun versions() {
        assertTrue(isNewer("0.1.1", "0.1.0"))
        assertTrue(isNewer("0.10.0", "0.9.1"))
        assertFalse(isNewer("0.1.0", "0.1.0"))
        assertFalse(isNewer("0.1", "0.1.0"))
    }
}
