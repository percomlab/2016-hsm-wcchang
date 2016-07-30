package psmp.test;

import java.util.Scanner;
import java.util.UUID;

import persam.node.NodeState;
import psmp.node.WorkerNode;

public class PsmpWorkerNodeTest {
	public static void main(String[] args) {
		UUID uuid = UUID.fromString(args[0]);
		WorkerNode workerNode = new WorkerNode("WorkerNode", uuid, "224.0.1.20", 2020, Integer.parseInt(args[1]), args[2]);
		workerNode.setNodeState(NodeState.DORMANT);
		workerNode.start();
	}
}
