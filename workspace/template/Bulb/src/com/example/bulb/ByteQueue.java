package com.example.bulb;

import java.util.Iterator;
import java.util.LinkedList;

public class ByteQueue {
	LinkedList<Byte> buffer;
	
	public ByteQueue () {
		buffer = new LinkedList<Byte>();
	}
	
	public void push (byte b) {
		buffer.add(b);
	}
	
	public void push (byte[] ba, int count) {
		for (int i = 0; i < count; i++) {
			buffer.add(ba[i]);
		}
	}
	
	public int size () {
		return buffer.size();
	}
	
	public byte[] first (int count) {
		int size = Math.min(buffer.size(), count);
		byte[] ret = new byte[size];
		Iterator<Byte> it = buffer.iterator();
		for (int i = 0; i < size; i++) {
			ret[i] = it.next();
		}
		return ret;
	}
	
	public void consume (int count) {
		if (count >= buffer.size()) {
			buffer.clear();
			return;
		}
		
		for (int i = 0; i < count; i++) {
			buffer.remove();
		}
	}
}