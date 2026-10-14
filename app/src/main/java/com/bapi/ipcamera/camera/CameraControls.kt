package com.bapi.ipcamera.camera

import android.hardware.camera2.CaptureRequest
import com.bapi.ipcamera.http.ControlAction

/**
 * Giữ trạng thái các thiết lập camera và áp dụng vào CaptureRequest.
 * Không phụ thuộc Android ngoài các hằng số CaptureRequest.
 */
class CameraControls {
    var awbMode: Int? = null
    var sceneMode: Int? = null
    var effect: Int? = null
    var night: Boolean = false
    var exposureComp: Int = 0
    /** 0..100, 50 = trung tính. Được map sang AE exposure compensation theo khả năng máy. */
    var brightness: Int = 50
    /** 0..100, chỉ áp dụng hậu kỳ (không có control trực tiếp trong Camera2). */
    var contrast: Int = 50
    var torch: Boolean = false

    fun applyTo(builder: CaptureRequest.Builder, exposureCompValue: Int) {
        if (night || sceneMode != null) {
            builder.set(
                CaptureRequest.CONTROL_MODE,
                CaptureRequest.CONTROL_MODE_USE_SCENE_MODE
            )
            builder.set(
                CaptureRequest.CONTROL_SCENE_MODE,
                if (night) CaptureRequest.CONTROL_SCENE_MODE_NIGHT else sceneMode
            )
        } else {
            builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
        }
        awbMode?.let { builder.set(CaptureRequest.CONTROL_AWB_MODE, it) }
        effect?.let { builder.set(CaptureRequest.CONTROL_EFFECT_MODE, it) }
        builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, exposureCompValue)
        // FLASH_MODE_TORCH chỉ hoạt động ổn định khi AE_MODE = ON (một số HAL
        // như Exynos lỗi ERROR_CAMERA_DEVICE nếu thiếu ràng buộc này).
        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        builder.set(
            CaptureRequest.FLASH_MODE,
            if (torch) CaptureRequest.FLASH_MODE_TORCH else CaptureRequest.FLASH_MODE_OFF
        )
    }

    fun handle(action: ControlAction) {
        when (action) {
            is ControlAction.SetAwb -> awbMode = Capabilities.awbValue(action.mode)
            is ControlAction.SetScene -> sceneMode = sceneNameToValue(action.mode)
            is ControlAction.SetEffect -> effect = effectNameToValue(action.mode)
            is ControlAction.SetNight -> night = action.on
            is ControlAction.SetBrightness -> brightness = action.value.coerceIn(0, 100)
            is ControlAction.SetContrast -> contrast = action.value.coerceIn(0, 100)
            else -> {}
        }
    }

    companion object {
        fun sceneNameToValue(n: String): Int? = when (n.lowercase()) {
            "auto", "none" -> CaptureRequest.CONTROL_SCENE_MODE_DISABLED
            "night" -> CaptureRequest.CONTROL_SCENE_MODE_NIGHT
            "sport", "sports" -> CaptureRequest.CONTROL_SCENE_MODE_SPORTS
            "portrait" -> CaptureRequest.CONTROL_SCENE_MODE_PORTRAIT
            "landscape" -> CaptureRequest.CONTROL_SCENE_MODE_LANDSCAPE
            else -> null
        }

        fun effectNameToValue(n: String): Int? = when (n.lowercase()) {
            "none", "off" -> CaptureRequest.CONTROL_EFFECT_MODE_OFF
            "mono", "grayscale" -> CaptureRequest.CONTROL_EFFECT_MODE_MONO
            "negative" -> CaptureRequest.CONTROL_EFFECT_MODE_NEGATIVE
            "sepia" -> CaptureRequest.CONTROL_EFFECT_MODE_SEPIA
            "posterize" -> CaptureRequest.CONTROL_EFFECT_MODE_POSTERIZE
            else -> null
        }
    }
}
