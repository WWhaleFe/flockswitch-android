package io.github.wwhalefe.flockswitch

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothRetriesTest {
    private var now = 0L
    private val retries = BluetoothRetries { now }
    private val mouse = "ble:AA:BB:CC:DD:EE:FF"

    @Test
    fun aDeviceThatNeverFailedIsTried() = assertFalse(retries.leftOut(mouse, connected = false))

    @Test
    fun anAwayDeviceWaitsUntilAndroidReportsItConnected() {
        retries.failed(mouse)
        now += 3_600_000
        assertTrue(retries.leftOut(mouse, connected = false))
        retries.linkUp(mouse)
        assertFalse(retries.leftOut(mouse, connected = false))
    }

    @Test
    fun aConnectedDeviceThatFailedIsTriedAgainLater() {
        // The mouse came back: Android reports the link, the first try still fails.
        retries.linkUp(mouse)
        retries.failed(mouse)
        assertTrue(retries.leftOut(mouse, connected = false))
        now += 5_000
        assertFalse(retries.leftOut(mouse, connected = false))
    }

    @Test
    fun aDeviceListedAsConnectedIsTriedAgainWithoutALinkReport() {
        // Linked before the app started, so Android sent no report.
        retries.failed(mouse)
        now += 5_000
        assertFalse(retries.leftOut(mouse, connected = true))
    }

    @Test
    fun thePauseGrowsWithEveryFailureUpToAMinute() {
        retries.linkUp(mouse)
        for (pause in listOf(5_000L, 10_000L, 20_000L, 40_000L, 60_000L, 60_000L)) {
            retries.failed(mouse)
            now += pause - 1
            assertTrue(retries.leftOut(mouse, connected = true))
            now += 1
            assertFalse(retries.leftOut(mouse, connected = true))
        }
    }

    @Test
    fun openingStartsThePauseOver() {
        retries.linkUp(mouse)
        repeat(4) { retries.failed(mouse) }
        retries.opened(mouse)
        assertFalse(retries.leftOut(mouse, connected = true))
        retries.failed(mouse)
        now += 5_000
        assertFalse(retries.leftOut(mouse, connected = true))
    }

    @Test
    fun aDeviceThatLeftIsNoLongerRetriedOnATimer() {
        retries.linkUp(mouse)
        retries.failed(mouse)
        retries.linkDown(mouse)
        now += 60_000
        assertTrue(retries.leftOut(mouse, connected = false))
    }
}
