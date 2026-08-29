package com.vmers.app.debug

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.BufferedReader
import java.io.File
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
    private const val TAG = "Vmers-Logcat"
    private const val MAX_LOGS = 5000

    private val logs = CopyOnWriteArrayList<LogEntry>()
    private val listeners = CopyOnWriteArrayList<(LogEntry) -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var isCapturing = false

    private val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    fun startLogCapture(context: Context) {
        if (isCapturing) return
        isCapturing = true

        // Capture system logcat in background thread
        thread(name = "LogcatCollector", isDaemon = true) {
            try {
                val process = Runtime.getRuntime().exec("logcat -v time *:V")
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String? = null
                while (isCapturing && reader.readLine().also { line = it } != null) {
                    line?.let { parseAndAddLog("SYS", it) }
                }
            } catch (e: Exception) {
                logError(TAG, "Logcat capture failed: ${e.message}")
            }
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
        addEntry(entry)
    }

    fun logInfo(tag: String, msg: String) {
        val entry = LogEntry(dateFormat.format(Date()), LogLevel.INFO, tag, msg, "[$tag] $msg")
        addEntry(entry)
        Log.i(tag, msg)
    }

    fun logWarn(tag: String, msg: String) {
        val entry = LogEntry(dateFormat.format(Date()), LogLevel.WARN, tag, msg, "[$tag] $msg")
        addEntry(entry)
        Log.w(tag, msg)
    }

    fun logError(tag: String, msg: String, tr: Throwable? = null) {
        val fullMsg = if (tr != null) "$msg\n${Log.getStackTraceString(tr)}" else msg
        val entry = LogEntry(dateFormat.format(Date()), LogLevel.ERROR, tag, fullMsg, "[$tag] $fullMsg")
        addEntry(entry)
        Log.e(tag, fullMsg, tr)
    }

    fun logFatal(tag: String, msg: String) {
        val entry = LogEntry(dateFormat.format(Date()), LogLevel.FATAL, tag, msg, "[FATAL][$tag] $msg")
        addEntry(entry)
        Log.e(tag, "FATAL CRASH: $msg")
    }

    private fun parseAndAddLog(source: String, rawLine: String) {
        val level = when {
            rawLine.contains(" F ") || rawLine.contains("SIGSEGV") || rawLine.contains("Signal 11") || rawLine.contains("backtrace:") -> LogLevel.FATAL
            rawLine.contains(" E ") || rawLine.contains("denied") || rawLine.contains("Fatal") -> LogLevel.ERROR
            rawLine.contains(" W ") || rawLine.contains("avc:") -> LogLevel.WARN
            rawLine.contains(" I ") -> LogLevel.INFO
            rawLine.contains(" D ") -> LogLevel.DEBUG
            else -> LogLevel.VERBOSE
        }

        val entry = LogEntry(
            timestamp = dateFormat.format(Date()),
            level = level,
            tag = source,
            message = rawLine,
            raw = rawLine
        )
        addEntry(entry)
    }

    private fun addEntry(entry: LogEntry) {
        if (logs.size >= MAX_LOGS) {
            logs.removeAt(0)
        }
        logs.add(entry)
        mainHandler.post {
            for (listener in listeners) {
                listener.invoke(entry)
            }
        }
    }

    fun getLogs(): List<LogEntry> = logs.toList()

    fun addListener(listener: (LogEntry) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (LogEntry) -> Unit) {
        listeners.remove(listener)
    }

    fun clearLogs() {
        logs.clear()
    }

    fun exportLogsToFile(context: Context): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val logFile = File(context.getExternalFilesDir(null) ?: context.filesDir, "vmers_crash_log_$timeStamp.txt")
        logFile.bufferedWriter().use { out ->
            out.write("================ VMERS SYSTEM & CONTAINER LOGCAT DUMP ================\n")
            out.write("Generated at: ${Date()}\n\n")
            for (entry in logs) {
                out.write("[${entry.timestamp}][${entry.level}][${entry.tag}] ${entry.message}\n")
            }
        }
        return logFile
    }
}
