package com.bapi.ipcamera.http

sealed interface ControlAction {
    data class SetResolution(val w: Int, val h: Int) : ControlAction
    data class SetQuality(val q: Int) : ControlAction
    data class SetFps(val fps: Int) : ControlAction
    data class SetFlash(val on: Boolean) : ControlAction
    data class SetCameraFront(val front: Boolean) : ControlAction
    data class SetAwb(val mode: String) : ControlAction
    data class SetScene(val mode: String) : ControlAction
    data class SetEffect(val mode: String) : ControlAction
    data class SetNight(val on: Boolean) : ControlAction
    data class SetBrightness(val value: Int) : ControlAction
    data class SetContrast(val value: Int) : ControlAction
}

object ControlParams {
    fun parse(q: Map<String, String>): ControlAction? {
        val v = q["var"] ?: return null
        val raw = q["val"] ?: return null
        val b = raw == "1" || raw.equals("true", true) || raw.equals("on", true)
        return when (v) {
            "framesize" -> parseFrameSize(raw)

            "quality" -> raw.toIntOrNull()?.let { ControlAction.SetQuality(it) }
            "fps" -> raw.toIntOrNull()?.let { ControlAction.SetFps(it) }
            "flash" -> ControlAction.SetFlash(b)
            "camera" -> ControlAction.SetCameraFront(raw == "1")
            "wb" -> ControlAction.SetAwb(raw)
            "env" -> ControlAction.SetScene(raw)
            "effect" -> ControlAction.SetEffect(raw)
            "night" -> ControlAction.SetNight(b)
            "brightness" -> raw.toIntOrNull()?.let { ControlAction.SetBrightness(it) }
            "contrast" -> raw.toIntOrNull()?.let { ControlAction.SetContrast(it) }
            else -> null
        }
    }

    /**
     * Chấp nhận cả dạng "WxH" (ví dụ 640x480) và chỉ số framesize kiểu
     * esp32-camera (ví dụ 8 = VGA) mà trang HTML ESP32-CAM hay gửi.
     */
    private fun parseFrameSize(raw: String): ControlAction? {
        raw.split("x").takeIf { it.size == 2 }?.let {
            val w = it[0].toIntOrNull() ?: return@let
            val h = it[1].toIntOrNull() ?: return@let
            return ControlAction.SetResolution(w, h)
        }
        val index = raw.toIntOrNull() ?: return null
        val size = FRAMESIZE_INDEX[index] ?: return null
        return ControlAction.SetResolution(size.first, size.second)
    }

    private val FRAMESIZE_INDEX = mapOf(
        0 to (96 to 96),
        1 to (160 to 120),
        2 to (176 to 144),
        3 to (240 to 176),
        4 to (240 to 240),
        5 to (320 to 240),
        6 to (400 to 296),
        7 to (480 to 320),
        8 to (640 to 480),
        9 to (800 to 600),
        10 to (1024 to 768),
        11 to (1280 to 720),
        12 to (1280 to 1024),
        13 to (1600 to 1200),
    )
}
