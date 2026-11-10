package io.github.wwhalefe.flockswitch

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import io.github.wwhalefe.flockswitch.hidpp.UsbTransport
import io.github.wwhalefe.flockswitch.ui.Actions
import io.github.wwhalefe.flockswitch.ui.FlockswitchApp

class MainActivity : ComponentActivity() {
    private var preview: EdgeOverlay? = null
    private var locatePreview: LocateOverlay? = null
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
            allowOverlay = ::allowOverlay,
            previewEdge = ::previewEdge,
            previewLocate = ::previewLocate,
            openAccessibility = ::openAccessibility,
            openAppInfo = { runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) } },
            overlayOn = { Settings.canDrawOverlays(this) },
            notificationsOn = { NotificationManagerCompat.from(this).areNotificationsEnabled() },
            switchTo = { channel -> shared.requestSwitch(channel) },
            open = { url -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
            report = ::report,
            feel = { what, intensity -> shared.requestFeel(what, intensity) },
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

    override fun onDestroy() {
        preview?.end()
        locatePreview?.end()
        super.onDestroy()
    }

    private fun allowOverlay() {
        runCatching { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
    }

    /** Opens flockswitch's own accessibility page where Android has one, else the list. */
    private fun openAccessibility() {
        if (Build.VERSION.SDK_INT >= 31) {
            val service = ComponentName(this, PointerService::class.java).flattenToString()
            // Not in the public SDK; where it does not exist the list opens below.
            val details = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").putExtra(Intent.EXTRA_COMPONENT_NAME, service)
            if (runCatching { startActivity(details) }.isSuccess) return
        }
        runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
    }

    /** Plays the countdown once, for the settings. */
    private fun previewEdge(edges: Edges) {
        if (!Settings.canDrawOverlays(this)) return allowOverlay()
        (preview ?: EdgeOverlay(this).also { preview = it }).preview(edges)
    }

    /** Plays the highlight once at this point, for the settings: through the accessibility
     *  service if it is on, otherwise in a window that needs "display over other apps". */
    private fun previewLocate(locate: Locate, x: Float, y: Float) {
        PointerService.instance?.let { return it.preview(locate, x, y) }
        if (!Settings.canDrawOverlays(this)) return allowOverlay()
        val overlay = locatePreview ?: LocateOverlay(applicationContext, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY).also { locatePreview = it }
        overlay.show(locate, x, y)
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
