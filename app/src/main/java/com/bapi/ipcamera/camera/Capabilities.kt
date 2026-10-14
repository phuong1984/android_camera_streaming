package com.bapi.ipcamera.camera

import android.hardware.camera2.CameraCharacteristics

object Capabilities {
    data class Supported(
        val awbModes: IntArray,
        val sceneModes: IntArray,
        val effects: IntArray,
        val hasFlash: Boolean,
        val minExposureComp: Int,
        val maxExposureComp: Int,
    ) {
        val awbNames: List<String>
            get() = awbModes.map { AWB_NAMES[it] }.filterNotNull()
    }

    private val AWB_NAMES = mapOf(
        CameraCharacteristics.CONTROL_AWB_MODE_AUTO to "auto",
        CameraCharacteristics.CONTROL_AWB_MODE_INCANDESCENT to "incandescent",
        CameraCharacteristics.CONTROL_AWB_MODE_FLUORESCENT to "fluorescent",
        CameraCharacteristics.CONTROL_AWB_MODE_DAYLIGHT to "daylight",
        CameraCharacteristics.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT to "cloudy",
        CameraCharacteristics.CONTROL_AWB_MODE_SHADE to "shade",
    )
    private val AWB_VALUES = AWB_NAMES.entries.associate { (k, v) -> v to k }

    fun awbValue(name: String): Int? = AWB_VALUES[name.lowercase()]

    fun from(ch: CameraCharacteristics): Supported {
        val awb = ch.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES) ?: intArrayOf()
        val scenes = ch.get(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES) ?: intArrayOf()
        val effects = ch.get(CameraCharacteristics.CONTROL_AVAILABLE_EFFECTS) ?: intArrayOf()
        val flash = ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
        val range = ch.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
        return Supported(
            awbModes = awb,
            sceneModes = scenes,
            effects = effects,
            hasFlash = flash,
            minExposureComp = range?.lower ?: 0,
            maxExposureComp = range?.upper ?: 0,
        )
    }
}
