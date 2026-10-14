package com.bapi.ipcamera.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewRotationTest {
    @Test
    fun mapsExifOrientationToClockwiseDegrees() {
        assertEquals(0, PreviewRotation.degreesForExifOrientation(1))
        assertEquals(180, PreviewRotation.degreesForExifOrientation(3))
        assertEquals(90, PreviewRotation.degreesForExifOrientation(6))
        assertEquals(270, PreviewRotation.degreesForExifOrientation(8))
    }

    @Test
    fun unknownExifOrientationNeedsNoRotation() {
        assertEquals(0, PreviewRotation.degreesForExifOrientation(0))
        assertEquals(0, PreviewRotation.degreesForExifOrientation(42))
    }

    @Test
    fun rotatedSizeSwapsDimensionsForQuarterTurns() {
        assertEquals(480 to 640, PreviewRotation.rotatedSize(640, 480, 90))
        assertEquals(480 to 640, PreviewRotation.rotatedSize(640, 480, 270))
        assertEquals(640 to 480, PreviewRotation.rotatedSize(640, 480, 0))
        assertEquals(640 to 480, PreviewRotation.rotatedSize(640, 480, 180))
    }

    @Test
    fun sampleSizeHalvesUntilUnderTarget() {
        assertEquals(1, PreviewRotation.sampleSize(640, 1000))
        assertEquals(2, PreviewRotation.sampleSize(1280, 640))
        assertEquals(4, PreviewRotation.sampleSize(1280, 320))
        assertEquals(1, PreviewRotation.sampleSize(1280, 0))
    }
}
