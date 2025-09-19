package io.ionic.starter.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.RequiresApi;
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
    private AudioRecord audioRecord;
    // Audio sources to try in order of preference
    private static final int[] AUDIO_SOURCES = {
           MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.CAMCORDER
    };

    private static final String[] SOURCE_NAMES = {
           "VOICE_COMMUNICATION",
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

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {

            // // 1️⃣ Start foreground first
            // Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
            //         .setContentTitle("Call Recording")
            //         .setContentText("Recording in progress")
            //         .setSmallIcon(R.mipmap.ic_launcher)
            //         .build();

            // startForeground(NOTIFICATION_ID, notification,
            //         ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);

            // // 2️⃣ Now get MediaProjection safely
            // int resultCode = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED);
            // Intent data = intent.getParcelableExtra("data");

            // if (data == null) {
            //     stopSelf();
            //     return START_NOT_STICKY;
            // }

            // MediaProjectionManager projectionManager =
            //         (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            // MediaProjection mediaProjection = projectionManager.getMediaProjection(resultCode, data);

            // AudioPlaybackCaptureConfiguration config =
            //         new AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
            //                 .addMatchingUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            //                 .build();


            // AudioFormat format = new AudioFormat.Builder()
            //         .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            //         .setSampleRate(44100)
            //         .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            //         .build();

            // int bufferSize = AudioRecord.getMinBufferSize(
            //         44100,
            //         AudioFormat.CHANNEL_IN_MONO,
            //         AudioFormat.ENCODING_PCM_16BIT
            // );

            // if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            //     // TODO: Consider calling
            //     //    ActivityCompat#requestPermissions
            //     // here to request the missing permissions, and then overriding
            //     //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //     //                                          int[] grantResults)
            //     // to handle the case where the user grants the permission. See the documentation
            //     // for ActivityCompat#requestPermissions for more details.
            //     return START_STICKY;
            // }
            // audioRecord = new AudioRecord.Builder()
            //         .setAudioFormat(format)
            //         .setBufferSizeInBytes(bufferSize)
            //         .setAudioPlaybackCaptureConfig(config)
            //         .build();

            if (intent.getAction().equals("START_RECORDING")) {

            //     String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            //     File file = new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC),
            //             "CallRecords/call_" + timestamp + ".wav");
            //     file.getParentFile().mkdirs();
            //     startRecording(file, bufferSize);
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

    // private void startRecording(File file, int bufferSize) {
    //     isRecording = true;
    //     audioRecord.startRecording();

    //     new Thread(() -> {
    //         try {
    //             FileOutputStream fos = new FileOutputStream(file);
    //             byte[] buffer = new byte[bufferSize];

    //             // Write placeholder WAV header
    //             byte[] wavHeader = new byte[44];
    //             fos.write(wavHeader);

    //             int totalAudioLen = 0;
    //             while (isRecording) {
    //                 int read = audioRecord.read(buffer, 0, buffer.length);
    //                 if (read > 0) {
    //                     fos.write(buffer, 0, read);
    //                     totalAudioLen += read;
    //                 }
    //             }

    //             fos.close();
    //             WavFileWriter.writeWavHeader(file, totalAudioLen, 44100, 1, 16);

    //         } catch (Exception e) {
    //             e.printStackTrace();
    //         }
    //     }).start();
    // }


    private boolean checkPermissions() {
        // Check if we have record audio permission
        if (ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        // Check for storage permission if needed
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
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
            String filename = "call_" + timestamp + ".wav"; // Using AMR format for better compatibility with voice
            outFile = new File(dir, filename);

            recorder = new MediaRecorder();
            // recorder.setOutputFormat(MediaRecorder.OutputFormat.AMR_NB);


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
                        // startForeground(NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
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
        // isRecording = false;
        // if (audioRecord != null) {
        //     audioRecord.stop();
        //     audioRecord.release();
        //     audioRecord = null;
        // }
        stopForeground(true);
        Log.i(TAG, "Recording service destroyed");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}