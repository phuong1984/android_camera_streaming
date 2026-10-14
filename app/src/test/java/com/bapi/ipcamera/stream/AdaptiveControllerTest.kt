package com.bapi.ipcamera.stream

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveControllerTest {
    @Test
    fun startsAtMax() {
        val a = AdaptiveController(maxFps = 15, maxQuality = 80)
        assertEquals(15, a.fps)
        assertEquals(80, a.quality)
    }

    @Test
    fun congestion_lowersFpsAndQuality() {
        val a = AdaptiveController(maxFps = 15, maxQuality = 80)
        a.onWindow(slowWrites = 2, totalWrites = 10, clients = 1)
        assertEquals(10, a.fps)
        assertEquals(70, a.quality)
    }

    @Test
    fun recovery_raisesGraduallyButCapsAtMax() {
        val a = AdaptiveController(maxFps = 15, maxQuality = 80)
        a.onWindow(slowWrites = 5, totalWrites = 10, clients = 1)
        repeat(10) { a.onWindow(slowWrites = 0, totalWrites = 10, clients = 1) }
        assertEquals(15, a.fps)
        assertEquals(80, a.quality)
    }

    @Test
    fun neverBelowMinimums() {
        val a = AdaptiveController(maxFps = 15, maxQuality = 80, minFps = 5, minQuality = 40)
        repeat(50) { a.onWindow(slowWrites = 5, totalWrites = 10, clients = 5) }
        assertEquals(5, a.fps)
        assertEquals(40, a.quality)
    }
}
