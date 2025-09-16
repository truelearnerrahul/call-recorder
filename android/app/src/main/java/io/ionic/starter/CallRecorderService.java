package io.ionic.starter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.NotificationCompat;
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
                // Start recording in a separate thread to avoid ANR
                new Thread(this::startRecording).start();
            } else if (intent.getAction().equals("STOP_RECORDING")) {
                stopRecordingAndService();
            }
        }
        return START_STICKY;
    }

    private void startRecording() {
        try {
            // Use a more accessible directory
            File dir = new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "CallRecords");

            if (!dir.exists() && !dir.mkdirs()) {
                Log.e(TAG, "Failed to create directory: " + dir.getAbsolutePath());
                stopSelf();
                return;
            }

            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String filename = "call_" + ts + ".mp4";
            outFile = new File(dir, filename);

            recorder = new MediaRecorder();

            // Try different audio sources systematically
            boolean audioSourceSet = false;
            int[] audioSources = {
//                    MediaRecorder.AudioSource.VOICE_CALL,
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    MediaRecorder.AudioSource.MIC,
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    MediaRecorder.AudioSource.CAMCORDER
            };

            String[] sourceNames = {
                    "VOICE_COMMUNICATION", "MIC", "VOICE_RECOGNITION", "CAMCORDER"
            };

            for (int i = 0; i < audioSources.length; i++) {
                try {
                    recorder.setAudioSource(audioSources[i]);
                    audioSourceSet = true;
                    Log.i(TAG, "Using audio source: " + sourceNames[i]);
                    break;
                } catch (Exception e) {
                    Log.w(TAG, "Audio source " + sourceNames[i] + " failed: " + e.getMessage());
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

            // Start immediately after prepare
            try {
                recorder.start();
                isRecording = true;

                // Update the notification as a foreground service
                Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setContentTitle("Call Recording Active")
                        .setContentText("Recording in progress...")
                        .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                        .setOngoing(true)
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .build();

                // Use the correct foreground service type for microphone access
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        int typeMicrophone = Service.class.getField("FOREGROUND_SERVICE_TYPE_MICROPHONE").getInt(null);
                        startForeground(NOTIFICATION_ID, notification, typeMicrophone);
                    } catch (Exception e) {
                        // Fallback if FOREGROUND_SERVICE_TYPE_MICROPHONE is not available
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
                recorder.stop();
                Log.i(TAG, "Recording stopped. File: " + outFile.getAbsolutePath());
            } catch (IllegalStateException e) {
                Log.w(TAG, "Recorder was not in a valid state to stop: " + e.getMessage());
            }
            recorder.reset();
            recorder.release();
            recorder = null;
            isRecording = false;
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