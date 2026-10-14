package com.bapi.ipcamera.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraOrientationTest {

    @Test
    fun snapOrientation_roundsToNearest90() {
        assertEquals(0, CameraController.snapOrientation(0))
        assertEquals(0, CameraController.snapOrientation(44))
        assertEquals(90, CameraController.snapOrientation(45))
        assertEquals(90, CameraController.snapOrientation(89))
        assertEquals(90, CameraController.snapOrientation(90))
        assertEquals(90, CameraController.snapOrientation(134))
        assertEquals(180, CameraController.snapOrientation(135))
        assertEquals(180, CameraController.snapOrientation(180))
        assertEquals(180, CameraController.snapOrientation(224))
        assertEquals(270, CameraController.snapOrientation(225))
        assertEquals(270, CameraController.snapOrientation(270))
        assertEquals(270, CameraController.snapOrientation(314))
        assertEquals(0, CameraController.snapOrientation(315))
        assertEquals(0, CameraController.snapOrientation(360))
        assertEquals(0, CameraController.snapOrientation(359))
    }

    @Test
    fun jpegOrientation_backCamera_addsDeviceRotation() {
        // Cảm biến sau điển hình = 90, chân dung (0) giữ nguyên như hành vi cũ.
        assertEquals(90, CameraController.jpegOrientation(90, false, 0))
        assertEquals(180, CameraController.jpegOrientation(90, false, 90))
        assertEquals(270, CameraController.jpegOrientation(90, false, 180))
        assertEquals(0, CameraController.jpegOrientation(90, false, 270))
        // Cảm biến 270.
        assertEquals(270, CameraController.jpegOrientation(270, false, 0))
        assertEquals(0, CameraController.jpegOrientation(270, false, 90))
        assertEquals(90, CameraController.jpegOrientation(270, false, 180))
        assertEquals(180, CameraController.jpegOrientation(270, false, 270))
    }

    @Test
    fun jpegOrientation_frontCamera_subtractsDeviceRotation() {
        assertEquals(270, CameraController.jpegOrientation(270, true, 0))
        assertEquals(180, CameraController.jpegOrientation(270, true, 90))
        assertEquals(90, CameraController.jpegOrientation(270, true, 180))
        assertEquals(0, CameraController.jpegOrientation(270, true, 270))
    }

    @Test
    fun jpegOrientation_isAlwaysInValidRange() {
        for (sensor in intArrayOf(0, 90, 180, 270)) {
            for (device in intArrayOf(0, 90, 180, 270)) {
                for (front in booleanArrayOf(true, false)) {
                    val v = CameraController.jpegOrientation(sensor, front, device)
                    assertEquals(true, v in 0..270 && v % 90 == 0)
                }
            }
        }
    }

    @Test
    fun displaySize_backSensor90_swapsOnlyWhenImageRotated90or270() {
        // Cảm biến sau = 90, frame 1280x720.
        // deg 0 -> JPEG xoay 90 -> ảnh hiển thị 720x1280 (dọc).
        assertEquals(720 to 1280, CameraController.displaySize(1280, 720, 90, false, 0))
        // deg 90 -> JPEG xoay 180 -> giữ 1280x720 (ngang).
        assertEquals(1280 to 720, CameraController.displaySize(1280, 720, 90, false, 90))
        // deg 180 -> JPEG xoay 270 -> 720x1280 (dọc).
        assertEquals(720 to 1280, CameraController.displaySize(1280, 720, 90, false, 180))
        // deg 270 -> JPEG xoay 0 -> 1280x720 (ngang).
        assertEquals(1280 to 720, CameraController.displaySize(1280, 720, 90, false, 270))
    }

    @Test
    fun displaySize_neverChangesAreaJustSwaps() {
        for (sensor in intArrayOf(0, 90, 180, 270)) {
            for (device in intArrayOf(0, 90, 180, 270)) {
                for (front in booleanArrayOf(true, false)) {
                    val (w, h) = CameraController.displaySize(1280, 720, sensor, front, device)
                    assertEquals(1280 * 720, w * h)
                    assertEquals(true, (w == 1280 && h == 720) || (w == 720 && h == 1280))
                }
            }
        }
    }
}
