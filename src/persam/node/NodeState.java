package persam.node;

public enum NodeState {
	INSTALLED, DORMANT, ACTIVE;
	
	// INSTALLED
	// After a node being installed, it is INSTALLED
	
	// DORMANT
	// A node loaded in to memory
	// Do not performed any message processing process
	// Do heartbeat process and is discoverable by discovery protocols
	
	// ACTIVE
	// A node goes into in ACTIVE state when it is activated. 
	// An activated node can receive, process, and send messages.
}
