package com.bapi.ipcamera.config

import kotlin.math.abs

data class StreamConfig(
    val port: Int = 8081,
    val width: Int = 640,
    val height: Int = 480,
    val fps: Int = 15,
    val jpegQuality: Int = 80,
    val autoFramesize: Boolean = true,
    val autostart: Boolean = false,
    val keepWarm: Boolean = false,
    val idleGraceSeconds: Int = 15,
)

enum class Resolution(val w: Int, val h: Int) {
    QVGA(320, 240),
    VGA(640, 480),
    SVGA(800, 600),
    HD(1280, 720);

    companion object {
        fun nearest(w: Int, h: Int): Resolution =
            values().minByOrNull { abs(it.w - w) + abs(it.h - h) } ?: VGA

        fun fromKey(key: String): Resolution? {
            val parts = key.split("x")
            if (parts.size != 2) return null
            val w = parts[0].toIntOrNull() ?: return null
            val h = parts[1].toIntOrNull() ?: return null
            return nearest(w, h)
        }
    }
}
