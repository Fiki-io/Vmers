package com.vmers.app.core

import android.view.Surface

object NativeEngine {

    init {
        try {
            System.loadLibrary("vmers_jni")
        } catch (e: UnsatisfiedLinkError) {
            e.printStackTrace()
        }
    }

    external fun initVMEnvironment(rootfsPath: String, width: Int, height: Int, dpi: Int): Boolean
    external fun setSurface(surface: Surface?)
    external fun sendTouchEvent(action: Int, x: Int, y: Int, pointerId: Int)
    external fun chmodRecursively(targetPath: String, mode: Int): Int
}
