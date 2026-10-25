package com.shraddha.assistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Low battery broadcasts are delivered by Android even when the Activity is not open. */
class ShraddhaBatteryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = context.getSharedPreferences("assistant", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("battery_alerts_enabled", false)) return
        when (intent?.action) {
            Intent.ACTION_BATTERY_LOW -> ShraddhaEventNotifications.batteryLow(context)
            Intent.ACTION_BATTERY_OKAY -> ShraddhaEventNotifications.clearBatteryLow(context)
            Intent.ACTION_POWER_DISCONNECTED -> prefs.edit().putBoolean("battery_full_notified", false).apply()
        }
    }
}
