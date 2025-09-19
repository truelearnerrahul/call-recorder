package io.ionic.starter.activities;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.telecom.Call;
import android.telecom.VideoProfile;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

import io.ionic.starter.R;
import io.ionic.starter.services.MyInCallService;

public class InCallActivity extends AppCompatActivity {

    private static final int REQ_PERMISSIONS = 101;

    private static Call currentCall;
    private Call.Callback callCallback;

    private AudioManager audioManager;
    private AudioFocusRequest afRequest;
    private int previousAudioMode = AudioManager.MODE_NORMAL;
    private boolean previousSpeakerphoneOn = false;
    private boolean isMuted = false;
    private boolean isSpeakerOn = false;
    private boolean isRecording = false;

    // UI
    private ImageButton btnRecord;
    private ImageButton btnMute;
    private ImageButton btnSpeaker;

    public static void setCall(Call call) {
        currentCall = call;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_incall);

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        TextView txtNumber = findViewById(R.id.txtNumber);
        Button btnAnswer = findViewById(R.id.btnAnswer);
        Button btnReject = findViewById(R.id.btnReject);
        LinearLayout controlsLayout = findViewById(R.id.controlsLayout);

        btnMute = findViewById(R.id.btnMute);
        btnSpeaker = findViewById(R.id.btnSpeaker);
        ImageButton btnDialPad = findViewById(R.id.btnDialPad);
        btnRecord = findViewById(R.id.btnRecord);

        // Request permissions before call features
        checkAndRequestPermissions();

        if (currentCall != null && currentCall.getDetails() != null) {
            CharSequence num = currentCall.getDetails().getHandle().getSchemeSpecificPart();
            txtNumber.setText(num != null ? num : "Unknown");
        }

        callCallback = new Call.Callback() {
            @Override
            public void onStateChanged(Call call, int state) {
                runOnUiThread(() -> {
                    if (state == Call.STATE_ACTIVE) {
                        setupAudioForCall();
                        btnAnswer.setVisibility(View.GONE);
                        btnReject.setText("End Call");
                        controlsLayout.setVisibility(View.VISIBLE);
                        txtNumber.setText("On Call: " + txtNumber.getText());
                    } else if (state == Call.STATE_DISCONNECTED) {
                        teardownAudioAfterCall();
                        finish();
                    } else if (state == Call.STATE_RINGING) {
                        btnAnswer.setVisibility(View.VISIBLE);
                        controlsLayout.setVisibility(View.GONE);
                    }
                });
            }
        };

        if (currentCall != null) {
            currentCall.registerCallback(callCallback);
        }

        // Answer
        btnAnswer.setOnClickListener(v -> {
            if (currentCall != null) {
                try {
                    currentCall.answer(VideoProfile.STATE_AUDIO_ONLY);
                } catch (SecurityException ex) {
                    Toast.makeText(this, "Answer failed: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Reject / End
        btnReject.setOnClickListener(v -> {
            if (currentCall != null) {
                currentCall.disconnect();
            } else {
                finish();
            }
        });

        // Mute toggle
        btnMute.setOnClickListener(v -> {
            isMuted = !isMuted;
            audioManager.setMicrophoneMute(isMuted);
            btnMute.setAlpha(isMuted ? 0.5f : 1.0f);
            Toast.makeText(this, isMuted ? "Muted" : "Unmuted", Toast.LENGTH_SHORT).show();
        });

        // Speaker toggle
        btnSpeaker.setOnClickListener(v -> {
            isSpeakerOn = !isSpeakerOn;
            MyInCallService service = MyInCallService.getInstance();
            if (service != null) {
                service.toggleSpeaker(isSpeakerOn);
                btnSpeaker.setAlpha(isSpeakerOn ? 0.5f : 1.0f);
                Toast.makeText(this, isSpeakerOn ? "Speaker On" : "Speaker Off", Toast.LENGTH_SHORT).show();
            } else {
                audioManager.setSpeakerphoneOn(isSpeakerOn);
                btnSpeaker.setAlpha(isSpeakerOn ? 0.5f : 1.0f);
            }
        });

        // Dialpad (DTMF)
        btnDialPad.setOnClickListener(v -> {
            if (currentCall != null) {
                currentCall.playDtmfTone('1');
                currentCall.stopDtmfTone();
            }
        });

        // Record toggle
        btnRecord.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO}, REQ_PERMISSIONS);
                return;
            }

            MyInCallService service = MyInCallService.getInstance();
            if (service != null) {
                if (!service.isRecording()) {
                    service.startRecording();
                    isRecording = true;
                    Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show();
                } else {
                    service.stopRecording();
                    isRecording = false;
                    Toast.makeText(this, "Recording stopped", Toast.LENGTH_SHORT).show();
                }
                updateRecordButtonUI();
            }
        });

        updateRecordButtonUI();
    }

    private void updateRecordButtonUI() {
        if (btnRecord != null) {
            MyInCallService service = MyInCallService.getInstance();
            if (service != null) {
                isRecording = service.isRecording();
            }
            btnRecord.setAlpha(isRecording ? 0.5f : 1.0f);
        }
    }

    // ================= Permission Handling =================
    private void checkAndRequestPermissions() {
        String[] permissions = {
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.WRITE_CALL_LOG
        };

        List<String> needed = new ArrayList<>();
        for (String perm : permissions) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                needed.add(perm);
            }
        }

        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), REQ_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_PERMISSIONS) {
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Permission denied: " + permissions[i], Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    // ================= Audio Handling =================
    private void setupAudioForCall() {
        previousAudioMode = audioManager.getMode();
        previousSpeakerphoneOn = audioManager.isSpeakerphoneOn();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
            afRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener(fc -> {})
                    .build();
            audioManager.requestAudioFocus(afRequest);
        } else {
            audioManager.requestAudioFocus(null,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        }

        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        audioManager.setMicrophoneMute(false);
        audioManager.setSpeakerphoneOn(false);

        isSpeakerOn = false;
        isMuted = false;
        btnMute.setAlpha(1f);
        btnSpeaker.setAlpha(1f);
        updateRecordButtonUI();
    }

    private void teardownAudioAfterCall() {
        audioManager.setSpeakerphoneOn(previousSpeakerphoneOn);
        audioManager.setMode(previousAudioMode);

        if (afRequest != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.abandonAudioFocusRequest(afRequest);
            afRequest = null;
        } else {
            audioManager.abandonAudioFocus(null);
        }

        MyInCallService service = MyInCallService.getInstance();
        if (service != null && service.isRecording()) {
            service.stopRecording();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateRecordButtonUI();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        teardownAudioAfterCall();
        if (currentCall != null && callCallback != null) {
            currentCall.unregisterCallback(callCallback);
        }
    }
}
