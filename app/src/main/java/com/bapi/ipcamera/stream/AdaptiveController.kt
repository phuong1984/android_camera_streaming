package com.bapi.ipcamera.stream

/**
 * Điều chỉnh FPS/JPEG quality tự động dựa trên tình trạng mạng và số client.
 * Logic thuần, không phụ thuộc Android → dễ unit-test.
 */
class AdaptiveController(
    val maxFps: Int,
    val maxQuality: Int,
    val minFps: Int = 5,
    val minQuality: Int = 40,
) {
    var fps: Int = maxFps
        private set
    var quality: Int = maxQuality
        private set

    /** Gọi mỗi cửa sổ thời gian (ví dụ 3 giây) với số lần ghi chậm và số client. */
    @Suppress("UNUSED_PARAMETER")
    fun onWindow(slowWrites: Long, totalWrites: Long, clients: Int) {
        val congested = slowWrites > 0 || clients >= 3
        if (congested) {
            quality = (quality - 10).coerceAtLeast(minQuality)
            fps = (fps - 5).coerceAtLeast(minFps)
        } else {
            quality = (quality + 5).coerceAtMost(maxQuality)
            fps = (fps + 5).coerceAtMost(maxFps)
        }
    }

    fun reset() {
        fps = maxFps
        quality = maxQuality
    }
}
