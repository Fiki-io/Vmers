package com.vmers.app.core

import android.content.Context
import android.util.Log
import java.io.File

class VMInstance(
    val context: Context,
    val config: VMConfig
) {
    private val TAG = "Vmers-Instance"

    val vmBaseDir: File = File(context.filesDir, "vm/${config.id}")
    val rootfsDir: File = File(vmBaseDir, "fs")
    val dataDir: File = File(rootfsDir, "data")
    val systemDir: File = File(rootfsDir, "system")

    @Volatile
    var isRunning: Boolean = false
        private set

    private var vmProcess: Process? = null

    init {
        vmBaseDir.mkdirs()
        rootfsDir.mkdirs()
    }

    fun isInstalled(): Boolean {
        val appProcess = File(rootfsDir, "system/bin/app_process64")
        return appProcess.exists() && appProcess.canExecute()
    }

    fun startVM(): Boolean {
        if (isRunning) return true

        Log.i(TAG, "Starting VM Instance ${config.id}...")
        
        // Initialize JNI native environment
        NativeEngine.initVMEnvironment(
            rootfsDir.absolutePath,
            config.width,
            config.height,
            config.dpi
        )

        // Locate engine binary
        val engineBin = File(context.applicationInfo.nativeLibraryDir, "libvmers_engine.so")
        val execPath = if (engineBin.exists()) engineBin.absolutePath else "/data/local/tmp/vmers_engine"

        try {
            val pb = ProcessBuilder(execPath, rootfsDir.absolutePath)
            pb.directory(rootfsDir)
            pb.redirectErrorStream(true)
            
            // Environment variables
            val env = pb.environment()
            env["ANDROID_ROOT"] = "/system"
            env["ANDROID_DATA"] = "/data"
            env["ANDROID_ART_ROOT"] = "/apex/com.android.art"
            env["BOOTCLASSPATH"] = "/apex/com.android.art/javalib/core-oj.jar:/system/framework/framework.jar"

            vmProcess = pb.start()
            isRunning = true
            Log.i(TAG, "VM Instance ${config.id} booted successfully.")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VM Instance: ${e.message}", e)
            isRunning = false
            return false
        }
    }

    fun stopVM() {
        Log.i(TAG, "Stopping VM Instance ${config.id}...")
        vmProcess?.destroy()
        vmProcess = null
        isRunning = false
    }
}
