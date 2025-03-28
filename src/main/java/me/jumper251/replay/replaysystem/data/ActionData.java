package me.jumper251.replay.replaysystem.data;

import java.io.Serializable;


import me.jumper251.replay.replaysystem.data.types.PacketData;

public class ActionData implements Serializable{

	/**
	 * 
	 */
	private static final long serialVersionUID = 8865556874115905167L;

	private int tickIndex;
	
	private ActionType type;
	
	private PacketData packetData;
	
	private String name;
	private String alias;
	
	public ActionData(int tickIndex, ActionType type, String name, PacketData packetData, String alias) {
		this.tickIndex = tickIndex;
		this.type = type;
		this.packetData = packetData;
		this.name = name;
		this.alias = alias;
	}
	
	public int getTickIndex() {
		return tickIndex;
	}
	
	public ActionType getType() {
		return type;
	}
	
	public PacketData getPacketData() {
		return packetData;
	}
	
	public String getName() {
		return name;
	}
	
	public String getAlias() {
		return alias;
	}	
}
