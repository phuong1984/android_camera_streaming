package com.bapi.ipcamera.config

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsStoreTest {
    private lateinit var store: SettingsStore

    @Before
    fun setup() {
        store = SettingsStore(ApplicationProvider.getApplicationContext())
        store.reset()
    }

    @Test
    fun defaults_areEsp32Like() {
        val c = store.load()
        assertEquals(8081, c.port)
        assertEquals(640, c.width)
        assertEquals(480, c.height)
        assertEquals(15, c.fps)
        assertEquals(true, c.autoFramesize)
    }

    @Test
    fun save_thenLoad_roundTrips() {
        store.save(
            StreamConfig(
                port = 9000, width = 1280, height = 720, fps = 30,
                jpegQuality = 70, autoFramesize = false, frontCamera = true
            )
        )
        val c = store.load()
        assertEquals(9000, c.port)
        assertEquals(1280, c.width)
        assertEquals(30, c.fps)
        assertEquals(70, c.jpegQuality)
        assertEquals(false, c.autoFramesize)
        assertEquals(true, c.frontCamera)
    }

    @Test
    fun resolution_nearest() {
        assertEquals(Resolution.HD, Resolution.nearest(1280, 720))
        assertEquals(Resolution.VGA, Resolution.fromKey("640x480"))
        assertEquals(Resolution.SVGA, Resolution.nearest(800, 600))
    }
}
