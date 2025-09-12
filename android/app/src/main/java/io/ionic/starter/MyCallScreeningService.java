package io.ionic.starter;

import android.telecom.CallScreeningService;
import android.telecom.Call.Details;

public class MyCallScreeningService extends CallScreeningService {
    @Override
    public void onScreenCall(Details callDetails) {
        // This is required for Android to treat app as Dialer
        CallResponse response = new CallResponse.Builder()
                .setDisallowCall(false)
                .setRejectCall(false)
                .setSilenceCall(false)
                .build();
        respondToCall(callDetails, response);
    }
}
