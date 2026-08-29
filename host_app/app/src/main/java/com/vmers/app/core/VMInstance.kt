package com.vmers.app.core

import android.content.Context
import android.util.Log
import com.vmers.app.debug.LogcatManager
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
                    LogcatManager.logInfo(TAG, "Extracted native asset: $f -> ${dest.absolutePath}")
                } catch (e: Exception) {
                    LogcatManager.logWarn(TAG, "Asset bin/$f: ${e.message}")
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

        LogcatManager.logInfo(TAG, "Starting VM Instance ${config.id}...")
        
        // Initialize JNI native environment
        NativeEngine.initVMEnvironment(
            rootfsDir.absolutePath,
            config.width,
            config.height,
            config.dpi
        )

        // 1. Locate engine binary and shim library (Prefer nativeLibraryDir for Android 10+ W^X compliance)
        val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)
        val libEngine = File(nativeLibDir, "libvmers_engine.so")
        val libShim = File(nativeLibDir, "libvmlink_shim.so")

        val binDir = File(context.filesDir, "bin")
        val fileEngine = File(binDir, "vmers_engine")
        val fileShim = File(binDir, "libvmlink_shim.so")

        val enginePath = when {
            libEngine.exists() -> libEngine.absolutePath
            fileEngine.exists() -> fileEngine.absolutePath
            else -> "/data/local/tmp/vmers_engine"
        }

        val shimPath = when {
            libShim.exists() -> libShim.absolutePath
            fileShim.exists() -> fileShim.absolutePath
            else -> null
        }

        LogcatManager.logInfo(TAG, "Resolved Engine Binary: $enginePath (Shim: $shimPath)")

        // 2. Build command line with fallback strategies
        val commandCandidates = listOf(
            listOf(enginePath, rootfsDir.absolutePath),
            listOf("/system/bin/linker64", enginePath, rootfsDir.absolutePath),
            listOf(fileEngine.absolutePath, rootfsDir.absolutePath),
            listOf("/system/bin/linker64", fileEngine.absolutePath, rootfsDir.absolutePath)
        )

        var lastException: Exception? = null
        for (cmd in commandCandidates) {
            try {
                LogcatManager.logInfo(TAG, "Attempting start command: ${cmd.joinToString(" ")}")
                val pb = ProcessBuilder(cmd)
                pb.directory(rootfsDir)
                pb.redirectErrorStream(true)
                
                // Environment variables for guest container isolation
                val env = pb.environment()
                env["VMERS_ROOTFS"] = rootfsDir.absolutePath
                if (shimPath != null) {
                    env["LD_PRELOAD"] = shimPath
                }
                env["ANDROID_ROOT"] = "/system"
                env["ANDROID_DATA"] = "/data"
                env["ANDROID_ART_ROOT"] = "/apex/com.android.art"
                env["BOOTCLASSPATH"] = "/apex/com.android.art/javalib/core-oj.jar:/system/framework/framework.jar"

                val process = pb.start()
                vmProcess = process
                isRunning = true
                LogcatManager.logInfo(TAG, "VM Instance ${config.id} booted successfully with: ${cmd[0]}")

                // Stream container stdout/stderr to LogcatManager
                kotlin.concurrent.thread(name = "VM-Log-Reader", isDaemon = true) {
                    try {
                        val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
                        var line: String? = null
                        while (reader.readLine().also { line = it } != null) {
                            line?.let { LogcatManager.logEngineOutput(it) }
                        }
                    } catch (ignored: Exception) {
                    }
                }

                return true
            } catch (e: Exception) {
                lastException = e
                LogcatManager.logWarn(TAG, "Command failed (${cmd[0]}): ${e.message}")
            }
        }

        LogcatManager.logError(TAG, "All start attempts failed. Last error: ${lastException?.message}", lastException)
        isRunning = false
        return false
    }

    fun stopVM() {
        LogcatManager.logInfo(TAG, "Stopping VM Instance ${config.id}...")
        vmProcess?.destroy()
        vmProcess = null
        isRunning = false
    }
}
