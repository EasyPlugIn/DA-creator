package com.example.cicbrick2openmtc;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.UUID;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class DeviceAgent {
    String device_name;
    String device_addr;
    String device_model;
    String[] df_list;
    String phone_mac_addr;
    
    boolean register_success;
    
    int status;
    BluetoothSocket bt_socket;
    OutputStream out_stream;
    InputStream in_stream;
    final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    static Handler message_handler;
    
    public DeviceAgent (String device_name, String device_addr, Handler message_handler) {
        
        this.device_name = device_name;
        this.device_addr = device_addr;
        this.device_model = "";
        this.status = C.IDLE;
        
        this.bt_socket = null;
        this.out_stream = null;
        
        DeviceAgent.message_handler = message_handler;
    }
    
    public void set_device_model (String device_model) {
        this.device_model = device_model;
    }
    
    public int status () {
        return status;
    }
    
    public String get_name () {
        return this.device_name;
    }
    
    public String get_addr() {
        return this.device_addr;
    }
    
    public boolean equals (Object o) {
        if (o instanceof DeviceAgent) {
            DeviceAgent oo = (DeviceAgent) o;
            return device_addr.equals( oo.device_addr );
        }
        return false;
    }
    
    public boolean start_working (String[] df_list, String phone_mac_addr) {
        
        this.df_list = df_list;
        this.phone_mac_addr = phone_mac_addr;
        
        notify_message("[DeviceAgent] start_working()");
        
        Custom.device = this;
        
        connect();
        
        while (status == C.CONNECTING) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        
        if ( status == C.CONNECTED ) {
            notify_message("[DeviceAgent] Device connected");
            
            notify_message("[DeviceAgent] Registering to OpenMTC");
            new RegisterThread().start();
            
            Custom.device_initialize();
            
            notify_message("[DeviceAgent] Starting UpThread");
            new UpThread().start();
            
            notify_message("[DeviceAgent] Starting DownThread");
            new DownThread().start();
            
            return true;
            
        } else {
            notify_message("[DeviceAgent] start_working() failed");
            return false;
            
        }
        
    }
    
    private void connect () {
        
        status = C.CONNECTING;
        
        (new Thread () {
            @Override
            public void run () {
                
                notify_message("[DeviceAgent] Connecting to device "+ device_name +" &"+ device_addr);
                
                BluetoothDevice target_device = BluetoothManager.bt_adapter.getRemoteDevice(device_addr);
                try {
                    bt_socket = create_bluetooth_socket(target_device);
                    bt_socket.connect();
                    out_stream = bt_socket.getOutputStream();
                    in_stream = bt_socket.getInputStream();
                    status = C.CONNECTED;

                } catch (IOException e) {
                    e.printStackTrace();
                    status = C.IDLE;
                    notify_message("[DeviceAgent] Connecting to device "+ device_name +" &"+ device_addr +" failed");
                } catch (Exception e) {
                    e.printStackTrace();
                    status = C.IDLE;
                    notify_message("[DeviceAgent] Connecting to device "+ device_name +" &"+ device_addr +" failed");
                }
                
            }
        }).start();

    }
    
    private void disconnect () {
        
        Custom.device_terminate();
        notify_message("[DeviceAgent] Disconnecting from device "+ device_name +" &"+ device_addr);
        try {

            if (out_stream != null) {
                out_stream.close();
                out_stream = null;
            }
            
            if (in_stream != null) {
                in_stream.close();
                in_stream = null;
            }

            if (bt_socket != null) {
                bt_socket.close();
                bt_socket = null;
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        
    }
    
    public void stop_working () {
        
        notify_message("[DeviceAgent] stop_working()");
        (new Thread () {
            @Override
            public void run () {
                
                notify_message("[DeviceAgent] detach from OpenMTC");
                EasyConnect.detach();
                
                notify_message("[DeviceAgent] disconnect from device");
                disconnect();
            }
        }).start();
    }
    
    private BluetoothSocket create_bluetooth_socket(BluetoothDevice device) throws IOException, Exception {
        if(Build.VERSION.SDK_INT >= 10){
            final Method  m = device.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", new Class[] { UUID.class });
            return (BluetoothSocket) m.invoke(device, MY_UUID);
        }
        return device.createRfcommSocketToServiceRecord(MY_UUID);
    }
    
    class RegisterThread extends Thread {
        @Override
        public void run () {
            /* fix here */
            register_success = EasyConnect.attach(device_addr, device_model, df_list, device_name, "yb", phone_mac_addr);
            
        }
    }
    
    class UpThread extends Thread {
        @Override
        public void run () {
            
            notify_message("[DeviceAgent] UpThread start running");
            
            byte[] buffer = new byte[1024];
            byte[] lbuffer = new byte[128];
            int index = 0;
            int data_consumed = 0;
            
            while (in_stream != null) {
                try {
                    int data_readed = in_stream.read(lbuffer);
                    notify_message("[DeviceAgent] read " + data_readed + "bytes");
                    
                    for (int a = 0; a < data_readed; a++) {
                        buffer[index + a] = lbuffer[a];
                    }
                    index += data_readed;
                    
                    if (index >= Custom.ONE_DATA_SIZE) {
                        data_consumed = Custom.json_encode_and_push(buffer);
                        for (int a = 0; a < index; a++) {
                            buffer[a] = buffer[a + data_consumed];
                            index = 0;
                        }
                        
                    }
                    
                } catch (IOException e) {
                    e.printStackTrace();
                }
                
            }
            
            notify_message("[DeviceAgent] UpThread ends");
            
        }
    }
    
    class DownThread extends Thread {
        @Override
        public void run () {
            
            notify_message("[DeviceAgent] DownThread start running");
            
            while (out_stream != null) {
                
                try {
                    send_data( Custom.json_decoder() );
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
                
                try {
                    Thread.sleep(150);
                    
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                
            }
            
            notify_message("[DeviceAgent] DownThread ends");
            
        }
    }
    
    public void send_data (String data) throws IOException {
        send_data( data.getBytes() );
    }
    
    public void send_data (byte[] data) throws IOException {
        if (out_stream == null) return;
        out_stream.write(data);
    }
    
    boolean logging = true;

    public void notify_message (String message) {
        
        if ( !logging ) return;
        
        //System.out.println(message);
        Log.i(C.log_tag, message);
        
        if (message_handler == null) return;
        
        message = message + "\n";
        Message msgObj = message_handler.obtainMessage();
        Bundle b = new Bundle();
        b.putString("data", message);
        msgObj.setData(b);
        message_handler.sendMessage(msgObj);
    }
    
}