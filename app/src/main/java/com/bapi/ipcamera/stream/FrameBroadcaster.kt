package com.bapi.ipcamera.stream

import java.util.concurrent.atomic.AtomicLong

/**
 * Giữ frame JPEG mới nhất và cho phép nhiều client cùng đọc (fan-out).
 * Chỉ một pipeline camera publish, các client HTTP chỉ đọc frame mới.
 */
class FrameBroadcaster {
    class Client internal constructor()

    private val lock = Object()
    @Volatile
    private var latest: ByteArray? = null
    private var seq: Long = 0L
    private val clients = LinkedHashSet<Client>()

    fun publish(frame: ByteArray) {
        synchronized(lock) {
            latest = frame
            seq++
            lock.notifyAll()
        }
    }

    fun latestFrame(): ByteArray? = latest

    /** Chờ frame mới hơn [afterSeq] tối đa [timeoutMs]; trả về (frame, seq) hoặc null. */
    fun awaitFrame(afterSeq: Long, timeoutMs: Long): Pair<ByteArray, Long>? {
        synchronized(lock) {
            if (seq <= afterSeq) {
                try {
                    lock.wait(timeoutMs)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return null
                }
            }
            if (seq <= afterSeq) return null
            val f = latest ?: return null
            return f to seq
        }
    }

    fun currentSeq(): Long = synchronized(lock) { seq }

    fun newClient(): Client = synchronized(lock) { Client().also { clients.add(it) } }

    fun removeClient(c: Client) {
        synchronized(lock) { clients.remove(c) }
    }

    fun clientCount(): Int = synchronized(lock) { clients.size }
}

/** Thống kê phục vụ chế độ tự động (auto) điều chỉnh chất lượng/FPS. */
object StreamStats {
    private val slowWrites = AtomicLong(0)
    private val writeCount = AtomicLong(0)
    private val writeNanos = AtomicLong(0)

    fun recordWrite(durationNanos: Long, slow: Boolean) {
        writeCount.incrementAndGet()
        writeNanos.addAndGet(durationNanos)
        if (slow) slowWrites.incrementAndGet()
    }

    /** Trả về và reset số lần ghi chậm trong cửa sổ; kèm tổng số lần ghi. */
    fun drainSlows(): Pair<Long, Long> {
        val s = slowWrites.getAndSet(0)
        val c = writeCount.getAndSet(0)
        writeNanos.getAndSet(0)
        return s to c
    }
}
