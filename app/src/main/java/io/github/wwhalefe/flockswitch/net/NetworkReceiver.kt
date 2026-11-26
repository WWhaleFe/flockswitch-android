package io.github.wwhalefe.flockswitch.net

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import io.github.wwhalefe.flockswitch.*
import org.json.JSONObject
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

/** Owned by SwitchService. JNI waits never run on the main thread. */
class NetworkReceiver(private val context: Context, private val shared: Shared) {
    private val main = Handler(Looper.getMainLooper())
    private var multicast: WifiManager.MulticastLock? = null
    private var worker: Thread? = null
    private var active = AtomicBoolean(false)
    @Volatile private var current: NetworkConfig? = null
    private val gate = ReconnectGate()
    private var keyRulesJob: Job? = null
    private val deviceName: String get() = Settings.Global.getString(context.contentResolver, "device_name") ?: Build.MODEL
    fun sync(config: NetworkConfig) {
        if (!NetBridge.available) return
        if (!config.on || !config.notice_accepted) { stop(); return }
        if (current != null) {
            NetBridge.updateConfig(config.toCoreJson(deviceName))
            current = config
            return
        }
        try {
            multicast = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                .createMulticastLock("flockswitch-mdns").apply { setReferenceCounted(false); acquire() }
            val dm = screenMetrics(context)
            NetBridge.setReady(PointerService.instance != null, context.getString(R.string.net_accessibility))
            val error = NetBridge.start(config.toCoreJson(deviceName), context.filesDir.absolutePath, deviceName, dm.widthPixels, dm.heightPixels)
            NetBridge.startError = error
            if (error != null) { Log.w("network: $error"); stop(); return }
            current = config
            val running = AtomicBoolean(true)
            active = running
            worker = Thread({
                while (running.get()) {
                    val action = NetBridge.nextAction(500) ?: continue
                    main.post { if (running.get()) consume(action) }
                }
            }, "flockswitch-actions").also { it.start() }
            keyRulesJob = CoroutineScope(Dispatchers.Main).launch {
                try {
                    while (isActive) {
                        try {
                            val status = withContext(Dispatchers.IO) { NetStatus.parse(NetBridge.status()) }
                            val edits = shared.keyRuleEdits
                            edits.observe(status.keys)
                            if (status.running && status.peers.any { p -> p.connected && current?.peers?.any { it.id == p.id } == true }) {
                                edits.next()?.let { keys ->
                                    withContext(Dispatchers.IO) { NetBridge.sendKeyRules(KeyRules.toJson(keys)) }
                                }
                            } else edits.disconnected()
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) {
                            shared.keyRuleEdits.disconnected()
                            Log.w("network key rules: ${e.message}")
                        }
                        delay(200)
                    }
                } finally { shared.keyRuleEdits.disconnected() }
            }
        } catch (e: Exception) { NetBridge.startError = e.message; Log.w("network: ${e.message}"); stop() }
    }
    fun screenChanged() {
        if (current == null) return
        val dm = screenMetrics(context)
        NetBridge.setScreen(dm.widthPixels, dm.heightPixels)
    }
    private fun consume(text: String) = runCatching {
        val a = JSONObject(text)
        when {
            a.has("peer_changed") -> {
                val p = Config.json.decodeFromString(NetworkPeer.serializer(), a.getJSONObject("peer_changed").toString())
                shared.updateConfig { it.copy(network = it.network.withChangedPeer(p)) }
            }
            a.has("paired") -> {
                val p = Config.json.decodeFromString(NetworkPeer.serializer(), a.getJSONObject("paired").toString())
                shared.updateConfig { it.copy(network = it.network.copy(peers = it.network.peers.filterNot { peer -> peer.id == p.id } + p)) }
            }
            a.has("unpaired") -> shared.updateConfig { it.copy(network = it.network.copy(peers = it.network.peers.filterNot { p -> p.id == a.getString("unpaired") })) }
            a.has("switch") -> shared.requestSwitch(a.getInt("switch"))
            a.has("clipboard") -> if (shared.config.value.network.share_clipboard) setClipboard(a.getJSONObject("clipboard").optString("text"))
            else -> PointerService.instance?.networkAction(a)
        }
    }.onFailure { Log.w("network action: ${it.message}") }
    /** Writing the clipboard is allowed from the background; only reading is not. */
    private fun setClipboard(text: String) {
        if (text.isEmpty() || text.length > MAX_CLIPBOARD_CHARS) return
        val manager = context.getSystemService(ClipboardManager::class.java)
        manager.setPrimaryClip(ClipData.newPlainText("flockswitch", text))
    }
    /** Redials the master now: after unlock, screen on and network changes. Not on the main
     *  thread: it takes the native lock. */
    fun reconnect() {
        if (current == null || !NetBridge.available || !gate.allow(System.currentTimeMillis())) return
        Thread({ runCatching { NetBridge.reconnect() } }, "flockswitch-reconnect").start()
    }
    fun stop() {
        keyRulesJob?.cancel()
        keyRulesJob = null
        active.set(false)
        if (NetBridge.available) NetBridge.stop()
        // Finish the old consumer before a new run can read the shared native queue.
        worker?.join(600)
        worker = null
        PointerService.instance?.resetNetworkInput()
        multicast?.let { if (it.isHeld) it.release() }
        multicast = null
        current = null
    }
}

/** A longer text is not put on the clipboard (Android's binder limit is about 1 MB). */
private const val MAX_CLIPBOARD_CHARS = 200_000

@Suppress("DEPRECATION")
internal fun screenMetrics(context: Context): android.util.DisplayMetrics = android.util.DisplayMetrics().also {
    context.getSystemService(android.view.WindowManager::class.java).defaultDisplay.getRealMetrics(it)
}

/** Unlock, screen on and a network change often come together: one redial is enough. */
class ReconnectGate(private val minGapMs: Long = 1500) {
    private var last = Long.MIN_VALUE
    @Synchronized fun allow(now: Long): Boolean {
        if (last != Long.MIN_VALUE && now - last in 0 until minGapMs) return false
        last = now
        return true
    }
}
