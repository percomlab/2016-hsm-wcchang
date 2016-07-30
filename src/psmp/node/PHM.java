package psmp.node;

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
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.log4j.Logger;

import persam.node.*;
import util.CancellableThread;

public class PHM extends PerNodeBase implements PerNode {
	private Logger logger = Logger.getLogger(PHM.class);
	public PHM(String nodeType, UUID uuid, String multicastIp, int multicastPort) {
		super(nodeType, uuid, multicastIp, multicastPort);
		heartbeatingWorkerNodes = new HashMap<>();
		installedWorkerNodes = new ArrayList<>();
		loadPHCMemberlist();
		startUdpServer();
	}
	
	private void suiside() {
		String processName = java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
		String processId = processName;
		//System.out.println("kill" + processId);
		try {
			Runtime.getRuntime().exec("kill " + processId);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	////////////////////////////////////////////////
	// PHM Task
	////////////////////////////////////////////////
	
	private Map<UUID, Date> heartbeatingWorkerNodes;
	private List<PervasiveHostMember> installedWorkerNodes;
	
	private void loadPHCMemberlist() {
		installedWorkerNodes.clear();
		try {
			FileReader fileReader = new FileReader("/usr/local/etc/psmp/workernode_list");
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
				PervasiveHostMember pervasiveHostMember = new PervasiveHostMember(uuid, udpServerPort, workerNodeType, workerNodeFilePath);
				installedWorkerNodes.add(pervasiveHostMember);
			}
			bufferedReader.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}
	
	private void loadWorkerNode(PervasiveHostMember pervasiveHostMember){
		String message = "Load\r\n";
		try {
			sendToUnicastSocket(InetAddress.getLocalHost().getHostAddress(), pervasiveHostMember.getUdpServerPort(), message);
		} catch (UnknownHostException e) {
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
		String workerNodeJarPath = "/usr/local/etc/psmp/WorkerNode.jar";
		PervasiveHostMember pervasiveHostMember = searchMember(workerNodeUuid);
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
	
	private PervasiveHostMember searchMember(UUID workerNodeUuid){
		PervasiveHostMember pervasiveHostMember = null;
		for (PervasiveHostMember p: installedWorkerNodes){
			if (p.getUuid().equals(workerNodeUuid)){
				pervasiveHostMember = p;
				break;
			}
		}
		return pervasiveHostMember;
	}
	
	////////////////////////////////////////////////
	// Unicast UDP socket
	////////////////////////////////////////////////
	
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
					if (msg.contains("Kill")) {
						int processIdIndex = msg.indexOf("PID:") + 4;
						int uuidIndex = msg.indexOf("Kill ") + 5;
						String processId = msg.substring(processIdIndex);
						String uuidString = msg.substring(uuidIndex, uuidIndex + 36);
						UUID workerNodeUuid = UUID.fromString(uuidString);
						killProcess(processId);
						startWorkerNode(workerNodeUuid);
					}
					else if (msg.contains("suiside")){
						suiside();
					}
					else if (msg.contains("Start test: ")){
						String path = "/usr/local/etc/psmp/exp1/"; 
						path += msg.substring(msg.indexOf("Start test: ") + 12) + ".txt";
						try {
							Files.write(Paths.get(path), "".getBytes());
						} catch (IOException e1) {
							e1.printStackTrace();
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
	// Pernode method
	////////////////////////////////////////////////
	
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
		if (string.contains("psmp:discover")) {
			int index = string.indexOf("ST: psmp:workernode:");
			index += 20;
			String searchTarget = string.substring(index, index + 3);
			for (PervasiveHostMember pervasiveHostMember : installedWorkerNodes) {
				if (pervasiveHostMember.getWorkerNodeType().equals(searchTarget)) {
					if (!heartbeatingWorkerNodes.containsKey(pervasiveHostMember.getUuid())) {
						startWorkerNode(pervasiveHostMember.getUuid());
					}
				}
			}
		} 
	}
}
