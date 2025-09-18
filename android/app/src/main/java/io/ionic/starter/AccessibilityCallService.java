package io.ionic.starter;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public class AccessibilityCallService extends AccessibilityService {
    private static final String TAG = "AccCallService";

    // Common dialer packages seen on OEMs
    private static final Set<String> KNOWN_DIALERS = new HashSet<>(Arrays.asList(
            "com.google.android.dialer",
            "com.android.dialer",
            "com.samsung.android.dialer",
            "com.miui.voip",
            "com.coloros.phone",
            "com.oneplus.dialer",
            "com.huawei.contacts"
    ));

    private static final Pattern DURATION_PATTERN = Pattern.compile("^\\d{1,2}:\\d{2}(:\\d{2})?$");

    private boolean callUiVisible = false;
    private boolean recordingStarted = false;
    private boolean speakerForced = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable delayedStop = () -> {
        if (recordingStarted) {
            stopRecording();
        }
        callUiVisible = false;
    };

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                | AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
                | AccessibilityEvent.TYPE_VIEW_CLICKED
                | AccessibilityEvent.TYPE_VIEW_FOCUSED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        info.notificationTimeout = 100; // ms
        setServiceInfo(info);
        Log.i(TAG, "Accessibility service connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        final CharSequence pkgCs = event.getPackageName();
        final String pkg = pkgCs != null ? pkgCs.toString() : "";
        if (TextUtils.isEmpty(pkg) || !looksLikeDialer(pkg)) {
            // Non-dialer app; if we were on call UI, consider this a potential exit.
            scheduleDelayedStop();
            return;
        }

        // We are in a dialer package; inspect the event for call state hints
        boolean indicatesActive = false;
        boolean indicatesEnd = false;

        // Class name often includes InCallActivity/InCallScreen, etc.
        final CharSequence clsCs = event.getClassName();
        final String cls = clsCs != null ? clsCs.toString() : "";
        if (cls.toLowerCase(Locale.ROOT).contains("incall") || cls.toLowerCase(Locale.ROOT).contains("call")) {
            indicatesActive = true;
        }

        // Text content can include timer like 00:12 or strings like "End call" / "Calling" / "Ringing"
        if (event.getText() != null) {
            for (CharSequence t : event.getText()) {
                if (t == null) continue;
                String s = t.toString().trim();
                if (s.isEmpty()) continue;
                if (DURATION_PATTERN.matcher(s).matches()) {
                    indicatesActive = true;
                }
                String sl = s.toLowerCase(Locale.ROOT);
                if (sl.contains("end call") || sl.contains("call ended") || sl.contains("ended")) {
                    indicatesEnd = true;
                }
                if (sl.contains("calling") || sl.contains("on going") || sl.contains("ongoing") || sl.contains("ringing")) {
                    indicatesActive = true;
                }
            }
        }

        if (indicatesActive) {
            callUiVisible = true;
            handler.removeCallbacks(delayedStop);
            if (!recordingStarted) {
                startRecording();
            }
        }

        if (indicatesEnd) {
            scheduleDelayedStop();
        }
    }

    @Override
    public void onInterrupt() {
        // No-op
    }

    private boolean looksLikeDialer(String pkg) {
        if (KNOWN_DIALERS.contains(pkg)) return true;
        // Fallback heuristic for OEM forks
        return pkg.contains("dialer") || pkg.contains("phone") || pkg.contains("incall");
    }

    private void startRecording() {
        try {
            // Try to force speaker route via InCallService when available
            MyInCallService svc = MyInCallService.getInstance();
            if (svc != null) {
                svc.toggleSpeaker(true);
                speakerForced = true;
            } else {
                // Fallback: set speakerphone via AudioManager
                AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
                if (am != null && !am.isSpeakerphoneOn()) {
                    am.setSpeakerphoneOn(true);
                    speakerForced = true;
                }
            }

            Intent i = new Intent(this, CallRecorderService.class);
            i.setAction("START_RECORDING");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(i);
            } else {
                startService(i);
            }
            recordingStarted = true;
            Log.i(TAG, "Recording triggered by Accessibility");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to start recording: " + t.getMessage(), t);
            recordingStarted = false;
        }
    }

    private void stopRecording() {
        try {
            Intent i = new Intent(this, CallRecorderService.class);
            i.setAction("STOP_RECORDING");
            stopService(i);
            recordingStarted = false;
            Log.i(TAG, "Recording stopped by Accessibility");
            if (speakerForced) {
                // Try to restore route via InCallService first
                MyInCallService svc = MyInCallService.getInstance();
                if (svc != null) {
                    svc.toggleSpeaker(false);
                } else {
                    AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
                    if (am != null) am.setSpeakerphoneOn(false);
                }
                speakerForced = false;
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to stop recording: " + t.getMessage(), t);
        }
    }

    private void scheduleDelayedStop() {
        // Give UI a short grace period to avoid flapping on transitions
        handler.removeCallbacks(delayedStop);
        handler.postDelayed(delayedStop, 1500);
    }
}
