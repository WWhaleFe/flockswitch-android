package io.github.wwhalefe.flockswitch

import io.github.wwhalefe.flockswitch.net.ClipboardQueue
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardQueueTest {
    private class Pending : Executor {
        private val tasks = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { tasks.addLast(command) }
        fun drain() { while (tasks.isNotEmpty()) tasks.removeFirst().run() }
    }

    @Test
    fun textReceivedWhileImageIsPreparingRemainsLast() {
        val worker = Pending()
        val main = Pending()
        val queue = ClipboardQueue(worker, main)
        val applied = mutableListOf<String>()
        queue.image {
            // Text arrives while the earlier image is still being prepared.
            queue.text { applied.add("text") }
            val apply: () -> Unit = { applied.add("image") }
            apply
        }
        worker.drain()
        main.drain()
        assertEquals(listOf("image", "text"), applied)
    }

    @Test
    fun mixedClipboardEventsAreAppliedInArrivalOrder() {
        val worker = Pending()
        val main = Pending()
        val queue = ClipboardQueue(worker, main)
        val applied = mutableListOf<String>()
        queue.text { applied.add("first text") }
        queue.image { { applied.add("image") } }
        queue.text { applied.add("last text") }
        assertTrue(applied.isEmpty())
        worker.drain()
        assertTrue(applied.isEmpty())
        main.drain()
        assertEquals(listOf("first text", "image", "last text"), applied)
    }

    @Test
    fun rejectedImageDoesNotBlockLaterText() {
        val worker = Pending()
        val main = Pending()
        val queue = ClipboardQueue(worker, main)
        val applied = mutableListOf<String>()
        queue.image { null }
        queue.text { applied.add("text") }
        worker.drain()
        main.drain()
        assertEquals(listOf("text"), applied)
    }
}
