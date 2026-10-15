package com.bapi.ipcamera

/** Chọn ngôn ngữ giao diện bằng per-app locale của AppCompat. */
object Language {
    const val DEFAULT = "vi"
    const val ENGLISH = "en"

    /** Ngôn ngữ kế tiếp khi người dùng bấm nút đổi ngôn ngữ. */
    fun next(current: String): String =
        if (current.equals(ENGLISH, ignoreCase = true)) DEFAULT else ENGLISH

    /** Cờ (ngôn ngữ sẽ chuyển tới) hiển thị trên nút. */
    fun flagRes(current: String): Int =
        if (current.equals(ENGLISH, ignoreCase = true)) {
            R.drawable.flag_vietnam
        } else {
            R.drawable.flag_uk
        }
}
