package com.vmers.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vmers.app.R
import com.vmers.app.debug.LogEntry
import com.vmers.app.debug.LogLevel
import com.vmers.app.debug.LogcatManager

class LogcatActivity : AppCompatActivity() {

    private lateinit var rvLogcat: RecyclerView
    private lateinit var etSearch: EditText
    private lateinit var spinnerLevel: Spinner
    private lateinit var btnCopy: Button
    private lateinit var btnClear: Button
    private lateinit var btnExport: Button

    private val adapter = LogAdapter()
    private var currentFilterLevel = "ALL"
    private var searchQuery = ""
    private var isFirstLoad = true

    private val logListener: (LogEntry) -> Unit = {
        runOnUiThread {
            refreshFilteredList(autoScroll = false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logcat)

        // Handle System Window Insets to avoid status bar overlap
        val rootLayout = findViewById<View>(R.id.logcat_root_layout)
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                statusBarInsets.left + 16,
                statusBarInsets.top + 16,
                statusBarInsets.right + 16,
                statusBarInsets.bottom + 12
            )
            insets
        }

        rvLogcat = findViewById(R.id.rv_logcat)
        etSearch = findViewById(R.id.et_search_log)
        spinnerLevel = findViewById(R.id.spinner_log_level)
        btnCopy = findViewById(R.id.btn_copy_logs)
        btnClear = findViewById(R.id.btn_clear_logs)
        btnExport = findViewById(R.id.btn_export_logs)

        val layoutManager = LinearLayoutManager(this)
        rvLogcat.layoutManager = layoutManager
        rvLogcat.adapter = adapter

        setupSpinner()
        setupListeners()
        refreshFilteredList(autoScroll = true)

        LogcatManager.addListener(logListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        LogcatManager.removeListener(logListener)
    }

    private fun setupSpinner() {
        val levels = arrayOf("ALL", "💥 CRASH / SIGNAL 11", "❌ ERROR / FAILED", "⚠️ WARN / DENIED", "ℹ️ INFO")
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, levels)
        spinnerLevel.adapter = spinnerAdapter
        spinnerLevel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentFilterLevel = levels[position]
                refreshFilteredList(autoScroll = false)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupListeners() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim() ?: ""
                refreshFilteredList(autoScroll = false)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnCopy.setOnClickListener {
            val allText = LogcatManager.getAllLogsText()
            if (allText.isEmpty()) {
                Toast.makeText(this, "Log masih kosong.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Vmers Debug Logs", allText)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Semua log berhasil disalin ke Clipboard!", Toast.LENGTH_SHORT).show()
        }

        btnClear.setOnClickListener {
            LogcatManager.clearLogs()
            refreshFilteredList(autoScroll = false)
            Toast.makeText(this, "Log permanen telah dibersihkan.", Toast.LENGTH_SHORT).show()
        }

        btnExport.setOnClickListener {
            val file = LogcatManager.getExportFile(this)
            try {
                val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "Share Logcat Dump"))
            } catch (e: Exception) {
                Toast.makeText(this, "File tersimpan di: ${file.absolutePath}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun refreshFilteredList(autoScroll: Boolean) {
        val allLogs = LogcatManager.getLogs()
        val filtered = allLogs.filter { entry ->
            val matchLevel = when {
                currentFilterLevel.contains("CRASH") -> entry.level == LogLevel.FATAL || entry.raw.contains("SIGSEGV", true) || entry.raw.contains("Signal 11", true)
                currentFilterLevel.contains("ERROR") -> entry.level == LogLevel.ERROR || entry.level == LogLevel.FATAL || entry.raw.contains("failed", true)
                currentFilterLevel.contains("WARN") -> entry.level == LogLevel.WARN || entry.level == LogLevel.ERROR || entry.level == LogLevel.FATAL || entry.raw.contains("denied", true)
                currentFilterLevel.contains("INFO") -> entry.level != LogLevel.DEBUG && entry.level != LogLevel.VERBOSE
                else -> true
            }

            val matchSearch = if (searchQuery.isEmpty()) true else entry.raw.contains(searchQuery, ignoreCase = true)
            matchLevel && matchSearch
        }

        adapter.setItems(filtered)
        if (autoScroll || isFirstLoad) {
            if (filtered.isNotEmpty()) {
                rvLogcat.scrollToPosition(filtered.size - 1)
            }
            isFirstLoad = false
        }
    }

    private class LogAdapter : RecyclerView.Adapter<LogViewHolder>() {
        private var items = listOf<LogEntry>()

        fun setItems(newItems: List<LogEntry>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_logcat_line, parent, false)
            return LogViewHolder(view as TextView)
        }

        override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size
    }

    private class LogViewHolder(val tv: TextView) : RecyclerView.ViewHolder(tv) {
        fun bind(entry: LogEntry) {
            tv.text = "[${entry.timestamp}] [${entry.tag}] ${entry.message}"
            when {
                entry.level == LogLevel.FATAL || entry.raw.contains("SIGSEGV", true) || entry.raw.contains("Signal 11", true) -> {
                    tv.setTextColor(Color.parseColor("#FF4757")) // Bright Red
                }
                entry.level == LogLevel.ERROR || entry.raw.contains("denied", true) || entry.raw.contains("error", true) -> {
                    tv.setTextColor(Color.parseColor("#FFA502")) // Orange/Red
                }
                entry.level == LogLevel.WARN || entry.raw.contains("avc:", true) -> {
                    tv.setTextColor(Color.parseColor("#ECCC68")) // Yellow
                }
                entry.tag == "CONTAINER" -> {
                    tv.setTextColor(Color.parseColor("#70A1FF")) // Sky Blue
                }
                else -> {
                    tv.setTextColor(Color.parseColor("#A4B0BE")) // Gray/White
                }
            }
        }
    }
}
