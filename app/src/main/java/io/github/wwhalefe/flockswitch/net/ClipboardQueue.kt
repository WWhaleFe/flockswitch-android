package io.github.wwhalefe.flockswitch.net

import java.util.concurrent.Executor

/** A single worker prepares clipboard events and posts their updates to main in arrival order. */
internal class ClipboardQueue(private val worker: Executor, private val main: Executor) {
    fun text(apply: () -> Unit) = enqueue { apply }

    fun image(prepare: () -> (() -> Unit)?) = enqueue(prepare)

    private fun enqueue(prepare: () -> (() -> Unit)?) {
        worker.execute { prepare()?.let { main.execute(it) } }
    }
}
