package com.example.cicbrick2openmtc;

import java.util.ArrayList;

import android.app.Activity;
import android.app.AlertDialog;
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

    ArrayList<DeviceAgent> device_list = null;
    DeviceAgentAdapter device_list_adapter = null;
    
    Handler bluetooth_handler = null;
    
    Handler device_handler = null;
    
    DeviceAgentService da_service = null;
    ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            
            da_service = ((DeviceAgentService.MyBinder)service).getService();
            logging("DeviceAgentService connected");
            
            String device_name = da_service.get_device_name();
            String device_addr = da_service.get_device_addr();
            
            TextView tv_device_name_prompt = (TextView) findViewById(R.id.tv_device_name_prompt);
            tv_device_name_prompt.setText("Device Name: " + device_name); 
            
            TextView tv_device_addr_prompt = (TextView) findViewById(R.id.tv_device_addr_prompt);
            tv_device_addr_prompt.setText("Device Address: " + device_addr);
            
        }

        public void onServiceDisconnected(ComponentName className) {
            logging("(Error) DeviceAgentService disconnected");
            
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        Log.e(C.log_tag, "create session filter");
        //DeFeMa.set_log_tag(C.log_tag);
        
        DeviceAgentService.set_wifi_mac_addr( get_wifi_mac_addr() );
        
        if ( !DeviceAgentService.is_running() ) {
            setContentView(R.layout.activity_main_device_list);
        
            ListView lv_device_list = (ListView) findViewById(R.id.lv_device_list);
            device_list = new ArrayList<DeviceAgent>();
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
                    dialog.setView(input);
                    
                    dialog.setPositiveButton("OK", new DialogInterface.OnClickListener () {
                        public void onClick (DialogInterface dialog, int id) {
                            String device_name = input.getText().toString();
                            
                            if (device_name.equals("")) {
                                device_name = device_list.get(position).device_name;
                            }

                            Intent intent = new Intent (MainActivity.this, DeviceAgentService.class);
                            Bundle b = new Bundle();
                            b.putString( "device_name", device_name );
                            b.putString( "device_addr", device_list.get(position).device_addr );
                            intent.putExtras(b);
                            startService(intent);
                            
                            intent = new Intent (MainActivity.this, MainActivity.class);
                            startActivity(intent);
                            end();
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
                        
                        device_list.add( new DeviceAgent(device_name, device_addr, device_handler) );
                        device_list_adapter.notifyDataSetChanged();
                    }
                }
            };
            
            device_handler = new Handler () {
                public void handleMessage (Message msg) {
                    
                }
            };
            
            BluetoothManager.init(bluetooth_handler);
            
            if ( !BluetoothManager.bluetooth_enabled() ) {
                /* this must be after BluetoothManager.init*/
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, C.REQUEST_ENABLE_BT);
                
            } else {
                logging("bluetooth is already enabled");
                BluetoothManager.start_discovery();
                IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
                registerReceiver(BluetoothManager.receiver, filter);
            }
            
        } else {
            setContentView(R.layout.activity_main_blocked);
            Button btn_stop_service = (Button) findViewById(R.id.btn_stop_service);
            btn_stop_service.setOnClickListener(new View.OnClickListener () {
                @Override
                public void onClick (View v) {
                    if (da_service != null) { // prevent unbind twice
                            
                        logging("Request DeviceAgentService stop");
                        stopService(new Intent(MainActivity.this, DeviceAgentService.class));
                        unbindService(mConnection);
                        da_service = null;
                        
                        end();
                        finish();
                        
                    }
                }
            });

            Intent i = new Intent (this, DeviceAgentService.class);
            bindService( i, mConnection, Context.BIND_AUTO_CREATE);
            
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
            end();
            finish();
            
        }

    }
    
    @Override
    public void onDestroy () {
        super.onDestroy();
        end();
        
    }
    
    public void end () {

        BluetoothManager.stop_discovery();

        try{
            unregisterReceiver(BluetoothManager.receiver);
        } catch (IllegalArgumentException e){
        }
        
        if (da_service != null) {
            unbindService(mConnection);
            da_service = null;
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

}
