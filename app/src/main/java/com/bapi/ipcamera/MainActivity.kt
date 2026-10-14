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
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bapi.ipcamera.config.SettingsStore
import com.bapi.ipcamera.databinding.ActivityMainBinding
import com.bapi.ipcamera.service.StreamState
import com.bapi.ipcamera.service.StreamingService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var store: SettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        store = SettingsStore(this)
        StreamState.frontCamera.value = store.load().frontCamera
        bindUiState()
        requestPermissionsIfNeeded()

        binding.btnToggle.setOnClickListener {
            if (StreamState.running.value == true) {
                StreamingService.stop(this)
            } else {
                if (hasCameraPermission()) {
                    StreamingService.start(this)
                } else {
                    requestPermissionsIfNeeded()
                }
            }
        }

        binding.swFlash.setOnCheckedChangeListener { _, checked ->
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
        }
        StreamState.address.observe(this) { addr ->
            binding.tvAddress.text = getString(R.string.label_ip) + ": " + addr
        }
        StreamState.clients.observe(this) { count ->
            binding.tvClients.text = getString(R.string.label_clients) + ": " + count
        }
        StreamState.torchOn.observe(this) { enabled ->
            if (binding.swFlash.isChecked != enabled) {
                binding.swFlash.setOnCheckedChangeListener(null)
                binding.swFlash.isChecked = enabled
                binding.swFlash.setOnCheckedChangeListener { _, checked ->
                    if (StreamState.torchOn.value == checked) return@setOnCheckedChangeListener
                    StreamState.torchOn.value = checked
                    StreamingService.setFlash(this, checked)
                }
            }
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
        }
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

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestPermissionsIfNeeded() {
        val missing = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) return

        val shouldExplain = missing.any { shouldShowRequestPermissionRationale(it) }
        val request = {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQ_PERMS)
        }

        if (shouldExplain) {
            AlertDialog.Builder(this)
                .setTitle(R.string.permission_needed)
                .setMessage(R.string.permission_rationale)
                .setPositiveButton(android.R.string.ok) { _, _ -> request() }
                .setNegativeButton(android.R.string.cancel, null)
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
            if (!hasCameraPermission()) {
                AlertDialog.Builder(this)
                    .setTitle(R.string.permission_needed)
                    .setMessage(R.string.permission_rationale)
                    .setPositiveButton(R.string.open_settings) { _, _ ->
                        openAppSettings()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            } else {
                store.load()
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
    }
}
