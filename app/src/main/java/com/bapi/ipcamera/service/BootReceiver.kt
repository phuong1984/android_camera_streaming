package com.bapi.ipcamera.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bapi.ipcamera.config.SettingsStore

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val cfg = SettingsStore(context).load()
        if (cfg.autostart) {
            StreamingService.start(context)
        }
    }
}
