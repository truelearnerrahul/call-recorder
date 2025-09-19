package io.ionic.starter;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

@CapacitorPlugin(
        name = "Contacts",
        permissions = {
                @Permission(strings = { Manifest.permission.READ_CONTACTS, Manifest.permission.READ_PHONE_NUMBERS }, alias = "contacts")
        }
)
public class ContactsPlugin extends Plugin {
    private static final String TAG = "ContactsPlugin";

    @PluginMethod
    public void getContacts(PluginCall call) {
        Log.d(TAG, "getContacts called");

        if (!hasRequiredPermissions()) {
            requestAllPermissions(call, "contactsPermsCallback");
            return;
        }

        try {
            JSObject result = fetchContacts();
            call.resolve(result);
        } catch (Exception e) {
            Log.e(TAG, "Error fetching contacts", e);
            call.reject("Failed to fetch contacts: " + e.getMessage());
        }
    }

    @PermissionCallback
    private void contactsPermsCallback(PluginCall call) {
        if (!hasRequiredPermissions()) {
            call.reject("Permission denied");
            return;
        }

        try {
            JSObject result = fetchContacts();
            call.resolve(result);
        } catch (Exception e) {
            Log.e(TAG, "Error fetching contacts after permission", e);
            call.reject("Failed to fetch contacts: " + e.getMessage());
        }
    }

    private JSObject fetchContacts() throws JSONException {
        Context context = getContext();
        ContentResolver cr = context.getContentResolver();

        JSONArray contactsArray = new JSONArray();

        String[] projection = {
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.HAS_PHONE_NUMBER
        };

        Cursor cursor = cr.query(
                ContactsContract.Contacts.CONTENT_URI,
                projection,
                null,
                null,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " ASC"
        );

        if (cursor != null) {
            while (cursor.moveToNext()) {
                String id = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID));
                String name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY));
                int hasPhoneNumber = cursor.getInt(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.HAS_PHONE_NUMBER));

                if (hasPhoneNumber > 0) {
                    JSONArray phonesArray = new JSONArray();

                    Cursor phoneCursor = cr.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER},
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                            new String[]{id},
                            null
                    );

                    if (phoneCursor != null) {
                        while (phoneCursor.moveToNext()) {
                            String phoneNumber = phoneCursor.getString(
                                    phoneCursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                            );
                            phonesArray.put(phoneNumber);
                        }
                        phoneCursor.close();
                    }

                    if (phonesArray.length() > 0) {
                        JSONObject contactObject = new JSONObject();
                        contactObject.put("id", id);
                        contactObject.put("name", name != null ? name : "Unknown");
                        contactObject.put("phoneNumbers", phonesArray);

                        contactsArray.put(contactObject);
                    }
                }
            }
            cursor.close();
        }

        JSObject result = new JSObject();
        result.put("contacts", contactsArray);
        return result;
    }
}
