package com.example.cicbrick2openmtc;

import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class BluetoothManager {
    
    public  static BluetoothAdapter bt_adapter = null;
    public  static BroadcastReceiver receiver = null;
    public  static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static boolean initial_bluetooth_on = false;
    
    private static int status = C.IDLE;
    
    private static Handler message_handler = null;
    
    static public void init (Handler handler) {
        
        bt_adapter = BluetoothAdapter.getDefaultAdapter();
        
        receiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                // When discovery finds a device
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    // Get the BluetoothDevice object from the Intent
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    logging("Found new device: " + device.getName() + " &" + device.getAddress());

                    notify_message(C.NEW_DEVICE, device.getName(), device.getAddress() );
                }
            }
        };
        
        status = C.IDLE;
        
        initial_bluetooth_on = bt_adapter.isEnabled();
        
        message_handler = handler;
        
    }
    
    static public boolean bluetooth_enabled () {
        if (bt_adapter == null) return false;
        
        return bt_adapter.isEnabled();
    }
    
    static public boolean enable_bluetooth () {
        if (bt_adapter == null) return false;
        
        return bt_adapter.enable();
    }
    
    static public void start_discovery () {
        if (bt_adapter == null) {
            logging("start_discovery: BluetoothManager is not initialized");
            return;
        }
        
        bt_adapter.cancelDiscovery();
        
        logging("start_discovery: " + bt_adapter.startDiscovery());
        status = C.SEARCHING;
    }

    static public void stop_discovery () {
        if (bt_adapter == null) {
            logging("stop_discovery: BluetoothManager is not initialized");
            return;
        }
        
        logging("stop_discovery");
        bt_adapter.cancelDiscovery();
        status = C.IDLE;
    }
    
    static public void recover_bluetooth_status () {
        if (bt_adapter == null) {
            logging("recover_bluetooth_status: BluetoothManager is not initialized");
            return;
        }
        
        if ( !initial_bluetooth_on ) {
            logging("recover_bluetooth_status: recover bluetooth status");
            bt_adapter.disable();
        }
    }
    
    static private void notify_message (int type, String device_name, String device_addr) {
        Message msgObj = message_handler.obtainMessage();
        Bundle b = new Bundle();
        b.putInt("type", type);
        b.putString("device_name", device_name);
        b.putString("device_addr", device_addr);
        msgObj.setData(b);
        message_handler.sendMessage(msgObj);

    }
    
    static private void logging (String message) {
        Log.i(C.log_tag, "[BluetoothManager] " + message);
    }
    
}