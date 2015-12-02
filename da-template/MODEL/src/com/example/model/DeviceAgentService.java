package com.example.{{ dm_name_l }};

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.Semaphore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

public class DeviceAgentService extends Service {
    static private DeviceAgentService self;
    static public enum States {
        INITIALIZED,
        CONNECTED,
        RECONNECTING,
        FUCKED_UP,
        DISCONNECTING,
        DISCONNECTED,
    };
    static Semaphore state_lock;
    static private States state;

    Handler easyconnect_status_handler;
    Handler easyconnect_data_handler;

    static private String device_name;
    static private String device_addr;
    static private BluetoothSocket bt_socket;
    static private OutputStream out_stream;
    static private InputStream in_stream;
    static private final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final IBinder mBinder = new MyBinder();
    public class MyBinder extends Binder {
        DeviceAgentService getService() {
            return DeviceAgentService.this;
        }
    }

    static public boolean is_running () {
        return self != null;
    }

    @Override
    public void onCreate () {
        logging("onCreate");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (self == null) {
            self = this;
            Bundle b = intent.getExtras();
            device_name = b.getString("device_name");
            device_addr = b.getString("device_addr");
            state_lock = new Semaphore(1);

            easyconnect_status_handler = new Handler () {
                public void handleMessage (Message msg) {
                    switch ((EasyConnect.Tag)msg.getData().getSerializable("tag")) {
                    case ATTACH_SUCCESS:
                        for (String f: Custom.odf_list) {
                            EasyConnect.subscribe(f, easyconnect_data_handler);
                        }
                        UpStreamThread.start_working();
                        break;
                    }
                }
            };
            EasyConnect.register(easyconnect_status_handler);

            easyconnect_data_handler = new Handler () {
                public void handleMessage (Message msg) {
                    String feature = msg.getData().getString("feature");
                    EasyConnect.DataSet dataset = msg.getData().getParcelable("dataset");
                    Custom.easyConnect2Device(feature, (JSONArray)(dataset.newest().data));
                }
            };

            set_state(States.INITIALIZED);
            logging("DeviceAgentService initialized");
            start_working();
        } else {
            logging("Already initialized");

        }
        return Service.START_NOT_STICKY;
    }

    static private class UpStreamThread extends Thread {
        static private UpStreamThread self;
        static private ByteQueue bq;

        private UpStreamThread () {
            bq = new ByteQueue();
        }

        static public void start_working () {
            if (self == null) {
                self = new UpStreamThread();
                self.start();
            }
        }

        public void run () {
            while (true) {
                try {
                    byte[] buffer = new byte[1024];
                        int read_bytes = in_stream.read(buffer);
                        if (read_bytes < 0) {
                            throw new IOException();
                        }
                        bq.push(buffer, read_bytes);
                        Custom.device2EasyConnect(bq);
                } catch (IOException e) {
                    logging("UpStreamThread: IOException");
                    if (get_state() != States.DISCONNECTED && get_state() != States.DISCONNECTING) {
                        reconnect();
                    } else {
                        break;
                    }
                } catch (NullPointerException e) {
                    logging("UpStreamThread: NullPointerException");
                    if (get_state() != States.DISCONNECTED && get_state() != States.DISCONNECTING) {
                        reconnect();
                    } else {
                        break;
                    }
                }
            }
        }
    }

    static private void start_working () {
        logging("start_working()");
        connect();
        if (get_state() != States.CONNECTED) {
            // something fucked up
            logging("start_working() failed");
            self.stopSelf();
            return;
        }

        logging("Registering to EC");
        JSONObject profile = new JSONObject();
        try {
            profile.put("d_name", device_name);
            profile.put("dm_name", Custom.DEVICE_MODEL);
            JSONArray feature_list = new JSONArray();
            for (String f: Custom.idf_list) {
                feature_list.put(f);
            }
            for (String f: Custom.odf_list) {
                feature_list.put(f);
            }
            profile.put("df_list", feature_list);
            profile.put("u_name", "yb");
            profile.put("monitor", EasyConnect.get_mac_addr());
            EasyConnect.attach(EasyConnect.get_d_id(device_addr.replace(":", "")), profile);
        } catch (JSONException e) {
            logging("JSONException when attaching");
            e.printStackTrace();
        }
    }

    static private void connect () {
        if (get_state() == States.CONNECTED) return;
        BluetoothDevice target_device = BluetoothManager.bt_adapter.getRemoteDevice(device_addr);
        while (get_state() == States.INITIALIZED || get_state() == States.RECONNECTING || get_state() == States.DISCONNECTED) {
            logging("Connecting to device "+ device_name +" ("+ device_addr +")");
            try {
                bt_socket = create_bluetooth_socket(target_device);
                bt_socket.connect();
                out_stream = bt_socket.getOutputStream();
                in_stream = bt_socket.getInputStream();
                set_state(States.CONNECTED);
                logging("Connected");
                return;

            } catch (Exception e) {
                e.printStackTrace();
                set_state(States.RECONNECTING);
                logging("Connecting to device "+ device_name +" ("+ device_addr +") failed, close socket");
                try {
                    if (bt_socket != null) { bt_socket.close(); }
                    if (out_stream != null) { out_stream.close(); }
                    if (in_stream != null) { in_stream.close(); }
                } catch (IOException e1) {
                    logging("IOException when cleaning up");
                    e1.printStackTrace();
                }
            }

            try {
                logging("Waiting 150ms to retry");
                Thread.sleep(150);
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            }
        }
    }

    static public void stop_working () {
        disconnect();
        EasyConnect.detach();
        self.stopSelf();
    }

    static private void disconnect () {
        if (get_state() != States.CONNECTED && get_state() != States.RECONNECTING) {
            return;
        }
        set_state(States.DISCONNECTING);

        logging("Disonnecting from device "+ device_name +" ("+ device_addr +")");
        try {
            if (out_stream != null) {
                out_stream.flush();
                out_stream.close();
            }
        } catch (IOException e) {
            logging("IOException when cleaning out_stream");
            e.printStackTrace();
        } finally {
            out_stream = null;
        }

        try {
            if (in_stream != null) { in_stream.close(); }
        } catch (IOException e) {
            logging("IOException when cleaning in_stream");
            e.printStackTrace();
        } finally {
            in_stream = null;
        }

        try {
            if (bt_socket != null) { bt_socket.close(); }
        } catch (IOException e) {
            logging("IOException when cleaning bt_socket");
            e.printStackTrace();
        } finally {
            bt_socket = null;
        }

        logging("Disonnected");
        set_state(States.DISCONNECTED);
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return mBinder;
    }

    @Override
    public void onDestroy () {
        self = null;
    }

    static public void reconnect () {
        if (get_state() == States.RECONNECTING) return;
        set_state(States.RECONNECTING);
        disconnect();
        connect();
    }

    static private BluetoothSocket create_bluetooth_socket(BluetoothDevice device) throws IOException, Exception {
        if(Build.VERSION.SDK_INT >= 10){
            Method m = device.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", new Class[] { UUID.class });
            return (BluetoothSocket) m.invoke(device, MY_UUID);
        }
        return device.createRfcommSocketToServiceRecord(MY_UUID);
    }

    static public String get_device_name () {
        if (device_name == null) return "(null)";
        return device_name;
    }

    static public String get_device_addr () {
        if (device_addr == null) return "(null)";
        return device_addr;
    }

    static public States get_state () {
        try {
            state_lock.acquire();
            States s = state;
            state_lock.release();
            return s;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return state;
    }

    static private void set_state (States s) {
        try {
            state_lock.acquire();
            state = s;
            state_lock.release();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    static public void send_data (String text) {
        send_data(text.getBytes());
    }

    static public void send_data (byte[] data) {
        if (out_stream == null) return;
        if (get_state() != States.CONNECTED) return;

        try {
            out_stream.write(data);
        } catch (IOException e) {
            logging("IOException when send_data()");
            reconnect();
        }
    }

    static private void logging (String message) {
        Log.i(C.log_tag, "[DeviceAgentService] " + message);
    }

}
