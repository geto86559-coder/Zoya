package com.example.data.audio

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * High-performance thread-safe audio buffer with backpressure limits.
 */
class AudioBufferManager(
    private val maxCapacityBytes: Int = 1024 * 1024 // 1 MB
) {
    private val queue = ConcurrentLinkedQueue<ByteArray>()
    private val currentSizeBytes = AtomicInteger(0)

    fun push(chunk: ByteArray): Boolean {
        if (currentSizeBytes.get() + chunk.size > maxCapacityBytes) {
            // Drop oldest packets under extreme backpressure
            poll()
        }
        queue.offer(chunk)
        currentSizeBytes.addAndGet(chunk.size)
        return true
    }

    fun poll(): ByteArray? {
        val chunk = queue.poll()
        if (chunk != null) {
            currentSizeBytes.addAndGet(-chunk.size)
        }
        return chunk
    }

    fun clear() {
        queue.clear()
        currentSizeBytes.set(0)
    }

    fun isEmpty(): Boolean = queue.isEmpty()

    fun availableBytes(): Int = currentSizeBytes.get()
}
