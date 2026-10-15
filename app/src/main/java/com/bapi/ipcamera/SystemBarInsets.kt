package com.bapi.ipcamera

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

internal object SystemBarInsets {
    fun apply(view: View) {
        val initialLeft = view.paddingLeft
        val initialTop = view.paddingTop
        val initialRight = view.paddingRight
        val initialBottom = view.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
            val systemInsets = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            target.setPadding(
                initialLeft + systemInsets.left,
                initialTop + systemInsets.top,
                initialRight + systemInsets.right,
                initialBottom + systemInsets.bottom,
            )
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }
}
