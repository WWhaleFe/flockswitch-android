// Where the engine finds endpoints on Android: Logitech receivers on USB, and (when
// turned on) Logitech devices connected over Bluetooth.

package io.github.wwhalefe.flockswitch

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import io.github.wwhalefe.flockswitch.hidpp.BleTransport
import io.github.wwhalefe.flockswitch.hidpp.Transport
import io.github.wwhalefe.flockswitch.hidpp.UsbTransport
import java.util.concurrent.ConcurrentHashMap

/** Major classes that are surely not a mouse or keyboard. Bluetooth LE devices often
 *  report no class at all, so those are tried. */
private val NOT_INPUT = setOf(
    BluetoothClass.Device.Major.AUDIO_VIDEO,
    BluetoothClass.Device.Major.COMPUTER,
    BluetoothClass.Device.Major.HEALTH,
    BluetoothClass.Device.Major.IMAGING,
    BluetoothClass.Device.Major.NETWORKING,
    BluetoothClass.Device.Major.PHONE,
    BluetoothClass.Device.Major.TOY,
    BluetoothClass.Device.Major.WEARABLE,
)

class AndroidSource(private val context: Context, private val shared: Shared) : EndpointSource, AutoCloseable {
    private val usb = context.getSystemService(UsbManager::class.java)
    private val bluetooth = context.getSystemService(BluetoothManager::class.java)

    /** Receivers handed back because Android's own driver uses them, until unplugged. */
    private val busy = mutableSetOf<String>()

    /** Bluetooth devices found not to be Logitech HID++ devices, so they are not asked again. */
    private val notLogitech = mutableSetOf<String>()

    /** Bluetooth devices that did not answer (off, out of range, on another computer).
     *  They are left out of [keys] until Android reports them connected again; that
     *  changes the keys, so the engine rescans once, instead of on a timer. */
    private val failed: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val connections = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            val device = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            } ?: return
            failed -= BleTransport.bleKey(device)
        }
    }

    init {
        ContextCompat.registerReceiver(
            context, connections, IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun close() {
        runCatching { context.unregisterReceiver(connections) }
    }

    private fun receivers() = usb?.deviceList?.values.orEmpty().filter { UsbTransport.isReceiver(it) }

    private fun bluetoothAllowed() =
        Build.VERSION.SDK_INT < 31 ||
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /** Bluetooth devices worth trying: those connected over GATT, and paired keyboards and
     *  mice (Android may not list a mouse its HID service uses as a GATT connection). */
    @SuppressLint("MissingPermission") // bluetoothAllowed() is checked first
    private fun bluetoothDevices(): List<BluetoothDevice> {
        val adapter = bluetooth?.adapter
        if (!shared.config.value.bluetooth || !bluetoothAllowed() || adapter?.isEnabled != true) return emptyList()
        val connected = runCatching { bluetooth.getConnectedDevices(BluetoothProfile.GATT) }.getOrDefault(emptyList())
        val paired = runCatching { adapter.bondedDevices.orEmpty() }.getOrDefault(emptySet()).filter {
            it.type != BluetoothDevice.DEVICE_TYPE_CLASSIC && it.bluetoothClass?.majorDeviceClass !in NOT_INPUT
        }
        return (connected + paired).distinctBy { it.address }.filter {
            val key = BleTransport.bleKey(it)
            key !in notLogitech && key !in failed
        }
    }

    override fun keys(): List<String> {
        val present = receivers()
        // A receiver that was unplugged may be used again when it comes back.
        busy.retainAll(present.map { UsbTransport.usbKey(it) }.toSet())
        // The permission is part of the key, so being allowed to open a receiver counts as
        // a change and the engine opens it.
        return present.map { UsbTransport.usbKey(it) + if (usb!!.hasPermission(it)) "" else ":no-permission" } +
            bluetoothDevices().map { BleTransport.bleKey(it) }
    }

    override fun open(): List<Transport> {
        val out = mutableListOf<Transport>()
        val present = receivers()
        val usable = present.filter { UsbTransport.usbKey(it) !in busy }
        val allowed = usable.filter { usb!!.hasPermission(it) }
        for (device in allowed) {
            val key = UsbTransport.usbKey(device)
            val t = UsbTransport.open(usb!!, device) {
                busy += key
                shared.setStatus { it.copy(receiverBusy = true) }
            }
            if (t != null) out += t
        }
        for (device in bluetoothDevices()) {
            val key = BleTransport.bleKey(device)
            when (val r = runCatching { BleTransport.open(context, device) }.getOrElse { BleTransport.Opened.Failed }) {
                is BleTransport.Opened.Ok -> out += r.transport
                BleTransport.Opened.NotLogitech -> notLogitech += key
                BleTransport.Opened.Failed -> failed += key
            }
        }
        shared.setStatus {
            it.copy(
                usbNeedsPermission = usable.size - allowed.size,
                receiverBusy = busy.isNotEmpty(),
                bluetoothNeedsPermission = shared.config.value.bluetooth && !bluetoothAllowed(),
            )
        }
        return out
    }
}
