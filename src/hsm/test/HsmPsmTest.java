package hsm.test;

import java.util.UUID;

import persam.node.NodeState;
import hsm.node.PSM;

public class HsmPsmTest {
	public static void main(String[] args) {
		PSM psm = new PSM("PSM", UUID.fromString(args[0]), "224.0.1.20", 2020, Integer.parseInt(args[1]), args[2]);
		String[] tempArray = args[3].split(",");
		for (String serviceTemplate : tempArray) {
			psm.addServiceTemplate(serviceTemplate);
		}
		psm.setNodeState(NodeState.ACTIVE);
		psm.start();
		psm.startServiceComposition();
	}
}
