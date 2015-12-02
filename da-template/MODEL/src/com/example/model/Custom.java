package com.example.{{ dm_name_l }};

import java.util.Arrays;

import org.json.JSONArray;
import org.json.JSONException;

import android.util.Log;

public class Custom {

    static final public String DEVICE_MODEL = "{{ dm_name }}";
    static final public String[] idf_list = new String[]{
    {% for f in idf_list %}
        "{{ f }}",
    {% endfor %}
    };

    static final public String[] odf_list = new String[]{
    {% for f in odf_list %}
        "{{ f }}",
    {% endfor %}
    };

    static public void deviceInitialize () {
{{ code_deviceInitialize }}
    }

    static public void device2EasyConnect (ByteQueue bq) {
{{ code_device2EasyConnect }}
    }

    static public void easyConnect2Device (String feature, JSONArray data) {
{{ code_easyConnect2Device }}
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
