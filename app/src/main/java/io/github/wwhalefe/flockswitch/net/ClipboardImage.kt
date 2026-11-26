package io.github.wwhalefe.flockswitch.net

import java.io.File

/** Images received from the master: kept in the cache for the clipboard's content:// URI. */
object ClipboardImage {
    /** Larger images are not put on the clipboard (netcore's own limit is 100 MB). */
    const val MAX_BYTES = 32L * 1024 * 1024
    /** The newest few stay so an app pasting a moment later can still read its image. */
    const val KEEP = 2
    const val SUBDIR = "clipboard"

    fun fits(size: Long) = size in 1..MAX_BYTES

    /** Moves the staged PNG into [dir] under a new name and drops the older images.
     *  Returns null (and removes the staged file) when it is missing or too large. */
    fun store(staged: File, dir: File, now: Long): File? {
        if (!staged.isFile || !fits(staged.length())) { staged.delete(); return null }
        if (!dir.isDirectory && !dir.mkdirs()) { staged.delete(); return null }
        var target = File(dir, "clip-$now.png")
        var n = 1
        while (target.exists()) target = File(dir, "clip-$now-${n++}.png")
        if (!staged.renameTo(target)) {
            try { staged.copyTo(target) } catch (e: Exception) { target.delete(); staged.delete(); return null }
            staged.delete()
        }
        prune(dir, target)
        return target
    }

    /** Deletes all but the newest [KEEP] images, never [keep]. */
    fun prune(dir: File, keep: File? = null) {
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".png") } ?: return
        files.filter { it != keep }
            .sortedWith(compareByDescending<File> { it.lastModified() }.thenByDescending { it.name })
            .drop(KEEP - 1)
            .forEach { it.delete() }
    }
}
