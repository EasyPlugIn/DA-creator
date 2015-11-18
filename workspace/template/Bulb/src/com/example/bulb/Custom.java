package com.example.bulb;

import java.util.Arrays;

import org.json.JSONArray;
import org.json.JSONException;

import android.util.Log;

public class Custom {

    static final public String DEVICE_MODEL = "Bulb";
    static final public String[] df_list = new String[]{"Luminance"};

    static public void deviceInitialize () {
    }

    static public void device2EasyConnect (ByteQueue bq) {
        byte[] data = bq.first(2);
        logging("Get data: ["+ new String(data) +"]");
        bq.consume(2);
    }

    static public void easyConnect2Device (String feature, JSONArray data) {
        try {
            int luminance = data.getInt(0);
            byte[] chars;
            if (luminance > 0) {
                chars = new byte[ luminance * 2 ];
                Arrays.fill(chars, (byte)48);
                send_data(chars);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    static public void deviceTerminate () {
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
