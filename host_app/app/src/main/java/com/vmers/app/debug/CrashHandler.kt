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
        LogcatManager.logFatal("CRASH_HANDLER", "Uncaught Exception on Thread ${t.name} (id: ${t.id}): ${e.message}\n$stackTrace")
        
        try {
            val file = LogcatManager.exportLogsToFile(context)
            Log.e("Vmers-Crash", "Crash log exported to ${file.absolutePath}")
        } catch (ignored: Exception) {
        }

        defaultHandler?.uncaughtException(t, e)
    }

    companion object {
        fun init(context: Context) {
            CrashHandler(context)
            LogcatManager.startLogCapture(context)
        }
    }
}
