package hsm.node;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.log4j.Logger;

import persam.node.NodeState;
import persam.node.PerNode;
import persam.node.PerNodeBase;
import util.CancellableThread;
import util.TimeFormat;

public class PHM extends PerNodeBase implements PerNode {
	private Logger logger = Logger.getLogger(PHM.class);

	public PHM(String nodeType, UUID uuid, String multicastIp, int multicastPort) {
		super(nodeType, uuid, multicastIp, multicastPort);
		installedWorkerNodes = new ArrayList<>();
		installedManagerNodes = new ArrayList<>();
		loadPHCMember();
		loadManagerNode();
		startUdpServer();
		updateUnavailableNode();
	}

	private void suiside() {
		String processName = java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
		String processId = processName;
		System.out.println("kill" + processId);
		try {
			Runtime.getRuntime().exec("kill " + processId);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	////////////////////////////////////////////////
	// Installed manager nodes
	////////////////////////////////////////////////

	private List<ManagerNode> installedManagerNodes;

	private void loadManagerNode() {
		try {
			FileReader fileReader = new FileReader("/usr/local/etc/hsm/managernode_list");
			BufferedReader bufferedReader = new BufferedReader(fileReader);
			String line, tempString;
			String[] tempArray = new String[4];
			while ((line = bufferedReader.readLine()) != null) {
				tempString = line;
				tempArray = tempString.split(",");
				UUID uuid = UUID.fromString(tempArray[0]);
				String managerNodeAddress = tempArray[1];
				int managerNodePort = Integer.parseInt(tempArray[2]);
				String managerNodeFilePath = tempArray[3];
				ManagerNode managerNode = new ManagerNode(uuid, managerNodeAddress, managerNodePort,
						managerNodeFilePath);
				installedManagerNodes.add(managerNode);
			}
			bufferedReader.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	////////////////////////////////////////////////
	// PHM Task
	////////////////////////////////////////////////

	private List<PervasiveHostMember> installedWorkerNodes;

	private void loadPHCMember() {
		try {
			FileReader fileReader = new FileReader("/usr/local/etc/hsm/workernode_list");
			BufferedReader bufferedReader = new BufferedReader(fileReader);
			String line, tempString;
			String[] tempArray = new String[4];
			while ((line = bufferedReader.readLine()) != null) {
				tempString = line;
				tempArray = tempString.split(",");
				UUID uuid = UUID.fromString(tempArray[0]);
				int udpServerPort = Integer.parseInt(tempArray[1]);
				String workerNodeType = tempArray[2];
				String workerNodeFilePath = tempArray[3];
				PervasiveHostMember pervasiveHostMember = new PervasiveHostMember(uuid, udpServerPort, workerNodeType,
						workerNodeFilePath);
				TimeFormat timeFormat = new TimeFormat();
				pervasiveHostMember.setLastHeartbeatTime(timeFormat.getCurrentTime());
				pervasiveHostMember.setNodeState(NodeState.INSTALLED);
				installedWorkerNodes.add(pervasiveHostMember);
			}
			bufferedReader.close();
		} catch (IOException | ParseException e) {
			e.printStackTrace();
		}
	}

	private void killProcess(String processId) {
		try {
			Runtime.getRuntime().exec("kill " + processId);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void startWorkerNode(UUID workerNodeUuid) {
		String parameterSetting = "-Djava.net.preferIPv4Stack=true";
		String workerNodeJarPath = "/usr/local/etc/hsm/WorkerNode.jar";
		PervasiveHostMember pervasiveHostMember = searchMember(workerNodeUuid);
		TimeFormat timeFormat = new TimeFormat();
		try {
			pervasiveHostMember.setLastHeartbeatTime(timeFormat.getCurrentTime());
		} catch (ParseException e2) {
			e2.printStackTrace();
		}
		String command = "echo 'java " + parameterSetting + " -jar " + workerNodeJarPath + " "
				+ pervasiveHostMember.getUuid() + " " + pervasiveHostMember.getUdpServerPort() + " "
				+ pervasiveHostMember.getWorkerNodeType() + " " + pervasiveHostMember.getFilePath() + "' > "
				+ pervasiveHostMember.getFilePath();
		try {
			Process p = new ProcessBuilder("/bin/sh", "-c", command).start();
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		try {
			Runtime.getRuntime().exec("chmod +x " + pervasiveHostMember.getFilePath());
			Runtime.getRuntime().exec("open " + pervasiveHostMember.getFilePath());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void startManagerNode(UUID managerNodeUuid) {
		ManagerNode managerNode = searchManager(managerNodeUuid);
		try {
			if (managerNode.getNodeIp().equals(InetAddress.getLocalHost().getHostAddress())) {
				try {
					Runtime.getRuntime().exec("chmod +x " + managerNode.getFilePath());
					Runtime.getRuntime().exec("open " + managerNode.getFilePath());
				} catch (IOException e) {
					e.printStackTrace();
				}
			} else {
				// can't restart
			}
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
	}

	private PervasiveHostMember searchMember(UUID workerNodeUuid) {
		PervasiveHostMember pervasiveHostMember = null;
		for (PervasiveHostMember p : installedWorkerNodes) {
			if (p.getUuid().equals(workerNodeUuid)) {
				pervasiveHostMember = p;
				break;
			}
		}
		return pervasiveHostMember;
	}

	private ManagerNode searchManager(UUID managerNodeUuid) {
		ManagerNode managerNode = null;
		for (ManagerNode m : installedManagerNodes) {
			if (m.getUuid().equals(managerNodeUuid)) {
				managerNode = m;
				break;
			}
		}
		return managerNode;
	}
	
	private void recoverMOM(){
		try {
			Runtime.getRuntime().exec("mosquitto");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	////////////////////////////////////////////////
	// Available list
	////////////////////////////////////////////////

	private final long heartBeatPeriod = 1500;
	private final long updateAvailableListPeriod = 1500;
	private Date lastUpdateTime;
	private List<PervasiveHostMember> unavailableWorkerNodeList;

	private void updateUnavailableNode() {
		unavailableWorkerNodeList = new ArrayList<>();
		Thread updateSuspectListThread = new Thread() {
			@Override
			public void run() {
				TimeFormat timeFormat2 = new TimeFormat();
				try {
					lastUpdateTime = timeFormat2.getCurrentTime();
				} catch (ParseException e1) {
					e1.printStackTrace();
				}
				while (true) {
					TimeFormat timeFormat = new TimeFormat();
					if (timeFormat.getPassingTime(lastUpdateTime) >= updateAvailableListPeriod) {
						for (PervasiveHostMember pervasiveHostMember : installedWorkerNodes) {
							if (timeFormat
									.getPassingTime(pervasiveHostMember.getLastHeartbeatTime()) >= heartBeatPeriod) {
								if (!unavailableWorkerNodeList.contains(pervasiveHostMember)){
									unavailableWorkerNodeList.add(pervasiveHostMember);
									pervasiveHostMember.setNodeState(NodeState.INSTALLED);
								}
							} else {
								if (unavailableWorkerNodeList.contains(pervasiveHostMember)){
									unavailableWorkerNodeList.remove(pervasiveHostMember);
									pervasiveHostMember.setNodeState(NodeState.DORMANT);
								}
							}
						}
						try {
							lastUpdateTime = timeFormat.getCurrentTime();
						} catch (ParseException e) {
							e.printStackTrace();
						}

					} else {
					}
				}
			}
		};
		updateSuspectListThread.start();
	}

	private void updateHeartbeatTime(String message) {
		int uuidIndex = message.indexOf("USN: uuid:") + 10;
		UUID workerNodeUuid = UUID.fromString(message.substring(uuidIndex, uuidIndex + 36));
		for (PervasiveHostMember pervasiveHostMember : installedWorkerNodes) {
			if (pervasiveHostMember.getUuid().equals(workerNodeUuid)) {
				TimeFormat timeFormat = new TimeFormat();
				try {
					pervasiveHostMember.setLastHeartbeatTime(timeFormat.getCurrentTime());
				} catch (ParseException e) {
					e.printStackTrace();
				}
				if (pervasiveHostMember.getNodeState().equals(NodeState.INSTALLED))
					pervasiveHostMember.setNodeState(NodeState.DORMANT);
				break;
			}
		}
	}

	////////////////////////////////////////////////
	// Unicast UDP socket
	////////////////////////////////////////////////

	private void sendToUnicastSocket(String address, int port, String message) {
		byte[] data = message.getBytes();

		InetAddress inetAddress;
		DatagramSocket datagramSocket;
		try {
			inetAddress = InetAddress.getByName(address);
			DatagramPacket packet = new DatagramPacket(data, data.length, inetAddress, port);
			datagramSocket = new DatagramSocket();
			datagramSocket.send(packet);
			datagramSocket.close();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (SocketException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private CancellableThread udpServerThread;
	private int udpSocketPort = 5566;

	private void startUdpServer() {
		udpServerThread = new CancellableThread() {
			private volatile boolean isRunning = true;
			DatagramSocket socket;

			@Override
			public void cancel() {
				isRunning = false;
			}

			@Override
			public void run() {
				try {
					socket = new DatagramSocket(udpSocketPort);
				} catch (IOException e) {
					System.exit(0);
					e.printStackTrace();
				}
				while (isRunning) {
					byte buffer[] = new byte[65535];
					DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
					try {
						socket.receive(packet);
					} catch (IOException e) {
						System.exit(0);
						e.printStackTrace();
					}
					String msg = new String(buffer, 0, packet.getLength());
					logger.debug(msg);
					if (msg.contains("Kill")) {
						if (msg.contains("PID")) {
							int processIdIndex = msg.indexOf("PID:") + 4;
							int uuidIndex = msg.indexOf("Kill ") + 5;
							String processId = msg.substring(processIdIndex);
							String uuidString = msg.substring(uuidIndex, uuidIndex + 36);
							UUID nodeUuid = UUID.fromString(uuidString);
							killProcess(processId);
							startManagerNode(nodeUuid);
						} else {
							int uuidIndex = msg.indexOf("Kill ") + 5;
							String uuidString = msg.substring(uuidIndex, uuidIndex + 36);
							UUID nodeUuid = UUID.fromString(uuidString);
							if (msg.contains("Worker Node")) {
								String pid = "";
								Map<String, String> wnList = new HashMap<>();
								try {
									FileReader fileReader = new FileReader("/usr/local/etc/hsm/wn_list");
									BufferedReader bufferedReader = new BufferedReader(fileReader);
									String line;
									String[] tempArray = new String[2];
									while ((line = bufferedReader.readLine()) != null) {
										tempArray = line.split(",");
										wnList.put(tempArray[0], tempArray[1]);
									}
									bufferedReader.close();
								} catch (IOException e) {
									e.printStackTrace();
								}
								pid = wnList.get(nodeUuid.toString());
								killProcess(pid);
								Iterator<PervasiveHostMember> it = unavailableWorkerNodeList.iterator();
								while (it.hasNext()) {
								    PervasiveHostMember pervasiveHostMember = it.next();
								    if (pervasiveHostMember.getUuid().equals(nodeUuid)) {
								        it.remove();
								    }
								    break;
								}
								startWorkerNode(nodeUuid);
							} else {
								startManagerNode(nodeUuid);
							}
						}
					} 
					else if (msg.contains("RRCP CHK")) {
						sendAckMessage(msg);
					} 
					else if (msg.contains("psmp:heartbeat")) {
						updateHeartbeatTime(msg);
					}
					else if (msg.contains("suiside")){
						suiside();
					}
					else if (msg.contains("Start test: ")){
						String path = "/usr/local/etc/hsm/exp1/"; 
						path += msg.substring(msg.indexOf("Start test: ") + 12) + ".txt";
						try {
							Files.write(Paths.get(path), "".getBytes());
						} catch (IOException e1) {
							e1.printStackTrace();
						}
					}else if (msg.contains("MOM failed")){
						recoverMOM();
						String nodeAddress = msg.substring(msg.indexOf("From: ") + 6, msg.indexOf("From: ") + 19);
						int nodePort = Integer
								.parseInt(msg.substring(msg.indexOf("From: ") + 20, msg.indexOf("From: ") + 24));
						sendToUnicastSocket(nodeAddress, nodePort, "MOM recovered");
					}
					// socket.close();
				}
			}
		};
		udpServerThread.start();
	}



	private void sendAckMessage(String message) {
		String leaderAddress = message.substring(message.indexOf("From: ") + 6, message.indexOf("From: ") + 19);
		int leaderPort = Integer
				.parseInt(message.substring(message.indexOf("From: ") + 20, message.indexOf("From: ") + 24));
		String ackMessage = "ACK\r\n";
		ackMessage += getUUID() + "\r\n";
		
		ackMessage += "Unavailable List: ";
		if (unavailableWorkerNodeList.isEmpty()) {
			ackMessage += "null";
		} else {
			Iterator<PervasiveHostMember> iterator = unavailableWorkerNodeList.iterator();
			while (iterator.hasNext()) {
				ackMessage += iterator.next().getUuid();
				if (iterator.hasNext())
					ackMessage += ",";
			}
		}
		
		sendToUnicastSocket(leaderAddress, leaderPort, ackMessage);
	}


	@Override
	protected void installedStateProcess(DatagramPacket datagramPacket) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void dormantStateProcess(DatagramPacket datagramPacket) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void activeStateProcess(DatagramPacket datagramPacket) {
		String string = new String(datagramPacket.getData(), 0, datagramPacket.getLength());
		//logger.debug(string);
		if (string.contains("psmp:discover")) {
			int index = string.indexOf("ST: psmp:workernode:");
			index += 20;
			String searchTarget = string.substring(index, index + 3);
			for (PervasiveHostMember pervasiveHostMember : installedWorkerNodes) {
				if (pervasiveHostMember.getWorkerNodeType().equals(searchTarget)) {
					if (pervasiveHostMember.getNodeState().equals(NodeState.INSTALLED)) {
						startWorkerNode(pervasiveHostMember.getUuid());
					}
				}
			}
		} 
	}
}
