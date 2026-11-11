// Logitech devices connected over Bluetooth LE.
//
// Android keeps the HID service of a Bluetooth mouse or keyboard to itself, but Logitech
// devices also offer HID++ in a service of their own. How HID++ reports are framed in
// it is not documented, so the first exchange tries the likely framings with a ping
// (root feature, getProtocolVersion) and keeps the one that answers.

package io.github.wwhalefe.flockswitch.hidpp

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import io.github.wwhalefe.flockswitch.Log
import java.io.IOException
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

val LOGITECH_HIDPP_SERVICE: UUID = UUID.fromString("00010000-0000-1000-8000-011f2000046d")
private val DEVICE_INFORMATION: UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
private val PNP_ID: UUID = UUID.fromString("00002a50-0000-1000-8000-00805f9b34fb")
private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

/** How a HID++ long report travels in the characteristic. */
private enum class Framing(val drop: Int) {
    /** [0x11, index, feature, fn|swid, params…] */
    WithReportId(0),

    /** [index, feature, fn|swid, params…] */
    WithIndex(1),

    /** [feature, fn|swid, params…] */
    FeatureFirst(2),
}

@SuppressLint("MissingPermission") // checked by the caller (BleSource)
class BleTransport private constructor(
    private val device: BluetoothDevice,
    private val gatt: BluetoothGatt,
    private val callback: Callback,
    private val characteristic: BluetoothGattCharacteristic,
    override val productId: Int,
) : Transport {
    override val kind = EndpointKind.Direct
    override val product: String = device.name ?: device.address
    override val key = bleKey(device)
    private var framing: Framing? = null
    private var closed = false

    class Callback : BluetoothGattCallback() {
        val connected = Semaphore(0)
        val discovered = Semaphore(0)

        /** One answer per kind of operation, so a late answer to one cannot be taken for
         *  another: true when it succeeded. */
        val written = LinkedBlockingQueue<Boolean>()
        val descriptorWritten = LinkedBlockingQueue<Boolean>()
        val read = LinkedBlockingQueue<ByteArray>()
        val reports = LinkedBlockingQueue<ByteArray>()

        @Volatile
        var lost = false

        /** Whether the last service discovery succeeded. */
        @Volatile
        var discoveryOk = false

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) connected.release() else lost = true
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            discoveryOk = status == BluetoothGatt.GATT_SUCCESS
            discovered.release()
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            written.offer(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            descriptorWritten.offer(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) read.offer(value)
        }

        @Deprecated("Android 12 and older")
        override fun onCharacteristicRead(gatt: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT < 33 && status == BluetoothGatt.GATT_SUCCESS) read.offer(c.value.copyOf())
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            reports.offer(value)
        }

        @Deprecated("Android 12 and older")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < 33) {
                @Suppress("DEPRECATION")
                reports.offer(c.value.copyOf())
            }
        }
    }

    override fun write(report: ByteArray) {
        if (closed || callback.lost) throw IOException("$product is disconnected")
        val f = framing ?: detectFraming() ?: throw IOException("$product does not answer HID++ over Bluetooth")
        send(frame(report, f))
    }

    private fun frame(report: ByteArray, f: Framing): ByteArray {
        // Receiver registers (short reports) do not exist on a directly connected device.
        val long = if (report.size >= LONG_LEN) report else report.copyOf(LONG_LEN).also { it[0] = REPORT_LONG.toByte() }
        return long.copyOfRange(f.drop, LONG_LEN)
    }

    /** Back to a report starting with its report ID and device index. */
    private fun unframe(value: ByteArray, f: Framing): ByteArray = when (f) {
        Framing.WithReportId -> value
        Framing.WithIndex -> byteArrayOf(REPORT_LONG.toByte()) + value
        Framing.FeatureFirst -> byteArrayOf(REPORT_LONG.toByte(), DIRECT_INDEX.toByte()) + value
    }

    private fun send(value: ByteArray) {
        val type = if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }
        callback.written.clear()
        val started = if (Build.VERSION.SDK_INT >= 33) {
            gatt.writeCharacteristic(characteristic, value, type) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = value
            characteristic.writeType = type
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(characteristic)
        }
        if (!started || callback.written.poll(1, TimeUnit.SECONDS) != true) throw IOException("$product: write failed")
    }

    /** Pings in each framing until one answers. */
    private fun detectFraming(): Framing? {
        for (f in Framing.entries) {
            callback.reports.clear()
            val ping = ByteArray(LONG_LEN)
            ping[0] = REPORT_LONG.toByte()
            ping[1] = DIRECT_INDEX.toByte()
            ping[2] = 0x00 // root feature
            ping[3] = 0x1F // getProtocolVersion, software ID 0xF
            ping[6] = 0x5A // echoed back
            // A framing of the wrong length may be refused outright: try the next one.
            try {
                send(frame(ping, f))
            } catch (e: IOException) {
                Log.d("$product: framing $f refused: ${e.message}")
                continue
            }
            val deadline = System.nanoTime() + 1_000_000_000L
            while (System.nanoTime() < deadline) {
                val v = callback.reports.poll(100, TimeUnit.MILLISECONDS) ?: continue
                val r = unframe(v, f)
                if (r.size >= 7 && r.u(2) == 0x00 && r.u(3) == 0x1F && r.u(6) == 0x5A) {
                    Log.i("$product: HID++ over Bluetooth works with framing $f")
                    framing = f
                    return f
                }
            }
        }
        return null
    }

    override fun read(timeoutMs: Int): ByteArray? {
        if (closed || callback.lost) throw IOException("$product is disconnected")
        val v = callback.reports.poll(timeoutMs.toLong(), TimeUnit.MILLISECONDS) ?: return null
        return unframe(v, framing ?: return null)
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { gatt.disconnect() }
        gatt.close()
    }

    /** What came of trying a Bluetooth device. */
    sealed class Opened {
        class Ok(val transport: BleTransport) : Opened()

        /** It has no Logitech HID++ service: no need to ask again. */
        data object NotLogitech : Opened()

        /** It did not connect or answer now (asleep, out of range…): try again later. */
        data object Failed : Opened()
    }

    companion object {
        fun bleKey(device: BluetoothDevice) = "ble:${device.address}"

        /** Connects to a Bluetooth device and opens its HID++ service. */
        fun open(context: Context, device: BluetoothDevice): Opened {
            val cb = Callback()
            @Suppress("DEPRECATION") // deprecated in API 37; the call every older version needs
            val gatt = device.connectGatt(context, false, cb, BluetoothDevice.TRANSPORT_LE) ?: return Opened.Failed
            // A connection that is not handed on is closed, also when Android throws half
            // way (the permission taken back, Bluetooth turned off): each one holds one of
            // Android's few GATT client slots.
            return try {
                connect(device, gatt, cb)
            } catch (e: Exception) {
                gatt.close()
                throw e
            }
        }

        private fun connect(device: BluetoothDevice, gatt: BluetoothGatt, cb: Callback): Opened {
            fun fail(why: String, result: Opened = Opened.Failed): Opened {
                Log.d("${device.address}: $why")
                gatt.close()
                return result
            }
            if (!cb.connected.tryAcquire(5, TimeUnit.SECONDS)) return fail("did not connect")
            if (!gatt.discoverServices() || !cb.discovered.tryAcquire(10, TimeUnit.SECONDS)) return fail("no services")
            // A discovery that failed (or lost the link half way) lists nothing, which must
            // not be taken for "not a Logitech device": that would be kept until restart.
            if (!cb.discoveryOk || cb.lost || gatt.services.isEmpty()) return fail("service discovery failed")
            val service = gatt.getService(LOGITECH_HIDPP_SERVICE) ?: return fail("no Logitech HID++ service", Opened.NotLogitech)
            val c = service.characteristics.firstOrNull {
                it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 &&
                    it.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
            } ?: return fail("no HID++ characteristic", Opened.NotLogitech)

            // The PnP ID (Device Information) carries the product ID, which identifies the
            // model. Without it the device is known by its name (see probeSlot).
            var productId = 0
            gatt.getService(DEVICE_INFORMATION)?.getCharacteristic(PNP_ID)?.let { pnp ->
                if (gatt.readCharacteristic(pnp)) {
                    val v = cb.read.poll(2, TimeUnit.SECONDS)
                    if (v != null && v.size >= 5) productId = v.u(3) or (v.u(4) shl 8)
                }
            }

            gatt.setCharacteristicNotification(c, true)
            val cccd = c.getDescriptor(CCCD) ?: return fail("notifications cannot be turned on")
            val enable = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            val started = if (Build.VERSION.SDK_INT >= 33) {
                gatt.writeDescriptor(cccd, enable) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                cccd.value = enable
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(cccd)
            }
            if (!started || cb.descriptorWritten.poll(2, TimeUnit.SECONDS) != true) return fail("notifications did not turn on")
            return Opened.Ok(BleTransport(device, gatt, cb, c, productId))
        }
    }
}
