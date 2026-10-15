package com.bapi.ipcamera.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.test.core.app.ApplicationProvider
import com.bapi.ipcamera.stream.FrameBroadcaster
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowCameraCharacteristics

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CameraControllerTorchTest {

    private lateinit var manager: CameraManager
    private lateinit var controller: CameraController

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val chars = ShadowCameraCharacteristics.newCameraCharacteristics()
        shadowOf(chars).set(
            CameraCharacteristics.LENS_FACING,
            CameraCharacteristics.LENS_FACING_BACK
        )
        shadowOf(chars).set(CameraCharacteristics.FLASH_INFO_AVAILABLE, true)
        shadowOf(manager).addCamera(BACK_ID, chars)
        controller = CameraController(context, FrameBroadcaster())
    }

    @Test
    fun stop_turnsOffTorchThatWasEnabledWhileCameraIdle() {
        // Camera chưa mở (không có client/preview) nên torch bật qua setTorchMode.
        controller.setTorch(true)
        assertTrue("torch phải bật", shadowOf(manager).getTorchMode(BACK_ID))

        controller.stop()

        assertFalse("torch phải tắt khi dừng", shadowOf(manager).getTorchMode(BACK_ID))
    }

    companion object {
        private const val BACK_ID = "0"
    }
}
