package io.github.wwhalefe.flockswitch.net

object NetBridge {
    val available: Boolean = runCatching { System.loadLibrary("flocknet"); true }.getOrDefault(false)
    var startError: String? = null
        internal set
    external fun start(configJson: String, baseDir: String, deviceName: String, width: Int, height: Int): String?
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
