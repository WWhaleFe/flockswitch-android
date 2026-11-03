package io.github.wwhalefe.flockswitch

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

const val RELEASES_PAGE = "https://github.com/WWhaleFe/flockswitch-android/releases/latest"
private const val RELEASES_API = "https://api.github.com/repos/WWhaleFe/flockswitch-android/releases/latest"

/** Where the app is with updates (shown on the General tab). */
sealed class UpdateState {
    data object Unknown : UpdateState()
    data object Checking : UpdateState()
    data object UpToDate : UpdateState()
    data object Failed : UpdateState()
    data class Available(val version: String) : UpdateState()
}

/** Asks GitHub for the latest release. Blocking; call it off the main thread. Nothing is
 *  downloaded: a newer version only shows a link to its release page. */
fun checkForUpdate(current: String): UpdateState = try {
    val conn = URL(RELEASES_API).openConnection() as HttpURLConnection
    conn.connectTimeout = 10_000
    conn.readTimeout = 10_000
    conn.setRequestProperty("Accept", "application/vnd.github+json")
    conn.setRequestProperty("User-Agent", "flockswitch-android/$current")
    val body = conn.inputStream.bufferedReader().use { it.readText() }
    val tag = Json.parseToJsonElement(body).jsonObject["tag_name"]!!.jsonPrimitive.content
    val latest = tag.removePrefix("v")
    if (isNewer(latest, current)) UpdateState.Available(latest) else UpdateState.UpToDate
} catch (e: Exception) {
    Log.i("update check failed: $e")
    UpdateState.Failed
}

/** Compares dotted version numbers ("0.10.0" is newer than "0.9.1"). */
fun isNewer(latest: String, current: String): Boolean {
    val a = latest.split('.').map { it.toIntOrNull() ?: 0 }
    val b = current.split('.').map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}
