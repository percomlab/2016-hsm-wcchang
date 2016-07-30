package hsm.node;

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
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import persam.node.NodeState;
import persam.node.PerNode;
import persam.node.PerNodeBase;
import util.CancellableThread;
import util.TimeFormat;

public class WorkerNode extends PerNodeBase implements PerNode {
	private Logger logger = Logger.getLogger(WorkerNode.class);
	private String mqttServerAddress = "tcp://localhost:1883";

	public WorkerNode(String nodeType, UUID uuid, String multicastIp, int multicastPort, int udpSocketPort,
			String workerNodeType) {
		super(nodeType, uuid, multicastIp, multicastPort);
		setType(workerNodeType);
		this.udpSocketPort = udpSocketPort;
		setProcessId();
		registerProcessId();
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
	// PSM info
	////////////////////////////////////////////////

	private String psmAddress;
	private int psmPort;

	////////////////////////////////////////////////
	// MOM
	////////////////////////////////////////////////

	private MqttAsyncClient mqttAsyncClient;
	private String topicName;

	private void subscribeMosquitto() {
		MemoryPersistence memoryPersistence = new MemoryPersistence();
		try {
			mqttAsyncClient = new MqttAsyncClient(mqttServerAddress, getUUID().toString(), memoryPersistence);
			MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
			mqttConnectOptions.setCleanSession(true);
			mqttAsyncClient.setCallback(new MqttCallback() {
				@Override
				public void connectionLost(Throwable arg0) {
					String message = "MOM failed";
					sendToMulticastSocket(message);
				}

				@Override
				public void deliveryComplete(IMqttDeliveryToken arg0) {
					// do nothing
				}

				@Override
				public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception {
					logger.debug("Message from mqtt broker: " + mqttMessage);
				}
			});
			IMqttToken conToken = mqttAsyncClient.connect(mqttConnectOptions);
			conToken.waitForCompletion();
			IMqttToken subToken = mqttAsyncClient.subscribe(topicName, 0, null, null);
			subToken.waitForCompletion();
		} catch (MqttException e) {
			e.printStackTrace();
			logger.error("MQTT error");
		}
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
		String heartBeatMessage = "USN: uuid:" + getUUID().toString() + "\r\n";
		heartBeatMessage += "NTS: psmp:heartbeat\r\n";
		heartBeatMessage += "\r\n";

		sendToUnicastSocket("localhost", 5566, heartBeatMessage);
	}

	////////////////////////////////////////////////
	// Process ID
	////////////////////////////////////////////////

	private String processId;

	private void setProcessId() {
		String processName = java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
		processId = processName;
	}

	private void registerProcessId() {
		String processName = java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
		String tempString = getUUID() + "," + processName + "\n";
		try {
			Files.write(Paths.get("/usr/local/etc/hsm/wn_list"), tempString.getBytes(), StandardOpenOption.APPEND);
		} catch (IOException e) {
		}
	}

	private void startPHM() {
		try {
			Runtime.getRuntime().exec("chmod +x /usr/local/etc/hsm/PHM.command");
			Runtime.getRuntime().exec("open /usr/local/etc/hsm/PHM.command");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void suiside() {
		System.out.println("kill" + processId);
		try {
			Runtime.getRuntime().exec("kill " + processId);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void applyForRecoverPHM(String string) {
		psmAddress = string.substring(string.indexOf("From: ") + 6, string.indexOf("From: ") + 19);
		psmPort = Integer.parseInt(string.substring(string.indexOf("From: ") + 20, string.indexOf("From: ") + 24));

		String message = "I will recover PHM\r\n";
		try {
			message += "From: " + InetAddress.getLocalHost().getHostAddress() + ":" + udpSocketPort + "\r\n";
		} catch (UnknownHostException e1) {
			e1.printStackTrace();
		}
		message += "\r\n";
		sendToUnicastSocket(psmAddress, psmPort, message);
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
					System.exit(0);
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
					if (!getNodeState().equals(NodeState.ACTIVE)) {
						if (msg.contains("Subscribe MQTT topic: ")) {
							psmAddress = msg.substring(msg.indexOf("From: ") + 6, msg.indexOf("From: ") + 19);
							psmPort = Integer
									.parseInt(msg.substring(msg.indexOf("From: ") + 20, msg.indexOf("From: ") + 24));
							topicName = msg.substring(msg.indexOf("Subscribe MQTT topic: ") + 22,
									msg.indexOf("Subscribe MQTT topic: ") + 37);
							subscribeMosquitto();
							setNodeState(NodeState.ACTIVE);
						}
					} else {

					}
					// socket.close();

					if (msg.contains("Recover PHM")) {
						startPHM();
					} else if (msg.contains("suiside")) {
						suiside();
					} else if (msg.contains("Test finished: ")) {
						String path = "/usr/local/etc/hsm/exp1/";
						path += msg.substring(msg.indexOf("Test finished: ") + 15) + ".txt";
						String message = getUUID() + "," + workerNodeType + "\r\n";
						try {
							Files.write(Paths.get(path), message.getBytes(), StandardOpenOption.APPEND);
						} catch (IOException e) {
						}
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
		} else if (string.contains("Recover PHM: ")) {
			String phmAddress = string.substring(string.indexOf("Recover PHM: ") + 13,
					string.indexOf("Recover PHM: ") + 26);
			try {
				if (phmAddress.equals(InetAddress.getLocalHost().getHostAddress())) {
					applyForRecoverPHM(string);
				}
			} catch (UnknownHostException e) {
				e.printStackTrace();
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
		} else if (string.contains("Recover PHM: ")) {
			String phmAddress = string.substring(string.indexOf("Recover PHM: ") + 13,
					string.indexOf("Recover PHM: ") + 26);
			try {
				if (phmAddress.equals(InetAddress.getLocalHost().getHostAddress())) {
					applyForRecoverPHM(string);
				}
			} catch (UnknownHostException e) {
				e.printStackTrace();
			}
		} else if (string.contains("MOM recovered")) {
			subscribeMosquitto();
		}
	}

}
