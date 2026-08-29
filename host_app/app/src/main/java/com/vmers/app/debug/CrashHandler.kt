package com.vmers.app.debug

import android.content.Context
import android.util.Log

class CrashHandler private constructor(private val context: Context) : Thread.UncaughtExceptionHandler {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    init {
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        val stackTrace = Log.getStackTraceString(e)
        LogcatManager.logFatal("CRASH_HANDLER", "FATAL UNCAUGHT CRASH on Thread ${t.name} (id: ${t.id}): ${e.message}\n$stackTrace")
        
        try {
            val file = LogcatManager.getExportFile(context)
            Log.e("Vmers-Crash", "Emergency crash log written to ${file.absolutePath}")
        } catch (ignored: Exception) {
        }

        defaultHandler?.uncaughtException(t, e)
    }

    companion object {
        fun init(context: Context) {
            LogcatManager.init(context)
            CrashHandler(context)
            LogcatManager.startSystemLogCapture(context)
        }
    }
}
