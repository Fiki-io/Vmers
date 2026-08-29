package com.vmers.app.core

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

object VMManager {

    private lateinit var appContext: Context
    private val instances = ConcurrentHashMap<String, VMInstance>()
    var activeInstanceId: String = "vm0"

    fun initialize(context: Context) {
        appContext = context.applicationContext
        // Initialize default instance
        val defaultConfig = VMConfig(id = "vm0", name = "Android 15 (ARM64)")
        instances["vm0"] = VMInstance(appContext, defaultConfig)
    }

    fun getInstance(id: String = activeInstanceId): VMInstance? {
        return instances[id]
    }

    fun getAllInstances(): List<VMInstance> {
        return instances.values.toList()
    }

    fun createInstance(config: VMConfig): VMInstance {
        val instance = VMInstance(appContext, config)
        instances[config.id] = instance
        return instance
    }

    fun removeInstance(id: String) {
        instances[id]?.stopVM()
        instances[id]?.vmBaseDir?.deleteRecursively()
        instances.remove(id)
    }
}
