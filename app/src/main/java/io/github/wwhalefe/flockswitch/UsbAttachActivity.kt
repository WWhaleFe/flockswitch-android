package io.github.wwhalefe.flockswitch

import android.app.Activity
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import io.github.wwhalefe.flockswitch.hidpp.UsbTransport

/** Opened by Android when a Logitech receiver is plugged in (see usb_device_filter.xml).
 *  Has no screen: it makes sure flockswitch may use the receiver, starts the background
 *  service and closes. */
class UsbAttachActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val device = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }
        if (device != null && UsbTransport.isReceiver(device)) {
            Log.i("receiver plugged in: ${device.productName}")
            requestUsbPermission(device)
            if (shared.config.value.enabled) SwitchService.start(this)
        }
        finish()
    }
}
