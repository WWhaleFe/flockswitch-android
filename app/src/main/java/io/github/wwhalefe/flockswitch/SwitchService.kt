package io.github.wwhalefe.flockswitch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import io.github.wwhalefe.flockswitch.hidpp.UsbTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Keeps the engine running in the background, with the notification Android requires
 *  for that. The notification shows the master mouse and this device's channel, and has
 *  a button for each other channel. */
class SwitchService : Service() {
    private var thread: Thread? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    /** The strips along the screen's edges, while the settings ask for them. */
    private var edges: EdgeWatcher? = null
    private val screenChanged = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            edges?.relayout(shared.config.value)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) },
        )
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(Status(), shared.config.value), type)
        } catch (e: Exception) {
            // Android 14+: the "connected device" kind of service needs a USB or nearby
            // devices permission first. The screen asks for it.
            Log.w("cannot run in the background: ${e.message}")
            // Without a receiver this is expected: plugging one in starts the service
            // with the USB permission. Only ask for more when that is not enough.
            val usb = getSystemService(UsbManager::class.java)
            val receiverAllowed = usb?.deviceList?.values.orEmpty().any { UsbTransport.isReceiver(it) && usb!!.hasPermission(it) }
            shared.setStatus { it.copy(serviceBlocked = receiverAllowed || shared.config.value.bluetooth) }
            stopSelf()
            return
        }
        shared.setStatus { it.copy(serviceBlocked = false) }
        val stop = shared.newRun()
        val previous = engineThread
        thread = Thread({ loop(previous, stop) }, "flockswitch-engine").also {
            engineThread = it
            it.start()
        }
        scope.launch {
            combine(shared.status, shared.config) { s, c -> s to c }
                .distinctUntilChanged()
                .collect { (s, c) -> nm.notify(NOTIFICATION_ID, notification(s, c)) }
        }
        scope.launch {
            var here: Boolean? = null
            shared.status.collect { s ->
                val now = s.trigger != null
                if (here == false && now) arrived()
                here = now
            }
        }
        val watcher = EdgeWatcher(this, shared)
        edges = watcher
        scope.launch { shared.config.collect { syncEdges(watcher, it) } }
        // The accessibility service decides which layer the top and bottom strips use.
        scope.launch {
            shared.status.map { it.pointerService }.distinctUntilChanged().collect { watcher.relayout(shared.config.value) }
        }
        registerReceiver(screenChanged, IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED))
        // GitHub build: look for a new version now and once a day (nothing in the Play build).
        scope.launch { SelfUpdate.periodic(this@SwitchService) }
    }

    /** The master mouse came from another computer: show where the pointer is. */
    private fun arrived() {
        val locate = shared.config.value.locate
        if (locate.on && locate.onArrival) PointerService.instance?.locateOnNextMove()
    }

    /** Shows or hides the edge strips for these settings and tells the screen whether
     *  the "display over other apps" permission is still needed. */
    private fun syncEdges(watcher: EdgeWatcher, cfg: Config) {
        watcher.sync(cfg)
        shared.setStatus { it.copy(overlayNeeded = cfg.edges.any() && !watcher.allowed()) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The screen starts the service again when it comes to the front: the overlay
        // permission may have been granted meanwhile.
        edges?.let { syncEdges(it, shared.config.value) }
        when (intent?.action) {
            ACTION_SWITCH -> shared.requestSwitch(intent.getIntExtra(EXTRA_CHANNEL, 0))
            ACTION_LOCATE -> PointerService.instance?.locateOnNextMove()
            ACTION_STOP -> {
                shared.updateConfig { it.copy(enabled = false) }
                stopSelf()
            }
        }
        return START_STICKY
    }

    /** Runs the engine; if it ends with an unexpected error, starts it again shortly.
     *  An engine of an earlier run may still be handing buttons back and releasing the
     *  receiver: wait for it first, or the receiver could not be opened. */
    private fun loop(previous: Thread?, stop: java.util.concurrent.atomic.AtomicBoolean) {
        try {
            previous?.join()
            if (stop.get()) return
            shared.setStatus { it.copy(running = true) }
            AndroidSource(this, shared).use { source ->
                while (!stop.get()) {
                    try {
                        Engine(shared, source, stop).run()
                    } catch (e: InterruptedException) {
                        break
                    } catch (e: Exception) {
                        Log.w("engine stopped: $e")
                        try {
                            Thread.sleep(2000)
                        } catch (_: InterruptedException) {
                            break
                        }
                    }
                }
            }
        } finally {
            // Only the latest run speaks for whether flockswitch is running.
            if (engineThread == Thread.currentThread()) shared.setStatus { it.copy(running = false) }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        if (edges != null) runCatching { unregisterReceiver(screenChanged) }
        edges?.hide()
        edges = null
        // The engine hands the buttons back on its way out, on its own thread; the next
        // run waits for it (see loop).
        if (thread != null) shared.setStopped(true)
        thread = null
        super.onDestroy()
    }

    private fun notification(s: Status, c: Config): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val text = when {
            !c.enabled -> getString(R.string.status_paused)
            s.trigger != null && s.channel != null -> getString(R.string.notification_here, s.trigger, s.channel)
            s.trigger != null -> s.trigger
            s.receivers == 0 -> getString(R.string.status_no_receiver)
            else -> getString(R.string.status_searching)
        }
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (c.enabled && s.trigger != null) {
            for (channel in 1..3) {
                if (channel == s.channel) continue
                b.addAction(0, getString(R.string.go_to_channel, channel), switchIntent(this, channel))
            }
            if (c.locate.on && s.pointerService && pointerReadable) {
                val intent = Intent(this, SwitchService::class.java).setAction(ACTION_LOCATE)
                b.addAction(0, getString(R.string.locate_action), PendingIntent.getService(this, LOCATE_REQUEST, intent, PendingIntent.FLAG_IMMUTABLE))
            }
        }
        return b.build()
    }

    companion object {
        private const val CHANNEL = "service"
        private const val NOTIFICATION_ID = 1

        /** The engine thread of the latest run. */
        @Volatile
        private var engineThread: Thread? = null
        const val ACTION_SWITCH = "io.github.wwhalefe.flockswitch.SWITCH"
        const val ACTION_STOP = "io.github.wwhalefe.flockswitch.STOP"
        const val ACTION_LOCATE = "io.github.wwhalefe.flockswitch.LOCATE"
        private const val LOCATE_REQUEST = 100
        const val EXTRA_CHANNEL = "channel"

        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, SwitchService::class.java))
            } catch (e: Exception) {
                // Not allowed from the background right now; the next receiver plug-in or
                // a visit to the app starts it.
                Log.w("cannot start the background service: ${e.message}")
            }
        }

        fun stop(context: Context) = context.stopService(Intent(context, SwitchService::class.java))

        fun switchIntent(context: Context, channel: Int): PendingIntent = PendingIntent.getService(
            context,
            channel,
            Intent(context, SwitchService::class.java).setAction(ACTION_SWITCH).putExtra(EXTRA_CHANNEL, channel),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
