package io.ionic.starter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.telecom.Call;
import android.telecom.InCallService;
import android.util.Log;
import androidx.core.app.NotificationCompat;

public class MyInCallService extends InCallService {
    private static final String TAG = "MyInCallService";
    private static final String INCOMING_CHANNEL_ID = "incoming_calls";

    @Override
    public void onCallAdded(Call call) {
        super.onCallAdded(call);
        Log.i(TAG, "onCallAdded: " + call.toString());

        // Create incoming call notification / full-screen intent
        createNotificationChannel();

        // Full screen Intent to open your Ionic incoming call Activity
        InCallActivity.setCall(call);

        Intent fullScreen = new Intent(this, InCallActivity.class);
        fullScreen.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        fullScreen.putExtra("incoming_call", true);
        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(this, 0, fullScreen,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder nb = new NotificationCompat.Builder(this, INCOMING_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle("Incoming call")
            .setContentText("Tap to open")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setOngoing(true);

        Notification n = nb.build();
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(1001, n);

        // you can also notify JS via your plugin here if you want
    }

    @Override
    public void onCallRemoved(Call call) {
        super.onCallRemoved(call);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.cancel(1001);
        InCallActivity.setCall(null);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(INCOMING_CHANNEL_ID,
                "Incoming calls", NotificationManager.IMPORTANCE_HIGH);
            channel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build());
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }
}
