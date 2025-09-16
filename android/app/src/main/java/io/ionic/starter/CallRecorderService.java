package io.ionic.starter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CallRecorderService extends Service {
    private static final String TAG = "CallRecorderService";
    private static final int NOTIFICATION_ID = 231;
    private static final String CHANNEL_ID = "callrec";

    private MediaRecorder recorder;
    private File outFile;
    private boolean isRecording = false;

    // Audio sources to try in order of preference
    private static final int[] AUDIO_SOURCES = {
//            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.CAMCORDER
    };

    private static final String[] SOURCE_NAMES = {
//            "VOICE_COMMUNICATION",
            "MIC", "VOICE_RECOGNITION",
            "CAMCORDER"
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Call Recorder",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Call recording service");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            if (intent.getAction().equals("START_RECORDING")) {
                // Check permissions before starting
                if (checkPermissions()) {
                    new Thread(this::startRecording).start();
                } else {
                    Log.e(TAG, "Missing required permissions");
                    stopSelf();
                }
            } else if (intent.getAction().equals("STOP_RECORDING")) {
                stopRecordingAndService();
            }
        }
        return START_STICKY;
    }

    private boolean checkPermissions() {
        // Check if we have record audio permission
        if (ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        // Check for storage permission if needed
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }

        return true;
    }

    private void startRecording() {
        try {
            // Create directory if it doesn't exist
            File dir = new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "CallRecords");
            if (!dir.exists() && !dir.mkdirs()) {
                Log.e(TAG, "Failed to create directory: " + dir.getAbsolutePath());
                stopSelf();
                return;
            }

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String filename = "call_" + timestamp + ".amr"; // Using AMR format for better compatibility with voice
            outFile = new File(dir, filename);

            recorder = new MediaRecorder();

            // Try different audio sources
            boolean audioSourceSet = false;
            for (int i = 0; i < AUDIO_SOURCES.length; i++) {
                try {
                    recorder.setAudioSource(AUDIO_SOURCES[i]);
                    audioSourceSet = true;
                    Log.i(TAG, "Using audio source: " + SOURCE_NAMES[i]);
                    break;
                } catch (Exception e) {
                    Log.w(TAG, "Audio source " + SOURCE_NAMES[i] + " failed: " + e.getMessage());
                }
            }

            if (!audioSourceSet) {
                Log.e(TAG, "All audio sources failed");
                stopSelf();
                return;
            }

            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioEncodingBitRate(128000);
            recorder.setAudioSamplingRate(44100);
            recorder.setOutputFile(outFile.getAbsolutePath());

            try {
                recorder.prepare();
            } catch (IOException e) {
                Log.e(TAG, "Recorder prepare failed: " + e.getMessage(), e);
                stopSelf();
                return;
            }

            // Start recording
            try {
                recorder.start();
                isRecording = true;

                // Create and show notification
                Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setContentTitle("Call Recording Active")
                        .setContentText("Recording: " + filename)
                        .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                        .setOngoing(true)
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .build();

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        int typeMicrophone = Service.class.getField("FOREGROUND_SERVICE_TYPE_MICROPHONE").getInt(null);
                        startForeground(NOTIFICATION_ID, notification, typeMicrophone);
                    } catch (Exception e) {
                        startForeground(NOTIFICATION_ID, notification);
                    }
                } else {
                    startForeground(NOTIFICATION_ID, notification);
                }

                Log.i(TAG, "Recording started at: " + outFile.getAbsolutePath());
            } catch (RuntimeException e) {
                Log.e(TAG, "Recorder start failed: " + e.getMessage(), e);
                stopSelf();
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to setup recorder: " + e.getMessage(), e);
            stopSelf();
        }
    }

    private void stopRecordingAndService() {
        stopRecording();
        stopSelf();
    }

    private void stopRecording() {
        if (recorder != null && isRecording) {
            try {
                // Properly stop and reset the recorder
                recorder.stop();
                recorder.reset();
                Log.i(TAG, "Recording stopped. File: " + outFile.getAbsolutePath());

                // Verify file was created and has content
                if (outFile.exists()) {
                    long fileSize = outFile.length();
                    Log.i(TAG, "Recording file size: " + fileSize + " bytes");
                    if (fileSize == 0) {
                        Log.w(TAG, "Recording file is empty - no audio was captured");
                    }
                } else {
                    Log.w(TAG, "Recording file was not created");
                }
            } catch (RuntimeException e) {
                Log.e(TAG, "Error stopping recorder: " + e.getMessage(), e);
            } finally {
                recorder.release();
                recorder = null;
                isRecording = false;
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopRecording();
        stopForeground(true);
        Log.i(TAG, "Recording service destroyed");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}