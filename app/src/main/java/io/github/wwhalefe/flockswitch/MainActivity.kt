package io.github.wwhalefe.flockswitch

import android.Manifest
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import io.github.wwhalefe.flockswitch.hidpp.UsbTransport
import io.github.wwhalefe.flockswitch.ui.Actions
import io.github.wwhalefe.flockswitch.ui.FlockswitchApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import java.time.LocalDateTime

class MainActivity : ComponentActivity() {
    private var preview: EdgeOverlay? = null
    private var locatePreview: LocateOverlay? = null
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val askNearby = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && shared.config.value.enabled) SwitchService.start(this)
    }
    /** The system's file picker: where to save the settings, and which file to load. */
    private val saveFile = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.data?.takeIf { r.resultCode == RESULT_OK }?.let(::writeSettings)
    }
    private val loadFile = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.data?.takeIf { r.resultCode == RESULT_OK }?.let(::readSettings)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val actions = Actions(
            setEnabled = ::setEnabled,
            allowUsb = { allowUsb(again = true) },
            allowNearby = { if (Build.VERSION.SDK_INT >= 31) askNearby.launch(Manifest.permission.BLUETOOTH_CONNECT) },
            allowOverlay = ::allowOverlay,
            previewEdge = ::previewEdge,
            previewLocate = ::previewLocate,
            openAccessibility = ::openAccessibility,
            openAppInfo = { runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) } },
            overlayOn = { Settings.canDrawOverlays(this) },
            notificationsOn = { NotificationManagerCompat.from(this).areNotificationsEnabled() },
            switchTo = { channel -> shared.requestSwitch(channel) },
            open = { url -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
            report = ::report,
            feel = { what, intensity -> shared.requestFeel(what, intensity) },
            checkUpdate = { SelfUpdate.check(this) },
            updateNow = { SelfUpdate.updateNow(this) },
            saveSettings = ::saveSettings,
            loadSettings = { pickSettings(lastSaved() ?: downloads()) },
            openSettingsFolder = ::openSettingsFolder,
            applySettings = ::applySettings,
            refreshSaved = { refreshSaved() },
            renameSaved = ::renameSaved,
            deleteSaved = ::deleteSaved,
            forgetSaved = ::forgetSaved,
        )
        setContent { FlockswitchApp(shared, actions) }
        SelfUpdate.autoCheck(this)
    }

    override fun onStart() {
        super.onStart()
        visible = true
    }

    override fun onStop() {
        visible = false
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // An update Android wanted confirmed while the app was in the background.
        SelfUpdate.onForeground(this)
        // Ask for any receiver plugged in that may not be used yet; then (or if all is
        // allowed already) make sure the background service runs.
        allowUsb(again = false)
        if (shared.config.value.enabled) SwitchService.start(this)
        // Files deleted elsewhere drop off the list of saved settings.
        refreshSaved()
    }

    override fun onDestroy() {
        preview?.end()
        locatePreview?.end()
        super.onDestroy()
    }

    private fun allowOverlay() {
        runCatching { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
    }

    /** Opens flockswitch's own accessibility page where Android has one, else the list. */
    private fun openAccessibility() {
        if (Build.VERSION.SDK_INT >= 31) {
            val service = ComponentName(this, PointerService::class.java).flattenToString()
            // Not in the public SDK; where it does not exist the list opens below.
            val details = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").putExtra(Intent.EXTRA_COMPONENT_NAME, service)
            if (runCatching { startActivity(details) }.isSuccess) return
        }
        runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
    }

    /** Plays the countdown once, for the settings. */
    private fun previewEdge(edges: Edges) {
        if (!Settings.canDrawOverlays(this)) return allowOverlay()
        (preview ?: EdgeOverlay(this).also { preview = it }).preview(edges)
    }

    /** Plays the highlight once at this point, for the settings: through the accessibility
     *  service if it is on, otherwise in a window that needs "display over other apps". */
    private fun previewLocate(locate: Locate, x: Float, y: Float) {
        PointerService.instance?.let { return it.preview(locate, x, y) }
        if (!Settings.canDrawOverlays(this)) return allowOverlay()
        val overlay = locatePreview ?: LocateOverlay(applicationContext, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY).also { locatePreview = it }
        overlay.show(locate, x, y)
    }

    private fun granted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun setEnabled(on: Boolean) {
        shared.updateConfig { it.copy(enabled = on) }
        if (on) SwitchService.start(this) else SwitchService.stop(this)
    }

    /** Asks for access to the receivers plugged in. On its own (when the screen opens)
     *  each receiver is asked about once, so a "no" is not asked again every time. */
    private fun allowUsb(again: Boolean) {
        val usb = getSystemService(UsbManager::class.java) ?: return
        usb.deviceList.values
            .filter { UsbTransport.isReceiver(it) && !usb.hasPermission(it) }
            .filter { again || asked.add(it.deviceName) }
            .forEach { requestUsbPermission(it) }
    }

    // Settings to another device: saved and loaded through the system's file picker
    // (Storage Access Framework), so no storage permission is needed.

    /** Asks where to save the settings, starting where the last ones went; with
     *  [SettingsFile.MAX_SAVED] files saved already, asks to tidy up instead. */
    private fun saveSettings() = refreshSaved { list ->
        if (!SettingsFile.canSave(list.size)) return@refreshSaved shared.setBackup(BackupState.Full)
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(SettingsFile.MIME)
            .putExtra(Intent.EXTRA_TITLE, SettingsFile.fileName(Build.MODEL, LocalDateTime.now()))
        (lastSaved() ?: downloads())?.let { intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) }
        launch(saveFile, intent)
    }

    private fun writeSettings(uri: Uri) {
        val header = SettingsFile.header(BuildConfig.VERSION_NAME, "${Build.MANUFACTURER} ${Build.MODEL}", LocalDateTime.now())
        val text = SettingsFile.write(shared.config.value, header)
        try {
            val out = contentResolver.openOutputStream(uri) ?: throw IOException("no stream for $uri")
            out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        } catch (e: Exception) {
            Log.w("cannot save the settings to a file: $e")
            return shared.setBackup(BackupState.SaveFailed)
        }
        prefs().edit { putString(LAST_SAVED, uri.toString()) }
        // Kept, so the file can be renamed or deleted from the list later.
        runCatching { contentResolver.takePersistableUriPermission(uri, RW) }
            .onFailure { Log.w("no lasting access to the saved file: $it") }
        val name = displayName(uri)
        keepSaved(SettingsFile.added(savedList(), SavedFile(uri.toString(), name, System.currentTimeMillis())))
        Log.i("settings saved to $name")
        shared.setBackup(BackupState.Saved(name))
    }

    // The settings files saved from here, kept as a list (at most SettingsFile.MAX_SAVED)
    // with lasting access to each, to rename or delete them. Changed on the main thread
    // only; the file providers are asked on another.

    private fun savedList(): List<SavedFile> = SettingsFile.sorted(SettingsFile.decodeList(prefs().getString(SAVED, null)))

    private fun keepSaved(list: List<SavedFile>) {
        val sorted = SettingsFile.sorted(list)
        prefs().edit { putString(SAVED, SettingsFile.encodeList(sorted)) }
        shared.setSaved(sorted)
    }

    /** Whether a document is there: its current name, null when it is gone, or
     *  [UNKNOWN] when that cannot be told (access lost, the provider failing). */
    private fun lookUp(uri: Uri): String? = try {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) ?: UNKNOWN else null
        }
    } catch (_: FileNotFoundException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: Exception) {
        UNKNOWN
    }

    /** Takes files that are gone off the list and picks up names changed elsewhere, then
     *  gives the list to [then]. */
    private fun refreshSaved(then: ((List<SavedFile>) -> Unit)? = null) {
        val before = savedList()
        shared.setSaved(before)
        lifecycleScope.launch {
            val found = withContext(Dispatchers.IO) { before.associate { it.uri to lookUp(Uri.parse(it.uri)) } }
            // The list may have changed meanwhile: only what was looked at is touched.
            val now = savedList()
            val next = now.mapNotNull { f ->
                if (f.uri !in found) return@mapNotNull f
                when (val name = found[f.uri]) {
                    null -> {
                        release(f)
                        null
                    }
                    UNKNOWN -> f
                    else -> f.copy(name = name)
                }
            }
            if (next != now) keepSaved(next)
            then?.invoke(next)
        }
    }

    private fun release(f: SavedFile) {
        runCatching { contentResolver.releasePersistableUriPermission(Uri.parse(f.uri), RW) }
    }

    /** Takes [f] off the list (the file stays where it is). */
    private fun forgetSaved(f: SavedFile) {
        release(f)
        val rest = savedList().filter { it.uri != f.uri }
        keepSaved(rest)
        if (prefs().getString(LAST_SAVED, null) == f.uri) {
            prefs().edit { rest.firstOrNull()?.let { putString(LAST_SAVED, it.uri) } ?: remove(LAST_SAVED) }
        }
    }

    /** Renames a saved file to [typed] (checked on the screen), keeping ".json". */
    private fun renameSaved(f: SavedFile, typed: String, done: (ManageOutcome) -> Unit) {
        val newName = SettingsFile.renamedFileName(typed)
        val uri = Uri.parse(f.uri)
        lifecycleScope.launch {
            val (renamed, name, gone) = withContext(Dispatchers.IO) {
                val to = try {
                    DocumentsContract.renameDocument(contentResolver, uri, newName)
                } catch (e: Exception) {
                    Log.w("cannot rename ${f.name}: $e")
                    null
                }
                if (to != null) {
                    // A provider whose document IDs follow the name gives a new document,
                    // with access to it for now; keep that access if it lets us.
                    if (to != uri) runCatching { contentResolver.takePersistableUriPermission(to, RW) }
                    Triple(to, lookUp(to)?.takeIf { it != UNKNOWN } ?: newName, false)
                } else {
                    Triple(null, null, lookUp(uri) == null)
                }
            }
            when {
                renamed != null -> {
                    if (renamed != uri) release(f)
                    keepSaved(savedList().map { if (it.uri == f.uri) it.copy(uri = renamed.toString(), name = name ?: newName) else it })
                    if (prefs().getString(LAST_SAVED, null) == f.uri) prefs().edit { putString(LAST_SAVED, renamed.toString()) }
                    Log.i("settings file ${f.name} renamed to ${name ?: newName}")
                    done(ManageOutcome.Done)
                }
                gone -> {
                    forgetSaved(f)
                    done(ManageOutcome.Gone)
                }
                else -> done(ManageOutcome.Failed)
            }
        }
    }

    /** Deletes a saved file from the device, and from the list. */
    private fun deleteSaved(f: SavedFile, done: (ManageOutcome) -> Unit) {
        val uri = Uri.parse(f.uri)
        lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                val deleted = try {
                    DocumentsContract.deleteDocument(contentResolver, uri)
                } catch (e: Exception) {
                    Log.w("cannot delete ${f.name}: $e")
                    false
                }
                when {
                    deleted -> ManageOutcome.Done
                    lookUp(uri) == null -> ManageOutcome.Gone
                    else -> ManageOutcome.Failed
                }
            }
            if (outcome != ManageOutcome.Failed) forgetSaved(f)
            if (outcome == ManageOutcome.Done) Log.i("settings file ${f.name} deleted")
            done(outcome)
        }
    }

    /** The system's file picker for a settings file, opened at [at] where it can be. */
    private fun pickSettings(at: Uri?) {
        // Some file managers call a .json file plain text or just bytes.
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
            .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(SettingsFile.MIME, "text/plain", "application/octet-stream"))
        at?.let { intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) }
        if (!launch(loadFile, intent)) runCatching { startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) }
    }

    /** "Open folder". File managers (Samsung's My Files, Files by Google, others) cannot
     *  all be told which folder to show, but every one opens Downloads for
     *  [DownloadManager.ACTION_VIEW_DOWNLOADS], and Downloads is where saving starts. So:
     *  Downloads when the last file went there (or none was saved yet); for any other
     *  folder, the system's file picker opened at that file, which shows the folder it is
     *  in on every device (a file chosen there is loaded, after asking). */
    private fun openSettingsFolder() {
        val last = lastSaved()
        if (last == null || inDownloads(last)) {
            if (runCatching { startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) }.isSuccess) return
        }
        pickSettings(last ?: downloads())
    }

    /** Whether a saved file is in the Downloads folder itself. */
    private fun inDownloads(uri: Uri): Boolean {
        val id = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return false
        return when (uri.authority) {
            // "raw:/storage/emulated/0/Download/x.json", or a MediaStore number.
            DOWNLOADS_AUTHORITY -> !id.startsWith("raw:") ||
                java.io.File(id.removePrefix("raw:")).parentFile?.name.equals(Environment.DIRECTORY_DOWNLOADS, ignoreCase = true)
            // "primary:Download/x.json"
            "com.android.externalstorage.documents" ->
                id.substringAfter(':').substringBeforeLast('/', "").equals(Environment.DIRECTORY_DOWNLOADS, ignoreCase = true)
            else -> false
        }
    }

    private fun readSettings(uri: Uri) {
        val name = displayName(uri)
        val bytes = try {
            val input = contentResolver.openInputStream(uri) ?: throw IOException("no stream for $uri")
            input.use { readAtMost(it, SettingsFile.MAX_BYTES + 1) }
        } catch (e: Exception) {
            Log.w("cannot read the settings file $name: $e")
            return shared.setBackup(BackupState.Rejected(null))
        }
        if (bytes.size > SettingsFile.MAX_BYTES) return shared.setBackup(BackupState.Rejected(ImportRejection.TooLarge))
        when (val r = SettingsFile.read(String(bytes, Charsets.UTF_8))) {
            is ImportResult.Ok -> shared.setBackup(BackupState.Confirm(r.config, r.from, name))
            is ImportResult.Rejected -> {
                Log.i("settings file $name refused: ${r.why} ${r.detail ?: ""}")
                shared.setBackup(BackupState.Rejected(r.why, r.detail))
            }
        }
    }

    /** Puts every loaded setting in place, and starts or stops what they turn on or off. */
    private fun applySettings(cfg: Config, name: String) {
        if (!shared.updateConfig { cfg }) return shared.setBackup(BackupState.Rejected(ImportRejection.Unusable))
        Log.i("settings loaded from $name")
        setEnabled(cfg.enabled)
        if (cfg.bluetooth && Build.VERSION.SDK_INT >= 31 && !granted(Manifest.permission.BLUETOOTH_CONNECT)) {
            askNearby.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
        shared.setBackup(BackupState.Loaded(name))
    }

    private fun launch(launcher: androidx.activity.result.ActivityResultLauncher<Intent>, intent: Intent): Boolean = try {
        launcher.launch(intent)
        true
    } catch (e: ActivityNotFoundException) {
        Log.w("no file picker: $e")
        false
    }

    private fun prefs() = getSharedPreferences("backup", MODE_PRIVATE)

    /** The settings file saved last, if any. */
    private fun lastSaved(): Uri? = prefs().getString(LAST_SAVED, null)?.let(Uri::parse)

    /** The Downloads folder, as the system's file picker knows it. */
    private fun downloads(): Uri? = runCatching { DocumentsContract.buildDocumentUri(DOWNLOADS_AUTHORITY, "downloads") }.getOrNull()

    private fun displayName(uri: Uri): String = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment ?: "?"

    private fun readAtMost(input: java.io.InputStream, limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (out.size() < limit) {
            val n = input.read(buf, 0, minOf(buf.size, limit - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun report() {
        val s = shared.status.value
        val body = buildString {
            appendLine()
            appendLine()
            appendLine("---")
            appendLine("flockswitch for Android ${BuildConfig.VERSION_NAME}")
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("master: ${s.trigger ?: "-"}, channel: ${s.channel ?: "-"}, receivers: ${s.receivers}")
            appendLine("devices: " + s.devices.joinToString { "${it.name} [${it.key}]" })
            appendLine()
            Log.recent().takeLast(60).forEach { appendLine(it) }
        }
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(REPORT_ADDRESS))
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.report_subject))
            putExtra(Intent.EXTRA_TEXT, body)
        }
        runCatching { startActivity(intent) }
    }

    companion object {
        /** Receivers asked about since the app started. */
        private val asked = mutableSetOf<String>()

        /** Where the last settings file went (a document URI). */
        private const val LAST_SAVED = "lastSaved"

        /** The settings files saved from here (JSON, see [SettingsFile.encodeList]). */
        private const val SAVED = "saved"

        /** [lookUp]: the document may be there, but cannot be asked about. */
        private const val UNKNOWN = "\u0000?"

        private const val RW = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        private const val DOWNLOADS_AUTHORITY = "com.android.providers.downloads.documents"

        /** The settings screen is on screen, so a system prompt may be opened. */
        @Volatile
        var visible = false
            private set
        const val REPORT_ADDRESS = "feedback.wwhale@gmail.com"
        const val ISSUES_PAGE = "https://github.com/WWhaleFe/flockswitch-android/issues/new"
    }
}
