package io.github.wwhalefe.flockswitch

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class SettingsFileTest {
    private val at = LocalDateTime.of(2026, 11, 10, 9, 5, 7, 123_000_000)
    private val header = SettingsFile.header("0.8.0", "samsung SM-F956N", at)

    /** Settings far from the defaults, touching every part. */
    private val custom = Config(
        enabled = false,
        triggerDevice = "B042",
        follow = listOf("B369", "MX Keys"),
        bluetooth = true,
        startAtBoot = false,
        checkUpdates = false,
        autoUpdate = false,
        buttons = Buttons(
            wheelMode = ButtonSetting(on = true, channels = listOf(2)),
            gesture = ButtonSetting(on = true, channels = listOf(1, 3), moves = Moves(left = MoveSetting(true, listOf(1)), right = MoveSetting(true, listOf(3)))),
        ),
        edges = Edges(dwellMs = 1000, left = 1, right = 3, requireControl = true, animStyle = EdgeAnimStyle.Vortex, animColor = "#0A84FF", animSize = 125),
        haptics = Haptics(onSwitch = false, onArrive = true, onEdge = false, switchWaveform = Waveform.RANDOM, intensity = 75, progressive = false),
        locate = Locate(on = true, onHotkey = false, style = LocateStyle.Spotlight, color = "#FF3B30", size = 150),
    )

    private fun rejected(text: String): ImportResult.Rejected {
        val r = SettingsFile.read(text)
        assertTrue("expected a refusal, got $r", r is ImportResult.Rejected)
        return r as ImportResult.Rejected
    }

    @Test
    fun roundTrip() {
        for (cfg in listOf(Config(), custom)) {
            val r = SettingsFile.read(SettingsFile.write(cfg, header))
            assertEquals(ImportResult.Ok(cfg, header), r)
        }
    }

    @Test
    fun headerSaysWhoWroteIt() {
        val root = Config.json.parseToJsonElement(SettingsFile.write(custom, header)).jsonObject
        val h = root[SettingsFile.HEADER]!!.jsonObject
        assertEquals("flockswitch", h["app"]!!.jsonPrimitive.content)
        assertEquals("android", h["os"]!!.jsonPrimitive.content)
        assertEquals("0.8.0", h["version"]!!.jsonPrimitive.content)
        assertEquals("samsung SM-F956N", h["device"]!!.jsonPrimitive.content)
        assertEquals("2026-11-10T09:05:07", h["date"]!!.jsonPrimitive.content)
        // The rest is config.json as the app saves it, so it reads as saved settings too.
        assertEquals(custom, Config.fromJson(SettingsFile.write(custom, header)))
    }

    @Test
    fun fileName() {
        assertEquals("flockswitch-settings-SM-F956N-20261110-090507.json", SettingsFile.fileName("SM-F956N", at))
        assertEquals("flockswitch-settings-Pixel-9-Pro-Fold-20261110-090507.json", SettingsFile.fileName("Pixel 9 Pro Fold", at))
        assertEquals("flockswitch-settings-a-b-20261110-090507.json", SettingsFile.fileName(" a/b: ", at))
        assertEquals("flockswitch-settings-android-20261110-090507.json", SettingsFile.fileName("  ", at))
    }

    @Test
    fun newerAndOlderFilesLoad() {
        // A newer version's file: an unknown field and an unknown effect keep the rest.
        val newer = SettingsFile.write(custom, header.copy(version = "0.9.0"))
            .replace("\"Vortex\"", "\"Sparkle\"")
            .replaceFirst("{", "{\n  \"futureSetting\": 1,")
        val r = SettingsFile.read(newer) as ImportResult.Ok
        assertEquals(EdgeAnimStyle.Ring, r.config.edges.animStyle)
        assertEquals(custom.locate, r.config.locate)
        // Settings from before 0.6.0 are brought up to date as saved ones are.
        val old = """{"export": {"app": "flockswitch", "os": "android", "version": "0.5.0"}, "haptics": {"onSwitch": false}}"""
        val h = (SettingsFile.read(old) as ImportResult.Ok).config.haptics
        assertTrue(!h.onSwitch && !h.onArrive)
    }

    @Test
    fun refusesWhatIsNotSettings() {
        assertEquals(ImportRejection.NotJson, rejected("").why)
        assertEquals(ImportRejection.NotJson, rejected("enabled = true\n[edges]\nleft = 2").why)
        assertEquals(ImportRejection.NotSettings, rejected("[1, 2, 3]").why)
        assertEquals(ImportRejection.NotSettings, rejected("\"flockswitch\"").why)
        // Plain config.json, or another app's JSON: no header.
        assertEquals(ImportRejection.NotSettings, rejected(Config().toJson()).why)
        assertEquals(ImportRejection.NotSettings, rejected("""{"export": {"app": "something else", "os": "android"}}""").why)
        assertEquals(ImportRejection.NotSettings, rejected("""{"export": "flockswitch"}""").why)
        assertEquals(ImportRejection.TooLarge, rejected(" ".repeat(SettingsFile.MAX_BYTES + 1)).why)
    }

    @Test
    fun refusesDesktopSettings() {
        val r = rejected("""{"export": {"app": "flockswitch", "os": "macos", "version": "0.9.0"}, "enabled": true}""")
        assertEquals(ImportRejection.OtherOs, r.why)
        assertEquals("macos", r.detail)
        assertEquals(ImportRejection.OtherOs, rejected("""{"export": {"app": "flockswitch"}}""").why)
    }

    @Test
    fun refusesUnusableSettings() {
        val good = SettingsFile.write(custom, header)
        // A channel that does not exist.
        val badChannel = rejected(good.replace("\"right\": 3", "\"right\": 7"))
        assertEquals(ImportRejection.Unusable, badChannel.why)
        assertTrue(badChannel.detail!!.contains("edges"))
        // Every button off.
        val noButton = SettingsFile.write(custom.copy(buttons = Buttons(wheelMode = ButtonSetting(on = false))), header)
        assertEquals(ImportRejection.Unusable, rejected(noButton).why)
        // A value of the wrong kind.
        assertEquals(ImportRejection.Unusable, rejected(good.replace("\"dwellMs\": 1000", "\"dwellMs\": \"long\"")).why)
    }
}
