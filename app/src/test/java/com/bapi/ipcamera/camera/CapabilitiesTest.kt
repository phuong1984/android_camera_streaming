package com.bapi.ipcamera.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilitiesTest {
    @Test
    fun mapsAwbModeNames() {
        val c = Capabilities.Supported(
            awbModes = intArrayOf(
                CameraCharacteristics.CONTROL_AWB_MODE_INCANDESCENT,
                CameraCharacteristics.CONTROL_AWB_MODE_DAYLIGHT,
            ),
            sceneModes = intArrayOf(CameraCharacteristics.CONTROL_SCENE_MODE_PORTRAIT),
            effects = intArrayOf(CameraCharacteristics.CONTROL_EFFECT_MODE_MONO),
            hasFlash = true,
            minExposureComp = -2,
            maxExposureComp = 2,
        )
        assertTrue(c.awbNames.contains("incandescent"))
        assertTrue(c.awbNames.contains("daylight"))
        assertTrue(c.effects.contains(CameraCharacteristics.CONTROL_EFFECT_MODE_MONO))
        assertTrue(c.hasFlash)
    }

    @Test
    fun awbValue_reverseLookup() {
        assertEquals(
            CameraCharacteristics.CONTROL_AWB_MODE_DAYLIGHT,
            Capabilities.awbValue("daylight")
        )
        assertNull(Capabilities.awbValue("nonsense"))
    }

    @Test
    fun sceneAndEffectNames() {
        assertEquals(
            CaptureRequest.CONTROL_SCENE_MODE_PORTRAIT,
            CameraControls.sceneNameToValue("portrait")
        )
        assertNull(CameraControls.sceneNameToValue("night"))
        assertEquals(
            CaptureRequest.CONTROL_EFFECT_MODE_SEPIA,
            CameraControls.effectNameToValue("sepia")
        )
        assertEquals(
            CaptureRequest.CONTROL_EFFECT_MODE_MONO,
            CameraControls.effectNameToValue("mono")
        )
        assertNull(CameraControls.sceneNameToValue("nonsense"))
    }
}
