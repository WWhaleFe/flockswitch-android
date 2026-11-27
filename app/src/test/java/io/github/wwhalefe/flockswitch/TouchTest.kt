package io.github.wwhalefe.flockswitch

import io.github.wwhalefe.flockswitch.net.*
import io.github.wwhalefe.flockswitch.net.TouchCommand.*
import org.junit.Assert.*
import org.junit.Test

class TouchTest {
    private var settings = TouchSettings()
    private val planner = TouchPlanner(TouchTuning(slopPx = 20f, holdMs = 120, doubleTapMs = 300, longPressMs = 700)) { settings }

    private fun at(x: Float, y: Float, now: Long = 0) = planner.move(x, y, now)

    @Test fun aClickIsOneTapAtTheSpotWhereTheButtonWentDown() {
        at(100f, 200f)
        assertEquals(emptyList<TouchCommand>(), planner.leftDown(10))
        // The mouse trembles a little while the button is down: the tap does not follow.
        assertEquals(emptyList<TouchCommand>(), at(108f, 195f, 30))
        assertEquals(listOf<TouchCommand>(Tap(100f, 200f)), planner.leftUp(60))
        assertFalse(planner.pressed)
    }

    @Test fun aQuickClickNeverPutsTheFingerDown() {
        at(5f, 5f)
        planner.leftDown(0)
        assertEquals(120L, planner.wakeAt)
        // The tick before the hold time does nothing, and the release cancels the wait.
        assertEquals(emptyList<TouchCommand>(), planner.tick(100))
        planner.leftUp(110)
        assertNull(planner.wakeAt)
        assertEquals(emptyList<TouchCommand>(), planner.tick(130))
    }

    @Test fun holdingWithoutMovingIsALongPress() {
        at(50f, 60f)
        planner.leftDown(0)
        assertEquals(listOf<TouchCommand>(Press(50f, 60f)), planner.tick(120))
        assertTrue(planner.pressed)
        // Small movement while held stays put.
        assertEquals(emptyList<TouchCommand>(), at(55f, 62f, 500))
        assertEquals(listOf<TouchCommand>(Release(50f, 60f)), planner.leftUp(900))
    }

    @Test fun pressAndMoveIsOneContinuousDrag() {
        at(100f, 100f)
        planner.leftDown(0)
        assertEquals(emptyList<TouchCommand>(), at(110f, 100f, 20))
        assertEquals(listOf<TouchCommand>(Press(100f, 100f), Drag(150f, 100f)), at(150f, 100f, 40))
        assertEquals(listOf<TouchCommand>(Drag(200f, 120f)), at(200f, 120f, 60))
        // Back near the start it is still the same drag.
        assertEquals(listOf<TouchCommand>(Drag(101f, 100f)), at(101f, 100f, 80))
        assertEquals(listOf<TouchCommand>(Release(101f, 100f)), planner.leftUp(100))
    }

    @Test fun movingAfterAHoldStartsTheDragFromTheHeldSpot() {
        at(10f, 10f)
        planner.leftDown(0)
        planner.tick(130)
        assertEquals(listOf<TouchCommand>(Drag(80f, 10f)), at(80f, 10f, 200))
        assertEquals(listOf<TouchCommand>(Release(80f, 10f)), planner.leftUp(300))
    }

    @Test fun doubleClickIsTwoTapsOnTheSameSpot() {
        at(100f, 100f)
        planner.leftDown(0)
        assertEquals(listOf<TouchCommand>(Tap(100f, 100f, false)), planner.leftUp(50))
        at(104f, 103f, 120)
        planner.leftDown(130)
        assertEquals(listOf<TouchCommand>(Tap(100f, 100f, true)), planner.leftUp(180))
    }

    @Test fun clicksFurtherApartInTimeOrSpaceAreSeparate() {
        at(100f, 100f)
        planner.leftDown(0); planner.leftUp(50)
        planner.leftDown(500)
        assertEquals(listOf<TouchCommand>(Tap(100f, 100f, false)), planner.leftUp(540))
        at(400f, 400f, 600)
        planner.leftDown(610)
        assertEquals(listOf<TouchCommand>(Tap(400f, 400f, false)), planner.leftUp(650))
    }

    @Test fun rightButtonIsBackOrALongPress() {
        at(30f, 40f)
        assertEquals(listOf<TouchCommand>(Global(GlobalKey.Back)), planner.rightDown())
        settings = TouchSettings(rightLongPress = true)
        assertEquals(listOf<TouchCommand>(LongPress(30f, 40f)), planner.rightDown())
        assertEquals(listOf<TouchCommand>(Global(GlobalKey.Back)), planner.back())
    }

    @Test fun middleButtonIsHomeOrNothing() {
        assertEquals(listOf<TouchCommand>(Global(GlobalKey.Home)), planner.middleDown())
        settings = TouchSettings(middleHome = false)
        assertEquals(emptyList<TouchCommand>(), planner.middleDown())
        assertEquals(listOf<TouchCommand>(Global(GlobalKey.Recents)), planner.forward())
    }

    @Test fun theWheelScrollsAtThePointerButNotWhileDragging() {
        at(70f, 80f)
        assertEquals(listOf<TouchCommand>(Scroll(70f, 80f, 0.0, 2.0)), planner.scroll(0.0, 2.0))
        assertEquals(emptyList<TouchCommand>(), planner.scroll(0.0, 0.0))
        planner.leftDown(0)
        assertEquals(emptyList<TouchCommand>(), planner.scroll(0.0, 1.0))
    }

    @Test fun aCancelledPressLetsTheNextClickStartClean() {
        at(1f, 1f)
        planner.leftDown(0); planner.tick(130)
        planner.cancelled()
        assertFalse(planner.pressed)
        // The release that follows the cancelled press is ignored, not a stray tap.
        assertEquals(emptyList<TouchCommand>(), planner.leftUp(200))
        planner.leftDown(300)
        assertEquals(listOf<TouchCommand>(Tap(1f, 1f, false)), planner.leftUp(340))
    }

    @Test fun resetLiftsAFingerThatIsDown() {
        at(10f, 10f)
        planner.leftDown(0)
        at(100f, 10f, 20)
        assertEquals(listOf<TouchCommand>(Release(100f, 10f)), planner.reset())
        assertEquals(emptyList<TouchCommand>(), planner.reset())
        assertEquals(emptyList<TouchCommand>(), planner.leftUp(50))
    }

    @Test fun queueKeepsOnlyTheLatestDragAndAddsUpWheelTurns() {
        val q = GestureQueue()
        q.push(Press(0f, 0f))
        q.push(Drag(1f, 1f)); q.push(Drag(2f, 2f)); q.push(Drag(3f, 3f))
        q.push(Release(3f, 3f))
        q.push(Scroll(5f, 5f, 0.0, 1.0)); q.push(Scroll(5f, 5f, 0.5, 1.5))
        assertEquals(Press(0f, 0f), q.poll())
        assertEquals(Drag(3f, 3f), q.poll())
        assertEquals(Release(3f, 3f), q.poll())
        assertEquals(Scroll(5f, 5f, 0.5, 2.5), q.poll())
        assertNull(q.poll())
    }

    @Test fun staleDragsAndReleasesAreDroppedAfterACancel() {
        val q = GestureQueue()
        q.push(Drag(1f, 1f)); q.push(Release(1f, 1f)); q.push(Tap(9f, 9f))
        q.dropStale()
        assertEquals(Tap(9f, 9f), q.poll())
    }

    @Test fun scrollSwipeMovesTheOtherWayAndStaysProportional() {
        val p = scrollPath(500f, 1000f, 0.0, 2.0, 1000, 2000, 100f)
        assertArrayEquals(floatArrayOf(500f, 1000f, 500f, 800f), p, 0.001f)
        val up = scrollPath(500f, 1000f, 0.0, -1.0, 1000, 2000, 100f)
        assertArrayEquals(floatArrayOf(500f, 1000f, 500f, 1100f), up, 0.001f)
        val sideways = scrollPath(500f, 1000f, 1.0, 0.0, 1000, 2000, 100f)
        assertArrayEquals(floatArrayOf(500f, 1000f, 400f, 1000f), sideways, 0.001f)
    }

    @Test fun scrollNearAnEdgeSlidesTheSwipeInsteadOfCuttingIt() {
        // 2 units down at 100 px = 200 px, but only 12 px are left above the pointer.
        val p = scrollPath(500f, 20f, 0.0, 2.0, 1000, 2000, 100f)
        assertEquals(200f, p[1] - p[3], 0.001f)
        assertTrue(p[3] >= 0f && p[1] <= 2000f)
        val bottom = scrollPath(500f, 1990f, 0.0, -2.0, 1000, 2000, 100f)
        assertEquals(200f, bottom[3] - bottom[1], 0.001f)
        assertTrue(bottom[3] <= 1999f)
    }

    @Test fun oneGestureNeverCrossesMoreThanTheSetShareOfTheScreen() {
        val p = scrollPath(500f, 1000f, 0.0, 100.0, 1000, 2000, 100f)
        assertEquals(0.6f * (2000 - 16), p[1] - p[3], 1f)
    }

    @Test fun swipeAndDragSpeedsStayInRange() {
        assertEquals(60L, scrollDurationMs(1f))
        assertEquals(450L, scrollDurationMs(100000f))
        assertTrue(scrollDurationMs(300f) in 200L..300L)
        assertEquals(16L, dragDurationMs(1f))
        assertEquals(100L, dragDurationMs(5000f))
    }

    @Test fun touchOptionsComeFromTheNetworkConfig() {
        assertEquals(TouchSettings(false, true), TouchSettings.of(NetworkConfig()))
        assertEquals(TouchSettings(true, false), TouchSettings.of(NetworkConfig(right_button = "long_press", middle_button = "none")))
        assertTrue(NetworkConfig(right_button = "x").problems().isNotEmpty())
        assertTrue(NetworkConfig(middle_button = "x").problems().isNotEmpty())
        assertTrue(NetworkConfig().problems().isEmpty())
    }

    @Test fun pausedPeersAndMonitorTilesCameInTheStatus() {
        val status = NetStatus.parse("""{"running":true,"me":{"id":"me","displays":[{"x":0,"y":0,"width":1080,"height":2400}]},
            "peers":[{"id":"a","connected":false,"paused":true,"displays":[{"x":0,"y":0,"width":1920,"height":1080},{"x":1920,"y":0,"width":1280,"height":720}]}],
            "layout":[{"id":"me","col":2,"row":1},{"id":"a","col":3,"row":1,"display":0},{"id":"a","col":4,"row":1,"display":1}]}""")
        assertTrue(status.peer("a")!!.paused)
        assertEquals(2, status.peer("a")!!.displays.size)
        assertEquals(1080.0, status.me.displays.single().width, 0.0)
        assertEquals("right", status.masterSide("a"))
        // A master's second monitor beside ours counts too.
        val second = NetStatus(me = NetMe(id = "me"), layout = listOf(NetCell("me", 2, 1), NetCell("a", 0, 0), NetCell("a", 1, 1, 1)))
        assertEquals("left", second.masterSide("a"))
        // An old status has none of it.
        assertFalse(NetStatus.parse("""{"peers":[{"id":"a"}]}""").peer("a")!!.paused)
    }

    @Test fun pausedIsSavedWithThePeerAndSentToTheCore() {
        val peer = NetworkPeer(id = "m", fingerprint = "f", paused = true)
        val config = NetworkConfig(peers = listOf(peer))
        assertTrue(Config.fromJson(Config(network = config).toJson()).network.peers.single().paused)
        assertTrue(config.toCoreJson().replace(" ", "").contains("\"paused\":true"))
        assertFalse(Config.fromJson("""{"network":{"peers":[{"id":"m"}]}}""").network.peers.single().paused)
        assertEquals(0, NetworkConfig().hangul_defaults)
    }

    @Test fun windowsAndLinuxDefaultsListShiftSpaceToo() {
        for (src in listOf("windows", "linux")) {
            val r = KeyRules.defaultFor(src)
            assertEquals(listOf("lang1", "shift_space"), r.hangul_src)
            assertEquals("shift_space", r.hangul_dst)
        }
        assertEquals(listOf("caps_lock", "shift_space"), KeyRules.defaultFor("mac").hangul_src)
    }
}
