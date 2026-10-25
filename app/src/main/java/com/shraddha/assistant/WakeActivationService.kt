package com.shraddha.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.os.BatteryManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import org.json.JSONArray
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * User-enabled foreground wake listener. Android displays an ongoing microphone notification.
 * Wake events use a notification action rather than relying on a prohibited background activity launch.
 */
class WakeActivationService : Service() {
    companion object {
        const val ACTION_START = "com.shraddha.assistant.wake.START"
        const val ACTION_STOP = "com.shraddha.assistant.wake.STOP"
        const val EXTRA_MODE = "wake_mode"
        const val EXTRA_AUTO_LISTEN = "wake_auto_listen"
        private const val CHANNEL_ID = "shraddha_wake_listener"
        private const val NOTIFICATION_ID = 8241
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var monitoring = false
    @Volatile private var wakeTriggered = false
    private var mode = "phrase"
    private var recognizer: SpeechRecognizer? = null
    private var audioRecord: AudioRecord? = null
    private var clapThread: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    private var batteryReceiverRegistered = false
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                val prefs = getSharedPreferences("assistant", MODE_PRIVATE)
                if (!prefs.getBoolean("battery_alerts_enabled", false)) return
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                val percent = if (level >= 0) (level * 100 / scale) else -1
                val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                if (percent >= 100 && plugged && !prefs.getBoolean("battery_full_notified", false)) {
                    prefs.edit().putBoolean("battery_full_notified", true).apply()
                    ShraddhaEventNotifications.batteryFull(this@WakeActivationService)
                } else if (!plugged || percent in 0..98) {
                    prefs.edit().putBoolean("battery_full_notified", false).apply()
                }
            } else if (intent?.action == Intent.ACTION_POWER_DISCONNECTED) {
                getSharedPreferences("assistant", MODE_PRIVATE).edit().putBoolean("battery_full_notified", false).apply()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
            registerReceiver(batteryReceiver, filter)
            batteryReceiverRegistered = true
        } catch (_: Exception) { batteryReceiverRegistered = false }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopMonitoring()
            if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }

        mode = intent?.getStringExtra(EXTRA_MODE)
            ?: getSharedPreferences("assistant", MODE_PRIVATE).getString("wake_mode", "phrase")
            ?: "phrase"
        monitoring = true
        wakeTriggered = false
        val notification = buildNotification(wakeDetected = false)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        if (mode == "clap") startClapMonitor() else mainHandler.post { startPhraseRecognition() }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "Shraddha AI wake listening", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Visible notification for user-enabled wake phrase or double-clap listening"
                setShowBadge(false)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(wakeDetected: Boolean): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (wakeDetected) putExtra(EXTRA_AUTO_LISTEN, true)
        }
        val pending = PendingIntent.getActivity(
            this, if (wakeDetected) 8243 else 8242, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        )
        val title = if (wakeDetected) "Shraddha heard the wake trigger" else "Shraddha AI · Wake mode active"
        val detail = if (wakeDetected) "Tap here to open Shraddha and start talking." else if (mode == "clap") {
            "Listening for two claps. Tap to return to the app."
        } else {
            "Listening for ‘Hey Shraddha’. Tap to return to the app."
        }
        @Suppress("DEPRECATION")
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        return builder
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(title)
            .setContentText(detail)
            .setStyle(Notification.BigTextStyle().bigText(detail))
            .setContentIntent(pending)
            .setOngoing(!wakeDetected)
            .setAutoCancel(wakeDetected)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    private fun startPhraseRecognition() {
        if (!monitoring || wakeTriggered || mode != "phrase") return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            updateStatusNotification("Speech recognition isn't available. Open Shraddha to change wake mode.")
            stopMonitoring()
            return
        }
        try {
            releaseRecognizer()
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) { scheduleNextPhraseAttempt(900L) }
                override fun onResults(results: android.os.Bundle?) { inspectSpeech(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)) }
                override fun onPartialResults(partialResults: android.os.Bundle?) { inspectSpeech(partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION), restartIfNoMatch = false) }
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })
            val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            }
            recognizer?.startListening(speechIntent)
        } catch (_: Exception) {
            scheduleNextPhraseAttempt(1500L)
        }
    }

    private fun inspectSpeech(results: ArrayList<String>?, restartIfNoMatch: Boolean = true) {
        if (!monitoring || wakeTriggered || results.isNullOrEmpty()) return
        val wakeMatched = results.any { utterance ->
            val normalized = utterance.lowercase().replace(Regex("[^a-z\\u0900-\\u097f ]"), " ").replace(Regex("\\s+"), " ").trim()
            normalized.contains("hey shraddha") || normalized.contains("hey shradha") ||
                normalized.contains("hai shraddha") || normalized.contains("hey shrada") ||
                normalized.contains("हे श्रद्धा") || normalized.contains("है श्रद्धा") ||
                normalized.contains("ए श्रद्धा")
        }
        if (wakeMatched) mainHandler.post { triggerWake() }
        else if (restartIfNoMatch) scheduleNextPhraseAttempt(450L)
    }

    private fun scheduleNextPhraseAttempt(delayMs: Long) {
        if (!monitoring || wakeTriggered || mode != "phrase") return
        mainHandler.removeCallbacks(phraseRestart)
        mainHandler.postDelayed(phraseRestart, delayMs)
    }

    private val phraseRestart = Runnable {
        if (monitoring && !wakeTriggered && mode == "phrase") startPhraseRecognition()
    }

    private fun releaseRecognizer() {
        try { recognizer?.cancel() } catch (_: Exception) {}
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
    }

    private fun startClapMonitor() {
        if (!monitoring || mode != "clap") return
        clapThread = Thread({
            val sampleRate = 16000
            val minSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minSize <= 0) {
                mainHandler.post { updateStatusNotification("Double-clap microphone setup failed. Open Shraddha to retry.") }
                return@Thread
            }
            val bufferSize = maxOf(minSize, 4096)
            val recorder = try {
                AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
            } catch (_: Exception) { null }
            if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
                try { recorder?.release() } catch (_: Exception) {}
                mainHandler.post { updateStatusNotification("Double-clap microphone is unavailable. Open Shraddha to retry.") }
                return@Thread
            }
            audioRecord = recorder
            val samples = ShortArray(1024)
            var lastPeakAt = 0L
            var firstClapAt = 0L
            try {
                recorder.startRecording()
                while (monitoring && !wakeTriggered && !Thread.currentThread().isInterrupted) {
                    val count = recorder.read(samples, 0, samples.size)
                    if (count > 0) {
                        var peak = 0
                        var squareSum = 0.0
                        for (i in 0 until count) {
                            val value = abs(samples[i].toInt())
                            if (value > peak) peak = value
                            squareSum += value.toDouble() * value.toDouble()
                        }
                        val rms = sqrt(squareSum / count)
                        val now = SystemClock.elapsedRealtime()
                        // Ignore sustained noise; look for a sharp transient followed by another transient.
                        if (peak > 15000 && rms > 1700 && now - lastPeakAt > 160) {
                            if (firstClapAt == 0L || now - firstClapAt > 1000) {
                                firstClapAt = now
                            } else {
                                val interval = now - firstClapAt
                                if (interval in 220L..850L) {
                                    mainHandler.post { triggerWake() }
                                    break
                                }
                                firstClapAt = now
                            }
                            lastPeakAt = now
                        }
                    }
                }
            } catch (_: Exception) {
                // Service may be stopping or the microphone may be reclaimed by Android.
            } finally {
                try { if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop() } catch (_: Exception) {}
                try { recorder.release() } catch (_: Exception) {}
                if (audioRecord === recorder) audioRecord = null
            }
        }, "ShraddhaDoubleClapMonitor").apply { isDaemon = true; start() }
    }

    private fun triggerWake() {
        if (!monitoring || wakeTriggered) return
        wakeTriggered = true
        getSharedPreferences("assistant", MODE_PRIVATE).edit().putBoolean("wake_pending", true).apply()
        mainHandler.removeCallbacks(phraseRestart)
        releaseRecognizer()
        try { audioRecord?.stop() } catch (_: Exception) {}
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, buildNotification(wakeDetected = true))
        // Listening is suspended after activation. The user taps the high-priority notification to begin a conversation.
    }

    private fun updateStatusNotification(message: String) {
        @Suppress("DEPRECATION")
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        val pending = PendingIntent.getActivity(this, 8244, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, builder
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("Shraddha wake listener")
            .setContentText(message).setContentIntent(pending).setOngoing(true).build())
    }

    private fun stopMonitoring() {
        monitoring = false
        mainHandler.removeCallbacksAndMessages(null)
        releaseRecognizer()
        try { audioRecord?.stop() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
        try { clapThread?.interrupt() } catch (_: Exception) {}
        clapThread = null
    }

    override fun onDestroy() {
        stopMonitoring()
        if (batteryReceiverRegistered) {
            try { unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
            batteryReceiverRegistered = false
        }
        super.onDestroy()
    }
}
