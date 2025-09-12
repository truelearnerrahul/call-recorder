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
import androidx.core.app.NotificationCompat;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class CallRecorderService extends Service {
    private static final String TAG = "CallRecorderService";
    private MediaRecorder recorder;
    private File outFile;

    @Override
    public void onCreate() {
        super.onCreate();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("callrec", "Call Recorder", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, "callrec")
                .setContentTitle("Call Recorder Active")
                .setContentText("Recording call...")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .build();

        startForeground(231, notification);

        startRecording(intent);
        return START_STICKY;
    }

    private void startRecording(Intent intent) {
        try {
            String dir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/CallRecords";
            File d = new File(dir);
            if (!d.exists()) d.mkdirs();

            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String filename = "call_" + ts + ".mp4";
            outFile = new File(d, filename);

            recorder = new MediaRecorder();

            // Try voice call source first (may fail on many devices)
            try {
                recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_CALL);
            } catch (Exception ex) {
                Log.w(TAG, "VOICE_CALL not allowed, falling back to MIC: " + ex.getMessage());
                recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            }

            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioEncodingBitRate(128000);
            recorder.setAudioSamplingRate(44100);
            recorder.setOutputFile(outFile.getAbsolutePath());

            recorder.prepare();
            recorder.start();
            Log.i(TAG, "Recording started at: " + outFile.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "Failed to start recorder: " + e.getMessage(), e);
            stopSelf();
        } catch (RuntimeException re) {
            Log.e(TAG, "Runtime exception starting recorder: " + re.getMessage(), re);
            stopSelf();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            if (recorder != null) {
                recorder.stop();
                recorder.reset();
                recorder.release();
                recorder = null;
            }
            Log.i(TAG, "Recording stopped. File: " + (outFile!=null ? outFile.getAbsolutePath() : "unknown"));
        } catch (Exception e) {
            Log.e(TAG, "Error stopping recorder: " + e.getMessage(), e);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
