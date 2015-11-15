package com.example.cicbrick2openmtc;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.json.JSONArray;
import org.json.JSONException;

public class Custom {
    
    static final public String DEVICE_MODEL = "CIC-brick";
    static final public String[] df_list = new String[]{"A-sensor", "G-sensor", "M-sensor"};
    static final public int ONE_DATA_SIZE = 35;
    
    static public void device_initialize () {
        try {
            send_dataln("*SETDB124V1");
            send_dataln("*ZOFF");
            send_dataln("*W2017");
            send_dataln("*W2067");
            send_dataln("*W2328");
            send_dataln("*MW0200");
            send_dataln("*MW001C");
            send_dataln("*MW0180");
            send_dataln("*GW200F");
            send_dataln("*GW208F");
            send_dataln("*GW2310");

        } catch (IOException e) {
            e.printStackTrace();
        }
        
    }

    /*
     * push data with DeFeMa.push_data( <feature_name>, <data> )
     * */
    static public int json_encode_and_push (byte[] raw_data) {
        
        float ax = 0, ay = 0, az = 0;
        float gx = 0, gy = 0, gz = 0;
        float mx = 0, my = 0, mz = 0;
        
        int index = 0;
        while( !(raw_data[index]== 115&& raw_data[index+1]==116) ) {
            index++;
            if(index>=127)
                break;
        }
        
        if (raw_data[index] == 115 && raw_data[index+1] == 116 && index+21<=128 && raw_data[index+21] == -128 ) {
            ay = (float)ByteBuffer.wrap(raw_data, index+2, 2).getShort();
            ax = (float)ByteBuffer.wrap(raw_data, index+4, 2).getShort();
            az = (float)ByteBuffer.wrap(raw_data, index+6, 2).getShort();
            
            gx = (float)ByteBuffer.wrap(raw_data, index+8, 2).getShort();
            gy = (float)ByteBuffer.wrap(raw_data, index+10, 2).getShort();
            gz = (float)ByteBuffer.wrap(raw_data, index+12, 2).getShort();
            
            my = (float)ByteBuffer.wrap(raw_data, index+14, 2).getShort();
            mx = (float)ByteBuffer.wrap(raw_data, index+16, 2).getShort();
            mz = (float)ByteBuffer.wrap(raw_data, index+18, 2).getShort();
            
            ay = ay * ( (float)4 )/( (float)16000 ) * ( (float)9.8 );
            ax = ax * ( (float)4 )/( (float)16000 ) * ( (float)9.8 );
            az = az * ( (float)4 )/( (float)16000 ) * ( (float)9.8 );
            
            gx = gx * ( (float)17.5 )/( (float)1000 );
            gy = gy * ( (float)17.5 )/( (float)1000 );
            gz = gz * ( (float)17.5 )/( (float)1000 );
            
            my = my / ( (float)450 );
            mx = mx / ( (float)450 );
            mz = mz / ( (float)450 );
            
            JSONArray pd;
            
            try {
                pd = new JSONArray();
                pd.put(ax);
                pd.put(ay);
                pd.put(az);
                EasyConnect.push_data("A-sensor", pd);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            try {
                pd = new JSONArray();
                pd.put(gx);
                pd.put(gy);
                pd.put(gz);
                EasyConnect.push_data("G-sensor", pd);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            try {
                pd = new JSONArray();
                pd.put(mx);
                pd.put(my);
                pd.put(mz);
                EasyConnect.push_data("M-sensor", pd);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            
            /* CIC-brick device consumes 35 bytes every time */
            return 35;
            
        }
        
        return 0;
        
    }
    
    /*
     * pull data with DeFeMa.pull_data( <feature_name> )
     * */
    static public String json_decoder () {
        return "*STARTONE\n";
    }
    
    static public void device_terminate () {
        try {
            send_dataln("*ZON");
            send_dataln("*STOP");
        } catch (IOException e) {
            e.printStackTrace();
        }
        
    }

    /******************************************************************************/
    /******************************************************************************/
    /******************************************************************************/
    /****************************** Don't touch here *****************************/
    /******************************************************************************/
    
    static private String timestamp = "";
    static public DeviceAgent device;
    
    /* Don't touch here */
    static private void notify_message (String message) {
        device.notify_message(message);
    }
    
    static private void send_dataln (String data) throws IOException {
        if (device != null) {
            device.send_data(data + "\n");
        }
    }
    
    static private void send_data (String data) throws IOException {
        if (device != null) {
            device.send_data(data);
        }
    }
    
    static private void send_data (byte[] data) throws IOException {
        if (device != null) {
            device.send_data(data);
        }
    }
    
}