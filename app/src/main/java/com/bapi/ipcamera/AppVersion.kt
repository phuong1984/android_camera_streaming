package com.bapi.ipcamera

/** Nhãn version hiển thị, ví dụ "v1.0". */
object AppVersion {
    fun label(versionName: String?): String = "v" + versionName.orEmpty()
}
