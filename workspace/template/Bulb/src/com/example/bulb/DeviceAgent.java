package com.example.bulb;

import java.util.UUID;

public class DeviceAgent {
    String device_name;
    String device_addr;
    
    final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    boolean running_permission;
    
    public DeviceAgent (String device_name, String device_addr) {
        this.device_name = device_name;
        this.device_addr = device_addr;
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
    
}