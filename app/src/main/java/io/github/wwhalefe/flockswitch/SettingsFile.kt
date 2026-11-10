// A settings file to take a setup to another device: the settings as config.json has
// them, with a small header saying which app, system and version wrote them. Reading one
// goes through the same parsing and checks as the saved settings, but a file that does
// not hold usable flockswitch settings for Android is refused instead of giving the
// defaults.

package io.github.wwhalefe.flockswitch

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Who wrote a settings file. */
@Serializable
data class ExportHeader(
    val app: String = "",
    /** "android"; the desktop versions write their own name. */
    val os: String = "",
    val version: String = "",
    /** Maker and model of the device the settings came from. */
    val device: String = "",
    /** When they were saved, local time, ISO 8601. */
    val date: String = "",
)

/** Why a file was not loaded. */
enum class ImportRejection {
    /** Not JSON at all. */
    NotJson,
    /** JSON, but not a flockswitch settings file. */
    NotSettings,
    /** flockswitch settings of another system (a desktop version). */
    OtherOs,
    /** flockswitch settings that cannot be read or used here. */
    Unusable,
    /** Far larger than any settings file. */
    TooLarge,
}

/** What reading a settings file gave. */
sealed interface ImportResult {
    data class Ok(val config: Config, val from: ExportHeader) : ImportResult

    /** [detail]: for [ImportRejection.OtherOs] the system named in the file, for
     *  [ImportRejection.Unusable] what is wrong. */
    data class Rejected(val why: ImportRejection, val detail: String? = null) : ImportResult
}

object SettingsFile {
    /** The field that holds the [ExportHeader]; config.json has no field of that name. */
    const val HEADER = "export"
    const val APP = "flockswitch"
    const val OS = "android"
    const val MIME = "application/json"

    /** Settings are a few kilobytes; anything much larger is not a settings file. */
    const val MAX_BYTES = 256 * 1024

    /** The file's text: [cfg] with the header in front. */
    fun write(cfg: Config, header: ExportHeader): String {
        val settings = Config.json.encodeToJsonElement(Config.serializer(), cfg).jsonObject
        val head = Config.json.encodeToJsonElement(ExportHeader.serializer(), header)
        return Config.json.encodeToString(JsonObject.serializer(), JsonObject(mapOf(HEADER to head) + settings))
    }

    /** A header for settings saved now from this app. */
    fun header(version: String, device: String, at: LocalDateTime): ExportHeader =
        ExportHeader(APP, OS, version, device.trim(), at.withNano(0).toString())

    /** Reads a settings file; see [ImportResult]. */
    fun read(text: String): ImportResult {
        if (text.length > MAX_BYTES) return ImportResult.Rejected(ImportRejection.TooLarge)
        val root: JsonElement = try {
            Config.json.parseToJsonElement(text)
        } catch (_: Exception) {
            return ImportResult.Rejected(ImportRejection.NotJson)
        }
        val obj = root as? JsonObject ?: return ImportResult.Rejected(ImportRejection.NotSettings)
        val header = (obj[HEADER] as? JsonObject)
            ?.let { runCatching { Config.json.decodeFromJsonElement(ExportHeader.serializer(), it) }.getOrNull() }
            ?.takeIf { it.app.equals(APP, ignoreCase = true) }
            ?: return ImportResult.Rejected(ImportRejection.NotSettings)
        if (!header.os.equals(OS, ignoreCase = true)) return ImportResult.Rejected(ImportRejection.OtherOs, header.os.ifBlank { "?" })
        val cfg = try {
            Config.decode(JsonObject(obj - HEADER))
        } catch (e: Exception) {
            return ImportResult.Rejected(ImportRejection.Unusable, e.message?.lineSequence()?.firstOrNull())
        }
        val problems = cfg.problems()
        if (problems.isNotEmpty()) return ImportResult.Rejected(ImportRejection.Unusable, problems.joinToString("; "))
        return ImportResult.Ok(cfg, header)
    }

    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    /** "flockswitch-settings-<model>-<YYYYMMDD-HHMMSS>.json", the model reduced to what
     *  every file system takes. */
    fun fileName(model: String, at: LocalDateTime): String {
        val safe = model.trim().replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('-', '.').ifEmpty { "android" }
        return "flockswitch-settings-$safe-${at.format(stamp)}.json"
    }
}
