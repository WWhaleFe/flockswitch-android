package io.github.wwhalefe.flockswitch

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import io.github.wwhalefe.flockswitch.hidpp.UsbTransport
import io.github.wwhalefe.flockswitch.ui.Actions
import io.github.wwhalefe.flockswitch.ui.FlockswitchApp

class MainActivity : ComponentActivity() {
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val askNearby = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && shared.config.value.enabled) SwitchService.start(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val actions = Actions(
            setEnabled = ::setEnabled,
            allowUsb = { allowUsb(again = true) },
            allowNearby = { if (Build.VERSION.SDK_INT >= 31) askNearby.launch(Manifest.permission.BLUETOOTH_CONNECT) },
            notificationsOn = { NotificationManagerCompat.from(this).areNotificationsEnabled() },
            switchTo = { channel -> shared.requestSwitch(channel) },
            open = { url -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
            report = ::report,
        )
        setContent { FlockswitchApp(shared, actions) }
    }

    override fun onResume() {
        super.onResume()
        // Ask for any receiver plugged in that may not be used yet; then (or if all is
        // allowed already) make sure the background service runs.
        allowUsb(again = false)
        if (shared.config.value.enabled) SwitchService.start(this)
    }

    private fun granted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun setEnabled(on: Boolean) {
        shared.updateConfig { it.copy(enabled = on) }
        if (on) SwitchService.start(this) else SwitchService.stop(this)
    }

    /** Asks for access to the receivers plugged in. On its own (when the screen opens)
     *  each receiver is asked about once, so a "no" is not asked again every time. */
    private fun allowUsb(again: Boolean) {
        val usb = getSystemService(UsbManager::class.java) ?: return
        usb.deviceList.values
            .filter { UsbTransport.isReceiver(it) && !usb.hasPermission(it) }
            .filter { again || asked.add(it.deviceName) }
            .forEach { requestUsbPermission(it) }
    }

    private fun report() {
        val s = shared.status.value
        val body = buildString {
            appendLine()
            appendLine()
            appendLine("---")
            appendLine("flockswitch for Android ${BuildConfig.VERSION_NAME}")
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("master: ${s.trigger ?: "-"}, channel: ${s.channel ?: "-"}, receivers: ${s.receivers}")
            appendLine("devices: " + s.devices.joinToString { "${it.name} [${it.key}]" })
            appendLine()
            Log.recent().takeLast(60).forEach { appendLine(it) }
        }
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(REPORT_ADDRESS))
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.report_subject))
            putExtra(Intent.EXTRA_TEXT, body)
        }
        runCatching { startActivity(intent) }
    }

    companion object {
        /** Receivers asked about since the app started. */
        private val asked = mutableSetOf<String>()
        const val REPORT_ADDRESS = "feedback.wwhale@gmail.com"
        const val ISSUES_PAGE = "https://github.com/WWhaleFe/flockswitch-android/issues/new"
    }
}
