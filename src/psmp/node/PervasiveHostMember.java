package psmp.node;

import java.util.UUID;

public class PervasiveHostMember {
	public PervasiveHostMember(UUID uuid, int udpServerPort, String workerNodeType, String filePath) {
		this.uuid = uuid;
		this.udpServerPort = udpServerPort;
		this.workerNodeType = workerNodeType;
		this.filePath = filePath;
	}
	
	////////////////////////////////////////////////
	// UUID
	////////////////////////////////////////////////
	
	private UUID uuid;
	
	public UUID getUuid() {
		return uuid;
	}
	
	////////////////////////////////////////////////
	// Udp Server Port
	////////////////////////////////////////////////
	
	private int udpServerPort;
	
	public int getUdpServerPort() {
		return udpServerPort;
	}
	
	////////////////////////////////////////////////
	// Worker Node Type
	////////////////////////////////////////////////
	
	private String workerNodeType;
	
	public String getWorkerNodeType() {
		return workerNodeType;
	}
	
	////////////////////////////////////////////////
	// File Path
	////////////////////////////////////////////////
	
	private String filePath;
	
	public String getFilePath() {
		return filePath;
	}
	
	////////////////////////////////////////////////
	// Process ID
	////////////////////////////////////////////////
	
	private String processId;
	
	public void setProcessId(String processId) {
		this.processId = processId;
	}
	
	public String getProcessId() {
		return processId;
	}
}
