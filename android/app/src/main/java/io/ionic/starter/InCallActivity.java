package io.ionic.starter;

import android.os.Bundle;
import android.telecom.Call;
import android.telecom.InCallService;
import android.telecom.VideoProfile;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;


public class InCallActivity extends AppCompatActivity {

    private static Call currentCall;

    public static void setCall(Call call) {
        currentCall = call;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_incall);

        TextView txtNumber = findViewById(R.id.txtNumber);
        Button btnAnswer = findViewById(R.id.btnAnswer);
        Button btnReject = findViewById(R.id.btnReject);

        if (currentCall != null && currentCall.getDetails() != null) {
            CharSequence num = currentCall.getDetails().getHandle().getSchemeSpecificPart();
            txtNumber.setText(num != null ? num : "Unknown");
        }

        btnAnswer.setOnClickListener(v -> {
            if (currentCall != null) {
                currentCall.answer(VideoProfile.STATE_AUDIO_ONLY);
            }
            finish();
        });

        btnReject.setOnClickListener(v -> {
            if (currentCall != null) {
                currentCall.disconnect();
            }
            finish();
        });
    }
}
