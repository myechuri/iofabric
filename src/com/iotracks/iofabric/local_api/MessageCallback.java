package com.iotracks.iofabric.local_api;

import com.iotracks.iofabric.message_bus.Message;

public class MessageCallback {
	private final String name;
	
	public MessageCallback(String name) {
		this.name = name;
	}
	
	public void sendRealtimeMessage(Message message) {
		//TODO : send received message to container "name"
	}
}