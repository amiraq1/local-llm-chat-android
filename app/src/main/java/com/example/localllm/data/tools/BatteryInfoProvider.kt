package com.example.localllm.data.tools

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

data class BatteryInfo(
    val level: Int,
    val scale: Int,
    val status: Int,
    val health: Int,
    val plugged: Int
)

interface BatteryInfoProvider {
    /** Returns the current battery snapshot, or null if the sticky broadcast is unavailable. */
    fun get(): BatteryInfo?
}

/** Production implementation — reads the sticky [Intent.ACTION_BATTERY_CHANGED] broadcast. */
class SystemBatteryInfoProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : BatteryInfoProvider {
    override fun get(): BatteryInfo? {
        val intent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ) ?: return null
        return BatteryInfo(
            level   = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
            scale   = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1),
            status  = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN),
            health  = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN),
            plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        )
    }
}
