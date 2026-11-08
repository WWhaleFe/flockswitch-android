// The background loop: finds devices, diverts the trigger buttons, and moves every
// following device to the next channel when one of them is pressed.
//
// Devices are remembered once seen. A receiver tells us when a device links or
// unlinks, and only that slot is re-read; the handles are re-opened only when the set
// of receivers / Bluetooth devices changes. Rebuilding everything on every link change
// used to drop a slow-waking keyboard from the list, so it stayed behind.

package io.github.wwhalefe.flockswitch

import io.github.wwhalefe.flockswitch.hidpp.Device
import io.github.wwhalefe.flockswitch.hidpp.DeviceKind
import io.github.wwhalefe.flockswitch.hidpp.Endpoint
import io.github.wwhalefe.flockswitch.hidpp.FEATURE_CHANGE_HOST
import io.github.wwhalefe.flockswitch.hidpp.FEATURE_REPROG_V4
import io.github.wwhalefe.flockswitch.hidpp.Pairing
import io.github.wwhalefe.flockswitch.hidpp.REPORT_SHORT
import io.github.wwhalefe.flockswitch.hidpp.Reporting
import io.github.wwhalefe.flockswitch.hidpp.Transport
import io.github.wwhalefe.flockswitch.hidpp.candidateSlots
import io.github.wwhalefe.flockswitch.hidpp.enableLinkNotifications
import io.github.wwhalefe.flockswitch.hidpp.probeSlot
import io.github.wwhalefe.flockswitch.hidpp.receiverPairings
import io.github.wwhalefe.flockswitch.hidpp.u
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/** Where the engine finds receivers and Bluetooth devices. */
interface EndpointSource {
    /** Keys of the endpoints present right now (opened or not). Cheap; opens nothing.
     *  Used to notice a receiver being plugged in or a Bluetooth device (dis)connecting. */
    fun keys(): List<String>

    /** Opens every endpoint present that flockswitch may use. */
    fun open(): List<Transport>
}

/** Receiver notification sent when a paired device links or unlinks:
 *  [10, slot, 41, protocol, device_info, wpid_lo, wpid_hi]. Bit 6 of device_info is set
 *  while the link is down (seen on Bolt: 0x02 = mouse up, 0x42 = mouse down). */
private const val NOTIF_DEVICE_CONNECTION = 0x41
private const val LINK_DOWN = 0x40

/** Give the device time to settle after it links. */
private const val SLOT_SETTLE = 1500L
/** A haptic pulse takes a moment to play; Change Host right after would cut it short. */
private const val HAPTIC_SETTLE = 150L
private const val SLOT_RETRIES = 3
private const val HEALTH_CHECK = 30_000L
private const val RESCAN_INTERVAL = 5_000L

/** How often to look for receivers being plugged in and Bluetooth devices (dis)connecting. */
private const val ENUMERATE_INTERVAL = 3_000L

/** Rescans caused by an endpoint that keeps failing back off up to this long. */
private const val MAX_BACKOFF = 60_000L

private fun now() = System.nanoTime() / 1_000_000

class Engine(
    private val shared: Shared,
    private val source: EndpointSource,
    /** This run's stop flag, kept even if a newer run starts meanwhile. */
    private val stop: AtomicBoolean = shared.stopFlag(),
) {
    private var cfg = shared.config.value

    private fun stopped() = stop.get()
    private var endpoints = mutableListOf<Endpoint>()

    /** Every device seen on the current endpoints, online or not right now. */
    val devices = mutableListOf<Device>()

    /** Devices learned earlier, by key, so a paired-but-offline slot can still be listed. */
    private val known = mutableMapOf<String, Device>()

    /** The device whose button we use. Kept by value (endpoint, slot, features), never as
     *  a position in `devices`, so a rescan can't leave it pointing at the wrong entry. */
    var trigger: Device? = null
        private set

    /** Divert state of each button we diverted on the trigger, as found before we
     *  touched it; restored on exit or when the button is switched off. */
    private var originalDivert = mutableMapOf<Int, Reporting>()

    /** Which of the trigger buttons the master mouse has. */
    private var triggerButtons = listOf<Int>()
    /** Haptic waveforms the master mouse offers (bit N = waveform N); null without a motor. */
    private var triggerHaptics: Int? = null
    /** The master mouse was switched away from here; a pulse greets it when it is back. */
    private var awayBySwitch = false

    /** Trigger buttons held down right now (a press counts once until released). */
    private var held = setOf<Int>()

    /** A button with gestures is held: (button, total dx, total dy) of the mouse since. */
    private var gesture: Triple<Int, Int, Int>? = null

    /** Slots to re-read: (endpoint, slot) -> (when, attempts left). */
    private val pending = mutableMapOf<Pair<Int, Int>, Pair<Long, Int>>()
    private var lastCheck = now()

    /** Slots that answered last time we asked: (endpoint, slot). */
    private val online = mutableSetOf<Pair<Int, Int>>()

    /** Every device ever seen, remembered on disk for the settings. */
    private val catalog = shared.loadCatalog().toMutableList()

    /** Keys of devices that carry the trigger button (can be the master mouse). */
    private val masters = mutableSetOf<String>()
    private var lastEnumerate = now()

    /** Endpoints the source listed at the last scan (opened or not), to spot changes. */
    private var seenEndpoints = listOf<String>()
    private var backoff = 1_000L

    fun endpoint(i: Int) = endpoints[i]

    private fun isTrigger(endpoint: Int, slot: Int) = trigger?.let { it.endpoint == endpoint && it.index == slot } == true

    /** Re-opens all endpoints and reads every device. Used at start-up and when the set
     *  of receivers / Bluetooth devices changes. One failing endpoint is marked dead and
     *  skipped instead of aborting the whole scan. */
    fun scan() {
        // The device keeps its divert while we re-open handles, so the button keeps
        // working through a rescan. Remember who had it to compare afterwards.
        val previous = trigger?.key()
        trigger = null
        val previousOriginal = originalDivert
        originalDivert = mutableMapOf()
        held = emptySet()
        gesture = null

        // Close the old handles before opening new ones.
        endpoints.forEach { runCatching { it.close() } }
        endpoints = source.open().map { Endpoint(it) }.toMutableList()
        seenEndpoints = source.keys().sorted()
        devices.clear()
        pending.clear()
        online.clear()
        masters.clear()
        lastEnumerate = now()
        for (i in endpoints.indices) {
            if (stopped()) return
            try {
                scanEndpoint(i)
            } catch (e: IOException) {
                Log.w("${endpoints[i].product}: ${e.message}")
            }
        }
        trigger = pickTrigger()
        readChannel()
        if (previous != null && previousOriginal.isNotEmpty()) {
            if (trigger?.key() == previous) {
                // Same master: still diverted; keep what we knew about the originals.
                originalDivert = previousOriginal
            } else {
                // The master changed: give the old one its buttons back if it is here.
                devices.firstOrNull { it.key() == previous }?.let { old ->
                    previousOriginal.forEach { (cid, orig) -> restoreButton(old, cid, orig) }
                }
            }
        }
        publish()
    }

    private fun scanEndpoint(i: Int) {
        try {
            enableLinkNotifications(endpoints[i])
        } catch (e: IOException) {
            Log.d("link notifications: ${e.message}")
        }
        val pairings = receiverPairings(endpoints[i])
        for (p in pairings) Log.d("receiver slot ${p.slot}: ${p.kind} %04X".format(p.wpid))
        for (slot in candidateSlots(endpoints[i], pairings)) {
            if (stopped()) break
            loadSlot(i, slot, pairings.firstOrNull { it.slot == slot })
        }
    }

    /** Updates the remembered device list and copies what the screen shows into the
     *  shared status. The list is saved when a device appears or changes, not on every
     *  link flip. */
    private fun publish() {
        var changed = false
        for (i in catalog.indices) catalog[i] = catalog[i].copy(online = false)
        for (d in devices) {
            if ((d.endpoint to d.index) !in online || !d.has(FEATURE_CHANGE_HOST)) continue
            val info = DeviceInfo(
                key = d.key(),
                name = d.name,
                kind = when (d.kind) {
                    DeviceKind.Mouse -> "mouse"
                    DeviceKind.Keyboard -> "keyboard"
                    else -> "other"
                },
                master = d.key() in masters,
                online = true,
            )
            val i = catalog.indexOfFirst { it.key == info.key }
            if (i >= 0) {
                val c = catalog[i]
                changed = changed || c.name != info.name || c.kind != info.kind || c.master != info.master
                catalog[i] = info
            } else {
                changed = true
                catalog += info
            }
        }
        if (changed) shared.saveCatalog(catalog)
        val t = trigger
        val triggerOnline = t != null && (t.endpoint to t.index) in online
        val devices = catalog.toList()
        val buttons = triggerButtons
        val receivers = endpoints.count { !it.dead }
        shared.setStatus {
            it.copy(
                buttons = buttons,
                trigger = t?.name?.takeIf { triggerOnline },
                triggerKey = t?.key(),
                devices = devices,
                receivers = receivers,
            )
        }
    }

    /** Picks up config changes made on the screen. */
    private fun reloadConfig() {
        val next = shared.config.value
        if (next == cfg) return
        val triggerChanged = next.triggers() != cfg.triggers() || next.triggerDevice != cfg.triggerDevice
        if (triggerChanged || (cfg.enabled && !next.enabled)) {
            // Hand every button back, then divert the ones still wanted below.
            release()
            trigger = null
            held = emptySet()
            gesture = null
        }
        val sourceChanged = next.bluetooth != cfg.bluetooth
        cfg = next
        Log.i("settings changed (${modeText()})")
        if (sourceChanged) {
            scan()
        }
        if (cfg.enabled) ensureDiverted() else Log.i("paused: the button is back to normal")
        publish()
    }

    /** Reads one slot and records the device. A device that is offline right now but
     *  was seen before is still listed, so it can be moved as soon as it wakes. */
    private fun loadSlot(endpoint: Int, slot: Int, pairing: Pairing?): Boolean {
        val probed = probeSlot(endpoints[endpoint], endpoint, slot, pairing)
        val isOnline = probed != null
        if (isOnline) online += endpoint to slot else online -= endpoint to slot
        val dev = if (probed != null) {
            // A mouse with any of the trigger buttons can be chosen as the master.
            val canBeMaster = (probed.kind == null || probed.kind == DeviceKind.Mouse) &&
                probed.has(FEATURE_REPROG_V4) &&
                probed.has(FEATURE_CHANGE_HOST) &&
                buttonsOn(probed, endpoints[endpoint], BUTTONS).isNotEmpty()
            if (canBeMaster) masters += probed.key()
            known[probed.key()] = probed
            probed
        } else {
            pairing?.let { known["%04X".format(it.wpid)] }?.relocated(endpoint, slot)
        }
        if (dev != null) {
            // The slot was re-paired to another device: the old trigger is gone.
            if (isTrigger(endpoint, slot) && trigger?.key() != dev.key()) {
                trigger = null
                originalDivert.clear()
                held = emptySet()
                gesture = null
            }
            val i = devices.indexOfFirst { it.endpoint == endpoint && it.index == slot }
            if (i >= 0) devices[i] = dev else devices += dev
        }
        return isOnline
    }

    /** Chooses the master mouse: the configured one (or, with "auto", the first mouse)
     *  that is here and has at least one of the buttons switched on. */
    private fun pickTrigger(): Device? {
        val want = cfg.triggerDevice.trim()
        for (d in devices.toList()) {
            if (!d.has(FEATURE_REPROG_V4) || !d.has(FEATURE_CHANGE_HOST) ||
                (d.endpoint to d.index) !in online || endpoints[d.endpoint].dead
            ) continue
            val matches = if (want.equals("auto", ignoreCase = true)) {
                d.kind == null || d.kind == DeviceKind.Mouse
            } else {
                d.key().equals(want, ignoreCase = true) || d.name.lowercase().contains(want.lowercase())
            }
            if (!matches) continue
            val has = buttonsOn(d, endpoints[d.endpoint], BUTTONS)
            if (has.any { cfg.button(it).on }) {
                triggerButtons = has
                triggerHaptics = readHaptics(d)
                return d
            }
        }
        triggerButtons = emptyList()
        triggerHaptics = null
        return null
    }

    /** The haptic waveforms the mouse offers, logged once; a mouse without a motor (or
     *  away right now) gives null, which is simply "no vibration". */
    private fun readHaptics(d: Device): Int? {
        val ep = endpoints.getOrNull(d.endpoint) ?: return null
        val mask = try { d.hapticWaveforms(ep) } catch (e: IOException) { null } ?: return null
        if (triggerHaptics != mask) {
            Log.i("haptics: ${d.label()} offers waveforms 0x%08x".format(mask))
            if (runCatching { d.hapticsEnabled(ep) }.getOrNull() == false) {
                Log.w("haptics are turned off on ${d.label()} (a Logi Options+ setting), so it will not vibrate")
            }
        }
        return mask
    }

    /** Plays a haptic waveform on the master mouse, if it is here and has that waveform.
     *  Quiet on a mouse without a motor. */
    private fun buzz(waveform: Int): Boolean {
        val d = trigger ?: return false
        val mask = triggerHaptics ?: return false
        if (waveform !in 0..31 || (mask and (1 shl waveform)) == 0) {
            Log.d("haptics: ${d.label()} has no waveform $waveform")
            return false
        }
        if ((d.endpoint to d.index) !in online) return false
        val ep = endpoints.getOrNull(d.endpoint) ?: return false
        return try {
            d.playHaptic(ep, waveform)
        } catch (e: IOException) {
            Log.d("haptics: ${e.message}")
            false
        }
    }

    /** The master mouse is back on this device after being switched away from here: a
     *  pulse says so. */
    private fun greetTrigger() {
        val t = trigger ?: return
        if (!awayBySwitch || (t.endpoint to t.index) !in online) return
        awayBySwitch = false
        if (cfg.haptics.onSwitch) buzz(cfg.haptics.arriveWaveform)
    }

    /** Makes sure the buttons switched on are diverted to us, and that a button switched
     *  off since an earlier run is back to normal. The state found before the first
     *  divert is written to disk, so even a killed run is undone correctly later. */
    fun ensureDiverted() {
        if (!cfg.enabled) return
        if (trigger == null) {
            trigger = pickTrigger()
            trigger?.let {
                Log.i("trigger: ${it.label()}")
                readChannel()
            }
            greetTrigger()
            publish()
        }
        val d = trigger ?: return
        if (d.endpoint >= endpoints.size) {
            trigger = null
            return
        }
        val ep = endpoints[d.endpoint]
        for (cid in BUTTONS) {
            val wanted = cfg.button(cid).on
            if (!wanted && originalDivert.containsKey(cid)) continue // release() handles buttons we diverted ourselves
            // null: the mouse lacks this button, or is not on this device right now.
            val now = d.controlReporting(ep, cid) ?: continue
            if (!wanted) {
                // Left diverted by an earlier run that used this button and was killed.
                shared.savedOriginalDivert(d.key(), cid)?.let { restoreButton(d, cid, it) }
                continue
            }
            if (cid !in originalDivert) {
                // A record is written before every divert, so without one the current
                // state is not ours (another app may divert the button for itself) and
                // is what the button must get back.
                val original = shared.savedOriginalDivert(d.key(), cid) ?: now
                shared.rememberOriginalDivert(d.key(), cid, original)
                originalDivert[cid] = original
            }
            // Gestures need the movement during a hold; otherwise leave the cursor alone.
            val want = Reporting(diverted = true, rawXy = cfg.button(cid).usesMoves())
            if (now != want) {
                held = held - cid
                val raw = if (now.rawXy != want.rawXy) want.rawXy else null
                if (d.setReporting(ep, cid, true, raw)) {
                    val how = if (want.rawXy) " with gestures" else ""
                    Log.i("${buttonName(cid)} button diverted$how on ${d.label()}")
                } else {
                    Log.w("${d.label()} did not confirm the button setting; retrying later")
                }
            }
        }
    }

    /** Puts every diverted button back the way we found it. Safe to call at any time. */
    fun release() {
        val originals = originalDivert
        originalDivert = mutableMapOf()
        val d = trigger ?: return
        originals.forEach { (cid, orig) -> restoreButton(d, cid, orig) }
    }

    private fun restoreButton(d: Device, cid: Int, orig: Reporting) {
        val ep = endpoints.getOrNull(d.endpoint) ?: return
        if (ep.dead) return
        try {
            // Touch the raw XY flag only if it differs (not every button supports it).
            val raw = d.controlReporting(ep, cid)?.takeIf { it.rawXy != orig.rawXy }?.let { orig.rawXy }
            d.setReporting(ep, cid, orig.diverted, raw)
            // Forget the record only once the device confirms its state. If the mouse is
            // on another computer the record stays, and the next run restores it.
            if (d.controlReporting(ep, cid) == orig) {
                shared.forgetOriginalDivert(d.key(), cid)
            } else {
                Log.i("${d.label()} not reachable; its button will be restored next time")
            }
        } catch (e: IOException) {
            Log.w("could not restore the ${buttonName(cid)} button: ${e.message}")
        }
    }

    /** Moves the followers, then the trigger device, to [channel] (1-based).
     *  Followers go first so the mouse — the thing the user just pressed — leaves last.
     *  Stale pairing slots answer with an error and are skipped on their own. */
    fun switchTo(channel: Int) {
        // A pulse on the mouse as it goes, felt before Change Host takes it away.
        if (cfg.haptics.onSwitch && buzz(cfg.haptics.switchWaveform)) Thread.sleep(HAPTIC_SETTLE)
        val t = trigger
        fun isTrigger(d: Device) = t != null && t.endpoint == d.endpoint && t.index == d.index
        val order = devices.filter { !isTrigger(it) } + listOfNotNull(t)
        for (d in order) {
            if (stopped()) break
            if (!d.has(FEATURE_CHANGE_HOST) || (!isTrigger(d) && !cfg.follows(d.wpid, d.name))) continue
            val ep = endpoints.getOrNull(d.endpoint) ?: continue
            if (ep.dead) continue
            // One device failing must not keep the others (or the mouse) from moving.
            // The mouse was just pressed, so it is here: if it does not answer (a
            // Unifying mouse was seen to drop its link for a moment), ask again rather
            // than leave it behind while the group has gone.
            val tries = if (isTrigger(d)) 3 else 1
            try {
                var state = d.hostState(ep)
                for (attempt in 2..tries) {
                    if (state != null) break
                    Thread.sleep(300)
                    state = d.hostState(ep)
                }
                when {
                    state != null && state.current + 1 == channel -> Log.d("${d.label()} already on $channel")
                    state != null && channel <= state.count -> {
                        d.setHost(ep, channel - 1)
                        Log.i("${d.label()} -> channel $channel")
                        if (isTrigger(d)) awayBySwitch = true
                    }
                    state != null -> Log.w("${d.label()} has no channel $channel")
                    isTrigger(d) -> Log.w("${d.label()} did not answer; it stays on this device")
                    else -> Log.d("${d.label()} (slot ${d.index}) not on this device, skipped")
                }
            } catch (e: IOException) {
                Log.w("${d.label()}: ${e.message}")
            }
        }
    }

    /** A switch asked for on the screen, in the notification or in the quick settings
     *  tile: the group and the master mouse go to [channel]. Only devices on this
     *  device can be told to move. */
    private fun onRequest(channel: Int) {
        if (!cfg.enabled) return
        Log.i("switch request: channel $channel")
        switchTo(channel)
        held = emptySet()
        gesture = null
        shared.setStatus { it.copy(lastSwitch = "→ $channel") }
    }

    /** The Quick Settings tile: like a press of the wheel mode-shift button, so where it
     *  goes depends on the channels the mouse really has. */
    private fun onPressRequest() {
        if (!cfg.enabled || trigger == null) return
        Log.i("tile pressed")
        onPress(BUTTON_WHEEL_MODE, Gesture.Press)
    }

    /** Asks the master mouse, if it is here, which channel it is on — this device's
     *  channel, which the screen keeps on. */
    private fun readChannel() {
        val d = trigger ?: return
        val ep = endpoints.getOrNull(d.endpoint) ?: return
        val state = runCatching { d.hostState(ep) }.getOrNull() ?: return
        shared.setStatus { it.copy(channel = state.current + 1) }
    }

    /** Moves everything on, as set for this gesture of the button [cid]. */
    private fun onPress(cid: Int, g: Gesture) {
        val d = trigger ?: return
        val ep = endpoints.getOrNull(d.endpoint) ?: return
        val state = d.hostState(ep)
        if (state == null) {
            Log.w("button pressed but ${d.label()} did not report its channel")
            return
        }
        shared.setStatus { it.copy(channel = state.current + 1) }
        val next = cfg.button(cid).channelAfter(g, state.current, state.count)
        if (next == null) {
            Log.i("${buttonName(cid)} button $g: nothing to do (already there, or not set)")
            return
        }
        Log.i("${buttonName(cid)} button $g: channel ${state.current + 1} -> $next")
        switchTo(next)
        // The release report never arrives once the mouse has left.
        held = emptySet()
        gesture = null
        shared.setStatus { it.copy(lastSwitch = "${state.current + 1} → $next") }
    }

    fun handle(endpoint: Int, report: ByteArray) {
        if (report.size >= 5 && report.u(0) == REPORT_SHORT && report.u(2) == NOTIF_DEVICE_CONNECTION) {
            val slot = report.u(1)
            val up = report.u(4) and LINK_DOWN == 0
            Log.d("slot $slot link ${if (up) "up" else "down"}")
            if (isTrigger(endpoint, slot)) {
                held = emptySet()
                gesture = null
            }
            if (up) {
                pending[endpoint to slot] = (now() + SLOT_SETTLE) to SLOT_RETRIES
            } else if (online.remove(endpoint to slot)) {
                publish()
            }
            return
        }
        val t = trigger ?: return
        if (t.endpoint != endpoint) return
        // Mouse movement while a button with gestures is held.
        t.rawMovement(report)?.let { (dx, dy) ->
            gesture = gesture?.let { (cid, x, y) -> Triple(cid, x + dx, y + dy) }
            return
        }
        val pressed = t.pressedControls(report) ?: return
        val down = pressed.filter { it in BUTTONS && cfg.button(it).on }.toSet()
        // The first button that went down now; it decides where to go.
        val newPress = BUTTONS.firstOrNull { it in down && it !in held }
        held = down
        if (!cfg.enabled) {
            gesture = null
            return
        }
        if (newPress != null) {
            if (cfg.button(newPress).usesMoves()) {
                // Decided on release, by where the mouse went meanwhile.
                gesture = Triple(newPress, 0, 0)
                Log.i("${buttonName(newPress)} button held: waiting for release")
            } else {
                onPress(newPress, Gesture.Press)
            }
        }
        val g = gesture
        if (g != null && g.first !in held) {
            gesture = null
            val (cid, dx, dy) = g
            val move = Gesture.fromMovement(dx, dy)
            Log.i("${buttonName(cid)} button released: moved dx=$dx dy=$dy -> $move")
            onPress(cid, move)
        }
    }

    /** Re-reads slots whose link came up, retrying while the device is still waking. */
    private fun processPending() {
        val t = now()
        val due = pending.filterValues { it.first <= t }.keys.toList()
        for ((endpoint, slot) in due) {
            if (stopped()) break
            val (_, left) = pending.remove(endpoint to slot) ?: continue
            if (endpoint >= endpoints.size) continue
            val pairings = receiverPairings(endpoints[endpoint])
            val isOnline = loadSlot(endpoint, slot, pairings.firstOrNull { it.slot == slot })
            publish()
            if (isOnline) {
                if (trigger == null || isTrigger(endpoint, slot)) ensureDiverted()
                if (isTrigger(endpoint, slot)) {
                    readChannel()
                    greetTrigger()
                }
            } else if (left > 1) {
                pending[endpoint to slot] = (t + SLOT_SETTLE) to (left - 1)
            }
        }
    }

    /** Queues paired receiver slots that have no device entry yet, in case a device was
     *  asleep at start-up and has not sent a link notification since. */
    private fun queueUnlisted() {
        for (endpoint in endpoints.indices) {
            if (endpoints[endpoint].dead) continue
            for (p in receiverPairings(endpoints[endpoint])) {
                val listed = devices.any { it.endpoint == endpoint && it.index == p.slot }
                if (!listed) pending.putIfAbsent(endpoint to p.slot, now() to 1)
            }
        }
    }

    /** Runs until the shared stop flag is set. The buttons are handed back on the way
     *  out, also when it ends with an exception. */
    fun run() {
        try {
            scan()
            describe()
            ensureDiverted()
            var lastScan = now()
            while (!stopped()) {
                guard("applying settings failed") { reloadConfig() }
                shared.takeSwitchRequest()?.let { channel -> guard("switch failed") { onRequest(channel) } }
                shared.takeHapticRequest()?.let { waveform -> guard("haptics failed") { buzz(waveform) } }
                if (shared.takePressRequest()) guard("switch failed") { onPressRequest() }
                if (shared.takeForgetRequest()) {
                    catalog.retainAll { it.online }
                    publish()
                }

                // Rescan when a receiver or Bluetooth device went away (read error, with
                // back-off) or when the source lists a different set of endpoints.
                val dead = endpoints.any { it.dead }
                var rescan = dead && now() - lastScan > backoff
                if (!rescan && now() - lastEnumerate > ENUMERATE_INTERVAL) {
                    lastEnumerate = now()
                    if (source.keys().sorted() != seenEndpoints) {
                        rescan = true
                        backoff = 1_000L
                    }
                }
                if (rescan || (endpoints.isEmpty() && now() - lastScan > RESCAN_INTERVAL)) {
                    lastScan = now()
                    if (rescan) Log.i("devices changed, rescanning")
                    guard("rescan failed") { scan() }
                    if (!stopped()) guard("divert failed") { ensureDiverted() }
                    backoff = if (endpoints.any { it.dead }) minOf(backoff * 5, MAX_BACKOFF) else 1_000L
                    continue
                }
                if (endpoints.isEmpty()) {
                    Thread.sleep(200)
                    continue
                }

                val wait = if (endpoints.size > 1) 5 else 20
                for (i in endpoints.indices) {
                    while (true) {
                        val r = endpoints[i].nextReport(wait) ?: break
                        guard("handling a report failed") { handle(i, r) }
                    }
                }

                guard("refresh failed") { processPending() }
                if (now() - lastCheck > HEALTH_CHECK) {
                    lastCheck = now()
                    guard("health check failed") {
                        queueUnlisted()
                        ensureDiverted()
                    }
                }
            }
        } finally {
            release()
            endpoints.forEach { runCatching { it.close() } }
            endpoints.clear()
        }
    }

    private inline fun guard(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: IOException) {
            Log.w("$what: ${e.message}")
        }
    }

    private fun modeText(): String {
        val paused = if (cfg.enabled) "" else ", paused"
        val buttons = cfg.triggers().joinToString("; ") { "${buttonName(it)} ${cfg.button(it).channels}" }
        return "$buttons$paused"
    }

    fun describe() {
        Log.i("buttons: ${modeText()}")
        val wanted = cfg.triggers().joinToString(", ") { buttonName(it) }
        val t = trigger
        if (t != null) Log.i("trigger: ${t.label()} (buttons $wanted)")
        else Log.w("no mouse with buttons $wanted on this device yet")
        for (d in devices) Log.i("device: ${d.label()} (slot ${d.index})")
    }
}

/** The buttons of [cids] that [d] has (it answers for them), in the same order. */
private fun buttonsOn(d: Device, ep: Endpoint, cids: List<Int>): List<Int> =
    cids.filter { runCatching { d.controlReporting(ep, it) }.getOrNull() != null }
