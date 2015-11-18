package com.example.bulb;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;


public class MainActivity extends Activity {
	JSONObject history = null;

    ArrayList<DeviceAgent> device_list = null;
    DeviceAgentAdapter device_list_adapter = null;
    HashSet<String> device_set;
    
    static public Handler bluetooth_handler = null;
    static public Handler ec_status_handler = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
    	if (DeviceAgentService.is_running()) {
    		// MainActivity is for searching device
    		// if a device is picked by user, we should switch to DeviceConnectedActivity
            Intent intent = new Intent (MainActivity.this, DeviceActivity.class);
            startActivity(intent);
            finish();
    	}
    	
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main_searching);
        
        DBManager.set_db_name(Custom.DEVICE_MODEL);
        history = new JSONObject();
        try {
        	history = DBManager.dump_db();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        
        ListView lv_device_list = (ListView) findViewById(R.id.lv_device_list);
        device_set = new HashSet<String>();
        device_list = db_to_array_list();
        device_list_adapter = new DeviceAgentAdapter(this, R.layout.device_list_item, device_list);
        lv_device_list.setAdapter(device_list_adapter);
        lv_device_list.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent,
                    View view, final int position, long id) {
                BluetoothManager.stop_discovery();
                AlertDialog.Builder dialog = new AlertDialog.Builder(MainActivity.this);
                dialog.setTitle("Device Name");
                dialog.setMessage("Please give a name to your device");
                
                final EditText input = new EditText(MainActivity.this);
                input.setText(device_list.get(position).device_name);
                dialog.setView(input);
                
                dialog.setPositiveButton("OK", new DialogInterface.OnClickListener () {
                    public void onClick (DialogInterface dialog, int id) {
                        String device_name = input.getText().toString();
                        String device_addr = device_list.get(position).device_addr;
                        
                        if (device_name.equals("")) {
                            device_name = device_list.get(position).device_name;
                        }
                        
                        try {
							history.put(device_addr, device_name);
							DBManager.write_db(history);
						} catch (JSONException e) {
							e.printStackTrace();
						} catch (IOException e) {
							e.printStackTrace();
						}
                        
                        Intent intent = new Intent (MainActivity.this, DeviceActivity.class);
                        Bundle b = new Bundle();
                        b.putString("device_name", device_name);
                        b.putString("device_addr", device_addr);
                        intent.putExtras(b);
                        startActivity(intent);
                        finish();
                        
                    }
                });
                
                dialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener () {
                    public void onClick (DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
                
                dialog.create().show();
            }
        });

        bluetooth_handler = new Handler () {
            public void handleMessage (Message msg) {
                int message_type = msg.getData().getInt("type");
                
                if ( message_type == C.NEW_DEVICE ) {
                    String device_name = msg.getData().getString("device_name");
                    String device_addr = msg.getData().getString("device_addr");
                    
                    if (!(device_set.contains(device_addr))) {
                    	device_list.add( new DeviceAgent(device_name, device_addr) );
                    	device_set.add(device_addr);
                    	device_list_adapter.notifyDataSetChanged();
                    }
                } else if (message_type == C.RESET_BLUETOOTH) {
                    BluetoothManager.stop_discovery();
                    try{
                        unregisterReceiver(BluetoothManager.receiver);
                    } catch (IllegalArgumentException e){
                    }
                }
            }
        };
        
        BluetoothManager.init(bluetooth_handler);
        if ( !BluetoothManager.bluetooth_enabled() ) {
            // this must be after BluetoothManager.init
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, C.REQUEST_ENABLE_BT);
        } else {
            logging("bluetooth is already enabled");
            BluetoothManager.start_discovery();
            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            registerReceiver(BluetoothManager.receiver, filter);
        }
    }
    
    @Override
    public void onConfigurationChanged (Configuration newConfig) {
        // to prevent "screen rotate caused onCreate being called again"
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if ( (requestCode == C.REQUEST_ENABLE_BT) && (resultCode == RESULT_OK) ) {
            BluetoothManager.start_discovery();
            /* this must be after BluetoothManager.init*/
            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            registerReceiver(BluetoothManager.receiver, filter);
        } else if ( (requestCode == C.REQUEST_ENABLE_BT) && (resultCode != RESULT_OK) ) {
            finish();
        }
    }
    
    @Override
    public void onPause () {
    	super.onPause();
    	if (isFinishing()) {
            BluetoothManager.stop_discovery();
            try{
                unregisterReceiver(BluetoothManager.receiver);
            } catch (IllegalArgumentException e){
            }
            logging("MainActivity closing");
    	}
    }
    
    public void logging (String l) {
        //System.out.println(l);
        Log.i(C.log_tag, "[MainActivity] " + l);
    }
    
    private String get_wifi_mac_addr () {
        WifiManager wifiMan = (WifiManager) this.getSystemService(
            Context.WIFI_SERVICE);
        WifiInfo wifiInf = wifiMan.getConnectionInfo();
        return wifiInf.getMacAddress();
    }
    
    private ArrayList<DeviceAgent> db_to_array_list () {
    	ArrayList<DeviceAgent> ret = new ArrayList<DeviceAgent>();
    	Iterator<String> iter = history.keys();
    	while (iter.hasNext()) {
    	    String key = iter.next();
    	    try {
    	        String value = history.getString(key);
                ret.add( new DeviceAgent(value, key) );
                device_set.add(key);
    	        
    	    } catch (JSONException e) {
    	        // Something went wrong!
    	    }
    	}

    	return ret;
    }

}
