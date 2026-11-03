package io.github.wwhalefe.flockswitch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager

/** Starts the background service when the phone or tablet has started. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val cfg = context.shared.config.value
        if (cfg.enabled && cfg.startAtBoot) SwitchService.start(context)
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
