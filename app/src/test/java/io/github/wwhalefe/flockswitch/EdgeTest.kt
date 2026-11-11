package io.github.wwhalefe.flockswitch

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EdgeTest {
    @Test
    fun pulsesQuickenAndSharpenTowardsTheSwitch() {
        assertEquals(EdgePulse(180, 4), risingPulse(0.0))
        assertEquals(EdgePulse(180, 4), risingPulse(0.39))
        assertEquals(EdgePulse(110, 3), risingPulse(0.40))
        assertEquals(EdgePulse(110, 3), risingPulse(0.74))
        assertEquals(EdgePulse(60, 2), risingPulse(0.75))
        assertEquals(EdgePulse(60, 2), risingPulse(1.2))
    }

    @Test
    fun evenPulsesWithTheOptionOff() {
        val even = Haptics(progressive = false, edgeWaveform = 9)
        assertEquals(EdgePulse(150, 9), edgePulse(even, 0.0))
        assertEquals(EdgePulse(150, 9), edgePulse(even, 0.9))
        assertEquals(risingPulse(0.9), edgePulse(Haptics(), 0.9))
    }

    @Test
    fun aFinalPulseOnlyWhenTheEngineWillNotPlayOne() {
        assertFalse(needsFinalPulse(Haptics()))
        assertTrue(needsFinalPulse(Haptics(onSwitch = false)))
        assertFalse(needsFinalPulse(Haptics(onSwitch = false, progressive = false)))
        assertFalse(needsFinalPulse(Haptics(onSwitch = false, onEdge = false)))
    }

    @Test
    fun controlIsReadFromThePointerEvent() {
        assertTrue(controlDown(KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON))
        assertFalse(controlDown(0))
        assertFalse(controlDown(KeyEvent.META_SHIFT_ON))
    }

    @Test
    fun everyEffectIsInTheTable() {
        for (style in EdgeAnimStyle.entries) {
            if (style == EdgeAnimStyle.None || style == EdgeAnimStyle.Random) assertNull(edgeRenderers[style]) else assertTrue("$style", style in edgeRenderers)
        }
        for (style in LocateStyle.entries) {
            if (style == LocateStyle.Random) assertNull(locateRenderers[style]) else assertTrue("$style", style in locateRenderers)
        }
    }

    @Test
    fun randomOnlyEverPicksARealEffect() {
        val random = Edges(animStyle = EdgeAnimStyle.Random)
        val edge = (1..500).map { random.countdown()!!.style }.toSet()
        assertFalse(EdgeAnimStyle.None in edge || EdgeAnimStyle.Random in edge)
        assertEquals("every real effect comes up", EdgeAnimStyle.entries.size - 2, edge.size)
        val locate = (1..500).map { LocateStyle.Random.resolved() }.toSet()
        assertFalse(LocateStyle.Random in locate)
        assertEquals(LocateStyle.entries.size - 1, locate.size)
        // Anything else stays what it is.
        assertNull(Edges(animStyle = EdgeAnimStyle.None).countdown())
        assertEquals(EdgeAnimStyle.Bar, Edges(animStyle = EdgeAnimStyle.Bar).countdown()!!.style)
        assertEquals(LocateStyle.Radar, LocateStyle.Radar.resolved())
        // Random has no direction or shrink of its own, so those controls hide.
        assertFalse(EdgeAnimStyle.Random.hasDirection || EdgeAnimStyle.Random.canShrink)
    }

    @Test
    fun theKeyboardStateIsPassedOnWhenItChanges() {
        val state = PointerState()
        val seen = mutableListOf<Boolean>()
        state.onControl = { seen += it }
        assertNull(state.ctrl)
        state.setControl(false)
        state.setControl(true)
        state.setControl(true)
        state.setControl(null)
        assertEquals(listOf(false, true), seen)
        assertNull(state.ctrl)
    }
}
