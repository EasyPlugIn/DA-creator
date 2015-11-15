package com.example.morsensor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
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
    public Handler message_handler = null;
    boolean running_permission;
    
    static DetectLocalECThread detect_local_ec_thread;
    
    public DeviceAgent (String device_name, String device_addr) {
        
        this.device_name = device_name;
        this.device_addr = device_addr;
        this.device_model = "";
        this.status = C.IDLE;
        
        this.bt_socket = null;
        this.out_stream = null;
        
        this.message_handler = null;
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
        
        logging("[DeviceAgent] start_working()");
        
        running_permission = true;
        
        Custom.device = this;
        
        connect();
        
        if ( status == C.CONNECTED ) {
            logging("[DeviceAgent] Device connected");
            logging("[DeviceAgent] Detecting Local EC");
            detect_local_ec_thread = new DetectLocalECThread();
            detect_local_ec_thread.start();
            
            logging("[DeviceAgent] Registering to EC");
            new RegisterThread().start();
            
            // initialize, up thread and down thread has to do after register successed
            // so they are put into RegisterThread.run()
            return true;
            
        } else {
            logging("[DeviceAgent] start_working() failed");
            return false;
            
        }
        
    }
    
    private void connect () {
    	if (status == C.CONNECTING) return;
        status = C.CONNECTING;
        
        (new Thread () {
            @Override
            public void run () {
                logging("[DeviceAgent] Connecting to device "+ device_name +" &"+ device_addr);
                
                BluetoothDevice target_device = BluetoothManager.bt_adapter.getRemoteDevice(device_addr);
                try {
                    bt_socket = create_bluetooth_socket(target_device);
                    bt_socket.connect();
                    out_stream = bt_socket.getOutputStream();
                    in_stream = bt_socket.getInputStream();
                    status = C.CONNECTED;

                } catch (Exception e) {
                    e.printStackTrace();
                    status = C.IDLE;
                    logging("[DeviceAgent] Connecting to device "+ device_name +" &"+ device_addr +" failed, close socket");
                    try {
						bt_socket.close();
					} catch (IOException e1) {
	                    logging("[DeviceAgent] close socket failed");
						e1.printStackTrace();
					}
                }
                
            }
        }).start();
        
        while (status == C.CONNECTING && running_permission) {
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }
    
    private void disconnect () {
    	if (status == C.IDLE || status == C.CONNECTING) {
    		return;
    	}
        Custom.device_terminate();
        logging("[DeviceAgent] Disconnecting from device "+ device_name +" &"+ device_addr);
        try {
            if (out_stream != null) {
            	out_stream.flush();
                out_stream.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        out_stream = null;

        try {
            if (in_stream != null) {
                in_stream.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        in_stream = null;

        try {
            if (bt_socket != null) {
                bt_socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        bt_socket = null;
        
        status = C.IDLE;
        
    }
    
    public void reconnect () {
    	if (status != C.CONNECTED) {
    		return;
    	}
    	
        disconnect();

    	while (status != C.CONNECTED && running_permission) {
        	connect();
            try {
                Thread.sleep(150);
            } catch (InterruptedException e2) {
                e2.printStackTrace();
            }
        }
    }
    
    public void stop_working () {
        logging("[DeviceAgent] stop_working()");
        (new Thread () {
            @Override
            public void run () {
            	logging("detach from EC");
                EasyConnect.detach();
    	        EasyConnect.reset_ec_host();
                
                logging("[DeviceAgent] disconnect from device");
                disconnect();
                
            }
        }).start();
        detect_local_ec_thread.stop_working();
        running_permission = false;
    }
    
    private BluetoothSocket create_bluetooth_socket(BluetoothDevice device) throws IOException, Exception {
        if(Build.VERSION.SDK_INT >= 10){
            Method m = device.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", new Class[] { UUID.class });
            return (BluetoothSocket) m.invoke(device, MY_UUID);
        }
        return device.createRfcommSocketToServiceRecord(MY_UUID);
    }
    
    class RegisterThread extends Thread {
        @Override
        public void run () {
        	show_ec_status(false);
        	while (!register_success && running_permission) {
        		if (bt_socket == null) break;
        		register_success = EasyConnect.attach(device_addr, device_model, df_list, device_name, "yb", phone_mac_addr);
        		if (!register_success) {
                    logging("[DeviceAgent] Attach failed, wait for 2000 ms and try again");
            		try {
    					Thread.sleep(2000);
    				} catch (InterruptedException e) {
    					e.printStackTrace();
    				}
            		
        		} else {
                	show_ec_status(true);
                    logging("[DeviceAgent] Attach successed");
                    Custom.device_initialize();
                    
                    logging("[DeviceAgent] Starting UpThread");
                    new UpThread().start();
                    
                    logging("[DeviceAgent] Starting DownThread");
                    new DownThread().start();
        		}
        	}

            logging("[DeviceAgent] RegisterThread ends");
            
        }
    }
    
    private class DetectLocalECThread extends Thread {
    	DatagramSocket socket;
    	public void stop_working () {
    		socket.close();
    	}
    	public void run () {
    		logging("Detection Thread starts");
    		try {
    			String current_ec_host = EasyConnect.EC_HOST;
				socket = new DatagramSocket(null);
				socket.setReuseAddress(true);
				socket.bind(new InetSocketAddress("0.0.0.0", EasyConnect.EC_BROADCAST_PORT));
				byte[] lmessage = new byte[20];
				DatagramPacket packet = new DatagramPacket(lmessage, lmessage.length);
				while (running_permission) {
                    socket.receive(packet);
                    String input_data = new String( lmessage, 0, packet.getLength() );
                    if (input_data.equals("easyconnect")) {
                    	InetAddress ec_raw_addr = packet.getAddress();
                    	String ec_addr = ec_raw_addr.getHostAddress();
                    	logging("Get easyconnect UDP Packet from "+ ec_addr);
                    	String new_ec_host = ec_addr +":"+ EasyConnect.EC_PORT;
                    	if (!current_ec_host.equals(new_ec_host)) {
                    		logging("Reattach to "+ new_ec_host);
	                    	EasyConnect.reattach_to(new_ec_host);
	                    	current_ec_host = new_ec_host;
                    	}
                    	show_ec_status(true);
                    }
                }
				socket.close();
			} catch (SocketException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
    		logging("Detection Thread ends");
    	}
    }
    
    class UpThread extends Thread {
        @Override
        public void run () {
            
            logging("[DeviceAgent] UpThread start running");
            
            byte[] buffer = new byte[1024];
            byte[] lbuffer = new byte[128];
            int index = 0;
            int data_consumed = 0;
            
            while (running_permission) {
                try {
                    int data_readed = in_stream.read(lbuffer);
                    logging("[DeviceAgent] read " + data_readed + "bytes");
                    
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
                    
                } catch (Exception e) {
                    try {
                        Thread.sleep(150);
                    } catch (InterruptedException e2) {
                        e2.printStackTrace();
                    }
                    
                    e.printStackTrace();
                    logging("[DeviceAgent] Bluetooth socket broken, try reconnecting...");
                    reconnect();
                	
                }
                
            }
            
            logging("[DeviceAgent] UpThread ends");
            
        }
    }
    
    class DownThread extends Thread {
        @Override
        public void run () {
            
            logging("[DeviceAgent] DownThread start running");
            
            while (running_permission) {
                
                try {
                    send_data( Custom.json_decoder() );
                } catch (Exception e) {
                    try {
                        Thread.sleep(150);
                    } catch (InterruptedException e2) {
                        e2.printStackTrace();
                    }
                    
                    e.printStackTrace();
                    logging("[DeviceAgent] Bluetooth socket broken, try reconnecting...");
                    reconnect();
                	
                }
                
                try {
                    Thread.sleep(150);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                
            }
            
            logging("[DeviceAgent] DownThread ends");
            
        }
    }
    
    public void send_data (String data) throws IOException {
        send_data( data.getBytes() );
    }
    
    public void send_data (byte[] data) throws IOException {
        if (out_stream == null) return;
        out_stream.write(data);
    }

    public void logging (String message) {
        //System.out.println(message);
        Log.i(C.log_tag, message);
    }
    
    boolean ec_status;
    public void show_ec_status (boolean b) {
    	ec_status = b;
    	show_ec_status();
    }
    
    public void show_ec_status () {
    	Handler handler = MainActivity.ec_status_handler;
    	Log.i(C.log_tag, "Update UI");
    	if (handler == null) {
        	Log.i(C.log_tag, "Update UI failed, handler is null");
    		return;
    	}
    	
        Message msgObj = handler.obtainMessage();
        Bundle b = new Bundle();
        b.putBoolean("message", ec_status);
        msgObj.setData(b);
        handler.sendMessage(msgObj);
    }
    
}