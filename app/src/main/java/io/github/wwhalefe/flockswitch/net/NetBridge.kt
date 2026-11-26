package io.github.wwhalefe.flockswitch.net

import io.github.wwhalefe.flockswitch.Log
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object NetBridge {
    val available: Boolean = runCatching { System.loadLibrary("flocknet"); true }.getOrDefault(false)
    @Volatile
    var startError: String? = null
        internal set

    /** Every call that takes the native lock or waits for the service to stop (start, stop,
     *  updateConfig, setScreen, setReady, request, reconnect) goes through this one thread,
     *  in order, so the main thread never waits for it. */
    private val serial: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "flockswitch-net").also { it.isDaemon = true }
    }

    /** Runs [block] on the native thread, after everything queued before it. */
    fun post(block: () -> Unit) {
        try {
            serial.execute {
                try { block() } catch (e: Throwable) { Log.w("network: ${e.message}") }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
        }
    }

    external fun start(configJson: String, baseDir: String, stageDir: String, deviceName: String, width: Int, height: Int): String?
    external fun stop()
    external fun updateConfig(configJson: String)
    external fun setScreen(width: Int, height: Int)
    external fun setReady(ok: Boolean, reason: String?)
    external fun nextAction(timeoutMs: Int): String?
    external fun status(): String
    external fun request(json: String)
    external fun reconnect()
    external fun sendKeyRules(json: String)
}
