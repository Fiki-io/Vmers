package com.vmers.app.core

import java.io.Serializable

data class VMConfig(
    val id: String = "vm0",
    var name: String = "Android 15 Vanilla",
    var osVersion: String = "15.0",
    var apiLevel: Int = 35,
    var width: Int = 1080,
    var height: Int = 2400,
    var dpi: Int = 420,
    var refreshRate: Int = 60,
    var enableRoot: Boolean = true,
    var enableGapps: Boolean = false,
    var enableGlesHw: Boolean = true,
    var fakeModel: String = "Pixel 9 Pro",
    var fakeBrand: String = "Google",
    var fakeImei: String = "860123456789012"
) : Serializable
