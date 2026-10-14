package com.bapi.ipcamera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bapi.ipcamera.config.SettingsStore
import com.bapi.ipcamera.databinding.ActivityMainBinding
import com.bapi.ipcamera.service.StreamState
import com.bapi.ipcamera.service.StreamingService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
            StreamingService.setFlash(this, checked)
        }
        binding.swNight.setOnCheckedChangeListener { _, checked ->
            StreamingService.setNight(this, checked)
        }
        binding.btnSwitchCamera.setOnClickListener {
            val front = !(StreamState.frontCamera.value ?: false)
            StreamState.frontCamera.postValue(front)
            StreamingService.switchCamera(this, front)
            binding.btnSwitchCamera.text =
                getString(R.string.switch_camera) + " (" +
                    getString(if (front) R.string.camera_front else R.string.camera_rear) + ")"
        }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
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
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestPermissionsIfNeeded() {
        val perms = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQ_PERMS)
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
                Toast.makeText(this, R.string.permission_needed, Toast.LENGTH_LONG).show()
            } else {
                // Đọc config lần đầu để tvAddress có dữ liệu
                SettingsStore(this).load()
            }
        }
    }

    companion object {
        private const val REQ_PERMS = 42
    }
}
