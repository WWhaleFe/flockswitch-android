package io.github.wwhalefe.flockswitch

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Logging that works in the app (logcat) and in plain JVM tests (stdout). The last
 *  lines are kept in memory for the mail to the developer, which carries them; the
 *  Google Play build has no such mail and keeps none. */
object Log {
    private const val TAG = "flockswitch"

    /** How many recent lines the mail to the developer carries. */
    private const val KEEP = 60

    /** Where lines go besides the in-memory buffer; the app points this at logcat. */
    @Volatile
    var sink: (level: Char, message: String) -> Unit = { level, message -> println("$level $message") }

    private val lines = ArrayDeque<String>(KEEP + 1)
    private val time = SimpleDateFormat("HH:mm:ss", Locale.ROOT)

    fun d(message: String) = log('D', message)
    fun i(message: String) = log('I', message)
    fun w(message: String) = log('W', message)

    private fun log(level: Char, message: String) {
        sink(level, message)
        if (level == 'D' || BuildConfig.PLAY_STORE) return
        synchronized(lines) {
            lines.addLast("${time.format(Date())} $level $message")
            while (lines.size > KEEP) lines.removeFirst()
        }
    }

    /** The last [KEEP] lines, oldest first (debug lines are only in logcat). */
    fun recent(): List<String> = synchronized(lines) { lines.toList() }

    fun useLogcat() {
        sink = { level, message ->
            when (level) {
                'D' -> android.util.Log.d(TAG, message)
                'W' -> android.util.Log.w(TAG, message)
                else -> android.util.Log.i(TAG, message)
            }
        }
    }
}
