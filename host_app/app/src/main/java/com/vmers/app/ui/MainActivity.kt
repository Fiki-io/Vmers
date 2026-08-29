package com.vmers.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.vmers.app.R
import com.vmers.app.core.VMManager
import com.vmers.app.service.FloatingWindowService
import com.vmers.app.service.VMCoreService

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnBoot: Button
    private lateinit var btnSettings: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tv_vm_status)
        btnBoot = findViewById(R.id.btn_boot_vm)
        btnSettings = findViewById(R.id.btn_vm_settings)

        checkOverlayPermission()
        updateUI()

        btnBoot.setOnClickListener {
            val vm = VMManager.getInstance()
            if (vm != null) {
                if (!vm.isRunning) {
                    val serviceIntent = Intent(this, VMCoreService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent)
                    } else {
                        startService(serviceIntent)
                    }
                    startService(Intent(this, FloatingWindowService::class.java))
                }

                // Open Display
                val displayIntent = Intent(this, VMDisplayActivity::class.java)
                startActivity(displayIntent)
            }
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }
    }

    private fun updateUI() {
        val vm = VMManager.getInstance()
        if (vm != null && vm.isRunning) {
            tvStatus.text = "Status: Online (Android 15 ARM64)"
            btnBoot.text = "Open VM Screen"
        } else {
            tvStatus.text = "Status: Offline"
            btnBoot.text = "Boot Android 15"
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }
}
