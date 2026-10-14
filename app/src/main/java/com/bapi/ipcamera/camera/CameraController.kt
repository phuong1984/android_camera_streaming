package com.bapi.ipcamera.camera

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import com.bapi.ipcamera.config.StreamConfig
import com.bapi.ipcamera.http.ControlAction
import com.bapi.ipcamera.stream.FrameBroadcaster

/**
 * Điều khiển pipeline Camera2: mở camera, đọc JPEG frame phần cứng và publish.
 * Xử lý ngắt kết nối và tự mở lại. Mọi lời gọi phải từ cùng một Handler thread.
 */
class CameraController(
    context: Context,
    private val broadcaster: FrameBroadcaster,
) {
    @Volatile
    var config: StreamConfig = StreamConfig()

    /** FPS/quality hiệu dụng (có thể bị AdaptiveController hạ xuống). */
    @Volatile
    var effectiveFps: Int = config.fps

    @Volatile
    var effectiveQuality: Int = config.jpegQuality

    private val manager =
        context.applicationContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val appContext = context.applicationContext

    /**
     * Góc xoay thiết bị hiện tại (0/90/180/270) theo gia tốc kế, giống app chụp ảnh
     * (vẫn đúng khi khoá xoay màn hình).
     */
    @Volatile
    private var deviceOrientationDeg: Int = 0

    private val orientationListener = object : OrientationEventListener(appContext) {
        override fun onOrientationChanged(orientation: Int) {
            if (orientation == ORIENTATION_UNKNOWN) return
            onDeviceOrientation(snapOrientation(orientation))
        }
    }

    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var cameraId: String? = null

    @Volatile
    private var frameWidth: Int = 0

    @Volatile
    private var frameHeight: Int = 0

    @Volatile
    private var sensorOrient: Int = 0

    @Volatile
    private var lastPublishNs: Long = 0

    val controls = CameraControls()

    /** Góc xoay ảnh hiện tại (0/90/180/270) theo cách cầm máy. */
    val orientationDegrees: Int get() = deviceOrientationDeg

    /**
     * Kích thước ảnh hiển thị (rộng x cao) sau khi áp dụng hướng xoay, để web
     * căn khung nhìn cho khớp. Dùng kích thước frame thật, nếu chưa mở thì lấy cấu hình.
     */
    fun displaySize(): Pair<Int, Int> {
        val w = if (frameWidth > 0) frameWidth else config.width
        val h = if (frameHeight > 0) frameHeight else config.height
        return displaySize(w, h, sensorOrient, front, deviceOrientationDeg)
    }

    @Volatile
    var capabilities: Capabilities.Supported? = null
        private set

    @Volatile
    var front: Boolean = false
        private set

    @Volatile
    var isRunning: Boolean = false
        private set

    fun start(front: Boolean = this.front) {
        if (isRunning) return
        Log.i(TAG, "start front=$front")
        isRunning = true
        this.front = front
        thread = HandlerThread("camera-thread").also { it.start() }
        handler = Handler(thread!!.looper)
        runCatching { orientationListener.enable() }
        openInternal()
    }

    /** Thiết bị đổi hướng -> cập nhật lại hướng ảnh đang stream ngay lập tức. */
    private fun onDeviceOrientation(deg: Int) {
        if (deg == deviceOrientationDeg) return
        deviceOrientationDeg = deg
        Log.i(TAG, "device orientation -> $deg")
        if (session != null) updateRepeating()
    }

    private fun openInternal() {
        val h = handler ?: return
        try {
            val id = chooseCameraId(front) ?: return
            Log.i(TAG, "openInternal id=$id")
            cameraId = id
            val ch = manager.getCameraCharacteristics(id)
            capabilities = Capabilities.from(ch)
            sensorOrient = ch.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            val map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val size = chooseSize(map, config.width, config.height)
            frameWidth = size.width
            frameHeight = size.height
            runCatching { session?.close() }
            session = null
            reader?.close()
            reader = ImageReader.newInstance(
                size.width, size.height, ImageFormat.JPEG, 4
            ).also { r ->
                r.setOnImageAvailableListener({ onFrame(it.acquireNextImage()) }, h)
            }
            @Suppress("MissingPermission")
            manager.openCamera(id, stateCallback, h)
        } catch (t: Throwable) {
            Log.e(TAG, "openInternal failed", t)
            isRunning = false
        }
    }

    private fun chooseCameraId(wantFront: Boolean): String? {
        val ids = manager.cameraIdList
        for (id in ids) {
            val facing = manager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.LENS_FACING)
            if (wantFront && facing == CameraCharacteristics.LENS_FACING_FRONT) return id
            if (!wantFront && facing == CameraCharacteristics.LENS_FACING_BACK) return id
        }
        return ids.firstOrNull()
    }

    private fun chooseSize(map: StreamConfigurationMap?, w: Int, h: Int): Size {
        val sizes = map?.getOutputSizes(ImageFormat.JPEG) ?: return Size(640, 480)
        if (sizes.isEmpty()) return Size(640, 480)
        return sizes.minByOrNull { kotlin.math.abs(it.width - w) + kotlin.math.abs(it.height - h) }
            ?: Size(640, 480)
    }

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            Log.i(TAG, "onOpened")
            device = camera
            createSession(camera)
        }

        override fun onDisconnected(camera: CameraDevice) {
            Log.w(TAG, "onDisconnected (device taken by system/other app?)")
            camera.close()
            device = null
            reopen()
        }

        override fun onError(camera: CameraDevice, error: Int) {
            Log.e(TAG, "camera error $error")
            camera.close()
            device = null
            reopen()
        }
    }

    private fun reopen() {
        Log.i(TAG, "reopen scheduled in 800ms (isRunning=$isRunning)")
        if (isRunning) handler?.postDelayed({ if (isRunning) openInternal() }, 800)
    }

    private fun buildRequest(camera: CameraDevice, surface: Surface): CaptureRequest {
        val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        builder.addTarget(surface)
        applyControls(builder)
        builder.set(
            CaptureRequest.CONTROL_AF_MODE,
            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
        )
        builder.set(CaptureRequest.JPEG_QUALITY, effectiveQuality.toByte())
        pickFpsRange(effectiveFps)?.let {
            builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, it)
        }
        val orientation = jpegOrientation(cameraId)
        if (orientation != null) {
            builder.set(CaptureRequest.JPEG_ORIENTATION, orientation)
        }
        return builder.build()
    }

    /**
     * Hướng JPEG để ảnh luôn thẳng đứng theo cách cầm máy (giống app chụp ảnh).
     * Công thức chuẩn: camera sau (sensor + xoay thiết bị), camera trước (sensor - xoay thiết bị).
     */
    private fun jpegOrientation(id: String?): Int? {
        val sensor = sensorOrientation(id) ?: return null
        return jpegOrientation(sensor, front, deviceOrientationDeg)
    }

    private fun applyControls(builder: CaptureRequest.Builder) {
        val expComp = mapBrightnessToExposure()
        controls.applyTo(builder, expComp)
    }

    private fun mapBrightnessToExposure(): Int {
        val caps = capabilities ?: return controls.exposureComp
        val lo = caps.minExposureComp
        val hi = caps.maxExposureComp
        if (lo == 0 && hi == 0) return controls.exposureComp
        val t = (controls.brightness - 50) / 50.0
        val scaled = if (t >= 0) t * hi else t * -lo
        return (controls.exposureComp + scaled.toInt()).coerceIn(lo, hi)
    }

    @Suppress("DEPRECATION")
    private fun createSession(camera: CameraDevice) {
        val surface = reader?.surface ?: return
        val request = buildRequest(camera, surface)
        camera.createCaptureSession(
            listOf(surface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) {
                    Log.i(TAG, "session onConfigured")
                    session = s
                    runCatching { s.setRepeatingRequest(request, null, handler) }
                        .onFailure { Log.e(TAG, "setRepeatingRequest failed", it) }
                    // Khôi phục trạng thái đèn sau khi (mở lại) phiên
                    if (controls.torch) setTorch(true)
                }

                override fun onConfigureFailed(s: CameraCaptureSession) {
                    Log.e(TAG, "session configure failed")
                }
            },
            handler
        )
    }

    private fun updateRepeating() {
        val s = session ?: return
        val c = device ?: return
        val surface = reader?.surface ?: return
        Log.i(TAG, "updateRepeating fps=$effectiveFps q=$effectiveQuality torch=${controls.torch}")
        val request = buildRequest(c, surface)
        handler?.post {
            runCatching { s.setRepeatingRequest(request, null, handler) }
        }
    }

    private fun pickFpsRange(fps: Int): Range<Int>? {
        val id = cameraId ?: return null
        val ranges = manager.getCameraCharacteristics(id)
            .get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) ?: return null
        return ranges.minByOrNull { kotlin.math.abs(it.upper - fps) }
    }

    private fun sensorOrientation(id: String?): Int? {
        id ?: return null
        return manager.getCameraCharacteristics(id)
            .get(CameraCharacteristics.SENSOR_ORIENTATION)
    }

    private fun onFrame(image: Image?) {
        if (image == null) return
        try {
            val now = System.nanoTime()
            val fps = effectiveFps.coerceAtLeast(1)
            val minIntervalNs = 1_000_000_000L / fps
            if (now - lastPublishNs < minIntervalNs) return
            lastPublishNs = now
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            broadcaster.publish(bytes)
        } catch (t: Throwable) {
            Log.e(TAG, "onFrame failed", t)
        } finally {
            image.close()
        }
    }

    fun applyControl(action: ControlAction) {
        when (action) {
            is ControlAction.SetFlash -> setTorch(action.on)
            is ControlAction.SetCameraFront -> if (action.front != front) switchCamera(action.front)
            is ControlAction.SetResolution -> {
                config = config.copy(width = action.w, height = action.h)
                restartSession()
            }

            is ControlAction.SetQuality -> {
                config = config.copy(jpegQuality = action.q)
                effectiveQuality = action.q
                updateRepeating()
            }

            is ControlAction.SetFps -> {
                config = config.copy(fps = action.fps)
                effectiveFps = action.fps
                updateRepeating()
            }

            else -> {
                controls.handle(action)
                updateRepeating()
            }
        }
    }

    fun switchCamera(front: Boolean) {
        this.front = front
        restart()
    }

    fun setTorch(on: Boolean) {
        controls.torch = on
        if (session != null) {
            // Trong lúc streaming: setTorchMode() bị CAMERA_IN_USE, phải dùng
            // FLASH_MODE_TORCH trong repeating request.
            Log.i(TAG, "setTorch(in-session) on=$on")
            updateRepeating()
        } else {
            val id = cameraId ?: chooseCameraId(front)
            if (id == null) {
                Log.w(TAG, "setTorch on=$on but no camera id")
                return
            }
            Log.i(TAG, "setTorchMode id=$id on=$on")
            runCatching { manager.setTorchMode(id, on) }
                .onFailure { Log.e(TAG, "setTorchMode failed", it) }
        }
    }

    fun setEffective(fps: Int, quality: Int) {
        if (fps == effectiveFps && quality == effectiveQuality) return
        effectiveFps = fps
        effectiveQuality = quality
        updateRepeating()
    }

    fun updateConfig(c: StreamConfig) {
        val sizeChanged = c.width != config.width || c.height != config.height
        config = c
        effectiveFps = c.fps
        effectiveQuality = c.jpegQuality
        if (sizeChanged) restartSession() else updateRepeating()
    }

    private fun restartSession() {
        Log.w(TAG, "restartSession")
        handler?.post {
            session?.close()
            device?.close()
            session = null
            device = null
            if (isRunning) openInternal()
        }
    }

    private fun restart() {
        if (!isRunning) return
        stop()
        start(front)
    }

    fun stop() {
        Log.w(TAG, "stop (was running=$isRunning)")
        isRunning = false
        runCatching { orientationListener.disable() }
        runCatching { session?.close() }
        runCatching { device?.close() }
        runCatching { reader?.close() }
        // Đóng phiên trước để nhả đèn; sau đó tắt torch qua API như phương án dự phòng.
        if (controls.torch) {
            val id = cameraId
            if (id != null) runCatching { manager.setTorchMode(id, false) }
            controls.torch = false
        }
        session = null
        device = null
        reader = null
        thread?.quitSafely()
        thread = null
        handler = null
    }

    companion object {
        private const val TAG = "CameraController"

        /** Làm tròn góc của OrientationEventListener về bội số gần nhất của 90 (0/90/180/270). */
        fun snapOrientation(orientationDeg: Int): Int =
            (((orientationDeg + 45) / 90) * 90) % 360

        /**
         * Hướng JPEG cần ghi để ảnh thẳng đứng theo cách cầm máy.
         * Camera sau: (sensor + xoay thiết bị); camera trước: (sensor - xoay thiết bị).
         */
        fun jpegOrientation(sensorOrientation: Int, front: Boolean, deviceOrientationDeg: Int): Int =
            if (front) {
                (sensorOrientation - deviceOrientationDeg + 360) % 360
            } else {
                (sensorOrientation + deviceOrientationDeg) % 360
            }

        /** Kích thước ảnh hiển thị sau khi áp dụng hướng JPEG (đổi chỗ rộng/cao khi xoay 90/270). */
        fun displaySize(
            width: Int,
            height: Int,
            sensorOrientation: Int,
            front: Boolean,
            deviceOrientationDeg: Int,
        ): Pair<Int, Int> {
            val orient = jpegOrientation(sensorOrientation, front, deviceOrientationDeg)
            return if (orient % 180 == 90) height to width else width to height
        }
    }
}
