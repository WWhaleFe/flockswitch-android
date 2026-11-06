// When to try a Bluetooth device again after it could not be opened. Plain Kotlin, so
// the rules can be tested without Android.

package io.github.wwhalefe.flockswitch

import java.util.concurrent.ConcurrentHashMap

/** The first pause before a connected device that failed is tried again; it doubles
 *  with every failure in a row, up to [MAX_RETRY]. */
private const val FIRST_RETRY = 5_000L
private const val MAX_RETRY = 60_000L

/** Bluetooth devices that did not answer (off, out of range, on another computer), by
 *  key. A device that is away is left out until Android reports it connected again. A
 *  device that is connected but still failed — usual right after the mouse comes back,
 *  while Android is bringing its own link up — is tried again after a growing pause.
 *  Either way the endpoint keys change once, so the engine rescans then and not on a
 *  timer. Reports from Android arrive on the main thread, the engine asks on its own. */
class BluetoothRetries(private val clock: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private class Failure(val count: Int, val retryAt: Long)

    private val failures = ConcurrentHashMap<String, Failure>()

    /** Devices whose link Android last reported up. */
    private val linked: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Opening [key] did not work. */
    fun failed(key: String) {
        val count = (failures[key]?.count ?: 0) + 1
        val pause = minOf(FIRST_RETRY shl minOf(count - 1, 4), MAX_RETRY)
        failures[key] = Failure(count, clock() + pause)
    }

    /** Opening [key] worked. */
    fun opened(key: String) {
        failures -= key
    }

    /** Android reports the link to [key] up: try it at once. */
    fun linkUp(key: String) {
        linked += key
        failures -= key
    }

    fun linkDown(key: String) {
        linked -= key
    }

    /** Whether [key] should not be tried now. [connected] tells whether Android lists it
     *  as connected right now (a device linked before the app started sends no report). */
    fun leftOut(key: String, connected: Boolean): Boolean {
        val f = failures[key] ?: return false
        if (!connected && key !in linked) return true
        return clock() < f.retryAt
    }
}
