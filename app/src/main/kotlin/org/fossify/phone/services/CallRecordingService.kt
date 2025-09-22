package org.fossify.phone.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.fossify.phone.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallRecordingService : Service() {
    companion object {
        private const val CHANNEL_ID = "call_recording_channel"
        private const val NOTIF_ID = 99
        const val ACTION_START = "org.fossify.phone.action.START_RECORDING"
        const val ACTION_STOP = "org.fossify.phone.action.STOP_RECORDING"
        const val ACTION_RECORDING_STARTED = "org.fossify.phone.action.RECORDING_STARTED"
        const val ACTION_RECORDING_STOPPED = "org.fossify.phone.action.RECORDING_STOPPED"

        fun start(context: Context) {
            val intent = Intent(context, CallRecordingService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            // Try sending a normal startService with STOP to let a running service handle cleanup
            try {
                val stopIntent = Intent(context, CallRecordingService::class.java).apply { action = ACTION_STOP }
                context.startService(stopIntent)
            } catch (_: Exception) {}
            // Also request stopping the service directly; if it's not running this is a no-op
            try {
                context.stopService(Intent(context, CallRecordingService::class.java))
            } catch (_: Exception) {}
        }
    }

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startRecording()
                return START_STICKY
            }
            ACTION_STOP -> {
                // Do not promote to foreground; just stop if running
                stopRecording()
                return START_NOT_STICKY
            }
            else -> {
                if (recorder == null) startRecording() else updateNotification()
                return START_STICKY
            }
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.recording_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            channel.setSound(null, null)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildStopPendingIntent(): PendingIntent {
        val stopIntent = Intent(this, CallRecordingService::class.java).apply { action = ACTION_STOP }
        return PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    private fun buildNotification(): Notification {
        createChannel()
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_microphone_off_vector)
            .setContentTitle(getString(R.string.recording_ongoing))
            .setOngoing(true)
            .setSound(null)
            .addAction(
                R.drawable.ic_call_end,
                getString(R.string.stop),
                buildStopPendingIntent()
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
        return builder.build()
    }

    private fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification())
    }

    private fun startRecording() {
        if (recorder != null) return
        try {
            val dir = getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: filesDir
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            outputFile = File(dir, "Call_${timestamp}.m4a")

            // Promote to foreground immediately to avoid 5s timeout
            startForeground(NOTIF_ID, buildNotification())

            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else MediaRecorder()
            var started = false
            val trySources = intArrayOf(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                MediaRecorder.AudioSource.MIC
            )
            for (source in trySources) {
                try {
                    recorder?.reset()
                } catch (_: Exception) {}
                try {
                    recorder!!.setAudioSource(source)
                    recorder!!.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    recorder!!.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    recorder!!.setAudioEncodingBitRate(128_000)
                    recorder!!.setAudioSamplingRate(44_100)
                    recorder!!.setOutputFile(outputFile!!.absolutePath)
                    recorder!!.prepare()
                    recorder!!.start()
                    started = true
                    break
                } catch (_: Exception) {
                    // try next source
                }
            }

            if (!started) throw IllegalStateException("Failed to start MediaRecorder")

            // Inform UI and user
            sendBroadcast(Intent(ACTION_RECORDING_STARTED))
            try {
                android.widget.Toast.makeText(applicationContext, getString(R.string.recording_ongoing), android.widget.Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {}
        } catch (e: Exception) {
            // Cleanup on failure
            try { recorder?.reset() } catch (_: Exception) {}
            try { recorder?.release() } catch (_: Exception) {}
            recorder = null
            outputFile = null
            try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
            // Inform UI/user
            sendBroadcast(Intent(ACTION_RECORDING_STOPPED))
            try { android.widget.Toast.makeText(applicationContext, getString(R.string.recording_failed), android.widget.Toast.LENGTH_SHORT).show() } catch (_: Exception) {}
            stopSelf()
        }
    }

    private fun stopRecording() {
        try {
            recorder?.apply {
                try {
                    stop()
                } catch (_: Exception) {
                }
                reset()
                release()
            }
        } catch (_: Exception) {
        } finally {
            recorder = null
        }
        stopForeground(STOP_FOREGROUND_REMOVE)

        // Inform UI and user
        sendBroadcast(Intent(ACTION_RECORDING_STOPPED))
        try {
            android.widget.Toast.makeText(applicationContext, getString(R.string.recording_saved), android.widget.Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            recorder?.reset()
            recorder?.release()
        } catch (_: Exception) {}
        recorder = null
    }
}
