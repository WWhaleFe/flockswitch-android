package io.github.wwhalefe.flockswitch

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

const val RELEASES_PAGE = "https://github.com/WWhaleFe/flockswitch-android/releases/latest"
private const val RELEASES_API = "https://api.github.com/repos/WWhaleFe/flockswitch-android/releases/latest"

/** The checksum list published next to each APK (see .github/workflows/release.yml). */
const val SUMS_NAME = "SHA256SUMS.txt"

/** The APK of a release, as the release workflow names it. */
fun apkName(version: String) = "flockswitch-android-$version.apk"

/** Why an update did not go through. */
enum class UpdateFailure { Download, Checksum, Install, Cancelled }

/** Where the app is with updates (shown on the General tab). */
sealed class UpdateState {
    data object Unknown : UpdateState()
    data object Checking : UpdateState()
    data object UpToDate : UpdateState()
    data object Failed : UpdateState()
    data class Available(val version: String) : UpdateState()
    /** Fetching the APK; [percent] is null while the size is not known. */
    data class Downloading(val version: String, val percent: Int?) : UpdateState()
    data class Installing(val version: String) : UpdateState()
    /** Android wants the user to confirm the install (its prompt or a notification). */
    data class Confirming(val version: String) : UpdateState()
    data class InstallFailed(val version: String, val why: UpdateFailure) : UpdateState()

    /** Something is under way: checking, downloading or installing. */
    fun busy() = this is Checking || this is Downloading || this is Installing
}

/** The latest release on GitHub: its version and where its APK and checksums are. */
data class Release(val version: String, val apkUrl: String?, val sumsUrl: String?)

/** Reads the answer of GitHub's latest-release API. */
fun parseRelease(body: String): Release {
    val root = Json.parseToJsonElement(body).jsonObject
    val version = root["tag_name"]!!.jsonPrimitive.content.removePrefix("v")
    val assets = (root["assets"]?.jsonArray ?: emptyList()).mapNotNull { it as? JsonObject }
    fun url(name: String) = assets.firstOrNull { it["name"]?.jsonPrimitive?.content == name }
        ?.get("browser_download_url")?.jsonPrimitive?.content
    return Release(version, url(apkName(version)), url(SUMS_NAME))
}

/** Asks GitHub for the latest release. Blocking; call it off the main thread. */
fun latestRelease(current: String): Release {
    val conn = URL(RELEASES_API).openConnection() as HttpURLConnection
    conn.connectTimeout = 10_000
    conn.readTimeout = 10_000
    conn.setRequestProperty("Accept", "application/vnd.github+json")
    conn.setRequestProperty("User-Agent", "flockswitch-android/$current")
    try {
        return parseRelease(conn.inputStream.bufferedReader().use { it.readText() })
    } finally {
        conn.disconnect()
    }
}

/** Asks GitHub whether a newer version exists. Blocking; call it off the main thread. */
fun checkForUpdate(current: String): UpdateState = try {
    val latest = latestRelease(current).version
    if (isNewer(latest, current)) UpdateState.Available(latest) else UpdateState.UpToDate
} catch (e: Exception) {
    Log.i("update check failed: $e")
    UpdateState.Failed
}

/** Reads `sha256sum` output: "<64 hex digits>  <file name>" per line (a `*` before the
 *  name marks binary mode). File name → lower-case digest; other lines are skipped. */
fun parseSha256Sums(text: String): Map<String, String> {
    val out = mutableMapOf<String, String>()
    for (raw in text.lineSequence()) {
        val line = raw.trim()
        val digest = line.substringBefore(' ', "")
        if (digest.length != 64 || !digest.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) continue
        val name = line.substring(digest.length).trimStart().removePrefix("*").trim()
        if (name.isNotEmpty()) out[name.substringAfterLast('/')] = digest.lowercase()
    }
    return out
}

/** The SHA-256 of a file, as lower-case hex. */
fun sha256(file: File): String {
    val md = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
        }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}

/** Whether [file] is the [name] listed in the checksums [sums] (text of SHA256SUMS.txt). */
fun matchesSums(file: File, name: String, sums: String): Boolean {
    val want = parseSha256Sums(sums)[name] ?: return false
    return sha256(file) == want
}

/** Compares dotted version numbers ("0.10.0" is newer than "0.9.1"). A part like "0-beta"
 *  counts by its leading digits. */
fun isNewer(latest: String, current: String): Boolean {
    fun parts(v: String) = v.trim().removePrefix("v").split('.').map { p -> p.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    val a = parts(latest)
    val b = parts(current)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}
