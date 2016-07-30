package hsm.test;

import java.util.UUID;

import persam.node.NodeState;
import hsm.node.PHM;

public class HsmPhmTest {
	public static void main(String[] args) {
		PHM phm = new PHM("PHM", UUID.fromString(args[0]), "224.0.1.20", 2020);
		phm.setNodeState(NodeState.ACTIVE);
		phm.start();
	}
}
