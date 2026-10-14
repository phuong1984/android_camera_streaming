package com.bapi.ipcamera.config

import android.content.Context
import android.content.SharedPreferences

class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("ipcamera", Context.MODE_PRIVATE)

    fun load(): StreamConfig = StreamConfig(
        port = prefs.getInt("port", 8081),
        width = prefs.getInt("width", 640),
        height = prefs.getInt("height", 480),
        fps = prefs.getInt("fps", 15),
        jpegQuality = prefs.getInt("jpegQuality", 80),
        autoFramesize = prefs.getBoolean("autoFramesize", true),
        autostart = prefs.getBoolean("autostart", false),
        keepWarm = prefs.getBoolean("keepWarm", false),
        idleGraceSeconds = prefs.getInt("idleGraceSeconds", 15),
        frontCamera = prefs.getBoolean("frontCamera", false),
    )

    fun save(c: StreamConfig) {
        prefs.edit()
            .putInt("port", c.port)
            .putInt("width", c.width)
            .putInt("height", c.height)
            .putInt("fps", c.fps)
            .putInt("jpegQuality", c.jpegQuality)
            .putBoolean("autoFramesize", c.autoFramesize)
            .putBoolean("autostart", c.autostart)
            .putBoolean("keepWarm", c.keepWarm)
            .putInt("idleGraceSeconds", c.idleGraceSeconds)
            .putBoolean("frontCamera", c.frontCamera)
            .apply()
    }

    fun reset() {
        prefs.edit().clear().commit()
    }

    fun loadPreviewEnabled(): Boolean = prefs.getBoolean("previewEnabled", false)

    fun savePreviewEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("previewEnabled", enabled).apply()
    }
}
