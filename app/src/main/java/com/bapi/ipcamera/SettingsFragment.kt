package com.bapi.ipcamera

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.bapi.ipcamera.config.Resolution
import com.bapi.ipcamera.config.SettingsStore
import com.bapi.ipcamera.config.StreamConfig
import com.bapi.ipcamera.net.NetworkInfo
import com.bapi.ipcamera.service.StreamState
import com.bapi.ipcamera.service.StreamingService

class SettingsFragment : PreferenceFragmentCompat() {

    private lateinit var store: SettingsStore

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        store = SettingsStore(requireContext())
        val cfg = store.load()

        findPreference<EditTextPreference>("port")?.apply {
            text = cfg.port.toString()
            setOnPreferenceChangeListener { pref, newValue ->
                val port = (newValue as? String)?.toIntOrNull()
                if (port == null || port !in 1024..65535) {
                    toast(getString(R.string.port_invalid))
                    return@setOnPreferenceChangeListener false
                }
                pref.summary = port.toString()
                update { it.copy(port = port) }
                true
            }
            summary = cfg.port.toString()
        }

        findPreference<ListPreference>("resolution")?.apply {
            value = "${cfg.width}x${cfg.height}"
            setOnPreferenceChangeListener { _, newValue ->
                val r = Resolution.fromKey(newValue as String) ?: return@setOnPreferenceChangeListener false
                update { it.copy(width = r.w, height = r.h) }
                true
            }
        }

        findPreference<ListPreference>("fps")?.apply {
            value = cfg.fps.toString()
            setOnPreferenceChangeListener { _, newValue ->
                val fps = (newValue as? String)?.toIntOrNull() ?: return@setOnPreferenceChangeListener false
                update { it.copy(fps = fps) }
                true
            }
        }

        findPreference<SeekBarPreference>("quality")?.apply {
            value = cfg.jpegQuality
            setOnPreferenceChangeListener { _, newValue ->
                update { it.copy(jpegQuality = (newValue as Int)) }
                true
            }
        }

        findPreference<SwitchPreferenceCompat>("autoFramesize")?.apply {
            isChecked = cfg.autoFramesize
            setOnPreferenceChangeListener { _, v -> update { it.copy(autoFramesize = v as Boolean) }; true }
        }
        findPreference<SwitchPreferenceCompat>("keepWarm")?.apply {
            isChecked = cfg.keepWarm
            setOnPreferenceChangeListener { _, v -> update { it.copy(keepWarm = v as Boolean) }; true }
        }
        findPreference<SwitchPreferenceCompat>("autostart")?.apply {
            isChecked = cfg.autostart
            setOnPreferenceChangeListener { _, v -> update { it.copy(autostart = v as Boolean) }; true }
        }

        findPreference<Preference>("battery_opt")?.setOnPreferenceClickListener {
            openBatteryOptimizationSettings()
            true
        }

        findPreference<Preference>("oem_autostart")?.setOnPreferenceClickListener {
            openOemAutostartSettings()
            true
        }
    }

    private fun update(change: (StreamConfig) -> StreamConfig) {
        val before = store.load()
        val updated = change(before)
        store.save(updated)
        if (StreamState.running.value != true) return
        if (before.port != updated.port) {
            // Đổi cổng phải bind lại HTTP server -> buộc khởi động lại service.
            StreamingService.stop(requireContext())
            StreamingService.start(requireContext())
        } else {
            // Áp dụng trực tiếp, giữ nguyên kết nối stream của client (không cần F5).
            StreamingService.applyConfig(requireContext())
        }
    }

    private fun openBatteryOptimizationSettings() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${requireContext().packageName}")
            }
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun openOemAutostartSettings() {
        val candidates = listOf(
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            "com.letv.android.letvsafe" to "com.letv.android.letvsafe.AutobootManageActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        )
        for ((pkg, cls) in candidates) {
            try {
                startActivity(Intent().setClassName(pkg, cls))
                return
            } catch (_: Throwable) {
                // thử tiếp
            }
        }
        // Fallback: mở trang chi tiết ứng dụng
        try {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${requireContext().packageName}")
                )
            )
        } catch (_: Throwable) {
            toast(getString(R.string.autostart_open_failed))
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

    @Suppress("unused")
    private fun prefsDiag(): String =
        "sdk=${Build.VERSION.SDK_INT}"
}
