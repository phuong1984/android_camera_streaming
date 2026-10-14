package com.bapi.ipcamera

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bapi.ipcamera.config.SettingsStore
import com.bapi.ipcamera.databinding.ActivityMainBinding
import com.bapi.ipcamera.service.StreamState
import com.bapi.ipcamera.service.StreamingService
import com.bapi.ipcamera.stream.FrameBroadcaster
import com.bapi.ipcamera.ui.PreviewDecoder

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var store: SettingsStore

    @Volatile
    private var previewRunning = false

    @Volatile
    private var previewGeneration = 0

    private var permissionFlowInProgress = false
    private var updatingFlashSwitch = false
    private var previewThread: Thread? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        store = SettingsStore(this)
        StreamState.frontCamera.value = store.load().frontCamera
        bindUiState()
        setupPreview()
        requestPermissionsIfNeeded()

        binding.btnToggle.setOnClickListener {
            if (StreamState.running.value == true) {
                StreamingService.stop(this)
            } else {
                if (hasCameraPermission()) {
                    if (binding.swPreview.isChecked) {
                        enablePreview()
                    } else {
                        StreamingService.start(this)
                    }
                } else {
                    requestPermissionsIfNeeded()
                }
            }
        }

        binding.swFlash.setOnCheckedChangeListener { _, checked ->
            if (updatingFlashSwitch) return@setOnCheckedChangeListener
            if (StreamState.torchOn.value == checked) return@setOnCheckedChangeListener
            StreamState.torchOn.value = checked
            StreamingService.setFlash(this, checked)
        }
        binding.swNight.setOnCheckedChangeListener { _, checked ->
            if (StreamState.nightMode.value == checked) return@setOnCheckedChangeListener
            StreamState.nightMode.value = checked
            StreamingService.setNight(this, checked)
        }
        binding.btnSwitchCamera.setOnClickListener {
            val front = !(StreamState.frontCamera.value ?: false)
            StreamState.frontCamera.value = front
            store.save(store.load().copy(frontCamera = front))
            StreamingService.switchCamera(this, front)
        }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnCopyUrl.setOnClickListener {
            val addr = StreamState.address.value.orEmpty()
            if (addr.isBlank()) {
                Toast.makeText(this, R.string.label_ip, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val url = "http://$addr/stream"
            val clipboard = getSystemService(android.content.ClipboardManager::class.java)
            clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("ip_camera_url", url))
            Toast.makeText(this, "URL copied: $url", Toast.LENGTH_SHORT).show()
        }

        StreamState.running.observe(this) { running ->
            binding.btnToggle.text =
                getString(if (running) R.string.stop_streaming else R.string.start_streaming)
            binding.tvStatus.text =
                getString(if (running) R.string.status_running else R.string.status_stopped)
            if (running && binding.swPreview.isChecked) {
                StreamState.previewActive.value = true
                startPreviewLoop()
            } else if (!running && binding.swPreview.isChecked) {
                stopPreview()
            }
        }
        StreamState.address.observe(this) { addr ->
            binding.tvAddress.text = getString(R.string.label_ip) + ": " + addr
        }
        StreamState.clients.observe(this) { count ->
            binding.tvClients.text = getString(R.string.label_clients) + ": " + count
        }
        StreamState.torchOn.observe(this) { enabled ->
            updateFlashUi(enabled, StreamState.frontCamera.value == true)
        }
        StreamState.nightMode.observe(this) { enabled ->
            if (binding.swNight.isChecked != enabled) {
                binding.swNight.setOnCheckedChangeListener(null)
                binding.swNight.isChecked = enabled
                binding.swNight.setOnCheckedChangeListener { _, checked ->
                    if (StreamState.nightMode.value == checked) return@setOnCheckedChangeListener
                    StreamState.nightMode.value = checked
                    StreamingService.setNight(this, checked)
                }
            }
        }
        StreamState.frontCamera.observe(this) { front ->
            updateCameraUi(front)
            updateFlashUi(StreamState.torchOn.value == true, front)
        }
    }

    override fun onStart() {
        super.onStart()
        if (binding.swPreview.isChecked) enablePreview()
    }

    override fun onStop() {
        stopPreview()
        super.onStop()
    }

    private fun setupPreview() {
        val enabled = store.loadPreviewEnabled()
        binding.swPreview.isChecked = enabled
        updatePreviewVisibility(enabled)
        binding.swPreview.setOnCheckedChangeListener { _, checked ->
            setPreviewEnabled(checked)
        }
    }

    private fun setPreviewEnabled(enabled: Boolean) {
        store.savePreviewEnabled(enabled)
        updatePreviewVisibility(enabled)
        if (enabled) enablePreview() else stopPreview()
    }

    private fun updatePreviewVisibility(enabled: Boolean) {
        binding.previewContainer.visibility = if (enabled) View.VISIBLE else View.GONE
    }

    /** Bật preview: giữ camera sống và khởi động streaming nếu chưa chạy. */
    private fun enablePreview() {
        if (!hasCameraPermission()) {
            requestPermissionsIfNeeded()
            return
        }
        StreamState.previewActive.value = true
        if (StreamState.running.value != true) StreamingService.start(this)
        startPreviewLoop()
    }

    private fun stopPreview() {
        previewGeneration++
        StreamState.previewActive.value = false
        previewRunning = false
        previewThread?.interrupt()
        previewThread = null
        binding.ivPreview.setImageDrawable(null)
    }

    private fun startPreviewLoop() {
        if (previewRunning) return
        previewRunning = true
        val generation = previewGeneration
        val maxDim = previewMaxDim()
        previewThread = Thread {
            var activeBroadcaster: FrameBroadcaster? = null
            var seq = -1L
            var lastShown = 0L
            while (previewRunning && generation == previewGeneration) {
                val b = StreamState.broadcaster
                if (b == null) {
                    try {
                        Thread.sleep(200)
                    } catch (_: InterruptedException) {
                        break
                    }
                    continue
                }
                if (b !== activeBroadcaster) {
                    activeBroadcaster = b
                    seq = b.currentSeq()
                    continue
                }
                val pair = b.awaitFrame(seq, 500) ?: continue
                if (StreamState.broadcaster !== b) continue
                seq = pair.second
                val now = System.currentTimeMillis()
                if (now - lastShown < PREVIEW_MIN_INTERVAL_MS) continue
                lastShown = now
                val bmp = PreviewDecoder.decode(pair.first, maxDim) ?: continue
                runOnUiThread {
                    if (previewRunning && generation == previewGeneration &&
                        StreamState.broadcaster === b
                    ) {
                        binding.ivPreview.setImageBitmap(bmp)
                    } else {
                        bmp.recycle()
                    }
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun previewMaxDim(): Int {
        val side = maxOf(binding.previewContainer.width, binding.previewContainer.height)
        return if (side > 0) side else (resources.displayMetrics.density * 300).toInt()
    }

    private fun bindUiState() {
        val front = StreamState.frontCamera.value ?: false
        updateCameraUi(front)
        binding.swFlash.isChecked = StreamState.torchOn.value ?: false
        binding.swNight.isChecked = StreamState.nightMode.value ?: false
    }

    private fun updateCameraUi(front: Boolean) {
        binding.btnSwitchCamera.text =
            getString(R.string.switch_camera) + " (" +
                getString(if (front) R.string.camera_front else R.string.camera_rear) + ")"
    }

    private fun updateFlashUi(torchOn: Boolean, frontCamera: Boolean) {
        binding.swFlash.isEnabled = !frontCamera
        val checked = torchOn && !frontCamera
        if (binding.swFlash.isChecked != checked) {
            updatingFlashSwitch = true
            binding.swFlash.isChecked = checked
            updatingFlashSwitch = false
        }
        if (frontCamera && torchOn) {
            StreamState.torchOn.value = false
            StreamingService.setFlash(this, false)
        }
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestPermissionsIfNeeded() {
        if (permissionFlowInProgress) return
        val missing = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) return

        permissionFlowInProgress = true
        val shouldExplain = missing.any { shouldShowRequestPermissionRationale(it) }
        val request = {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQ_PERMS)
        }

        if (shouldExplain) {
            AlertDialog.Builder(this)
                .setTitle(R.string.permission_needed)
                .setMessage(R.string.permission_rationale)
                .setPositiveButton(android.R.string.ok) { _, _ -> request() }
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    permissionFlowInProgress = false
                }
                .setOnCancelListener { permissionFlowInProgress = false }
                .show()
        } else {
            request()
        }
    }

    private fun requiredPermissions(): List<String> = buildList {
        add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMS) {
            permissionFlowInProgress = false
            if (!hasCameraPermission()) {
                if (binding.swPreview.isChecked) setPreviewEnabled(false)
                AlertDialog.Builder(this)
                    .setTitle(R.string.permission_needed)
                    .setMessage(R.string.permission_rationale)
                    .setPositiveButton(R.string.open_settings) { _, _ ->
                        openAppSettings()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            } else {
                if (binding.swPreview.isChecked) enablePreview()
            }
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    companion object {
        private const val REQ_PERMS = 42

        /** Khoảng nghỉ tối thiểu giữa hai lần vẽ preview (~10fps). */
        private const val PREVIEW_MIN_INTERVAL_MS = 100L
    }
}
