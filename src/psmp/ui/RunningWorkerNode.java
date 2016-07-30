package psmp.ui;

import java.util.UUID;

public class RunningWorkerNode {
	public RunningWorkerNode() {
	}
	
	////////////////////////////////////////////////
	// PSMPControlPanel ID
	////////////////////////////////////////////////
	
	private int psmpControlPanelId;
	
	public void setPsmpControlPanelId(int psmpControlPanelId) {
		this.psmpControlPanelId = psmpControlPanelId;
	}
	
	public int getPsmpControlPanelId() {
		return psmpControlPanelId;
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
	// Node address
	////////////////////////////////////////////////

	private String nodeAddress;
	
	public void setNodeAddress(String nodeAddress) {
		this.nodeAddress = nodeAddress;
	}

	public String getNodeAddress() {
		return nodeAddress;
	}
	
	////////////////////////////////////////////////
	// Udp Server Port
	////////////////////////////////////////////////
	
	private int udpServerPort;
	
	public void setUdpServerPort(int udpServerPort) {
		this.udpServerPort = udpServerPort;
	}
	
	public int getUdpServerPort() {
		return udpServerPort;
	}
	
	////////////////////////////////////////////////
	// Worker Node Type
	////////////////////////////////////////////////
	
	private String workerNodeType;
	
	public void setWorkerNodeType(String workerNodeType) {
		this.workerNodeType = workerNodeType;
	}
	
	public String getWorkerNodeType() {
		return workerNodeType;
	}
}
