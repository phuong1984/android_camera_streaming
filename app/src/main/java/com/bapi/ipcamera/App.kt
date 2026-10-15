package com.bapi.ipcamera

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.bapi.ipcamera.config.SettingsStore

/**
 * Áp dụng ngôn ngữ giao diện đã lưu (mặc định Tiếng Việt) mỗi khi tiến trình khởi động.
 * Nguồn dữ liệu là [SettingsStore]; AppCompat chỉ là cơ chế đổi cấu hình/tài nguyên.
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val tag = SettingsStore(this).loadLanguage()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }
}
