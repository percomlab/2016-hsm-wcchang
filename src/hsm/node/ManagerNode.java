package hsm.node;

import java.util.UUID;

public class ManagerNode {
	public ManagerNode(UUID uuid, String ip, int port, String filePath) {
		setUuid(uuid);
		setNodeIp(ip);
		setNodePort(port);
		setFilePath(filePath);
	}
	////////////////////////////////////////////////
	// UUID
	////////////////////////////////////////////////

	private UUID uuid;
	
	public void setUuid(UUID uuid) {
		this.uuid = uuid;
	}

	public UUID getUuid() {
		return uuid;
	}

	////////////////////////////////////////////////
	// Node IP
	////////////////////////////////////////////////
	
	private String nodeIp;
	
	public void setNodeIp(String nodeIp) {
		this.nodeIp = nodeIp;
	}
	
	public String getNodeIp() {
		return nodeIp;
	}

	////////////////////////////////////////////////
	// Node Port
	////////////////////////////////////////////////

	private int nodePort;
	
	public void setNodePort(int nodePort) {
		this.nodePort = nodePort;
	}

	public int getNodePort() {
		return nodePort;
	}

	////////////////////////////////////////////////
	// File Path
	////////////////////////////////////////////////

	private String filePath;
	
	public void setFilePath(String filePath) {
		this.filePath = filePath;
	}

	public String getFilePath() {
		return filePath;
	}
}
