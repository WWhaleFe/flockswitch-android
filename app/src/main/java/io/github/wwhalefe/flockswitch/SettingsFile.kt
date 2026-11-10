// A settings file to take a setup to another device: the settings as config.json has
// them, with a small header saying which app, system and version wrote them. Reading one
// goes through the same parsing and checks as the saved settings, but a file that does
// not hold usable flockswitch settings for Android is refused instead of giving the
// defaults.

package io.github.wwhalefe.flockswitch

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
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

/** A settings file this app saved: its document (a content URI the app keeps access
 *  to), its name, and when it was saved (milliseconds since 1970). */
@Serializable
data class SavedFile(val uri: String, val name: String, val savedAt: Long)

/** Why a new name for a saved file cannot be used. */
enum class RenameProblem { Empty, TooLong, BadCharacter, Taken }

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

    /** How many saved files the app keeps track of; saving more asks to tidy up first. */
    const val MAX_SAVED = 10

    /** The longest name a saved file can be renamed to, without ".json". */
    const val MAX_NAME = 60

    /** Characters a new name may not hold: what some file system or another refuses. */
    const val FORBIDDEN = "/\\:*?\"<>|"

    private const val EXT = ".json"

    /** Whether another file can be saved next to [count] saved ones. */
    fun canSave(count: Int): Boolean = count < MAX_SAVED

    /** A file's name as the list shows it: without ".json". */
    fun shownName(fileName: String): String =
        if (fileName.endsWith(EXT, ignoreCase = true) && fileName.length > EXT.length) fileName.dropLast(EXT.length) else fileName

    /** What is wrong with [typed] as a new name; null when it can be used. [others]: the
     *  file names of the other saved files, which it may not repeat. */
    fun renameProblem(typed: String, others: List<String> = emptyList()): RenameProblem? {
        val name = stem(typed)
        return when {
            name.isEmpty() || name.all { it == '.' } -> RenameProblem.Empty
            name.length > MAX_NAME -> RenameProblem.TooLong
            name.any { it in FORBIDDEN || it.isISOControl() } -> RenameProblem.BadCharacter
            others.any { shownName(it).equals(name, ignoreCase = true) } -> RenameProblem.Taken
            else -> null
        }
    }

    /** The file name [typed] gives (check [renameProblem] first): trimmed, ending in ".json". */
    fun renamedFileName(typed: String): String = stem(typed) + EXT

    /** What the user typed as a name, without spaces around it or a ".json" of its own. */
    private fun stem(typed: String): String {
        val t = typed.trim()
        return (if (t.endsWith(EXT, ignoreCase = true)) t.dropLast(EXT.length) else t).trim()
    }

    /** [list] with [file] added as the newest (a file saved over again moves to the top). */
    fun added(list: List<SavedFile>, file: SavedFile): List<SavedFile> =
        listOf(file) + list.filter { it.uri != file.uri }

    /** Newest first. */
    fun sorted(list: List<SavedFile>): List<SavedFile> = list.sortedByDescending { it.savedAt }

    /** The list as the app keeps it. */
    fun encodeList(list: List<SavedFile>): String = Config.json.encodeToString(ListSerializer(SavedFile.serializer()), list)

    /** A kept list; anything unreadable gives an empty one. */
    fun decodeList(text: String?): List<SavedFile> =
        text?.let { runCatching { Config.json.decodeFromString(ListSerializer(SavedFile.serializer()), it) }.getOrNull() }
            ?.filter { it.uri.isNotBlank() }
            ?.distinctBy { it.uri }
            .orEmpty()

    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    /** "flockswitch-settings-<model>-<YYYYMMDD-HHMMSS>.json", the model reduced to what
     *  every file system takes. */
    fun fileName(model: String, at: LocalDateTime): String {
        val safe = model.trim().replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('-', '.').ifEmpty { "android" }
        return "flockswitch-settings-$safe-${at.format(stamp)}.json"
    }
}
