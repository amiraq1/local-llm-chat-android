package com.example.localllm.data.tools

import android.os.Build
import javax.inject.Inject

data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val brand: String,
    val androidVersion: String,
    val sdkLevel: Int,
    val device: String,
    val hardware: String,
    val supportedAbis: List<String>
)

interface DeviceInfoProvider {
    fun get(): DeviceInfo
}

/** Production implementation — reads from [android.os.Build] static fields. */
class BuildDeviceInfoProvider @Inject constructor() : DeviceInfoProvider {
    override fun get() = DeviceInfo(
        manufacturer  = Build.MANUFACTURER,
        model         = Build.MODEL,
        brand         = Build.BRAND,
        androidVersion = Build.VERSION.RELEASE,
        sdkLevel      = Build.VERSION.SDK_INT,
        device        = Build.DEVICE,
        hardware      = Build.HARDWARE,
        supportedAbis = Build.SUPPORTED_ABIS.toList()
    )
}
