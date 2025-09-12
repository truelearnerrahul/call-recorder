package io.ionic.starter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;
import android.os.Build;
import android.util.Log;

public class CallReceiver extends BroadcastReceiver {
    private static final String TAG = "CallReceiver";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        if (action.equals("android.intent.action.NEW_OUTGOING_CALL")) {
            String outgoingNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
            Log.i(TAG, "Outgoing call: " + outgoingNumber);
            // start recorder service
            Intent s = new Intent(ctx, CallRecorderService.class);
            s.putExtra("call_type", "outgoing");
            s.putExtra("number", outgoingNumber);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(s);
            } else {
                ctx.startService(s);
            }
        } else if (action.equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED) || action.equals("android.intent.action.PHONE_STATE")) {
            String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
            String incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
            Log.i(TAG, "Phone state changed: " + state + " number: " + incomingNumber);

            if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
                // ringing — do nothing, wait for OFFHOOK
            } else if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
                // call answered or outgoing in progress — start recording if not running
                Intent s = new Intent(ctx, CallRecorderService.class);
                s.putExtra("call_type", "incall");
                s.putExtra("number", incomingNumber);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ctx.startForegroundService(s);
                } else {
                    ctx.startService(s);
                }
            } else if (TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
                // call ended — stop the service
                Intent stop = new Intent(ctx, CallRecorderService.class);
                ctx.stopService(stop);
            }
        }
    }
}
