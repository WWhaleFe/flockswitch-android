package io.github.wwhalefe.flockswitch.net

import android.content.ClipData
import android.content.ClipboardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.FileProvider
import io.github.wwhalefe.flockswitch.*
import org.json.JSONObject
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Owned by SwitchService. Everything that takes the native lock or waits for the service
 *  runs in order on [NetBridge]'s own thread, never on the main thread; the fields below
 *  that are not volatile belong to that thread. */
class NetworkReceiver(private val context: Context, private val shared: Shared) {
    private val main = Handler(Looper.getMainLooper())
    private var multicast: WifiManager.MulticastLock? = null
    private var worker: Thread? = null
    private var active = AtomicBoolean(false)
    /** Text and images share one worker so clipboard updates keep their arrival order. */
    private var clips: ExecutorService? = null
    private var clipboard: ClipboardQueue? = null
    @Volatile private var current: NetworkConfig? = null
    private val gate = ReconnectGate()
    private var keyRulesJob: Job? = null
    private var connectivity: ConnectivityManager? = null
    private var networkChanged: ConnectivityManager.NetworkCallback? = null
    private var wakeRegistered = false
    private val wake = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT || intent.action == Intent.ACTION_SCREEN_ON) reconnect()
        }
    }
    private val deviceName: String get() = Settings.Global.getString(context.contentResolver, "device_name") ?: Build.MODEL
    fun sync(config: NetworkConfig) {
        if (!NetBridge.available) return
        NetBridge.post { syncNow(config) }
    }
    private fun syncNow(config: NetworkConfig) {
        if (!config.on || !config.notice_accepted) { stopNow(); return }
        if (current != null) {
            NetBridge.updateConfig(config.toCoreJson(deviceName))
            current = config
            shared.keyRuleEdits.wake()
            return
        }
        try {
            multicast = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                .createMulticastLock("flockswitch-mdns").apply { setReferenceCounted(false); acquire() }
            val dm = screenMetrics(context)
            NetBridge.setReady(PointerService.instance != null, context.getString(R.string.net_accessibility))
            val staging = File(context.cacheDir, ClipboardImage.STAGING_SUBDIR).absolutePath
            val error = NetBridge.start(config.toCoreJson(deviceName), context.filesDir.absolutePath, staging, deviceName, dm.widthPixels, dm.heightPixels)
            NetBridge.startError = error
            if (error != null) { Log.w("network: $error"); stopNow(); return }
            current = config
            monitorNetwork()
            val running = AtomicBoolean(true)
            active = running
            val clipWorker = Executors.newSingleThreadExecutor { r -> Thread(r, "flockswitch-clipboard") }
            clips = clipWorker
            clipboard = ClipboardQueue(clipWorker) { main.post(it) }
            worker = Thread({
                while (running.get()) {
                    val action = NetBridge.nextAction(500) ?: continue
                    main.postAtTime({ if (running.get()) consume(action) }, running, SystemClock.uptimeMillis())
                }
            }, "flockswitch-actions").also { it.start() }
            keyRulesJob = CoroutineScope(Dispatchers.Main).launch {
                try {
                    while (isActive) {
                        var connected = false
                        try {
                            val status = withContext(Dispatchers.IO) { NetStatus.parse(NetBridge.status()) }
                            val edits = shared.keyRuleEdits
                            // Rules are only the master's once its settings arrived.
                            edits.observe(status.keys, status.following != null,
                                masterTakes = status.masterTakesKeyRules(current?.peers?.map { it.id }?.toSet().orEmpty()))
                            connected = status.running && status.peers.any { p -> p.connected && current?.peers?.any { it.id == p.id } == true }
                            if (connected && status.following != null) {
                                edits.next()?.let { keys ->
                                    withContext(Dispatchers.IO) { NetBridge.sendKeyRules(KeyRules.toJson(keys)) }
                                }
                            } else edits.disconnected()
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) {
                            shared.keyRuleEdits.disconnected()
                            Log.w("network key rules: ${e.message}")
                        }
                        shared.keyRuleEdits.awaitChange(keyRulesPollMs(connected, shared.keyRuleEdits.hasPending))
                    }
                } finally { shared.keyRuleEdits.disconnected() }
            }
        } catch (e: Exception) { NetBridge.startError = e.message; Log.w("network: ${e.message}"); stopNow() }
    }
    fun screenChanged() {
        if (current == null) return
        NetBridge.post {
            if (current == null) return@post
            val dm = screenMetrics(context)
            NetBridge.setScreen(dm.widthPixels, dm.heightPixels)
        }
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
                shared.keyRuleEdits.reset()
            }
            a.has("unpaired") -> {
                shared.updateConfig { it.copy(network = it.network.copy(peers = it.network.peers.filterNot { p -> p.id == a.getString("unpaired") })) }
                shared.keyRuleEdits.reset()
            }
            a.has("switch") -> shared.requestSwitch(a.getInt("switch"))
            a.has("clipboard") -> {
                val c = a.getJSONObject("clipboard")
                val image = c.optString("image")
                if (image.isNotEmpty()) setClipboardImage(File(image)) else if (shared.config.value.network.share_clipboard) setClipboard(c.optString("text"))
            }
            else -> PointerService.instance?.networkAction(a)
        }
    }.onFailure { Log.w("network action: ${it.message}") }
    /** Text follows the same worker and main-thread path as received images. */
    private fun setClipboard(text: String) {
        if (text.isEmpty() || text.length > MAX_CLIPBOARD_CHARS) return
        val queue = clipboard ?: return
        val flag = active
        if (!flag.get()) return
        try {
            queue.text {
                if (flag.get()) runCatching {
                    val manager = context.getSystemService(ClipboardManager::class.java)
                    manager.setPrimaryClip(ClipData.newPlainText("flockswitch", text))
                }.onFailure { Log.w("clipboard text: ${it.message}") }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) { }
    }
    /** The staged PNG becomes a cache file behind a content:// URI. One worker takes the
     *  images in the order they came, so a small later image cannot land first and a
     *  later one never overwrites an earlier one's file. */
    private fun setClipboardImage(staged: File) {
        val queue = clipboard
        val flag = active
        if (!shared.config.value.network.share_clipboard || queue == null || !flag.get()) { staged.delete(); return }
        try {
            queue.image {
                runCatching<(() -> Unit)?> {
                    if (!flag.get()) { staged.delete(); return@runCatching null }
                    val dir = File(context.cacheDir, ClipboardImage.SUBDIR)
                    val file = ClipboardImage.store(staged, dir, System.currentTimeMillis()) ?: return@runCatching null
                    val uri = FileProvider.getUriForFile(context, context.packageName + ".clipimages", file)
                    val apply: () -> Unit = {
                        if (flag.get()) runCatching {
                            val clip = ClipData.newUri(context.contentResolver, "flockswitch", uri)
                            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
                        }.onFailure { Log.w("clipboard image: ${it.message}") }
                    }
                    apply
                }.onFailure { Log.w("clipboard image: ${it.message}") }.getOrNull()
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) { staged.delete() }
    }
    /** Redials the master now: after unlock, screen on and network changes. Not on the main
     *  thread: it takes the native lock. */
    fun reconnect() {
        if (current == null || !NetBridge.available || !gate.allow(System.currentTimeMillis())) return
        shared.keyRuleEdits.wake()
        NetBridge.post { NetBridge.reconnect() }
    }
    /** These system events only matter while the native receiver is running. */
    private fun monitorNetwork() {
        context.registerReceiver(wake, IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
        })
        wakeRegistered = true
        val callback = object : ConnectivityManager.NetworkCallback() {
            @Volatile private var seen = false
            override fun onAvailable(network: Network) = changed()
            override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) = changed()
            private fun changed() {
                if (!seen) { seen = true; return }
                reconnect()
            }
        }
        runCatching {
            context.getSystemService(ConnectivityManager::class.java)?.let {
                it.registerDefaultNetworkCallback(callback)
                connectivity = it
                networkChanged = callback
            }
        }.onFailure { Log.w("network callback: ${it.message}") }
    }
    fun stop() {
        NetBridge.post { stopNow() }
    }
    private fun stopNow() {
        current = null
        keyRulesJob?.cancel()
        keyRulesJob = null
        active.set(false)
        if (wakeRegistered) runCatching { context.unregisterReceiver(wake) }
        wakeRegistered = false
        networkChanged?.let { callback -> runCatching { connectivity?.unregisterNetworkCallback(callback) } }
        networkChanged = null
        connectivity = null
        if (NetBridge.available) NetBridge.stop()
        // Images not stored yet are gone with the native queue (it removed their files).
        clips?.shutdownNow()
        clips = null
        clipboard = null
        // Finish the old consumer before a new run can read the shared native queue.
        worker?.join(600)
        worker = null
        main.removeCallbacksAndMessages(active)
        main.post { PointerService.instance?.resetNetworkInput() }
        multicast?.let { if (it.isHeld) it.release() }
        multicast = null
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
