package io.ionic.starter;

import android.net.Uri;
import android.os.Bundle;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;

public class MyConnectionService extends ConnectionService {

    @Override
    public Connection onCreateIncomingConnection(PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
        MyPhoneConnection connection = new MyPhoneConnection();
        connection.setConnectionProperties(Connection.PROPERTY_SELF_MANAGED);
        connection.setConnectionCapabilities(
                Connection.CAPABILITY_HOLD | Connection.CAPABILITY_SUPPORT_HOLD
        );
        connection.setCallerDisplayName("Unknown", TelecomManager.PRESENTATION_ALLOWED);
        connection.setAddress(request.getAddress(), TelecomManager.PRESENTATION_ALLOWED);
        connection.setInitializing();
        connection.setActive();
        connection.setRinging();
        return connection;
    }

    @Override
    public Connection onCreateOutgoingConnection(PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
        MyPhoneConnection connection = new MyPhoneConnection();
        Uri address = request.getAddress();
        connection.setConnectionProperties(Connection.PROPERTY_SELF_MANAGED);
        connection.setConnectionCapabilities(
                Connection.CAPABILITY_HOLD | Connection.CAPABILITY_SUPPORT_HOLD
        );
        connection.setCallerDisplayName("Unknown", TelecomManager.PRESENTATION_ALLOWED);

        connection.setAddress(address, TelecomManager.PRESENTATION_ALLOWED);
        connection.setInitializing();
        connection.setActive();
        return connection;
    }
}
