package io.github.wwhalefe.flockswitch

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import java.io.ByteArrayOutputStream
import java.io.InputStream

class App : Application() {
    lateinit var shared: Shared
        private set

    override fun onCreate() {
        super.onCreate()
        Log.useLogcat()
        shared = Shared(filesDir)
    }
}

val Context.shared: Shared get() = (applicationContext as App).shared

const val ACTION_USB_PERMISSION = "io.github.wwhalefe.flockswitch.USB_PERMISSION"

/** Asks for access to a receiver. Android shows its dialog only when the app does not
 *  have it yet; the answer comes back to [UsbPermissionReceiver]. Calling this is also
 *  what Android 14 wants to see before the background service may run. */
fun Context.requestUsbPermission(device: UsbDevice) {
    val usb = getSystemService(UsbManager::class.java) ?: return
    val intent = Intent(ACTION_USB_PERMISSION).setClass(this, UsbPermissionReceiver::class.java)
    // Mutable: Android adds the device and the answer to it.
    val flags = PendingIntent.FLAG_UPDATE_CURRENT or
        (if (android.os.Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
    usb.requestPermission(device, PendingIntent.getBroadcast(this, 0, intent, flags))
}

/** At most [limit] bytes of the stream: a file or download larger than expected is not
 *  read in whole just to be refused. */
fun InputStream.readAtMost(limit: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buf = ByteArray(8192)
    while (out.size() < limit) {
        val n = read(buf, 0, minOf(buf.size, limit - out.size()))
        if (n < 0) break
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}
