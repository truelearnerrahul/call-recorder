package io.ionic.starter; // your package

import android.Manifest;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    private static final int REQUEST_PERM = 1001;
    private static final int REQUEST_ROLE = 1002;

    private WebView webView;   // ✅ store as a field

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // keep a reference
        webView = this.bridge.getWebView();

        // expose to JS
        webView.addJavascriptInterface(new AndroidBridge(this, webView), "AndroidBridge");
        registerPhoneAccount();
        registerPlugin(DialerPlugin.class);
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
                            null
                    ));
                }
            } else {
                // User rejected
                if (webView != null) {
                    webView.post(() -> webView.evaluateJavascript(
                            "window._androidDialerResult && window._androidDialerResult({granted:false})",
                            null
                    ));
                }
            }
        }
    }


    public static class AndroidBridge {
        Activity activity;
        WebView webView;  // ✅ have reference here

        AndroidBridge(Activity act, WebView wv) {
            activity = act;
            webView = wv;
        }

        @JavascriptInterface
        public void requestPermissions() {
            final String[] perms;
                perms = new String[]{
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.READ_PHONE_STATE,
                        Manifest.permission.READ_CALL_LOG,
                        Manifest.permission.ANSWER_PHONE_CALLS,
                        Manifest.permission.CALL_PHONE,
                        Manifest.permission.MANAGE_OWN_CALLS,
                        Manifest.permission.WRITE_CALL_LOG,
                };

            activity.runOnUiThread(() -> {
                boolean allGranted = true;
                for (String p : perms) {
                    if (ContextCompat.checkSelfPermission(activity, p) != PackageManager.PERMISSION_GRANTED) {
                        allGranted = false;
                        break;
                    }
                }
                if (!allGranted) {
                    ActivityCompat.requestPermissions(activity, perms, REQUEST_PERM);
                } else {
                    // ✅ notify JS
                    webView.post(() ->
                            webView.evaluateJavascript(
                                    "window._androidPermissionsResult && window._androidPermissionsResult({granted:true})",
                                   null
                            )
                    );
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

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERM) {
            boolean ok = true;
            for (int r : grantResults) {
                if (r != PackageManager.PERMISSION_GRANTED) { ok = false; break; }
            }
            final boolean granted = ok;

            // ✅ use the field webView
            if (webView != null) {
                runOnUiThread(() ->
                        webView.evaluateJavascript(
                                "window._androidPermissionsResult && window._androidPermissionsResult(" + (granted ? "{granted:true}" : "{granted:false}") + ")",
                                null
                        )
                );
            }
        }
    }
}
