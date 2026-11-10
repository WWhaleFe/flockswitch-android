package io.github.wwhalefe.flockswitch

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Each countdown effect's own colour, size, direction and shrinking. */
class EffectSettingsTest {
    private fun edgesOf(json: String) = Config.fromJson(json).edges

    @Test
    fun olderSettingsGiveEveryEffectTheSharedLook() {
        // Saved by 0.8.0: one look for every effect.
        val e = edgesOf(
            """{"edges": {"animStyle": "Bar", "animColor": "#0A84FF", "animSize": 125, "animDirection": "Both", "animShrink": true}}""",
        )
        assertEquals(EdgeAnimStyle.Bar, e.animStyle)
        for (style in DRAWN_EFFECTS) {
            val l = e.look(style)
            assertEquals("$style", "#0A84FF", l.color)
            assertEquals("$style", 125, l.size)
        }
        assertEquals(EdgeAnimDirection.Both, e.look(EdgeAnimStyle.Ring).direction)
        assertTrue(e.look(EdgeAnimStyle.Semicircle).shrink)
        assertEquals(DRAWN_EFFECTS.map { it.key }.toSet(), e.effects.keys)
        // Only the fields an effect has are written out.
        assertNull(e.effects["bar"]!!.shrink)
        assertNull(e.effects["pulse"]!!.direction)
        assertNull(e.effects["pulse"]!!.shrink)
        // No edges at all: the defaults.
        assertEquals(EffectLook(), edgesOf("""{"enabled": true}""").look(EdgeAnimStyle.Vortex))
    }

    @Test
    fun missingFieldsComeFromTheSharedOnes() {
        val e = edgesOf(
            """{"edges": {"animColor": "#34C759", "animSize": 75, "animDirection": "CounterClockwise",
               "effects": {"ring": {"color": "#FF3B30"}, "bar": {"size": 150}}}}""",
        )
        assertEquals(EffectLook("#FF3B30", 75, EdgeAnimDirection.CounterClockwise, false), e.look(EdgeAnimStyle.Ring))
        assertEquals(EffectLook("#34C759", 150, EdgeAnimDirection.CounterClockwise, false), e.look(EdgeAnimStyle.Bar))
        assertEquals("#34C759", e.look(EdgeAnimStyle.Liquid).color)
    }

    @Test
    fun roundTrip() {
        var e = Edges()
        e = e.withLook(EdgeAnimStyle.Ring, EffectLook("#FF3B30", 150, EdgeAnimDirection.Both, true))
        e = e.withLook(EdgeAnimStyle.Bar, EffectLook("#0A84FF", 75, EdgeAnimDirection.CounterClockwise, false))
        e = e.withLook(EdgeAnimStyle.Vortex, EffectLook("#FFFFFF", 125))
        val c = Config().copy(edges = e)
        assertTrue(c.problems().isEmpty())
        val back = Config.fromJson(c.toJson())
        assertEquals(e, back.edges)
        // As config.json writes it: by lower-case name, enums by their names.
        val ring = Config.json.parseToJsonElement(c.toJson()).jsonObject["edges"]!!.jsonObject["effects"]!!.jsonObject["ring"]!!.jsonObject
        assertEquals("#FF3B30", ring["color"]!!.jsonPrimitive.content)
        assertEquals("150", ring["size"]!!.jsonPrimitive.content)
        assertEquals("Both", ring["direction"]!!.jsonPrimitive.content)
        assertEquals("true", ring["shrink"]!!.jsonPrimitive.content)
        val pulse = Config.json.parseToJsonElement(c.toJson()).jsonObject["edges"]!!.jsonObject["effects"]!!.jsonObject["pulse"]!!.jsonObject
        assertEquals(setOf("color", "size"), pulse.keys)
        // And through a settings file.
        val header = SettingsFile.header("0.9.0", "test", java.time.LocalDateTime.of(2026, 11, 10, 9, 0))
        assertEquals(ImportResult.Ok(c, header), SettingsFile.read(SettingsFile.write(c, header)))
    }

    @Test
    fun effectsAreIndependent() {
        val e = Edges().withStyle(EdgeAnimStyle.Ring)
            .withLook(EdgeAnimStyle.Ring, EffectLook("#FF3B30", 150, EdgeAnimDirection.Both, false))
        // Changing the ring leaves the bar alone.
        assertEquals(EffectLook(), e.look(EdgeAnimStyle.Bar))
        assertEquals(EdgeAnimDirection.Both, e.look(EdgeAnimStyle.Ring).direction)
        // The shared fields mirror the chosen effect, for older versions.
        assertEquals("#FF3B30", e.animColor)
        assertEquals(150, e.animSize)
        assertEquals(EdgeAnimDirection.Both, e.animDirection)
        // Choosing the bar shows its own, and the shared fields follow.
        val bar = e.withStyle(EdgeAnimStyle.Bar)
        assertEquals(EffectLook(), bar.look(EdgeAnimStyle.Bar))
        assertEquals(DEFAULT_EFFECT_COLOR, bar.animColor)
        assertEquals(100, bar.animSize)
        assertEquals(EdgeAnimDirection.Clockwise, bar.animDirection)
        // Back to the ring: still its own.
        assertEquals("#FF3B30", bar.withStyle(EdgeAnimStyle.Ring).look(EdgeAnimStyle.Ring).color)
        // A change to an effect that is not chosen leaves the shared fields alone.
        val other = e.withLook(EdgeAnimStyle.Liquid, EffectLook("#34C759", 75))
        assertEquals("#FF3B30", other.animColor)
        assertEquals("#34C759", other.look(EdgeAnimStyle.Liquid).color)
        // Random and None keep the shared fields as the last effect left them.
        val random = e.withStyle(EdgeAnimStyle.Random)
        assertEquals("#FF3B30", random.animColor)
        assertEquals(e, random.withLook(EdgeAnimStyle.Random, EffectLook("#000000")).copy(animStyle = EdgeAnimStyle.Ring))
    }

    @Test
    fun unknownEntriesAreIgnored() {
        val e = edgesOf(
            """{"edges": {"animColor": "#0A84FF", "right": 2,
               "effects": {"sparkle": {"color": "#FFFFFF"}, "ring": "red", "random": {"color": "#000000"},
                           "bar": {"color": 5, "size": "big", "direction": "Sideways", "shrink": true, "glow": 3}}}}""",
        )
        assertEquals(2, e.right)
        assertEquals(DRAWN_EFFECTS.map { it.key }.toSet(), e.effects.keys)
        assertEquals("#0A84FF", e.look(EdgeAnimStyle.Ring).color)
        // Odd values fall back to the shared fields; an unknown direction to the default.
        val bar = e.look(EdgeAnimStyle.Bar)
        assertEquals("#0A84FF", bar.color)
        assertEquals(100, bar.size)
        assertEquals(EdgeAnimDirection.Clockwise, bar.direction)
        assertNull(e.effects["bar"]!!.shrink)
        // Not an object at all.
        assertEquals(2, edgesOf("""{"edges": {"right": 2, "effects": [1, 2]}}""").right)
    }

    @Test
    fun badEffectValuesAreProblems() {
        val badColor = Edges().copy(effects = mapOf("ring" to EffectEntry(color = "red")))
        assertFalse(Config().copy(edges = badColor).problems().isEmpty())
        val badSize = Edges().copy(effects = mapOf("bar" to EffectEntry(size = 500)))
        assertFalse(Config().copy(edges = badSize).problems().isEmpty())
        assertTrue(Config().problems().isEmpty())
    }

    @Test
    fun aChosenEffectIsDrawnWithItsOwnSettings() {
        val e = Edges().withStyle(EdgeAnimStyle.Semicircle)
            .withLook(EdgeAnimStyle.Semicircle, EffectLook("#FF2D92", 125, EdgeAnimDirection.CounterClockwise, true))
        // The pick is never asked: nothing random about it.
        val c = e.countdown { error("no pick") }!!
        assertEquals(EdgeAnimStyle.Semicircle, c.style)
        assertEquals(EffectLook("#FF2D92", 125, EdgeAnimDirection.CounterClockwise, true), c.look)
        assertNull(e.withStyle(EdgeAnimStyle.None).countdown())
    }

    @Test
    fun randomPicksTheEffectAndItsOptions() {
        val e = Edges()
            .withLook(EdgeAnimStyle.Ring, EffectLook("#FF3B30", 150, EdgeAnimDirection.Clockwise, false))
            .withLook(EdgeAnimStyle.Vortex, EffectLook("#0A84FF", 75))
            .withStyle(EdgeAnimStyle.Random)
        // Picks in order: the effect, then the direction, then shrinking.
        fun with(vararg picks: Int): Countdown {
            val queue = ArrayDeque(picks.toList())
            return e.countdown { n -> queue.removeFirst().also { assertTrue(it < n) } }!!.also { assertTrue(queue.isEmpty()) }
        }
        val ring = with(DRAWN_EFFECTS.indexOf(EdgeAnimStyle.Ring), 2, 1)
        assertEquals(EdgeAnimStyle.Ring, ring.style)
        // Its own colour and size, a random direction and shrinking.
        assertEquals(EffectLook("#FF3B30", 150, EdgeAnimDirection.Both, true), ring.look)
        val bar = with(DRAWN_EFFECTS.indexOf(EdgeAnimStyle.Bar), 1)
        assertEquals(EdgeAnimDirection.CounterClockwise, bar.look.direction)
        // Without options of its own, only the effect is picked.
        val vortex = with(DRAWN_EFFECTS.indexOf(EdgeAnimStyle.Vortex))
        assertEquals(EffectLook("#0A84FF", 75), vortex.look)
        // Over many, every effect, direction and both shrink choices turn up.
        val many = (1..3000).map { e.countdown()!! }
        assertEquals(DRAWN_EFFECTS.toSet(), many.map { it.style }.toSet())
        assertEquals(EdgeAnimDirection.entries.toSet(), many.filter { it.style == EdgeAnimStyle.Bar }.map { it.look.direction }.toSet())
        assertEquals(setOf(true, false), many.filter { it.style == EdgeAnimStyle.Ring }.map { it.look.shrink }.toSet())
        // The saved settings stay as they were.
        assertEquals(EffectLook("#FF3B30", 150, EdgeAnimDirection.Clockwise, false), e.look(EdgeAnimStyle.Ring))
    }

    @Test
    fun theEffectsHaveTheirOptions() {
        assertEquals(listOf("ring", "semicircle", "bar", "pulse", "laser", "vortex", "scan", "liquid"), DRAWN_EFFECTS.map { it.key })
        assertEquals(setOf(EdgeAnimStyle.Ring, EdgeAnimStyle.Semicircle, EdgeAnimStyle.Bar), DRAWN_EFFECTS.filter { it.hasDirection }.toSet())
        assertEquals(setOf(EdgeAnimStyle.Ring, EdgeAnimStyle.Semicircle), DRAWN_EFFECTS.filter { it.canShrink }.toSet())
    }
}
