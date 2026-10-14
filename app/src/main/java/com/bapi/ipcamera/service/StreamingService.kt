package com.bapi.ipcamera.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.bapi.ipcamera.MainActivity
import com.bapi.ipcamera.R
import com.bapi.ipcamera.camera.CameraController
import com.bapi.ipcamera.config.SettingsStore
import com.bapi.ipcamera.config.StreamConfig
import com.bapi.ipcamera.http.ControlAction
import com.bapi.ipcamera.http.HttpServer
import com.bapi.ipcamera.http.StreamControlHandler
import com.bapi.ipcamera.net.NetworkInfo
import com.bapi.ipcamera.stream.AdaptiveController
import com.bapi.ipcamera.stream.FrameBroadcaster
import com.bapi.ipcamera.stream.StreamStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class StreamingService : Service(), StreamControlHandler {

    private val broadcaster = FrameBroadcaster()
    private lateinit var store: SettingsStore
    private lateinit var camera: CameraController
    private var http: HttpServer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var lastClientSeenMs: Long = 0
    private var adaptive: AdaptiveController? = null

    override fun onCreate() {
        super.onCreate()
        store = SettingsStore(this)
        camera = CameraController(this, broadcaster)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        if (http == null) startServer()
        acquireWakeLock()
        when (intent?.action) {
            ACTION_FLASH -> onControl(ControlAction.SetFlash(intent.getBooleanExtra(EXTRA_ON, false)))
            ACTION_NIGHT -> onControl(ControlAction.SetNight(intent.getBooleanExtra(EXTRA_ON, false)))
            ACTION_SWITCH -> onControl(
                ControlAction.SetCameraFront(intent.getBooleanExtra(EXTRA_FRONT, false))
            )
            ACTION_APPLY_CONFIG -> applyServiceConfig()
        }
        return START_STICKY
    }

    /** Áp dụng lại cấu hình đang chạy mà không dựng lại HTTP server/phiên stream. */
    private fun applyServiceConfig() {
        val cfg = store.load()
        Log.i(TAG, "applyServiceConfig fps=${cfg.fps} q=${cfg.jpegQuality} ${cfg.width}x${cfg.height}")
        StreamState.frontCamera.postValue(cfg.frontCamera)
        camera.updateConfig(cfg)
        adaptive = if (cfg.autoFramesize) {
            AdaptiveController(maxFps = cfg.fps, maxQuality = cfg.jpegQuality)
        } else null
    }

    private fun startServer() {
        val cfg = store.load()
        StreamState.frontCamera.postValue(cfg.frontCamera)
        camera.updateConfig(cfg)
        val server = HttpServer(cfg.port, broadcaster, this)
        try {
            server.start(SOCKET_READ_TIMEOUT, true)
            http = server
        } catch (t: Throwable) {
            Log.e(TAG, "cannot bind port ${cfg.port}", t)
            StreamState.notifyAddress("port ${cfg.port} bị chiếm")
            return
        }
        StreamState.notifyRunning(true)
        StreamState.notifyAddress("${NetworkInfo.currentIpv4() ?: "?"}:${cfg.port}")
        idleLoop(cfg)
    }

    private fun idleLoop(cfg: StreamConfig) {
        adaptive = if (cfg.autoFramesize) {
            AdaptiveController(maxFps = cfg.fps, maxQuality = cfg.jpegQuality)
        } else null

        scope.launch {
            while (isActive) {
                // Đọc lại cấu hình để thay đổi cài đặt (keepWarm/port/grace) áp dụng ngay.
                val live = store.load()
                val count = broadcaster.clientCount()
                StreamState.notifyClients(count)
                val now = System.currentTimeMillis()
                if (count > 0) {
                    lastClientSeenMs = now
                    if (!camera.isRunning) camera.start(front = StreamState.frontCamera.value ?: false)
                } else {
                    val stop = IdlePolicy.shouldStopNow(
                        clientCount = count,
                        lastActivityMs = lastClientSeenMs,
                        nowMs = now,
                        graceMs = live.idleGraceSeconds * 1000L,
                        keepWarm = live.keepWarm,
                        cameraRunning = camera.isRunning,
                    )
                    if (stop) {
                        camera.stop()
                        adaptive?.reset()
                    }
                }
                // Cập nhật địa chỉ nếu IP đổi
                StreamState.notifyAddress(
                    "${NetworkInfo.currentIpv4() ?: "?"}:${live.port}"
                )
                delay(1000)
            }
        }

        // Vòng tự động điều chỉnh chất lượng/FPS
        scope.launch {
            while (isActive) {
                delay(3000)
                val a = adaptive ?: continue
                val (slow, total) = StreamStats.drainSlows()
                val before = a.fps to a.quality
                a.onWindow(slow, total, broadcaster.clientCount())
                if (before != a.fps to a.quality) {
                    camera.setEffective(a.fps, a.quality)
                }
            }
        }
    }

    private fun stopServer() {
        scope.cancel()
        runCatching { http?.stop() }
        http = null
        camera.stop()
        releaseWakeLock()
        StreamState.notifyRunning(false)
        StreamState.notifyClients(0)
        StreamState.notifyAddress("")
        StreamState.torchOn.postValue(false)
    }

    // ---- StreamControlHandler ----

    override fun onControl(action: ControlAction) {
        Log.i(TAG, "onControl $action")
        when (action) {
            is ControlAction.SetResolution -> {
                val c = store.load().copy(width = action.w, height = action.h)
                store.save(c)
                camera.updateConfig(c)
            }

            is ControlAction.SetQuality -> {
                store.save(store.load().copy(jpegQuality = action.q))
                camera.applyControl(action)
            }

            is ControlAction.SetFps -> {
                store.save(store.load().copy(fps = action.fps))
                camera.applyControl(action)
            }

            is ControlAction.SetFlash -> {
                camera.applyControl(action)
                StreamState.torchOn.postValue(action.on)
            }

            is ControlAction.SetNight -> {
                camera.applyControl(action)
                StreamState.notifyNight(action.on)
            }

            is ControlAction.SetCameraFront -> {
                camera.applyControl(action)
                StreamState.frontCamera.postValue(action.front)
            }

            else -> camera.applyControl(action)
        }
    }

    override fun statusJson(): String {
        val cfg = store.load()
        val caps = camera.capabilities
        val awb = caps?.awbNames?.joinToString(",") { "\"$it\"" } ?: ""
        val (dispW, dispH) = camera.displaySize()
        return buildString {
            append("{")
            append("\"running\":${camera.isRunning},")
            append("\"clients\":${broadcaster.clientCount()},")
            append("\"ip\":\"${NetworkInfo.currentIpv4() ?: ""}\",")
            append("\"port\":${cfg.port},")
            append("\"width\":${cfg.width},\"height\":${cfg.height},")
            append("\"displayWidth\":$dispW,\"displayHeight\":$dispH,")
            append("\"orientation\":${camera.orientationDegrees},")
            append("\"fps\":${camera.effectiveFps},\"quality\":${camera.effectiveQuality},")
            append("\"front\":${camera.front},")
            append("\"torch\":${camera.controls.torch},")
            append("\"hasFlash\":${caps?.hasFlash ?: false},")
            append("\"awb\":[$awb]")
            append("}")
        }
    }

    override fun currentConfig(): StreamConfig = store.load()

    override fun ensureCameraRunning() {
        Log.i(TAG, "ensureCameraRunning running=${camera.isRunning}")
        lastClientSeenMs = System.currentTimeMillis()
        if (!camera.isRunning) camera.start(front = StreamState.frontCamera.value ?: false)
    }

    // ---- Foreground / wake lock ----

    private fun startForegroundCompat() {
        val port = store.load().port
        val notification = buildNotification(port)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun buildNotification(port: Int): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pi = PendingIntent.getActivity(this, 0, intent, flags)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text, port))
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(channel)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ipcamera:stream").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i(TAG, "onTaskRemoved: app closed -> stop stream, camera, torch")
        stopServer()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "StreamingService"
        private const val CHANNEL_ID = "streaming"
        private const val NOTIF_ID = 1001
        private const val SOCKET_READ_TIMEOUT = 0

        fun start(context: Context) {
            val intent = Intent(context, StreamingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, StreamingService::class.java))
        }

        /** Áp dụng lại cấu hình cho service đang chạy (không khởi động lại, không cắt stream). */
        fun applyConfig(context: Context) {
            val intent = Intent(context, StreamingService::class.java).setAction(ACTION_APPLY_CONFIG)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun setFlash(context: Context, on: Boolean) = send(context, ACTION_FLASH, EXTRA_ON, on)
        fun setNight(context: Context, on: Boolean) = send(context, ACTION_NIGHT, EXTRA_ON, on)
        fun switchCamera(context: Context, front: Boolean) =
            send(context, ACTION_SWITCH, EXTRA_FRONT, front)

        private fun send(context: Context, action: String, key: String, value: Boolean) {
            val intent = Intent(context, StreamingService::class.java).apply {
                this.action = action
                putExtra(key, value)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        const val ACTION_FLASH = "com.bapi.ipcamera.FLASH"
        const val ACTION_NIGHT = "com.bapi.ipcamera.NIGHT"
        const val ACTION_SWITCH = "com.bapi.ipcamera.SWITCH"
        const val ACTION_APPLY_CONFIG = "com.bapi.ipcamera.APPLY_CONFIG"
        const val EXTRA_ON = "on"
        const val EXTRA_FRONT = "front"
    }
}
