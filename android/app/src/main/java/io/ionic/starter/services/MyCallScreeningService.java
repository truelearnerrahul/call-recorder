package io.ionic.starter.services;

import android.os.Build;
import android.telecom.CallScreeningService;
import android.telecom.Call.Details;

import androidx.annotation.RequiresApi;

@RequiresApi(api = Build.VERSION_CODES.N)
public class MyCallScreeningService extends CallScreeningService {
    @Override
    public void onScreenCall(Details callDetails) {
        // This is required for Android to treat app as Dialer
        CallResponse response = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            response = new CallResponse.Builder()
                    .setDisallowCall(false)
                    .setRejectCall(false)
                    .setSilenceCall(false)
                    .build();
        }
        respondToCall(callDetails, response);
    }
}
