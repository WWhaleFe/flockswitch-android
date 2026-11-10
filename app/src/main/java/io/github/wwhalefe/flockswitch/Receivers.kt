package io.github.wwhalefe.flockswitch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import java.io.File

/** Starts the background service when the phone or tablet has started, and again after
 *  flockswitch was updated (Android stops the old version as it replaces it). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val cfg = context.shared.config.value
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> if (cfg.enabled && cfg.startAtBoot) SwitchService.start(context)
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.i("updated to ${BuildConfig.VERSION_NAME}")
                // The downloaded APK of a self-update is not needed any more.
                File(context.cacheDir, "update").deleteRecursively()
                if (cfg.enabled) SwitchService.start(context)
            }
        }
    }
}

/** The answer to [requestUsbPermission]: start working once a receiver may be opened. */
class UsbPermissionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_USB_PERMISSION) return
        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
            Log.i("USB permission granted")
            SwitchService.start(context)
        } else {
            Log.w("USB permission refused")
        }
    }
}
