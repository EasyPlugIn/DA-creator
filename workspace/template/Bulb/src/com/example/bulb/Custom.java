package com.example.bulb;

import java.util.Arrays;

import org.json.JSONArray;
import org.json.JSONException;

import android.util.Log;

public class Custom {

    static final public String DEVICE_MODEL = "Bulb";
    static final public String[] df_list = new String[]{"Luminance"};

    static public void deviceInitialize () {
{{ code_deviceInitialize }}
    }

    static public void device2EasyConnect (ByteQueue bq) {
{{ code_device2easyconnect }}
    }

    static public void easyConnect2Device (String feature, JSONArray data) {
{{ code_easyconnect2Device }}
    }

    static public void deviceTerminate () {
{{ code_deviceTerminate }}
    }

    /******************************************************************************/
    /******************************************************************************/
    /******************************************************************************/
    /****************************** Don't touch here *****************************/
    /******************************************************************************/

    static private void send_data (char[] data) {
        send_data(new String(data));
    }

    static private void send_data (String data) {
        send_data(data.getBytes());
    }

    static private void send_data (byte[] data) {
        DeviceAgentService.send_data(data);
    }

    static private void logging (String message) {
        Log.i(C.log_tag, "[Custom] " + message);
    }
}
