package psmp.test;

import java.util.UUID;

public class InstalledNode {
	private UUID uuid;
	private String address;
	private int port;
	
	public InstalledNode(UUID uuid, String address, int port) {
		this.uuid = uuid;
		this.address = address;
		this.port = port;
	}
	
	public UUID getUuid() {
		return uuid;
	}
	
	public String getAddress() {
		return address;
	}
	
	public int getPort() {
		return port;
	}
	
}
