package io.ionic.starter;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

@CapacitorPlugin(
    name = "CallHistory",
    permissions = {
        @Permission(strings = { Manifest.permission.READ_CALL_LOG }, alias = "callLog")
    }
)
public class CallHistoryPlugin extends Plugin {

    private static final String TAG = "CallHistoryPlugin";

    @Override
    public void load() {
        super.load();
        Log.d(TAG, "CallHistoryPlugin loaded successfully");
    }
    
    @PluginMethod
    public void getCallHistory(PluginCall call) {
        Log.d(TAG, "getCallHistory method called");

        // Check if we have permission
        if (!hasRequiredPermissions()) {
            requestAllPermissions(call, "getCallHistory");
            return;
        }

        try {
            int limit = call.getInt("limit", 100);
            JSObject result = fetchCallHistory(limit);
            call.resolve(result);
        } catch (Exception e) {
            Log.e(TAG, "Error getting call history", e);
            call.reject("Failed to get call history: " + e.getMessage());
        }
    }

    private JSObject fetchCallHistory(int limit) throws JSONException {
        Context context = getContext();
        ContentResolver contentResolver = context.getContentResolver();
        
        String[] projection = new String[] {
            Calls._ID,
            Calls.NUMBER,
            Calls.DATE,
            Calls.DURATION,
            Calls.TYPE,
            Calls.CACHED_NAME,
            Calls.CACHED_NUMBER_TYPE,
            Calls.CACHED_NUMBER_LABEL
        };

        String sortOrder = Calls.DATE + " DESC LIMIT " + limit;
        
        Cursor cursor = null;
        JSONArray callsArray = new JSONArray();
        
        try {
            cursor = contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            );

            if (cursor != null && cursor.getCount() > 0) {
                while (cursor.moveToNext()) {
                    @SuppressLint("Range") String number = cursor.getString(cursor.getColumnIndex(Calls.NUMBER));
                    @SuppressLint("Range") long date = cursor.getLong(cursor.getColumnIndex(Calls.DATE));
                    @SuppressLint("Range") int duration = cursor.getInt(cursor.getColumnIndex(Calls.DURATION));
                    @SuppressLint("Range") int type = cursor.getInt(cursor.getColumnIndex(Calls.TYPE));
                    @SuppressLint("Range") String cachedName = cursor.getString(cursor.getColumnIndex(Calls.CACHED_NAME));
                    
                    JSONObject callObject = new JSONObject();
                    callObject.put("number", number != null ? number : "");
                    callObject.put("date", date);
                    callObject.put("duration", duration);
                    callObject.put("type", getCallTypeString(type));
                    callObject.put("name", cachedName != null ? cachedName : "");
                    
                    callsArray.put(callObject);
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        JSObject result = new JSObject();
        result.put("calls", callsArray);
        return result;
    }

    private String getCallTypeString(int type) {
        switch (type) {
            case Calls.INCOMING_TYPE:
                return "INCOMING";
            case Calls.OUTGOING_TYPE:
                return "OUTGOING";
            case Calls.MISSED_TYPE:
                return "MISSED";
            case Calls.VOICEMAIL_TYPE:
                return "VOICEMAIL";
            case Calls.REJECTED_TYPE:
                return "REJECTED";
            case Calls.BLOCKED_TYPE:
                return "BLOCKED";
            default:
                return "UNKNOWN";
        }
    }

    @Override
    protected void handleRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.handleRequestPermissionsResult(requestCode, permissions, grantResults);
        
        PluginCall savedCall = getSavedCall();
        if (savedCall == null) {
            Log.e(TAG, "No stored plugin call for permissions request result");
            return;
        }
        
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                savedCall.reject("Permission denied");
                return;
            }
        }
        
        if (savedCall.getMethodName().equals("getCallHistory")) {
            try {
                int limit = savedCall.getInt("limit", 100);
                JSObject result = fetchCallHistory(limit);
                savedCall.resolve(result);
            } catch (Exception e) {
                Log.e(TAG, "Error getting call history after permission grant", e);
                savedCall.reject("Failed to get call history: " + e.getMessage());
            }
        }
    }
}