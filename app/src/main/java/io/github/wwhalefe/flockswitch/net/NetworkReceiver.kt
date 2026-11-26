package io.github.wwhalefe.flockswitch.net

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import io.github.wwhalefe.flockswitch.*
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/** Owned by SwitchService. JNI waits never run on the main thread. */
class NetworkReceiver(private val context: Context, private val shared: Shared) {
    private val main = Handler(Looper.getMainLooper())
    private var multicast: WifiManager.MulticastLock? = null
    private var worker: Thread? = null
    private var active = AtomicBoolean(false)
    private var current: NetworkConfig? = null
    private val deviceName: String get() = Settings.Global.getString(context.contentResolver, "device_name") ?: Build.MODEL
    fun sync(config: NetworkConfig) {
        if (!NetBridge.available) return
        if (!config.on) { stop(); return }
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
            a.has("paired") -> {
                val p = Config.json.decodeFromString(NetworkPeer.serializer(), a.getJSONObject("paired").toString())
                shared.updateConfig { it.copy(network = it.network.copy(peers = it.network.peers.filterNot { peer -> peer.id == p.id } + p)) }
            }
            a.has("unpaired") -> shared.updateConfig { it.copy(network = it.network.copy(peers = it.network.peers.filterNot { p -> p.id == a.getString("unpaired") })) }
            a.has("switch") -> shared.requestSwitch(a.getInt("switch"))
            else -> PointerService.instance?.networkAction(a)
        }
    }.onFailure { Log.w("network action: ${it.message}") }
    fun stop() {
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

@Suppress("DEPRECATION")
internal fun screenMetrics(context: Context): android.util.DisplayMetrics = android.util.DisplayMetrics().also {
    context.getSystemService(android.view.WindowManager::class.java).defaultDisplay.getRealMetrics(it)
}
