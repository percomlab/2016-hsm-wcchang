package hsm.node;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.ParseException;
import java.time.zone.ZoneOffsetTransitionRule.TimeDefinition;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import persam.node.PerNode;
import persam.node.PerNodeBase;
import util.CancellableThread;
import util.TimeFormat;

public class PSM extends PerNodeBase implements PerNode {
	private Logger logger = Logger.getLogger(PSM.class);
	private int electionServerPort = 55688;
	private String electionServerAddress = "192.168.4.241";

	public PSM(String nodeType, UUID uuid, String multicastIp, int multicastPort, int udpSocketPort, String topicName) {
		super(nodeType, uuid, multicastIp, multicastPort);
		missedTypeList = new ArrayList<>();
		workerNodeList = new ArrayList<>();
		installedManagerNodes = new ArrayList<>();
		suspectManagerNodeList = new HashMap<>();
		unavailableWorkerNodeList = new ArrayList<>();
		isLeader = false;
		// setProcessId();
		registerProcessId();
		loadManagerNodeList();
		this.udpSocketPort = udpSocketPort;
		this.topicName = topicName;
		startUdpServer();
	}
	
	private void serviceStart(){
		TimeFormat timeFormat = new TimeFormat();
		String string = "";
		try {
			string = timeFormat.getDateString(timeFormat.getCurrentTime()) + " " + getUUID() + " start\r\n";
		} catch (ParseException e1) {
			e1.printStackTrace();
		}
		try {
			Files.write(Paths.get("/usr/local/etc/hsm/psm1"), string.getBytes(), StandardOpenOption.APPEND);
		} catch (IOException e) {
		}
	}
	
	private void serviceStop(){
		TimeFormat timeFormat = new TimeFormat();
		String string = "";
		try {
			string = timeFormat.getDateString(timeFormat.getCurrentTime()) + " " + getUUID() + " stop\r\n";
		} catch (ParseException e1) {
			e1.printStackTrace();
		}
		try {
			Files.write(Paths.get("/usr/local/etc/hsm/psm1"), string.getBytes(), StandardOpenOption.APPEND);
		} catch (IOException e) {
		}
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
		//sendLeaveAnnoucement(perNodeUuid.toString());
	}

	private String topicName;

	private List<ManagerNode> installedManagerNodes;

	private void loadManagerNodeList() {
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
	// Register process ID
	////////////////////////////////////////////////

	private void registerProcessId() {
		String processName = java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
		String tempString = getUUID() + "," + processName + "\n";
		try {
			Files.write(Paths.get("/usr/local/etc/hsm/psm_list"), tempString.getBytes(), StandardOpenOption.APPEND);
		} catch (IOException e) {
			// exception handling left as an exercise for the reader
		}
	}

	////////////////////////////////////////////////
	// Service
	////////////////////////////////////////////////

	private volatile List<String> missedTypeList;
	// The set of worker nodes belonging to ps
	private List<PervasiveServiceMember> workerNodeList;
	//private boolean serviceStart = false;

	public void addServiceTemplate(String serviceTemplate) {
		missedTypeList.add(serviceTemplate);
	}

	public void startServiceComposition() {
		for (String st : missedTypeList) {
			mSearch(st);
		}
	}

	public void mSearch(String searchTarget) {
		String discoverMessage = "M-SEARCH * HTTP/1.1\r\n";
		discoverMessage += "ST: psmp:workernode:" + searchTarget + "\r\n";
		discoverMessage += "MAN: \"psmp:discover\"\r\n";
		try {
			discoverMessage += "From: " + InetAddress.getLocalHost().getHostAddress() + ":" + udpSocketPort + "\r\n";
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		discoverMessage += "\r\n";

		sendToMulticastSocket(discoverMessage);
	}

	private void searchTargetResponse(String responseMessage) {
		int typeIndex = responseMessage.indexOf("ST: psmp:workernode:") + 20;
		String searchTarget = responseMessage.substring(typeIndex, typeIndex + 3);
		if (missedTypeList.contains(searchTarget)) {
			int uuidIndex = responseMessage.indexOf("USN: ") + 5;
			int addressIndex = responseMessage.indexOf("From: ") + 6;
			UUID workerNodeUuid = UUID.fromString(responseMessage.substring(uuidIndex, uuidIndex + 36));
			String workerNodeAddress = responseMessage.substring(addressIndex, addressIndex + 13);
			int workerNodePort = Integer.parseInt(responseMessage.substring(responseMessage.indexOf("From: ") + 20,
					responseMessage.indexOf("From: ") + 24));

			PervasiveServiceMember pervasiveServiceMember = new PervasiveServiceMember(workerNodeUuid, searchTarget,
					workerNodeAddress);
			pervasiveServiceMember.setNodePort(workerNodePort);
			if (workerNodeList.size() == 0) {
				workerNodeList.add(pervasiveServiceMember);
			} else {
				workerNodeList.add(pervasiveServiceMember);
			}
			missedTypeList.remove(searchTarget);

			assignMission(responseMessage);
		}
	}

	private void assignMission(String responseMessage) {
		String workerNodeAddress = responseMessage.substring(responseMessage.indexOf("From: ") + 6,
				responseMessage.indexOf("From: ") + 19);
		int workerNodePort = Integer.parseInt(responseMessage.substring(responseMessage.indexOf("From: ") + 20,
				responseMessage.indexOf("From: ") + 24));

		String assignMessage = "Subscribe MQTT topic: " + topicName + "\r\n";
		try {
			assignMessage += "From: " + InetAddress.getLocalHost().getHostAddress() + ":" + udpSocketPort + "\r\n";
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		assignMessage += "\r\n";

		sendToUnicastSocket(workerNodeAddress, workerNodePort, assignMessage);
		checkServiceStatus();
	}

	private void checkServiceStatus() {
		if (missedTypeList.isEmpty()) {
			serviceStart();
			TimeFormat timeFormat = new TimeFormat();
			String message;
			try {
				message = "[" + timeFormat.getDateString(timeFormat.getCurrentTime()) + "] " + getUUID() + ":"
						+ InetAddress.getLocalHost().getHostAddress() + ":" + udpSocketPort + " service start";
				System.out.println("Service start (" + getUUID() + ")");
				publishToMqtt(topicName, message);
			} catch (ParseException e) {
				e.printStackTrace();
			} catch (UnknownHostException e) {
				e.printStackTrace();
			}
			//serviceStart = true;
		}
	}
	private void sendKillWorkerNodeToPHM(PervasiveServiceMember pervasiveServiceMember) {
		// logger.debug("send kill " + pervasiveServiceMember.getNodeUuid());
		String message = "Kill " + pervasiveServiceMember.getNodeUuid() + "\r\n";
		message += "Worker Node\r\n";
		sendToUnicastSocket(pervasiveServiceMember.getNodeAddress(), 5566, message);
		sendWorkerNodeLeaveAnnouncement(pervasiveServiceMember);
	}

	private void sendWorkerNodeLeaveAnnouncement(PervasiveServiceMember pervasiveServiceMember) {
		String message = "NOTIFY * HTTP/1.1\r\n";
		message += "HOST: " + getHostIp() + ":" + getPort() + "\r\n";
		message += "NTS: ssdp:bye-bye\r\n";
		message += "From: " + pervasiveServiceMember.getNodeAddress() + ":" + pervasiveServiceMember.getNodePort()
				+ "\r\n";
		message += "ST: psmp:workernode:" + pervasiveServiceMember.getNodeType() + "\r\n";
		message += "USN: " + pervasiveServiceMember.getNodeUuid() + "::hsm:workernode:"
				+ pervasiveServiceMember.getNodeType() + "\r\n";
		message += "\r\n";
		sendToMulticastSocket(message);
	}

	////////////////////////////////////////////////
	// MQTT
	////////////////////////////////////////////////

	private final String mqttServerAddress = "tcp://192.168.4.205:1883";

	private void publishToMqtt(String topicName, String data) {
		String clientId = getUUID().toString();
		MemoryPersistence memoryPersistence = new MemoryPersistence();
		try {
			MqttClient mqttClient = new MqttClient(mqttServerAddress, clientId, memoryPersistence);
			MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
			mqttConnectOptions.setCleanSession(true);
			mqttClient.connect(mqttConnectOptions);

			MqttMessage mqttMessage = new MqttMessage(data.getBytes());
			mqttMessage.setQos(2);

			mqttClient.publish(topicName, mqttMessage);

			mqttClient.disconnect();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	////////////////////////////////////////////////
	// Unicast UDP socket
	////////////////////////////////////////////////

	private CancellableThread udpServerThread;
	private int udpSocketPort;

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
					if (msg.contains("ST: psmp:workernode:")) {
						searchTargetResponse(msg);
					} else if (msg.contains("You are leader")) {
						runningManagerNodeNum = Integer
								.parseInt(msg.substring(msg.indexOf("Node Number: ") + 13, msg.indexOf("(s)")));
						isLeader = true;
						leaderProcess(msg);
					} else if (msg.contains("RRCP CHK")) {
						sendAckMessage(msg);
					} else if (msg.contains("ACK")) {
						if (isLeader && msg.contains(chkTargetUuid.toString())) {
							if (msg.contains("Unavailable List: ") && !msg.contains("null"))
								updateUnavailableWorkerNodeList(msg);
							getReplyMessage = true;
						}
					}
					else if (msg.contains("I will recover PHM") && (getRecoverPHMReply == false)){
						recoverPHMMessage = msg;
						getRecoverPHMReply = true;
						
					}
					else if (msg.contains("suiside"))
						suiside();
					// socket.close();
					else if (msg.contains("MOM recovered")){
						sendToMulticastSocket("MOM recovered");
					}
				}
			}
		};
		udpServerThread.start();
	}

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

	////////////////////////////////////////////////
	// Roll-Call Process
	////////////////////////////////////////////////

	private Boolean isLeader;
	private int runningManagerNodeNum;
	private UUID chkTargetUuid = null;
	private List<String> unavailableWorkerNodeList;

	private void leaderProcess(String msg) {
		updateSuspectList(msg);
		Thread leaderThread = new Thread() {
			@Override
			public void run() {
				while (isLeader) {
					for (ManagerNode managerNode : installedManagerNodes) {
						unavailableWorkerNodeList.clear();
						if (!getUUID().equals(managerNode.getUuid())) {
							chkTargetUuid = managerNode.getUuid();
							sendCheckMessage(managerNode.getNodeIp(), managerNode.getNodePort());
							waitForReply(managerNode);
							if (!unavailableWorkerNodeList.isEmpty()){
								for(Iterator it = workerNodeList.iterator(); it.hasNext();){
									PervasiveServiceMember pervasiveServiceMember = (PervasiveServiceMember)it.next(); 
									for (String string: unavailableWorkerNodeList){
										if (pervasiveServiceMember.getNodeUuid().toString().equals(string)){
											serviceStop();
											it.remove();
											missedTypeList.add(pervasiveServiceMember.getNodeType());
											sendKillWorkerNodeToPHM(pervasiveServiceMember);
											break;
										}
									}
								}
							}
						}
					}
					leaderMissionComplete();
					// break;
				}
			}
		};
		leaderThread.start();
	}

	private void updateSuspectList(String msg) {
		System.out.println(msg);
		if (msg.contains("null")) {
			suspectManagerNodeList.clear();
		} else {
			suspectManagerNodeList.clear();
			msg = msg.substring(msg.indexOf("Suspect List: ") + 14);
			int count = msg.length() - msg.replace(",", "").length();
			count += 1;
			String[] tempArray = new String[count];
			tempArray = msg.split(",");
			for (int i = 0; i < count; i++) {
				String[] tempAry = new String[2];
				tempAry = tempArray[i].split(":");
				for (ManagerNode managerNode : installedManagerNodes) {
					if (managerNode.getUuid().toString().equals(tempAry[0])) {
						suspectManagerNodeList.put(managerNode, Integer.parseInt(tempAry[1]));
						break;
					}
				}
			}
		}
	}
	
	private void updateUnavailableWorkerNodeList(String msg){
		if (!msg.contains("null")) {
			msg = msg.substring(msg.indexOf("Unavailable List: ") + 18);
			int count = msg.length() - msg.replace(",", "").length();
			count += 1;
			String[] tempArray = new String[count];
			tempArray = msg.split(",");
			for (int i = 0; i < count; i++) {
				unavailableWorkerNodeList.add(tempArray[i]);
			}
		}
	}

	private void sendCheckMessage(String managerNodeIp, int managerNodePort) {
		String checkMessage = "RRCP CHK\r\n";
		try {
			checkMessage += "From: " + InetAddress.getLocalHost().getHostAddress() + ":" + udpSocketPort + "\r\n";
		} catch (UnknownHostException e1) {
			e1.printStackTrace();
		}
		checkMessage += "\r\n";

		sendToUnicastSocket(managerNodeIp, managerNodePort, checkMessage);
	}

	private void sendAckMessage(String message) {
		String leaderAddress = message.substring(message.indexOf("From: ") + 6, message.indexOf("From: ") + 19);
		int leaderPort = Integer
				.parseInt(message.substring(message.indexOf("From: ") + 20, message.indexOf("From: ") + 24));
		String ackMessage = "ACK\r\n";
		ackMessage += getUUID();
		sendToUnicastSocket(leaderAddress, leaderPort, ackMessage);
	}

	private void applyForLeader() {
		String message = "I want to be leader: " + getUUID() + "\r\n";
		try {
			message += "Address: " + InetAddress.getLocalHost().getHostAddress() + "\r\n";
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		message += "Port: " + udpSocketPort + "\r\n";
		sendToUnicastSocket(electionServerAddress, electionServerPort, message);
	}

	private volatile boolean getReplyMessage = false;
	private final long replyTimeout = 100;
	private Map<ManagerNode, Integer> suspectManagerNodeList;

	private void waitForReply(ManagerNode managerNode) {
		getReplyMessage = false;
		TimeFormat timeFormat = new TimeFormat();
		Date startTime = null;
		try {
			startTime = timeFormat.getCurrentTime();
		} catch (ParseException e) {
			e.printStackTrace();
		}
		while (!getReplyMessage) {
			if (timeFormat.getPassingTime(startTime) >= replyTimeout) {
				if (suspectManagerNodeList.containsKey(managerNode)) {
					int value = suspectManagerNodeList.get(managerNode);
					if (value >= runningManagerNodeNum / 2) {
						if (managerNode.getFilePath().contains("PSM")) {
							String pid = "";
							Map<String, String> psmList = new HashMap<>();
							try {
								FileReader fileReader = new FileReader("/usr/local/etc/hsm/psm_list");
								BufferedReader bufferedReader = new BufferedReader(fileReader);
								String line;
								String[] tempArray = new String[2];
								while ((line = bufferedReader.readLine()) != null) {
									tempArray = line.split(",");
									psmList.put(tempArray[0], tempArray[1]);
								}
								bufferedReader.close();
							} catch (IOException e) {
								e.printStackTrace();
							}
							pid = psmList.get(managerNode.getUuid().toString());
							String message = "Kill " + managerNode.getUuid() + "\r\n";
							message += "Leader Election\r\n";
							if (pid != null)
								message += "PID: " + pid;
							sendToUnicastSocket(managerNode.getNodeIp(), 5566, message);
							suspectManagerNodeList.remove(managerNode);
						}
						// PHM
						else {
							sendRecoverPHMMessage(managerNode);
							waitForRecoverPHMReply(managerNode);
						}
					} else {
						suspectManagerNodeList.put(managerNode, value + 1);
					}
				} else {
					suspectManagerNodeList.put(managerNode, 1);
				}
				return;
			}
		}
		suspectManagerNodeList.remove(managerNode);
	}

	private void leaderMissionComplete() {
		isLeader = false;
		String message = "Next round election\r\n";
		message += "Suspect List: ";
		if (suspectManagerNodeList.isEmpty()) {
			message += "null";
		} else {
			Iterator iter = suspectManagerNodeList.entrySet().iterator();
			System.out.println("suspectManagerNodeList size = " + suspectManagerNodeList.size());
			while (iter.hasNext()) {
				Map.Entry entry = (Map.Entry) iter.next();
				ManagerNode key = (ManagerNode) entry.getKey();
				Integer val = (Integer) entry.getValue();
				if (!key.getUuid().equals(getUUID())) {
					message += key.getUuid() + ":" + val;
					if (iter.hasNext())
						message += ",";
				}
				System.out.println(key + ":" + val);
			}
		}
		sendToUnicastSocket(electionServerAddress, electionServerPort, message);
	}
	
	private volatile boolean getRecoverPHMReply = false;
	private String recoverPHMMessage;
	
	private void waitForRecoverPHMReply(ManagerNode managerNode){
		getRecoverPHMReply = false;
		recoverPHMMessage = "";
		TimeFormat timeFormat = new TimeFormat();
		Date startTime = null;
		try {
			startTime = timeFormat.getCurrentTime();
		} catch (ParseException e) {
			e.printStackTrace();
		}
		while (!getRecoverPHMReply) {
			if (timeFormat.getPassingTime(startTime) >= 200) {
				return;
			}
		}
		sendRecoverPermission(recoverPHMMessage, managerNode.getUuid());
	}
	
	private void sendRecoverPHMMessage(ManagerNode managerNode){
		String message = "Recover PHM: " + managerNode.getNodeIp() + "\r\n";
		try {
			message += "From: " + InetAddress.getLocalHost().getHostAddress() + ":" + udpSocketPort + "\r\n";
		} catch (UnknownHostException e1) {
			e1.printStackTrace();
		}
		message += "\r\n";
		sendToMulticastSocket(message);
	}
	
	private void sendRecoverPermission(String string, UUID phmUuid){
		String message = "Recover PHM: " + phmUuid + "\r\n";
		
		String nodeAddress = string.substring(string.indexOf("From: ") + 6, string.indexOf("From: ") + 19);
		int nodePort = Integer
				.parseInt(string.substring(string.indexOf("From: ") + 20, string.indexOf("From: ") + 24));
		sendToUnicastSocket(nodeAddress, nodePort, message);
	}

	////////////////////////////////////////////////
	// Milticast UDP socket
	////////////////////////////////////////////////

	private void sendToMulticastSocket(String message) {
		InetAddress inetAddress = null;
		int port = 2020;
		byte[] data = message.getBytes();

		try {
			inetAddress = InetAddress.getByName(getHostIp());
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		DatagramPacket datagramPacket = new DatagramPacket(data, data.length, inetAddress, port);
		MulticastSocket multicastSocket;
		try {
			multicastSocket = new MulticastSocket();
			multicastSocket.joinGroup(inetAddress);
			// multicastSocket.setTimeToLive(1);
			multicastSocket.send(datagramPacket);
			multicastSocket.leaveGroup(inetAddress);
			multicastSocket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	////////////////////////////////////////////////
	// Pernode method
	////////////////////////////////////////////////

 	@Override
	public void stop() {
		// closeMqttConnection();
		udpServerThread.cancel();
		super.stop();
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
		if (string.contains("ssdp:alive")) {
			if (string.contains("ST: psmp:workernode:")) {
				String workerNodeType = string.substring(string.indexOf("ST: psmp:workernode:") + 20,
						string.indexOf("ST: psmp:workernode:") + 23);
				for (String missedType : missedTypeList) {
					if (missedType.equals(workerNodeType)) {
						int uuidIndex = string.indexOf("USN: ") + 5;
						int addressIndex = string.indexOf("From: ") + 6;
						int workerNodePort = Integer.parseInt(
								string.substring(string.indexOf("From: ") + 20, string.indexOf("From: ") + 24));
						UUID workerNodeUuid = UUID.fromString(string.substring(uuidIndex, uuidIndex + 36));
						String workerNodeAddress = string.substring(addressIndex, addressIndex + 13);

						PervasiveServiceMember pervasiveServiceMember = new PervasiveServiceMember(workerNodeUuid,
								workerNodeType, workerNodeAddress);
						pervasiveServiceMember.setNodePort(workerNodePort);
						if (workerNodeList.size() == 0) {
							workerNodeList.add(pervasiveServiceMember);
						} else {
							workerNodeList.add(pervasiveServiceMember);
						}
						missedTypeList.remove(workerNodeType);

						assignMission(string);
						break;
					}
				}
			}
		} 
		else if (string.contains("Start election")) {
			applyForLeader();
		} 
		else if (isLeader && string.contains("MOM failed")){
			String message = "MOM failed\r\n";
			try {
				message += "From: " + InetAddress.getLocalHost().getHostAddress() + ":" + udpSocketPort + "\r\n";
			} catch (UnknownHostException e1) {
				e1.printStackTrace();
			}
			sendToUnicastSocket("192.168.4.205", 5566, message);
		}
		else {

		}
	}
}
