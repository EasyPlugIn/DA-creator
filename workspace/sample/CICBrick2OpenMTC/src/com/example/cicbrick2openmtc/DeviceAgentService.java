package com.example.cicbrick2openmtc;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

public class DeviceAgentService extends Service {
    
    static private boolean running = false;
    static private boolean device_working = false;
    static private String phone_mac_addr = "";
    
    DeviceAgent da = null;
    
    private final IBinder mBinder = new MyBinder();
    public class MyBinder extends Binder {
        DeviceAgentService getService() {
            return DeviceAgentService.this;
        }
    }
    
    public DeviceAgentService () {
        running = true;
        da = null;
        notify_message("constructor");
    }
    
    static boolean is_running () {
        return running;
    }
    
    @Override
    public void onCreate () {
        running = true;
        notify_message("onCreate");
        
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if ( da == null ) {
            Bundle b = intent.getExtras();
            String device_name = b.getString("device_name");
            String device_addr = b.getString("device_addr");
            da = new DeviceAgent(device_name, device_addr, null);
            da.set_device_model(Custom.DEVICE_MODEL);
            
            notify_message("initialize done");
            notify_message("Launching DeviceAgent");
            
            device_working = da.start_working(Custom.df_list, phone_mac_addr);
            
            if ( !device_working ) {
                notify_message("failed");
                running = false;
                this.stopSelf();
            }
            
        } else {
            notify_message("already initialized");
            notify_message("Device Agent: " + da);
            notify_message("Device Name: " + da.get_name());
            notify_message("Device Addr: " + da.get_addr());
            notify_message("Device working: " + device_working);
            
        }
        return Service.START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return mBinder;
    }
    
    @Override
    public void onDestroy () {
    	if (da != null) {
    		da.stop_working();
    	}
        running = false;
    }
    
    public String get_device_name () {
        if (da == null) return "(null)";
        return da.get_name();
    }
    
    public String get_device_addr () {
        if (da == null) return "(null)";
        return da.get_addr();
    }
    
    static public void set_wifi_mac_addr (String phone_mac_addr) {
        DeviceAgentService.phone_mac_addr = phone_mac_addr;
    }
    
    boolean logging = true;

    private void notify_message (String message) {
        
        if ( !logging ) return;
        
        //System.out.println("[Service] " + message);
        Log.i(C.log_tag, "[Service] " + message);
    }
    
}