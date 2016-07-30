package psmp.node;

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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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

	public PSM(String nodeType, UUID uuid, String multicastIp, int multicastPort, int udpSocketPort, String topicName) {
		super(nodeType, uuid, multicastIp, multicastPort);
		this.udpSocketPort = udpSocketPort;
		this.topicName = topicName;
		serviceTemplateList = new ArrayList<>();
		missedTypeList = new ArrayList<>();
		workerNodeList = new ArrayList<>();
		startUdpServer();
	}

	private String topicName;
	
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
	
	private void serviceStart(){
		TimeFormat timeFormat = new TimeFormat();
		String string = "";
		try {
			string = timeFormat.getDateString(timeFormat.getCurrentTime()) + " " + getUUID() + " start\r\n";
		} catch (ParseException e1) {
			e1.printStackTrace();
		}
		try {
			Files.write(Paths.get("/usr/local/etc/psmp/psm1"), string.getBytes(), StandardOpenOption.APPEND);
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
			Files.write(Paths.get("/usr/local/etc/psmp/psm1"), string.getBytes(), StandardOpenOption.APPEND);
		} catch (IOException e) {
		}
	}

	////////////////////////////////////////////////
	// Service
	////////////////////////////////////////////////

	// required worker node type list
	List<String> serviceTemplateList;
	List<String> missedTypeList;
	// The set of worker nodes belonging to ps
	List<PervasiveServiceMember> workerNodeList;
	private boolean serviceStart = false;

	public void addServiceTemplate(String serviceTemplate) {
		serviceTemplateList.add(serviceTemplate);
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
			TimeFormat timeFormat = new TimeFormat();
			try {
				pervasiveServiceMember.setLastHeartbeatTime(timeFormat.getCurrentTime());
			} catch (ParseException e) {
				e.printStackTrace();
			}
			if (workerNodeList.size() == 0) {
				workerNodeList.add(pervasiveServiceMember);
				checkSuspectNode();
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
			serviceStart = true;
		}
	}

	////////////////////////////////////////////////
	// Suspect node
	////////////////////////////////////////////////

	private final long heartBeatPeriod = 1500;
	private final long updateSuspectListPeriod = 1500;
	private Date lastUpdateTime;

	private void checkSuspectNode() {
		Thread updateSuspectListThread = new Thread() {
			@Override
			public void run() {
				TimeFormat timeFormat = new TimeFormat();
				try {
					lastUpdateTime = timeFormat.getCurrentTime();
				} catch (ParseException e1) {
					e1.printStackTrace();
				}
				while (workerNodeList.size() != 0) {
					if (timeFormat.getPassingTime(lastUpdateTime) >= updateSuspectListPeriod) {
						List<PervasiveServiceMember> suspectList = new ArrayList<>();
						for (PervasiveServiceMember pervasiveServiceMember : workerNodeList) {
							if (timeFormat
									.getPassingTime(pervasiveServiceMember.getLastHeartbeatTime()) >= heartBeatPeriod) {

								//serviceStop();
								suspectList.add(pervasiveServiceMember);
							}
						}
						if (!suspectList.isEmpty())
							manageSuspectNode(suspectList);
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

	private void manageSuspectNode(List<PervasiveServiceMember> suspectList) {
		for (PervasiveServiceMember pervasiveServiceMember : suspectList) {
			workerNodeList.remove(pervasiveServiceMember);
			missedTypeList.add(pervasiveServiceMember.getNodeType());
			// System.out.println(missedTypeList);
			if (serviceStart == true) {
				TimeFormat timeFormat = new TimeFormat();
				String message;
				try {
					message = "[" + timeFormat.getDateString(timeFormat.getCurrentTime()) + "] " + getUUID()
							+ " service stop";
					System.out.println("Service stop");
					publishToMqtt(topicName, message);
				} catch (ParseException e) {
					e.printStackTrace();
				}
				serviceStart = false;
			}
			serviceStop();
			sendKillWorkerNodeToPHM(pervasiveServiceMember);
		}
	}

	private void sendKillWorkerNodeToPHM(PervasiveServiceMember pervasiveServiceMember) {
		String message = "Kill " + pervasiveServiceMember.getNodeUuid() + "\r\n";
		message += "PID: " + pervasiveServiceMember.getProcessId();
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

	private void updateHeartbeatTime(String message) {
		int uuidIndex = message.indexOf("USN: uuid:") + 10;
		int pidIndex = message.indexOf("PID: ") + 5;
		UUID workerNodeUuid = UUID.fromString(message.substring(uuidIndex, uuidIndex + 36));
		String processId = message.substring(pidIndex).replaceAll("(\\r|\\n)", "");

		for (PervasiveServiceMember pervasiveServiceMember : workerNodeList) {
			if (pervasiveServiceMember.getNodeUuid().equals(workerNodeUuid)) {
				TimeFormat timeFormat = new TimeFormat();
				pervasiveServiceMember.setProcessId(processId);
				try {
					pervasiveServiceMember.setLastHeartbeatTime(timeFormat.getCurrentTime());
				} catch (ParseException e) {
					e.printStackTrace();
				}
				break;
			}
		}
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
					e.printStackTrace();
				}
				while (isRunning) {
					byte buffer[] = new byte[65535];
					DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
					try {
						socket.receive(packet);
					} catch (IOException e) {
						e.printStackTrace();
					}
					String msg = new String(buffer, 0, packet.getLength());
					if (msg.contains("psmp:heartbeat")) {
						updateHeartbeatTime(msg);
					} else if (msg.contains("ST: psmp:workernode:")) {
						searchTargetResponse(msg);
					}
					else if (msg.contains("suiside"))
						suiside();

					// socket.close();
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
						missedTypeList.remove(workerNodeType);

						assignMission(string);
						TimeFormat timeFormat = new TimeFormat();
						try {
							pervasiveServiceMember.setLastHeartbeatTime(timeFormat.getCurrentTime());
						} catch (ParseException e) {
							e.printStackTrace();
						}


						if (workerNodeList.size() == 0) {
							workerNodeList.add(pervasiveServiceMember);
							checkSuspectNode();
						} else {
							workerNodeList.add(pervasiveServiceMember);
						}
						break;
					}
				}
			}
		} 
	}
}
