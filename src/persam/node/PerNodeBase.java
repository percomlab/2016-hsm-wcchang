package persam.node;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.UUID;

public abstract class PerNodeBase {
	//private Logger logger = Logger.getLogger(PerNodeBase.class);
	public PerNodeBase(String nodeType, UUID uuid, String multicastIp, int multicastPort) {
		this.nodeType = nodeType;
		this.uuid = uuid;
		this.hostIp = multicastIp;
		this.port = multicastPort;
		this.nodeState = NodeState.INSTALLED;
		this.isRunning = false;
	}
	////////////////////////////////////////////////
	// nodeType
	////////////////////////////////////////////////

	private String nodeType;

	public String getNodeType() {
		return nodeType;
	}

	////////////////////////////////////////////////
	// nodeState
	////////////////////////////////////////////////

	private volatile NodeState nodeState;

	public void setNodeState(NodeState nodeState) {
		this.nodeState = nodeState;
	}

	public NodeState getNodeState() {
		return nodeState;
	}

	////////////////////////////////////////////////
	// uuid
	////////////////////////////////////////////////

	private UUID uuid;

	public UUID getUUID() {
		return uuid;
	}

	////////////////////////////////////////////////
	// hostIp
	////////////////////////////////////////////////

	private String hostIp;

	public String getHostIp() {
		return hostIp;
	}

	////////////////////////////////////////////////
	// port
	////////////////////////////////////////////////

	private int port;

	public int getPort() {
		return port;
	}

	////////////////////////////////////////////////
	// subscribe multicast address
	////////////////////////////////////////////////
	private Thread thread;
	private volatile Boolean isRunning;
	private MulticastSocket ms;

	public void stop() {
		isRunning = false;
		ms.close();
	}


	public void start() {
		isRunning = true;
		InetAddress ia = null;
		try {
			ia = InetAddress.getByName(getHostIp());
			ms = new MulticastSocket(getPort());
			ms.joinGroup(ia);
		} catch (IOException e) {
			e.printStackTrace();
		}
		thread = new Thread(() -> {
			while (isRunning) {
				byte[] buffer = new byte[65535];
				DatagramPacket datagramPacket = new DatagramPacket(buffer, buffer.length);
				try {
					ms.receive(datagramPacket);
					switch (nodeState) {
					case INSTALLED:
						installedStateProcess(datagramPacket);
						break;
					case DORMANT:
						dormantStateProcess(datagramPacket);
						break;
					case ACTIVE:
						activeStateProcess(datagramPacket);
						break;
					default:
						break;
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
		thread.start();
	}

	protected abstract void installedStateProcess(DatagramPacket datagramPacket);

	protected abstract void dormantStateProcess(DatagramPacket datagramPacket);

	protected abstract void activeStateProcess(DatagramPacket datagramPacket);
}
