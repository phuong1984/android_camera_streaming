package com.bapi.ipcamera.http

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ControlParamsTest {
    @Test
    fun parsesFrameSize() {
        val a = ControlParams.parse(mapOf("var" to "framesize", "val" to "1280x720"))
        assertEquals(ControlAction.SetResolution(1280, 720), a)
    }

    @Test
    fun parsesFrameSizeByEsp32Index() {
        assertEquals(
            ControlAction.SetResolution(640, 480),
            ControlParams.parse(mapOf("var" to "framesize", "val" to "8"))
        )
        assertEquals(
            ControlAction.SetResolution(320, 240),
            ControlParams.parse(mapOf("var" to "framesize", "val" to "5"))
        )
        assertEquals(
            ControlAction.SetResolution(1280, 720),
            ControlParams.parse(mapOf("var" to "framesize", "val" to "11"))
        )
    }

    @Test
    fun parsesFlash() {
        assertEquals(
            ControlAction.SetFlash(true),
            ControlParams.parse(mapOf("var" to "flash", "val" to "1"))
        )
        assertEquals(
            ControlAction.SetFlash(false),
            ControlParams.parse(mapOf("var" to "flash", "val" to "0"))
        )
    }

    @Test
    fun parsesCamera() {
        assertEquals(
            ControlAction.SetCameraFront(true),
            ControlParams.parse(mapOf("var" to "camera", "val" to "1"))
        )
    }

    @Test
    fun parsesQualityAndFps() {
        assertEquals(
            ControlAction.SetQuality(70),
            ControlParams.parse(mapOf("var" to "quality", "val" to "70"))
        )
        assertEquals(
            ControlAction.SetFps(10),
            ControlParams.parse(mapOf("var" to "fps", "val" to "10"))
        )
    }

    @Test
    fun unknownReturnsNull() {
        assertNull(ControlParams.parse(mapOf("var" to "nope", "val" to "1")))
    }

    @Test
    fun invalidFrameSizeReturnsNull() {
        assertNull(ControlParams.parse(mapOf("var" to "framesize", "val" to "abc")))
    }
}
