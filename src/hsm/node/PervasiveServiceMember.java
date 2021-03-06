package hsm.node;

import java.util.UUID;

public class PervasiveServiceMember {
	public PervasiveServiceMember(UUID nodeUuid, String nodeType, String nodeAddress) {
		this.nodeUuid = nodeUuid;
		this.nodeType = nodeType;
		this.nodeAddress = nodeAddress;
	}

	////////////////////////////////////////////////
	// UUID
	////////////////////////////////////////////////

	private UUID nodeUuid;

	public UUID getNodeUuid() {
		return nodeUuid;
	}

	////////////////////////////////////////////////
	// Node type
	////////////////////////////////////////////////

	private String nodeType;

	public String getNodeType() {
		return nodeType;
	}

	////////////////////////////////////////////////
	// Node address
	////////////////////////////////////////////////

	private String nodeAddress;

	public String getNodeAddress() {
		return nodeAddress;
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
}
