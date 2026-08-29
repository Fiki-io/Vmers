package com.vmers.app.ui

import android.app.ProgressDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.vmers.app.R
import com.vmers.app.core.VMManager
import com.vmers.app.service.FloatingWindowService
import com.vmers.app.service.VMCoreService
import java.io.File
import java.io.FileOutputStream
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnBoot: Button
    private lateinit var btnImportRom: Button
    private lateinit var btnSettings: Button

    private val selectRomLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { handleImportRomUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tv_vm_status)
        btnBoot = findViewById(R.id.btn_boot_vm)
        btnImportRom = findViewById(R.id.btn_import_rom)
        btnSettings = findViewById(R.id.btn_vm_settings)

        checkOverlayPermission()
        updateUI()

        btnBoot.setOnClickListener {
            val vm = VMManager.getInstance()
            if (vm != null) {
                if (!vm.isInstalled()) {
                    Toast.makeText(this, "ROM Android 15 belum terpasang. Silakan Import ROM .7z terlebih dahulu.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }

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

        btnImportRom.setOnClickListener {
            selectRomLauncher.launch("*/*")
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun handleImportRomUri(uri: Uri) {
        val dialog = ProgressDialog(this).apply {
            setMessage("Mengekstrak dan memasang ROM Android 15 ke container...")
            setCancelable(false)
            show()
        }

        thread {
            try {
                val vm = VMManager.getInstance()
                if (vm != null) {
                    val tempArchive = File(cacheDir, "rom_temp.7z")
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempArchive).use { output ->
                            input.copyTo(output)
                        }
                    }

                    // Extract using 7z command or unzip
                    val pb = ProcessBuilder("7z", "x", "-y", tempArchive.absolutePath, "-o${vm.rootfsDir.absolutePath}")
                    val p = pb.start()
                    p.waitFor()

                    tempArchive.delete()
                    runOnUiThread {
                        dialog.dismiss()
                        Toast.makeText(this, "ROM Android 15 Berhasil Dipasang!", Toast.LENGTH_SHORT).show()
                        updateUI()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    dialog.dismiss()
                    Toast.makeText(this, "Gagal memasang ROM: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
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
        if (vm != null) {
            if (vm.isRunning) {
                tvStatus.text = "Status: Online (Android 15 ARM64)"
                btnBoot.text = "Open VM Screen"
            } else if (vm.isInstalled()) {
                tvStatus.text = "Status: Ready (ROM Installed)"
                btnBoot.text = "Boot Android 15"
            } else {
                tvStatus.text = "Status: No ROM Installed"
                btnBoot.text = "Boot Android 15 (Requires ROM)"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }
}
