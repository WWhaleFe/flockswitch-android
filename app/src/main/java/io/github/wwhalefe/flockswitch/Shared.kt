// State shared by the engine thread, the background service and the screen, and what
// is kept on disk: settings, the devices seen so far, and the button state found
// before flockswitch diverted a button (so even a killed run is undone later).

package io.github.wwhalefe.flockswitch

import io.github.wwhalefe.flockswitch.hidpp.Reporting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** A device seen at least once, remembered on disk so the settings can list it even
 *  while it is connected to another computer. */
@Serializable
data class DeviceInfo(
    /** Wireless product ID ("B042") or Bluetooth product ID; used in `follow` and `triggerDevice`. */
    val key: String,
    val name: String,
    /** "mouse", "keyboard" or "other". */
    val kind: String,
    /** Has one of the trigger buttons (e.g. MX Master wheel mode-shift), so it can be the master. */
    val master: Boolean,
    @kotlinx.serialization.Transient val online: Boolean = false,
)

data class Status(
    /** The background service is running. */
    val running: Boolean = false,
    /** Name of the master mouse, when it is on this device. */
    val trigger: String? = null,
    /** Key of the master mouse currently in use (resolves "auto"). */
    val triggerKey: String? = null,
    /** Every device seen so far. */
    val devices: List<DeviceInfo> = emptyList(),
    /** Last switch, e.g. "2 → 1". */
    val lastSwitch: String? = null,
    /** Trigger buttons the master mouse has (control IDs). */
    val buttons: List<Int> = emptyList(),
    /** This device's channel (1-based): the one the master mouse was on when last seen
     *  here. Kept while the mouse is away. */
    val channel: Int? = null,
    /** Logitech receivers plugged in that flockswitch may not open yet. */
    val usbNeedsPermission: Int = 0,
    /** Receivers plugged in and opened. */
    val receivers: Int = 0,
    /** A receiver was handed back because Android's own driver uses it (DJ mode). */
    val receiverBusy: Boolean = false,
    /** Bluetooth is turned on in the settings but the permission is missing. */
    val bluetoothNeedsPermission: Boolean = false,
    /** Android refused to run the background service (a permission is missing). */
    val serviceBlocked: Boolean = false,
    /** An edge has a channel but "display over other apps" is not allowed yet. */
    val overlayNeeded: Boolean = false,
    /** The user has turned flockswitch's accessibility service on. */
    val pointerService: Boolean = false,
)

/** Where saving or loading a settings file is. */
sealed interface BackupState {
    data object Idle : BackupState

    /** Saved under this file name. */
    data class Saved(val name: String) : BackupState
    data object SaveFailed : BackupState

    /** Read and usable; waiting for the user to confirm loading it. */
    data class Confirm(val config: Config, val from: ExportHeader, val name: String) : BackupState

    /** Loaded from this file. */
    data class Loaded(val name: String) : BackupState

    /** Not loaded: [why] (null when the file could not be read at all). */
    data class Rejected(val why: ImportRejection?, val detail: String? = null) : BackupState

    /** Not saved: [SettingsFile.MAX_SAVED] files are saved already. */
    data object Full : BackupState
}

/** How renaming or deleting a saved settings file went. */
enum class ManageOutcome {
    Done,

    /** The file is not there any more; it was taken off the list. */
    Gone,

    /** Where the file is does not let flockswitch do that. */
    Failed,
}

/** What the haptics dialog asks to feel on the mouse. */
sealed interface HapticPreview {
    /** One pulse of this waveform (or [Waveform.RANDOM]). */
    data class Pulse(val waveform: Int) : HapticPreview

    /** A second of waiting at an edge: rising pulses, or even ones. */
    data class Edge(val rising: Boolean) : HapticPreview
}

/** A preview asked for, at a strength (0 = as the mouse is set), and when. */
data class FeelRequest(val what: HapticPreview, val intensity: Int, val atMs: Long)

/** A Feel button press older than this is not played any more (the engine was busy, or
 *  not running). */
const val FEEL_STALE_MS = 3000L

/** What the accessibility service sees of the keyboard. Main thread only. */
class PointerState {
    /** Whether Control is held; null while the service is not reading keys. */
    var ctrl: Boolean? = null
        private set

    /** Told when Control goes down or up. */
    var onControl: ((Boolean) -> Unit)? = null

    fun setControl(down: Boolean?) {
        if (down == ctrl) return
        ctrl = down
        if (down != null) onControl?.invoke(down)
    }
}

class Shared(private val dir: File) {
    private val configFile = File(dir, "config.json")
    private val catalogFile = File(dir, "devices.json")
    private val buttonStateFile = File(dir, "button-state.json")

    private val _config = MutableStateFlow(load())
    val config: StateFlow<Config> = _config.asStateFlow()

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    val pointer = PointerState()

    /** Looking for, downloading and installing a new version (GitHub build only). */
    private val _update = MutableStateFlow<UpdateState>(UpdateState.Unknown)
    val update: StateFlow<UpdateState> = _update.asStateFlow()

    fun setUpdate(state: UpdateState) {
        _update.value = state
    }

    /** Saving the settings to a file and loading them from one (the General tab). */
    private val _backup = MutableStateFlow<BackupState>(BackupState.Idle)
    val backup: StateFlow<BackupState> = _backup.asStateFlow()

    fun setBackup(state: BackupState) {
        _backup.value = state
    }

    /** The settings files this app saved and still finds, newest first. */
    private val _saved = MutableStateFlow<List<SavedFile>>(emptyList())
    val saved: StateFlow<List<SavedFile>> = _saved.asStateFlow()

    fun setSaved(list: List<SavedFile>) {
        _saved.value = list
    }

    private val switchRequest = AtomicInteger(0)
    private val hapticRequest = AtomicInteger(-1)
    private val feelRequest = java.util.concurrent.atomic.AtomicReference<FeelRequest?>(null)
    private val pressRequest = AtomicBoolean(false)
    private val forgetRequest = AtomicBoolean(false)

    /** The stop flag of the current run. Each run gets its own, so an engine still
     *  finishing an earlier run cannot be revived by the next one starting. */
    @Volatile
    private var stop = AtomicBoolean(false)

    private fun load(): Config = runCatching { Config.fromJson(configFile.readText()) }.getOrElse { Config() }

    /** Changes the settings; an unusable result, or one that cannot be saved, is refused
     *  and false returned. */
    @Synchronized
    fun updateConfig(change: (Config) -> Config): Boolean {
        val next = change(_config.value)
        if (next.problems().isNotEmpty()) return false
        try {
            dir.mkdirs()
            configFile.writeText(next.toJson())
        } catch (e: java.io.IOException) {
            Log.w("cannot save the settings: ${e.message}")
            return false
        }
        _config.value = next
        return true
    }

    fun setStatus(change: (Status) -> Status) = _status.update(change)

    /** Asks the engine to send the group and the master mouse to [channel] (1-based). */
    fun requestSwitch(channel: Int) = switchRequest.set(channel)

    fun takeSwitchRequest(): Int? = switchRequest.getAndSet(0).takeIf { it in 1..3 }

    /** Asks the engine to play a haptic waveform on the master mouse (if it has a motor). */
    fun requestHaptic(waveform: Int) = hapticRequest.set(waveform)

    fun takeHapticRequest(): Int? = hapticRequest.getAndSet(-1).takeIf { it >= 0 }

    /** Asks the engine to play [what] on the master mouse at [intensity] percent (0 = as
     *  the mouse is set), putting the mouse's own strength back afterwards. */
    fun requestFeel(what: HapticPreview, intensity: Int) =
        feelRequest.set(FeelRequest(what, intensity, System.currentTimeMillis()))

    /** The preview asked for, if any and still fresh; asked once. */
    fun takeFeelRequest(): FeelRequest? =
        feelRequest.getAndSet(null)?.takeIf { System.currentTimeMillis() - it.atMs in 0..FEEL_STALE_MS }

    /** Asks the engine to switch as a press of the wheel mode-shift button would. */
    fun requestPress() = pressRequest.set(true)

    fun takePressRequest() = pressRequest.getAndSet(false)

    /** Starts a new run with a stop flag of its own and returns it. */
    fun newRun(): AtomicBoolean = AtomicBoolean(false).also { stop = it }

    /** The current run's stop flag; the engine keeps it for its whole run. */
    fun stopFlag(): AtomicBoolean = stop

    fun stopped() = stop.get()

    fun setStopped(value: Boolean) = stop.set(value)

    // Devices seen so far.

    fun loadCatalog(): List<DeviceInfo> = runCatching {
        Config.json.decodeFromString(ListSerializer(DeviceInfo.serializer()), catalogFile.readText())
    }.getOrElse { emptyList() }

    @Synchronized
    fun saveCatalog(devices: List<DeviceInfo>) {
        dir.mkdirs()
        catalogFile.writeText(Config.json.encodeToString(ListSerializer(DeviceInfo.serializer()), devices))
    }

    /** Forgets the remembered devices that are not here. The engine, when running, does
     *  it on its own list too (it would otherwise write them back). */
    @Synchronized
    fun forgetDevices() {
        val here = _status.value.devices.filter { it.online }
        if (here.isEmpty()) catalogFile.delete() else saveCatalog(here)
        setStatus { it.copy(devices = here) }
        forgetRequest.set(true)
    }

    fun takeForgetRequest() = forgetRequest.getAndSet(false)

    // Button state before flockswitch diverted it, by "<device key>/<control ID>".

    @Serializable
    private data class SavedReporting(val diverted: Boolean, val rawXy: Boolean)

    private val buttonStateSerializer = MapSerializer(String.serializer(), SavedReporting.serializer())

    private fun buttonStates(): MutableMap<String, SavedReporting> = runCatching {
        Config.json.decodeFromString(buttonStateSerializer, buttonStateFile.readText()).toMutableMap()
    }.getOrElse { mutableMapOf() }

    private fun saveButtonStates(states: Map<String, SavedReporting>) {
        dir.mkdirs()
        if (states.isEmpty()) buttonStateFile.delete()
        else buttonStateFile.writeText(Config.json.encodeToString(buttonStateSerializer, states))
    }

    private fun stateKey(device: String, cid: Int) = "%s/%04X".format(device, cid)

    @Synchronized
    fun savedOriginalDivert(device: String, cid: Int): Reporting? =
        buttonStates()[stateKey(device, cid)]?.let { Reporting(it.diverted, it.rawXy) }

    @Synchronized
    fun rememberOriginalDivert(device: String, cid: Int, original: Reporting) {
        val states = buttonStates()
        states[stateKey(device, cid)] = SavedReporting(original.diverted, original.rawXy)
        saveButtonStates(states)
    }

    @Synchronized
    fun forgetOriginalDivert(device: String, cid: Int) {
        val states = buttonStates()
        if (states.remove(stateKey(device, cid)) != null) saveButtonStates(states)
    }
}
