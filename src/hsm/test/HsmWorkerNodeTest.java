package hsm.test;

import java.util.UUID;

import persam.node.NodeState;
import hsm.node.WorkerNode;

public class HsmWorkerNodeTest {
	public static void main(String[] args) {
		UUID uuid = UUID.fromString(args[0]);
		WorkerNode workerNode = new WorkerNode("WorkerNode", uuid, "224.0.1.20", 2020, Integer.parseInt(args[1]),
				args[2]);
		workerNode.setNodeState(NodeState.DORMANT);
		workerNode.start();
		workerNode.doHeartbeatProcess();
	}
}
