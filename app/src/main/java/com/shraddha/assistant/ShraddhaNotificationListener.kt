package com.shraddha.assistant

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Optional listener. It never reads notification text, titles, or conversation content.
 * A user's explicit Notification Access grant is required in Android Settings.
 */
class ShraddhaNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val posted = sbn ?: return
        if (posted.packageName == packageName) return
        val prefs = getSharedPreferences("assistant", MODE_PRIVATE)
        if (!prefs.getBoolean("notification_wake_enabled", false)) return

        val now = System.currentTimeMillis()
        val last = prefs.getLong("notification_wake_last_at", 0L)
        // Avoid repeatedly waking for a burst of system or messaging notifications.
        if (now - last < 20_000L) return
        prefs.edit().putLong("notification_wake_last_at", now).apply()
        ShraddhaEventNotifications.notificationWake(this)
    }
}
