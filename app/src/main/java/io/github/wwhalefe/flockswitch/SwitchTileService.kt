package io.github.wwhalefe.flockswitch

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick settings tile: switches like a press of the wheel mode-shift button would, so
 *  the group can be sent on without the mouse (Android lets no app take keyboard
 *  shortcuts for the whole system). */
class SwitchTileService : TileService() {
    /** Where a tap sends everything from this device's channel, for the label (as if
     *  the mouse had three channels); null when it cannot. */
    private fun target(): Int? {
        val s = shared.status.value
        val c = shared.config.value
        if (!c.enabled || s.trigger == null) return null
        val here = s.channel ?: return null
        return c.button(BUTTON_WHEEL_MODE).channelAfter(Gesture.Press, here - 1, 3)
    }

    override fun onStartListening() {
        val tile = qsTile ?: return
        val target = target()
        tile.state = if (target != null) Tile.STATE_INACTIVE else Tile.STATE_UNAVAILABLE
        tile.label = if (target != null) getString(R.string.go_to_channel, target) else getString(R.string.app_name)
        if (android.os.Build.VERSION.SDK_INT >= 29) tile.subtitle = shared.status.value.trigger
        tile.updateTile()
    }

    override fun onClick() {
        if (target() == null) return
        // The engine works out the channel itself, from those the mouse really has.
        shared.requestPress()
    }
}
