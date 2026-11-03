// Logitech receivers on USB (directly or through an OTG adapter), through Android's USB
// host API.
//
// A receiver has three HID interfaces: 0 keyboard, 1 mouse, 2 HID++. Apps cannot reach
// /dev/hidraw on Android, so flockswitch takes interface 2 away from the kernel (forced
// claim) and talks to it directly; interfaces 0 and 1 stay with Android, so typing and
// pointing keep working. Releasing the interface hands it back to the kernel.
//
// If the kernel's Logitech DJ driver had the receiver (it does so for Unifying on some
// kernels), the receiver sends all input through interface 2 and taking it would cut
// the keyboard and mouse off. That shows as DJ reports (IDs 0x20/0x21) arriving here:
// flockswitch then hands the interface back at once and leaves that receiver alone
// until it is plugged in again.

package io.github.wwhalefe.flockswitch.hidpp

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import io.github.wwhalefe.flockswitch.Log
import java.io.IOException

private const val HIDPP_INTERFACE = 2
private const val REPORT_DJ_SHORT = 0x20
private const val REPORT_DJ_LONG = 0x21

/** Reads failing at once this many times in a row mean the receiver is gone. */
private const val QUICK_FAILURES = 5

/** HID class request SET_REPORT with report type "output". */
private const val SET_REPORT = 0x09
private const val REPORT_TYPE_OUTPUT = 0x02

class UsbTransport private constructor(
    private val device: UsbDevice,
    private val connection: UsbDeviceConnection,
    private val iface: UsbInterface,
    private val input: UsbEndpoint,
    private val output: UsbEndpoint?,
    private val onDjMode: () -> Unit,
) : Transport {
    override val kind = EndpointKind.Receiver
    override val product: String = device.productName ?: "USB receiver %04X".format(device.productId)
    override val productId = device.productId
    override val key = usbKey(device)
    private val buffer = ByteArray(maxOf(input.maxPacketSize, 64))
    private var closed = false
    private var quickFailures = 0

    override fun write(report: ByteArray) {
        if (closed) throw IOException("$product is closed")
        val n = if (output != null) {
            connection.bulkTransfer(output, report, report.size, 1000)
        } else {
            // The receiver has no interrupt OUT endpoint: reports go as SET_REPORT.
            val requestType = UsbConstants.USB_DIR_OUT or UsbConstants.USB_TYPE_CLASS or 0x01 // to the interface
            val value = (REPORT_TYPE_OUTPUT shl 8) or report.u(0)
            connection.controlTransfer(requestType, SET_REPORT, value, iface.id, report, report.size, 1000)
        }
        if (n < 0) throw IOException("$product: write failed")
    }

    override fun read(timeoutMs: Int): ByteArray? {
        if (closed) throw IOException("$product is closed")
        // A negative result is a timeout or an error, which Android does not tell
        // apart. An error comes back at once instead of after the wait, so several in a
        // row mean the receiver is gone (or stalled): report it, or the loop would spin.
        val start = System.nanoTime()
        val n = connection.bulkTransfer(input, buffer, buffer.size, timeoutMs)
        if (n <= 0) {
            val waitedMs = (System.nanoTime() - start) / 1_000_000
            if (waitedMs * 2 < timeoutMs) {
                if (++quickFailures >= QUICK_FAILURES) throw IOException("$product: reading fails")
                Thread.sleep(maxOf(timeoutMs, 5).toLong())
            } else {
                quickFailures = 0
            }
            return null
        }
        quickFailures = 0
        val report = buffer.copyOf(n)
        if (report.u(0) == REPORT_DJ_SHORT || report.u(0) == REPORT_DJ_LONG) {
            Log.w("$product is in DJ mode (Android's driver uses it); handing it back")
            onDjMode()
            close()
            throw IOException("$product is used by Android's driver")
        }
        return report
    }

    override fun close() {
        if (closed) return
        closed = true
        // Hands the interface back to the kernel driver.
        runCatching { connection.releaseInterface(iface) }
        connection.close()
    }

    companion object {
        fun usbKey(device: UsbDevice) = "usb:${device.deviceName}:%04X".format(device.productId)

        /** Whether [device] is a Logitech receiver flockswitch can use. */
        fun isReceiver(device: UsbDevice) =
            device.vendorId == LOGITECH_VID && device.productId in RECEIVER_PIDS && hidppInterface(device) != null

        private fun hidppInterface(device: UsbDevice): UsbInterface? =
            (0 until device.interfaceCount).map { device.getInterface(it) }
                .firstOrNull { it.id == HIDPP_INTERFACE && it.interfaceClass == UsbConstants.USB_CLASS_HID }

        /** Opens the HID++ interface of a receiver the app has permission for; null when
         *  it cannot be opened. */
        fun open(manager: UsbManager, device: UsbDevice, onDjMode: () -> Unit): UsbTransport? {
            val iface = hidppInterface(device) ?: return null
            val endpoints = (0 until iface.endpointCount).map { iface.getEndpoint(it) }
                .filter { it.type == UsbConstants.USB_ENDPOINT_XFER_INT }
            val input = endpoints.firstOrNull { it.direction == UsbConstants.USB_DIR_IN } ?: return null
            val output = endpoints.firstOrNull { it.direction == UsbConstants.USB_DIR_OUT }
            val connection = manager.openDevice(device) ?: return null
            if (!connection.claimInterface(iface, true)) {
                Log.w("cannot claim the HID++ interface of ${device.productName}")
                connection.close()
                return null
            }
            return UsbTransport(device, connection, iface, input, output, onDjMode)
        }
    }
}
