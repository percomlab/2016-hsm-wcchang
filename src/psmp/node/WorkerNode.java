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
import java.util.Date;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.simple.Test;

import persam.node.NodeState;
import persam.node.PerNode;
import persam.node.PerNodeBase;
import util.CancellableThread;
import util.TimeFormat;

public class WorkerNode extends PerNodeBase implements PerNode {
	private Logger logger = Logger.getLogger(WorkerNode.class);

	public WorkerNode(String nodeType, UUID uuid, String multicastIp, int multicastPort, int udpSocketPort,
			String workerNodeType) {
		super(nodeType, uuid, multicastIp, multicastPort);
		setType(workerNodeType);
		this.udpSocketPort = udpSocketPort;
		setProcessId();
		startUdpServer();
		sendPresenceAnnouncement();
	}

	////////////////////////////////////////////////
	// Worker node type
	////////////////////////////////////////////////

	// private String location;
	private String workerNodeType;

	private void setType(String type) {
		workerNodeType = type;
	}

	////////////////////////////////////////////////
	// Register process ID
	////////////////////////////////////////////////

	private String processId;

	private void setProcessId() {
		String processName = java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
		processId = processName;
	}
	
	private void suiside() {
		System.out.println("kill" + processId);
		try {
			Runtime.getRuntime().exec("kill " + processId);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	////////////////////////////////////////////////
	// PSM info
	////////////////////////////////////////////////

	private String psmAddress;
	private int psmPort;

	////////////////////////////////////////////////
	// Worker node task
	////////////////////////////////////////////////

	CancellableThread workerNodeTaskThread;
	private long taskPeriod = 3000;

	private void workerNodeTask(String topicName) {
		workerNodeTaskThread = new CancellableThread() {
			private volatile boolean isRunning = true;

			@Override
			public void cancel() {
				isRunning = false;
			}

			@Override
			public void run() {
				TimeFormat timeFormat = new TimeFormat();
				Date lastWorkTime = null;
				try {
					lastWorkTime = timeFormat.getCurrentTime();
				} catch (ParseException e) {
					e.printStackTrace();
				}
				while (isRunning) {
					if (timeFormat.getPassingTime(lastWorkTime) >= taskPeriod) {
						try {
							publishToMqtt(topicName,
									timeFormat.getDateString(timeFormat.getCurrentTime()) + ", " + workerNodeType);
						} catch (ParseException e1) {
							e1.printStackTrace();
						}
						try {
							lastWorkTime = timeFormat.getCurrentTime();
						} catch (ParseException e) {
							e.printStackTrace();
						}
					}
				}
			}
		};
		workerNodeTaskThread.start();
	}

	////////////////////////////////////////////////
	// Heartbeat process
	////////////////////////////////////////////////

	private boolean isHeartBeating = false;
	private Thread heartbeatThread;
	private long heartbeatPeriod = 1000;
	private Date lastHeartbeatTime;

	public void setHeartbeatPeriod(long hbp) {
		heartbeatPeriod = hbp;
	}

	public void doHeartbeatProcess() {
		sendHeartbeatMessage();
		isHeartBeating = true;
		TimeFormat timeFormat = new TimeFormat();
		try {
			lastHeartbeatTime = timeFormat.getCurrentTime();
		} catch (ParseException e1) {
			e1.printStackTrace();
		}
		heartbeatThread = new Thread() {
			@Override
			public void run() {
				TimeFormat timeFormat2 = new TimeFormat();
				while (isHeartBeating) {
					if (timeFormat2.getPassingTime(lastHeartbeatTime) >= heartbeatPeriod) {
						try {
							lastHeartbeatTime = timeFormat2.getCurrentTime();
						} catch (ParseException e1) {
							e1.printStackTrace();
						}
						sendHeartbeatMessage();
					}
				}
			}

		};
		heartbeatThread.start();
	}

	public void stopHeartbeatProcess() {
		isHeartBeating = false;
		heartbeatThread.interrupt();
	}

	private void sendHeartbeatMessage() {
		String heartBeatMessage = "NOTIFY * HTTP/1.1\r\n";
		heartBeatMessage += "NT: uuid:device-UUID\r\n";
		heartBeatMessage += "USN: uuid:" + getUUID().toString() + "\r\n";
		heartBeatMessage += "NTS: psmp:heartbeat\r\n";
		heartBeatMessage += "PID: " + processId + "\r\n";
		heartBeatMessage += "\r\n";

		sendToUnicastSocket(psmAddress, psmPort, heartBeatMessage);
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
	// Unicast udp socket
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
					if (!getNodeState().equals(NodeState.ACTIVE)) {
						if (msg.contains("Subscribe MQTT topic: ")) {
							psmAddress = msg.substring(msg.indexOf("From: ") + 6, msg.indexOf("From: ") + 19);
							psmPort = Integer
									.parseInt(msg.substring(msg.indexOf("From: ") + 20, msg.indexOf("From: ") + 24));
							doHeartbeatProcess();
							setNodeState(NodeState.ACTIVE);
							workerNodeTask(msg.substring(msg.indexOf("Subscribe MQTT topic: ") + 22,
									msg.indexOf("Subscribe MQTT topic: ") + 37));
						}
					}
					else{
						
					}
					if (msg.contains("suiside")) {
							suiside();
						}
					else if (msg.contains("Test finished: ")){
						String path = "/usr/local/etc/psmp/exp1/"; 
						path += msg.substring(msg.indexOf("Test finished: ") + 15) + ".txt";
						String message = getUUID() + "," + workerNodeType + "\r\n";
						try {
							Files.write(Paths.get(path), message.getBytes(), StandardOpenOption.APPEND);
						} catch (IOException e) {
						}
					}
						
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
	// Multicast UDP socket
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

	private void sendPresenceAnnouncement() {
		String message = "NOTIFY * HTTP/1.1\r\n";
		message += "Host: 224.0.1.20:2020\r\n";
		message += "Cache-Control: max-age=1800\r\n";
		try {
			message += "From: " + InetAddress.getLocalHost().getHostAddress() + ":" + udpSocketPort + "\r\n";
		} catch (UnknownHostException e1) {
			e1.printStackTrace();
		}
		message += "Server: " + System.getProperty("os.name") + "PSMPJava/" + System.getProperty("java.version")
				+ "\r\n";
		message += "NTS: ssdp:alive\r\n";
		message += "ST: psmp:workernode:" + workerNodeType + "\r\n";
		message += "USN: " + getUUID() + "::psmp:workernode:" + workerNodeType + "\r\n";
		message += "\r\n";

		sendToMulticastSocket(message);
	}

	private void sendResponseMessage(String sourceAddress, int sourcePort) {
		// package size must fit ssdp package
		String message = "HTTP/1.1 200 OK\r\n";
		message += "Ext: \r\n";
		message += "USN: " + getUUID() + "::psmp:workernode:" + workerNodeType + "\r\n";
		message += "NodeState: " + getNodeState() + "\r\n";
		message += "ST: psmp:workernode:" + workerNodeType + "\r\n";
		try {
			message += "From: " + InetAddress.getLocalHost().getHostAddress() + ":" + udpSocketPort + "\r\n";
		} catch (UnknownHostException e1) {
			e1.printStackTrace();
		}
		message += "\r\n";

		sendToUnicastSocket(sourceAddress, sourcePort, message);
	}

	@Override
	protected void installedStateProcess(DatagramPacket datagramPacket) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void dormantStateProcess(DatagramPacket datagramPacket) {
		String string = new String(datagramPacket.getData(), 0, datagramPacket.getLength());
		if (string.contains("psmp:discover")) {
			int index = string.indexOf("ST: psmp:workernode:");
			index += 20;
			String searchTarget = string.substring(index, index + 3);
			if (workerNodeType.equals(searchTarget)) {
				String sourceAddress = string.substring(string.indexOf("From: ") + 6, string.indexOf("From: ") + 19);
				int sourcePort = Integer
						.parseInt(string.substring(string.indexOf("From: ") + 20, string.indexOf("From: ") + 24));
				sendResponseMessage(sourceAddress, sourcePort);
			}
		}

	}

	@Override
	protected void activeStateProcess(DatagramPacket datagramPacket) {
		String string = new String(datagramPacket.getData(), 0, datagramPacket.getLength());
		if (string.contains("psmp:discover")) {
			int index = string.indexOf("ST: psmp:workernode:");
			index += 20;
			String searchTarget = string.substring(index, index + 3);
			if (workerNodeType.equals(searchTarget)) {
				String sourceAddress = string.substring(string.indexOf("From: ") + 6, string.indexOf("From: ") + 19);
				int sourcePort = Integer
						.parseInt(string.substring(string.indexOf("From: ") + 20, string.indexOf("From: ") + 24));
				sendResponseMessage(sourceAddress, sourcePort);
			}
		}
	}
}
