package io.ionic.starter;

import android.Manifest;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.getcapacitor.BridgeActivity;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends BridgeActivity {
    private static final int REQUEST_PERM = 1001;
    private static final int REQUEST_ROLE = 1002;
    private static final int REQUEST_CODE_CAPTURE_AUDIO = 1003;

    private WebView webView; // ✅ store as a field

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // keep a reference
        webView = this.bridge.getWebView();

        // expose to JS
        webView.addJavascriptInterface(new AndroidBridge(this, webView), "AndroidBridge");
        registerPhoneAccount();
        registerPlugin(DialerPlugin.class);
        registerPlugin(CallHistoryPlugin.class);
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void requestCapturePermission() {
        MediaProjectionManager projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        Intent intent = projectionManager.createScreenCaptureIntent();
        startActivityForResult(intent, REQUEST_CODE_CAPTURE_AUDIO);
    }

    void registerPhoneAccount() {
        TelecomManager telecomManager = (TelecomManager) getSystemService(TELECOM_SERVICE);
        ComponentName componentName = new ComponentName(this, MyConnectionService.class);
        PhoneAccountHandle handle = new PhoneAccountHandle(componentName, getPackageName());

        PhoneAccount phoneAccount = PhoneAccount.builder(handle, "My Dialer")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .build();

        telecomManager.registerPhoneAccount(phoneAccount);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_ROLE) {
            if (resultCode == Activity.RESULT_OK) {
                // User accepted → notify JS
                if (webView != null) {
                    webView.post(() -> webView.evaluateJavascript(
                            "window._androidDialerResult && window._androidDialerResult({granted:true})",
                            null));
                }
            } else {
                // User rejected
                if (webView != null) {
                    webView.post(() -> webView.evaluateJavascript(
                            "window._androidDialerResult && window._androidDialerResult({granted:false})",
                            null));
                }
            }
        }

        // if (requestCode == REQUEST_CODE_CAPTURE_AUDIO && resultCode == Activity.RESULT_OK && data != null) {
        //     Intent serviceIntent = new Intent(this, CallRecorderService.class);
        //     serviceIntent.setAction("START_RECORDING");
        //     serviceIntent.putExtra("resultCode", resultCode);
        //     serviceIntent.putExtra("data", data);
        //     if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        //         startForegroundService(serviceIntent);
        //     } else {
        //         startService(serviceIntent);
        //     }

        // }
    }

    public class AndroidBridge {
        Activity activity;
        WebView webView; // ✅ have reference here

        AndroidBridge(Activity act, WebView wv) {
            activity = act;
            webView = wv;
        }

        @JavascriptInterface
        public boolean isDefaultDialer() {
            try {
                TelecomManager tm = (TelecomManager) activity.getSystemService(Context.TELECOM_SERVICE);
                if (tm == null)
                    return false;
                String defaultPkg = tm.getDefaultDialerPackage();
                boolean isDefault = activity.getPackageName().equals(defaultPkg);
                if (webView != null) {
                    webView.post(() -> webView.evaluateJavascript(
                            "window._androidDialerResult && window._androidDialerResult({granted:true})",
                            null));
                }
                return isDefault;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public void requestPermissions() {
            List<String> perms = new ArrayList<>();

            // Always include these
            perms.add(Manifest.permission.RECORD_AUDIO);
            perms.add(Manifest.permission.READ_PHONE_STATE);
            perms.add(Manifest.permission.READ_CALL_LOG);
            perms.add(Manifest.permission.CALL_PHONE);
            perms.add(Manifest.permission.WRITE_CALL_LOG);

            // API 26+ permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                perms.add(Manifest.permission.ANSWER_PHONE_CALLS);
                perms.add(Manifest.permission.MANAGE_OWN_CALLS);
            }
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//                requestCapturePermission();
//            }

            final String[] runtimePerms = perms.toArray(new String[0]);

            activity.runOnUiThread(() -> {
                boolean allGranted = true;
                for (String p : runtimePerms) {
                    if (ContextCompat.checkSelfPermission(activity, p) != PackageManager.PERMISSION_GRANTED) {
                        allGranted = false;
                        break;
                    }
                }

                if (!allGranted) {
                    ActivityCompat.requestPermissions(activity, runtimePerms, REQUEST_PERM);
                } else {
                    webView.post(() -> webView.evaluateJavascript(
                            "window._androidPermissionsResult && window._androidPermissionsResult({granted:true})",
                            null));
                }
            });
        }

        @JavascriptInterface
        public void requestDefaultDialer() {
            activity.runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    RoleManager rm = (RoleManager) activity.getSystemService(RoleManager.class);
                    if (rm != null && !rm.isRoleHeld(RoleManager.ROLE_DIALER)) {
                        Intent roleRequestIntent = rm.createRequestRoleIntent(RoleManager.ROLE_DIALER);
                        activity.startActivityForResult(roleRequestIntent, REQUEST_ROLE);
                        Toast.makeText(activity, "rm" + rm, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(activity, "Already dialer or cannot request role", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    // Fallback for Android < 10
                    Intent intent = new Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER);
                    intent.putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, activity.getPackageName());
                    activity.startActivityForResult(intent, REQUEST_ROLE);
                }
            });
        }

        @JavascriptInterface
        public void openAccessibilitySettings() {
            activity.runOnUiThread(() -> {
                try {
                    Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    activity.startActivity(i);
                } catch (Exception e) {
                    Toast.makeText(activity, "Cannot open Accessibility settings: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                }
            });
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERM) {
            boolean ok = true;
            for (int r : grantResults) {
                if (r != PackageManager.PERMISSION_GRANTED) {
                    ok = false;
                    break;
                }
            }
            final boolean granted = ok;

            // ✅ use the field webView
            if (webView != null) {
                runOnUiThread(() -> webView.evaluateJavascript(
                        "window._androidPermissionsResult && window._androidPermissionsResult("
                                + (granted ? "{granted:true}" : "{granted:false}") + ")",
                        null));
            }
        }
    }
}
