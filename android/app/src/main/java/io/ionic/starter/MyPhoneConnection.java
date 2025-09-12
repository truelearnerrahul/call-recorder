package io.ionic.starter;

import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.util.Log;

import com.getcapacitor.JSObject;

public class MyPhoneConnection extends Connection {
    private static final String TAG = "MyPhoneConnection";


    @Override
    public void onAnswer() {
        super.onAnswer();
        setActive();
        Log.i(TAG, "onAnswer");
        JSObject data = new JSObject();
        data.put("event", "answered");
        DialerPlugin.emitEventStatic("callAnswered", data);
    }

    @Override
    public void onDisconnect() {
        super.onDisconnect();
        setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
        destroy();
        Log.i(TAG, "onDisconnect");
        JSObject data = new JSObject();
        data.put("event", "ended");
        DialerPlugin.emitEventStatic("callEnded", data);
    }

    @Override
    public void onReject() {
        super.onReject();
        setDisconnected(new DisconnectCause(DisconnectCause.REJECTED));
        destroy();
        Log.i(TAG, "onReject");
        JSObject data = new JSObject();
        data.put("event", "rejected");
        DialerPlugin.emitEventStatic("callEnded", data);
    }

    public void notifyIncoming(String number) {
        JSObject data = new JSObject();
        data.put("event", "incoming");
        data.put("number", number);
        DialerPlugin.emitEventStatic("callIncoming", data);
    }

}
