package com.vmers.app.core

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream

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
        extractEngineBinaries()
    }

    private fun extractEngineBinaries() {
        val binDir = File(context.filesDir, "bin")
        binDir.mkdirs()

        val files = listOf("vmers_engine", "libvmlink_shim.so")
        for (f in files) {
            val dest = File(binDir, f)
            if (!dest.exists() || dest.length() == 0L) {
                try {
                    context.assets.open("bin/$f").use { input ->
                        FileOutputStream(dest).use { output ->
                            input.copyTo(output)
                        }
                    }
                    dest.setExecutable(true, false)
                    dest.setReadable(true, false)
                    Log.i(TAG, "Extracted native asset: $f -> ${dest.absolutePath}")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not extract asset bin/$f: ${e.message}")
                }
            }
        }
    }

    fun isInstalled(): Boolean {
        val marker = File(rootfsDir, ".vmers_installed")
        val buildProp = File(rootfsDir, "system/build.prop")
        val appProcess = File(rootfsDir, "system/bin/app_process64")
        return marker.exists() || buildProp.exists() || appProcess.exists()
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
        val binDir = File(context.filesDir, "bin")
        val engineBin = File(binDir, "vmers_engine")
        val shimLib = File(binDir, "libvmlink_shim.so")
        val execPath = if (engineBin.exists()) engineBin.absolutePath else "/data/local/tmp/vmers_engine"

        try {
            val pb = ProcessBuilder(execPath, rootfsDir.absolutePath)
            pb.directory(rootfsDir)
            pb.redirectErrorStream(true)
            
            // Environment variables for guest container isolation
            val env = pb.environment()
            env["VMERS_ROOTFS"] = rootfsDir.absolutePath
            if (shimLib.exists()) {
                env["LD_PRELOAD"] = shimLib.absolutePath
            }
            env["ANDROID_ROOT"] = "/system"
            env["ANDROID_DATA"] = "/data"
            env["ANDROID_ART_ROOT"] = "/apex/com.android.art"
            env["BOOTCLASSPATH"] = "/apex/com.android.art/javalib/core-oj.jar:/system/framework/framework.jar"

            val process = pb.start()
            vmProcess = process
            isRunning = true
            Log.i(TAG, "VM Instance ${config.id} booted successfully.")

            // Stream container stdout/stderr to LogcatManager
            kotlin.concurrent.thread(name = "VM-Log-Reader", isDaemon = true) {
                try {
                    val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
                    var line: String? = null
                    while (reader.readLine().also { line = it } != null) {
                        line?.let { com.vmers.app.debug.LogcatManager.logEngineOutput(it) }
                    }
                } catch (ignored: Exception) {
                }
            }

            return true
        } catch (e: Exception) {
            com.vmers.app.debug.LogcatManager.logError(TAG, "Failed to start VM Instance: ${e.message}", e)
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
