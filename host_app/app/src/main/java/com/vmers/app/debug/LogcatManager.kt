package com.vmers.app.debug

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

enum class LogLevel {
    VERBOSE, DEBUG, INFO, WARN, ERROR, FATAL
}

data class LogEntry(
    val timestamp: String,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val raw: String
)

object LogcatManager {
    private const val TAG = "Vmers-Logger"
    private const val LOG_FILE_NAME = "vmers_persistent_history.log"
    private val logs = CopyOnWriteArrayList<LogEntry>()
    private val listeners = CopyOnWriteArrayList<(LogEntry) -> Unit>()
    private var appContext: Context? = null
    private val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private val fileLock = Any()

    @Volatile
    private var isCapturing = false

    fun init(context: Context) {
        appContext = context.applicationContext
        loadLogsFromDisk()
    }

    private fun getLogFile(): File? {
        val ctx = appContext ?: return null
        val logDir = File(ctx.filesDir, "logs")
        if (!logDir.exists()) logDir.mkdirs()
        return File(logDir, LOG_FILE_NAME)
    }

    private fun loadLogsFromDisk() {
        val file = getLogFile() ?: return
        if (!file.exists()) return

        try {
            BufferedReader(FileReader(file)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val l = line ?: continue
                    parseRawLine(l)?.let { logs.add(it) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load logs from disk: ${e.message}")
        }
    }

    private fun parseRawLine(line: String): LogEntry? {
        // Expected format: [timestamp][LEVEL][TAG] message
        return try {
            if (line.startsWith("[")) {
                val parts = line.split("]", limit = 4)
                if (parts.size >= 4) {
                    val time = parts[0].removePrefix("[")
                    val levelStr = parts[1].removePrefix("[")
                    val tag = parts[2].removePrefix("[")
                    val msg = parts[3].trim()
                    val level = try { LogLevel.valueOf(levelStr) } catch (e: Exception) { LogLevel.INFO }
                    return LogEntry(time, level, tag, msg, line)
                }
            }
            LogEntry(dateFormat.format(Date()), LogLevel.INFO, "LOG", line, line)
        } catch (e: Exception) {
            null
        }
    }

    private fun writeToDisk(entry: LogEntry) {
        synchronized(fileLock) {
            val file = getLogFile() ?: return
            try {
                FileWriter(file, true).use { writer ->
                    writer.write("[${entry.timestamp}][${entry.level}][${entry.tag}] ${entry.message}\n")
                    writer.flush()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed writing to log file: ${e.message}")
            }
        }
    }

    fun startSystemLogCapture(context: Context) {
        if (isCapturing) return
        isCapturing = true

        thread(name = "Vmers-SysLog", isDaemon = true) {
            try {
                val process = Runtime.getRuntime().exec("logcat -v time *:W")
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String? = null
                while (isCapturing && reader.readLine().also { line = it } != null) {
                    line?.let { raw ->
                        if (raw.contains("SIGSEGV", true) || raw.contains("Signal 11", true) || raw.contains("denied", true) || raw.contains("FATAL", true)) {
                            parseAndRecord("SYSTEM", raw)
                        }
                    }
                }
            } catch (ignored: Exception) {}
        }
    }

    fun logEngineOutput(line: String) {
        val level = when {
            line.contains("SIGSEGV", ignoreCase = true) || line.contains("Signal 11", ignoreCase = true) || line.contains("Fatal", ignoreCase = true) -> LogLevel.FATAL
            line.contains("error", ignoreCase = true) || line.contains("failed", ignoreCase = true) || line.contains("denied", ignoreCase = true) -> LogLevel.ERROR
            line.contains("warn", ignoreCase = true) || line.contains("avc:", ignoreCase = true) -> LogLevel.WARN
            else -> LogLevel.INFO
        }
        val entry = LogEntry(
            timestamp = dateFormat.format(Date()),
            level = level,
            tag = "CONTAINER",
            message = line,
            raw = "[CONTAINER] $line"
        )
        addAndPersist(entry)
    }

    fun logInfo(tag: String, msg: String) {
        val entry = LogEntry(dateFormat.format(Date()), LogLevel.INFO, tag, msg, "[$tag] $msg")
        addAndPersist(entry)
        Log.i(tag, msg)
    }

    fun logWarn(tag: String, msg: String) {
        val entry = LogEntry(dateFormat.format(Date()), LogLevel.WARN, tag, msg, "[$tag] $msg")
        addAndPersist(entry)
        Log.w(tag, msg)
    }

    fun logError(tag: String, msg: String, tr: Throwable? = null) {
        val fullMsg = if (tr != null) "$msg\n${Log.getStackTraceString(tr)}" else msg
        val entry = LogEntry(dateFormat.format(Date()), LogLevel.ERROR, tag, fullMsg, "[$tag] $fullMsg")
        addAndPersist(entry)
        Log.e(tag, fullMsg, tr)
    }

    fun logFatal(tag: String, msg: String) {
        val entry = LogEntry(dateFormat.format(Date()), LogLevel.FATAL, tag, msg, "[FATAL][$tag] $msg")
        addAndPersist(entry)
        Log.e(tag, "FATAL CRASH: $msg")
    }

    private fun parseAndRecord(source: String, rawLine: String) {
        val level = when {
            rawLine.contains(" F ") || rawLine.contains("SIGSEGV") || rawLine.contains("Signal 11") -> LogLevel.FATAL
            rawLine.contains(" E ") || rawLine.contains("denied") -> LogLevel.ERROR
            else -> LogLevel.WARN
        }
        val entry = LogEntry(dateFormat.format(Date()), level, source, rawLine, rawLine)
        addAndPersist(entry)
    }

    private fun addAndPersist(entry: LogEntry) {
        logs.add(entry)
        writeToDisk(entry)
        for (listener in listeners) {
            listener.invoke(entry)
        }
    }

    fun getLogs(): List<LogEntry> = logs.toList()

    fun getAllLogsText(): String {
        val sb = StringBuilder()
        for (entry in logs) {
            sb.append("[${entry.timestamp}][${entry.level}][${entry.tag}] ${entry.message}\n")
        }
        return sb.toString()
    }

    fun addListener(listener: (LogEntry) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (LogEntry) -> Unit) {
        listeners.remove(listener)
    }

    fun clearLogs() {
        logs.clear()
        synchronized(fileLock) {
            val file = getLogFile()
            if (file != null && file.exists()) {
                file.writeText("")
            }
        }
    }

    fun getExportFile(context: Context): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val exportFile = File(context.getExternalFilesDir(null) ?: context.filesDir, "vmers_crash_dump_$timeStamp.txt")
        exportFile.writeText(getAllLogsText())
        return exportFile
    }
}
