package io.ionic.starter.plugins;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.PluginMethod;

import io.ionic.starter.MainActivity;

/**
 * DialerPlugin - exposes methods and emits events to JS
 */
@CapacitorPlugin(name = "DialerPlugin")
public class DialerPlugin extends Plugin {

    private static final String TAG = "DialerPlugin";
    private static DialerPlugin instance;

    @Override
    public void load() {
        super.load();
        instance = this;
    }

    @Override
    public void handleOnDestroy() {
        super.handleOnDestroy();
        instance = null;
    }

    // Static helper so native connection/service classes can emit events easily
    public static void emitEventStatic(String eventName, JSObject data) {
        if (instance != null) {
            instance.notifyListeners(eventName, data);
        } else {
            Log.w(TAG, "emitEventStatic: plugin instance is null, can't emit " + eventName);
        }
    }

    @PluginMethod
    public void requestDefaultDialer(PluginCall call) {
        // call MainActivity's role request helper via an intent or using the activity
        try {
            if (getActivity() == null) {
                call.reject("Activity is null");
                return;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                android.app.role.RoleManager rm = (android.app.role.RoleManager) getActivity().getSystemService(Context.ROLE_SERVICE);
                if (rm != null && !rm.isRoleHeld(android.app.role.RoleManager.ROLE_DIALER)) {
                    Intent roleRequestIntent = rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_DIALER);
                    getActivity().startActivityForResult(roleRequestIntent, 34567);
                    call.resolve();
                    return;
                }
            } else {
                // fallback for older versions - ask Telecom to change default dialer
                Intent intent = new Intent(android.telecom.TelecomManager.ACTION_CHANGE_DEFAULT_DIALER);
                intent.putExtra(android.telecom.TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, getActivity().getPackageName());
                getActivity().startActivityForResult(intent, 34567);
                call.resolve();
                return;
            }
            call.resolve();
        } catch (Exception ex) {
            call.reject("Error requesting default dialer: " + ex.getMessage());
        }
    }

    @PluginMethod
    public void makeCall(PluginCall call) {
        String number = call.getString("number");
        if (number == null || number.length() == 0) {
            call.reject("number is required");
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_CALL);
            intent.setData(Uri.parse("tel:" + number));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getActivity().startActivity(intent);
            call.resolve();
        } catch (Exception ex) {
            call.reject("makeCall failed: " + ex.getMessage());
        }
    }

    @PluginMethod
    public void registerPhoneAccount(PluginCall call) {
        try {
            // Please ensure you've implemented registerPhoneAccount() in MainActivity or here.
            // We'll call a helper in MainActivity to register PhoneAccount via TelecomManager
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).registerPhoneAccount();
                call.resolve();
            } else {
                call.reject("Activity is not MainActivity");
            }
        } catch (Exception ex) {
            call.reject("registerPhoneAccount failed: " + ex.getMessage());
        }
    }

    // you can add more methods (mute, hold, end call) as needed
}
