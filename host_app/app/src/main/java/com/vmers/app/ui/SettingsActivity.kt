package com.vmers.app.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.vmers.app.R
import com.vmers.app.core.VMManager

class SettingsActivity : AppCompatActivity() {

    private lateinit var swRoot: Switch
    private lateinit var swGles: Switch
    private lateinit var etWidth: EditText
    private lateinit var etHeight: EditText
    private lateinit var etDpi: EditText
    private lateinit var etModel: EditText
    private lateinit var btnSave: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        swRoot = findViewById(R.id.sw_root)
        swGles = findViewById(R.id.sw_gles)
        etWidth = findViewById(R.id.et_res_width)
        etHeight = findViewById(R.id.et_res_height)
        etDpi = findViewById(R.id.et_dpi)
        etModel = findViewById(R.id.et_fake_model)
        btnSave = findViewById(R.id.btn_save_settings)

        val vm = VMManager.getInstance()
        if (vm != null) {
            val cfg = vm.config
            swRoot.isChecked = cfg.enableRoot
            swGles.isChecked = cfg.enableGlesHw
            etWidth.setText(cfg.width.toString())
            etHeight.setText(cfg.height.toString())
            etDpi.setText(cfg.dpi.toString())
            etModel.setText(cfg.fakeModel)
        }

        btnSave.setOnClickListener {
            if (vm != null) {
                val cfg = vm.config
                cfg.enableRoot = swRoot.isChecked
                cfg.enableGlesHw = swGles.isChecked
                cfg.width = etWidth.text.toString().toIntOrNull() ?: 1080
                cfg.height = etHeight.text.toString().toIntOrNull() ?: 2400
                cfg.dpi = etDpi.text.toString().toIntOrNull() ?: 420
                cfg.fakeModel = etModel.text.toString()
                Toast.makeText(this, "Settings saved for Android 15 VM.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
