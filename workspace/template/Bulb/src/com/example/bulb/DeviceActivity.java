package com.example.bulb;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;


public class DeviceActivity extends Activity {
	static DeviceActivity self = null;
    
    DeviceAgentService da_service = null;
    ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            
            da_service = ((DeviceAgentService.MyBinder)service).getService();
            logging("DeviceAgentService connected");
            
            String device_name = DeviceAgentService.get_device_name();
            String device_addr = DeviceAgentService.get_device_addr();
            
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
    	if (self != null) {
    		finish();
    	}
    	self = this;
    	
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main_connected);

        // start EasyConnect Service
        EasyConnect.start(this, Custom.DEVICE_MODEL);
        
        Bundle b = getIntent().getExtras();
        if (b != null) {
	        String device_name = b.getString("device_name");
	        String device_addr = b.getString("device_addr");
	        Intent intent = new Intent (DeviceActivity.this, DeviceAgentService.class);
	        b = new Bundle();
	        b.putString("device_name", device_name);
	        b.putString("device_addr", device_addr);
	        intent.putExtras(b);
	        startService(intent);
        }
    
        Button btn_stop_service = (Button) findViewById(R.id.btn_stop_service);
        btn_stop_service.setOnClickListener(new View.OnClickListener () {
            @Override
            public void onClick (View v) {
                if (da_service != null) { // prevent unbind twice
                    logging("Request DeviceAgentService stop");
//                    stopService(new Intent(DeviceActivity.this, DeviceAgentService.class));
                    DeviceAgentService.stop_working();
                    unbindService(mConnection);
                    da_service = null;
                    end();
                    finish();
                }
            }
        });
        
        Button btn_rebuild_service = (Button) findViewById(R.id.btn_rebuild_service);
        btn_rebuild_service.setOnClickListener(new View.OnClickListener () {
            @Override
            public void onClick (View v) {
                if (da_service != null) {
                	DeviceAgentService.reconnect();
                }
            }
        });
        
        Button btn_send = (Button)findViewById(R.id.btn_send);
        btn_send.setOnClickListener(new View.OnClickListener () {
            @Override
            public void onClick (View v) {
                if (DeviceAgentService.get_state() == DeviceAgentService.States.CONNECTED) {
                	EditText et_send = (EditText)findViewById(R.id.et_send);
					DeviceAgentService.send_data(et_send.getText().toString());
                }
            }
        });
        
        Handler easyconnect_status_handler = new Handler () {
            public void handleMessage (Message msg) {
        		TextView tv_ec_host_address = (TextView)findViewById(R.id.tv_ec_host_address);
        		TextView tv_ec_host_status = (TextView)findViewById(R.id.tv_ec_host_status);

        		tv_ec_host_address.setText(msg.getData().getString("message"));
            	switch ((EasyConnect.Tag)msg.getData().getSerializable("tag")) {
            	case ATTACH_TRYING:
        			tv_ec_host_status.setText("...");
        			tv_ec_host_status.setTextColor(Color.rgb(128, 0, 0));
        			break;
        		
        		case ATTACH_FAILED:
        			tv_ec_host_status.setText("!");
        			tv_ec_host_status.setTextColor(Color.rgb(128, 0, 0));
        			break;
        			
        		case ATTACH_SUCCESS:
        			tv_ec_host_status.setText("~");
        			tv_ec_host_status.setTextColor(Color.rgb(0, 128, 0));
        			break;
            	}
            }
        };
        EasyConnect.register(easyconnect_status_handler);

        Intent i = new Intent(this, DeviceAgentService.class);
        bindService(i, mConnection, Context.BIND_AUTO_CREATE);
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
        self = null;
        logging("end");
    }
    
    public void logging (String l) {
        //System.out.println(l);
        Log.i(C.log_tag, "[DeviceActivity] " + l);
    }
}
