package com.example.{{ dm_name_l }};

import java.util.ArrayList;
import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class DeviceAgentAdapter extends ArrayAdapter<DeviceAgent> {

    Context context;
    int layout_resource_id;
    ArrayList<DeviceAgent> data = null;

    public DeviceAgentAdapter (Context context, int layout_resource_id, ArrayList<DeviceAgent> data) {
        super(context, layout_resource_id, data);
        this.layout_resource_id = layout_resource_id;
        this.context = context;
        this.data = data;
    }

    @Override
    public View getView (final int position, View convertView, ViewGroup parent) {
        View row = convertView;
        DeviceHolder holder = null;

        if(row == null) {
            LayoutInflater inflater = ((Activity)context).getLayoutInflater();
            row = inflater.inflate(layout_resource_id, parent, false);

            holder = new DeviceHolder();
            holder.device_name = (TextView)row.findViewById(R.id.device_name);
            holder.device_addr = (TextView)row.findViewById(R.id.device_addr);

            row.setTag(holder);

        } else {
            holder = (DeviceHolder)row.getTag();
        }

        final DeviceAgent target_device = data.get(position);

        String name_str = target_device.get_name();
        if (name_str == null) {
            name_str = "(null)";
        }
        holder.device_name.setText(name_str);
        holder.device_addr.setText(target_device.get_addr());

        return row;
    }

    static class DeviceHolder {
        TextView device_name;
        TextView device_addr;
    }
}
