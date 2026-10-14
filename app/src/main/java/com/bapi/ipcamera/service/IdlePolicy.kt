package com.bapi.ipcamera.service

/**
 * Quyết định có nên dừng camera khi không có client xem hay không.
 * Logic thuần, không phụ thuộc Android → unit-test.
 */
object IdlePolicy {
    fun shouldStopNow(
        clientCount: Int,
        lastActivityMs: Long,
        nowMs: Long,
        graceMs: Long,
        keepWarm: Boolean,
        cameraRunning: Boolean,
        previewActive: Boolean = false,
    ): Boolean {
        if (!cameraRunning) return false
        if (keepWarm) return false
        if (previewActive) return false
        if (clientCount > 0) return false
        if (lastActivityMs <= 0L) return false
        return (nowMs - lastActivityMs) >= graceMs
    }
}
