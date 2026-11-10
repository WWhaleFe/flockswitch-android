// The GitHub (sideload) build updates itself: it looks for a new release, downloads its
// APK into the cache, checks it against the release's SHA256SUMS.txt and hands it to
// Android's PackageInstaller. This file exists only in that build (see app/build.gradle.kts);
// the Google Play build has an empty stand-in under src/play.

package io.github.wwhalefe.flockswitch

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.IntentCompat
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

object SelfUpdate {
    /** Whether this build looks for, downloads and installs new versions itself. */
    const val SUPPORTED = true

    private const val HOUR = 3_600_000L
    private const val DAY = 24 * HOUR
    /** An APK larger than this is not what the release workflow makes. */
    private const val MAX_APK = 200L * 1024 * 1024
    private const val CHANNEL = "updates"
    private const val NOTIFICATION_ID = 2
    const val ACTION_STATUS = "io.github.wwhalefe.flockswitch.INSTALL_STATUS"
    const val EXTRA_VERSION = "version"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** One check or download at a time. */
    private val working = AtomicBoolean(false)

    /** When GitHub was last asked (this run of the app). */
    @Volatile
    private var lastCheck = 0L

    /** The release found by the last check. */
    @Volatile
    private var release: Release? = null

    /** Android's own install prompt for a committed session, while it waits for the user. */
    @Volatile
    private var confirmation: Intent? = null

    /** The version that [confirmation] installs. */
    @Volatile
    private var confirming: String? = null

    /** The prompt was shown already, so coming back to the app does not show it again. */
    @Volatile
    private var confirmationShown = false

    /** A version whose install the user cancelled: not installed again on its own. */
    @Volatile
    private var declined: String? = null

    /** Checks on its own (app or service start, once a day) unless turned off or done
     *  less than [minGapMs] ago. */
    fun autoCheck(context: Context, minGapMs: Long = HOUR) {
        val app = context.applicationContext
        if (!app.shared.config.value.checkUpdates) return
        if (System.currentTimeMillis() - lastCheck < minGapMs) return
        // Waiting for the user to confirm: nothing new to do until they answer.
        if (confirmation != null) return
        work { checkAndMaybeInstall(app, manual = false) }
    }

    /** "Check now". */
    fun check(context: Context) {
        val app = context.applicationContext
        work { checkAndMaybeInstall(app, manual = true) }
    }

    /** "Update now": show Android's prompt if one is waiting, else download and install. */
    fun updateNow(context: Context) {
        val app = context.applicationContext
        confirmation?.let { if (showConfirmation(app, it)) return }
        work {
            val r = release?.takeIf { isNewer(it.version, BuildConfig.VERSION_NAME) } ?: fetch(app) ?: return@work
            if (isNewer(r.version, BuildConfig.VERSION_NAME)) install(app, r) else app.shared.setUpdate(UpdateState.UpToDate)
        }
    }

    /** The app came to the front: show a prompt that only made it into a notification. */
    fun onForeground(activity: Activity) {
        val confirm = confirmation ?: return
        if (confirmationShown) return
        showConfirmation(activity.applicationContext, confirm)
    }

    /** For the background service: a check at its start and then once a day. Waits in
     *  steps of an hour, since a long wait stops counting while the device sleeps. */
    suspend fun periodic(context: Context) {
        autoCheck(context, HOUR)
        while (true) {
            delay(HOUR)
            autoCheck(context, DAY)
        }
    }

    private fun work(block: () -> Unit) {
        if (!working.compareAndSet(false, true)) return
        scope.launch {
            try {
                block()
            } catch (e: Exception) {
                Log.w("update: $e")
            } finally {
                working.set(false)
            }
        }
    }

    /** Asks GitHub for the latest release; null (and the screen says so) if it cannot. */
    private fun fetch(app: Context): Release? {
        lastCheck = System.currentTimeMillis()
        app.shared.setUpdate(UpdateState.Checking)
        return try {
            latestRelease(BuildConfig.VERSION_NAME).also { release = it }
        } catch (e: Exception) {
            Log.i("update check failed: $e")
            app.shared.setUpdate(UpdateState.Failed)
            null
        }
    }

    private fun checkAndMaybeInstall(app: Context, manual: Boolean) {
        val r = fetch(app) ?: return
        if (!isNewer(r.version, BuildConfig.VERSION_NAME)) {
            Log.i("update check: ${BuildConfig.VERSION_NAME} is the latest (GitHub has ${r.version})")
            app.shared.setUpdate(UpdateState.UpToDate)
            return
        }
        Log.i("update check: version ${r.version} is available")
        // Already handed to Android and waiting for the user: not a second time.
        if (confirmation != null && confirming == r.version) return app.shared.setUpdate(UpdateState.Confirming(r.version))
        app.shared.setUpdate(UpdateState.Available(r.version))
        val cfg = app.shared.config.value
        if (!cfg.autoUpdate) return
        if (!manual && declined == r.version) {
            Log.i("update: ${r.version} was cancelled before, not installing it on its own")
            return
        }
        install(app, r)
    }

    private fun fail(app: Context, version: String, why: UpdateFailure) =
        app.shared.setUpdate(UpdateState.InstallFailed(version, why))

    /** Downloads, verifies and installs [r]. Never installs an APK whose SHA-256 is not
     *  the one in the release's SHA256SUMS.txt. */
    private fun install(app: Context, r: Release) {
        val v = r.version
        val name = apkName(v)
        if (r.apkUrl == null || r.sumsUrl == null) {
            Log.w("update: release $v has no ${if (r.apkUrl == null) name else SUMS_NAME}")
            return fail(app, v, UpdateFailure.Download)
        }
        val dir = File(app.cacheDir, "update").apply { mkdirs() }
        dir.listFiles()?.filter { it.name != name }?.forEach { it.delete() }
        val apk = File(dir, name)
        app.shared.setUpdate(UpdateState.Downloading(v, null))
        try {
            val sums = String(fetchBytes(r.sumsUrl, 64 * 1024), Charsets.UTF_8)
            val want = parseSha256Sums(sums)[name]
            if (want == null) {
                Log.w("update: $SUMS_NAME of $v lists no $name")
                return fail(app, v, UpdateFailure.Checksum)
            }
            // A download verified earlier (an install that was cancelled) is used again.
            if (!(apk.isFile && sha256(apk) == want)) {
                download(r.apkUrl, apk) { percent -> app.shared.setUpdate(UpdateState.Downloading(v, percent)) }
            }
            val got = sha256(apk)
            if (got != want) {
                Log.w("update: $name does not match $SUMS_NAME (expected $want, got $got); not installing")
                apk.delete()
                return fail(app, v, UpdateFailure.Checksum)
            }
            Log.i("update: $name downloaded, SHA-256 $got matches $SUMS_NAME")
        } catch (e: Exception) {
            Log.w("update: download of $v failed: $e")
            apk.delete()
            return fail(app, v, UpdateFailure.Download)
        }

        // Android checks the signature itself; this catches a wrong file early.
        @Suppress("DEPRECATION")
        val info = app.packageManager.getPackageArchiveInfo(apk.path, 0)
        val code = info?.let { PackageInfoCompat.getLongVersionCode(it) }
        if (info == null || info.packageName != app.packageName || code == null || code <= BuildConfig.VERSION_CODE) {
            Log.w("update: $name is not a newer flockswitch (package ${info?.packageName}, version code $code)")
            apk.delete()
            return fail(app, v, UpdateFailure.Install)
        }

        app.shared.setUpdate(UpdateState.Installing(v))
        commit(app, apk, v)
    }

    private fun commit(app: Context, apk: File, v: String) {
        val installer = app.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(app.packageName)
            setSize(apk.length())
            setInstallReason(PackageManager.INSTALL_REASON_USER)
            // Without asking where Android allows it: an app updating itself that may
            // install packages (Android 12+). Otherwise the status below asks the user.
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        var id = -1
        try {
            id = installer.createSession(params)
            installer.openSession(id).use { session ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val intent = Intent(app, InstallResultReceiver::class.java).setAction(ACTION_STATUS).putExtra(EXTRA_VERSION, v)
                // Mutable: the installer adds the status to it.
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                session.commit(PendingIntent.getBroadcast(app, id, intent, flags).intentSender)
            }
            Log.i("update: install session $id for $v committed")
        } catch (e: Exception) {
            Log.w("update: cannot install $v: $e")
            if (id >= 0) runCatching { installer.abandonSession(id) }
            fail(app, v, UpdateFailure.Install)
        }
    }

    /** What PackageInstaller says about a committed session. */
    fun onStatus(context: Context, intent: Intent) {
        val app = context.applicationContext
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val v = intent.getStringExtra(EXTRA_VERSION) ?: "?"
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm == null) {
                    Log.w("update: Android wants a confirmation for $v but gave no prompt")
                    return fail(app, v, UpdateFailure.Install)
                }
                Log.i("update: Android asks the user to confirm installing $v")
                confirmation = confirm
                confirming = v
                confirmationShown = false
                app.shared.setUpdate(UpdateState.Confirming(v))
                if (!(MainActivity.visible && showConfirmation(app, confirm))) notifyReady(app, confirm, v)
            }
            PackageInstaller.STATUS_SUCCESS -> {
                // Usually not seen: Android stops the app as it replaces it.
                Log.i("update: version $v installed")
                done(app)
                app.shared.setUpdate(UpdateState.UpToDate)
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                Log.i("update: installing $v was cancelled ($message)")
                declined = v
                done(app)
                fail(app, v, UpdateFailure.Cancelled)
            }
            else -> {
                Log.w("update: installing $v failed, status $status ($message)")
                done(app)
                fail(app, v, UpdateFailure.Install)
            }
        }
    }

    private fun done(app: Context) {
        confirmation = null
        confirming = null
        confirmationShown = false
        NotificationManagerCompat.from(app).cancel(NOTIFICATION_ID)
    }

    /** Opens Android's install prompt; false if it could not be opened. */
    private fun showConfirmation(app: Context, confirm: Intent): Boolean = try {
        app.startActivity(Intent(confirm).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        confirmationShown = true
        NotificationManagerCompat.from(app).cancel(NOTIFICATION_ID)
        true
    } catch (e: Exception) {
        Log.w("update: cannot show the install prompt: $e")
        false
    }

    /** In the background Android's prompt cannot be opened: a notification opens it. */
    private fun notifyReady(app: Context, confirm: Intent, v: String) {
        val nm = app.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, app.getString(R.string.update_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(app, NOTIFICATION_ID, confirm, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${app.getString(R.string.app_name)} $v")
            .setContentText(app.getString(R.string.update_ready))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            if (NotificationManagerCompat.from(app).areNotificationsEnabled()) {
                NotificationManagerCompat.from(app).notify(NOTIFICATION_ID, n)
                Log.i("update: notification posted for $v")
            } else {
                Log.w("update: notifications are off; $v waits for the app to be opened")
            }
        } catch (e: SecurityException) {
            Log.w("update: cannot post the notification: $e")
        }
    }

    private fun open(url: String): HttpURLConnection {
        require(url.startsWith("https://")) { "not https: $url" }
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("User-Agent", "flockswitch-android/${BuildConfig.VERSION_NAME}")
        if (conn.responseCode !in 200..299) {
            val code = conn.responseCode
            conn.disconnect()
            throw IOException("HTTP $code for $url")
        }
        return conn
    }

    private fun fetchBytes(url: String, limit: Int): ByteArray {
        val conn = open(url)
        try {
            val bytes = conn.inputStream.use { it.readNBytesCompat(limit + 1) }
            if (bytes.size > limit) throw IOException("$url is too large")
            return bytes
        } finally {
            conn.disconnect()
        }
    }

    private fun download(url: String, to: File, progress: (Int?) -> Unit) {
        val conn = open(url)
        val part = File(to.path + ".part")
        try {
            val total = conn.contentLengthLong
            if (total > MAX_APK) throw IOException("$url is too large ($total bytes)")
            var got = 0L
            var shown = -1
            conn.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        got += n
                        if (got > MAX_APK) throw IOException("$url is too large")
                        out.write(buf, 0, n)
                        val percent = if (total > 0) (got * 100 / total).toInt() else null
                        if (percent == null || percent != shown) {
                            if (percent != null) shown = percent
                            progress(percent)
                        }
                    }
                }
            }
            if (total > 0 && got != total) throw IOException("download ended after $got of $total bytes")
            to.delete()
            if (!part.renameTo(to)) throw IOException("cannot move the download into place")
        } finally {
            part.delete()
            conn.disconnect()
        }
    }

    private fun java.io.InputStream.readNBytesCompat(n: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (out.size() < n) {
            val r = read(buf, 0, minOf(buf.size, n - out.size()))
            if (r < 0) break
            out.write(buf, 0, r)
        }
        return out.toByteArray()
    }
}

/** Receives the status of an install session (see [SelfUpdate.onStatus]). */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != SelfUpdate.ACTION_STATUS) return
        SelfUpdate.onStatus(context, intent)
    }
}
