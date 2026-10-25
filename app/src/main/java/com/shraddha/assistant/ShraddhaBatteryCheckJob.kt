package com.shraddha.assistant

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/** Periodic, low-impact check for full charge when the app UI is closed. */
class ShraddhaBatteryCheckJob : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        val prefs = getSharedPreferences("assistant", MODE_PRIVATE)
        if (!prefs.getBoolean("battery_alerts_enabled", false)) return false
        val status = try { registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) } catch (_: Exception) { null }
        if (status != null) {
            val level = status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = status.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            val percent = if (level >= 0) level * 100 / scale else -1
            val plugged = status.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            if (percent >= 100 && plugged && !prefs.getBoolean("battery_full_notified", false)) {
                prefs.edit().putBoolean("battery_full_notified", true).apply()
                ShraddhaEventNotifications.batteryFull(this)
            } else if (!plugged || percent in 0..98) {
                prefs.edit().putBoolean("battery_full_notified", false).apply()
            }
        }
        return false // no ongoing work; JobScheduler will run the next periodic check
    }

    override fun onStopJob(params: JobParameters?): Boolean = false

    companion object {
        private const val JOB_ID = 8270
        private const val CHECK_INTERVAL_MS = 15 * 60 * 1000L

        fun setEnabled(context: Context, enabled: Boolean) {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            if (!enabled) {
                scheduler.cancel(JOB_ID)
                context.getSharedPreferences("assistant", Context.MODE_PRIVATE).edit()
                    .putBoolean("battery_full_notified", false).apply()
                return
            }
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, ShraddhaBatteryCheckJob::class.java))
                .setPeriodic(CHECK_INTERVAL_MS)
                .setPersisted(true)
                .build()
            scheduler.schedule(job)
        }
    }
}
