package psmp.ui;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Vector;

import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

public class PSMPControlPanel extends JFrame {

	public PSMPControlPanel() {
		initPanel();
		initEventListeners();
		initJList();
		initTextArea();
		initLabel();
		initComboBox();
		initButton();
		initTextField();
		initRunningWorkerNodes();
		startMulticastServerThread();
		subscribeMqttBroker();
	}

	private void initPanel() {
		setTitle("PSMP Control Panel");
		setSize(550, 525);
		setLayout(null);
		setLocation(350, 250);
		setResizable(false);
		setVisible(true);
	}

	private void initEventListeners() {
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	}

	////////////////////////////////////////////////
	// JList
	////////////////////////////////////////////////

	private JList<String> runningWorkerNodeList, availableServiceList;
	private JScrollPane jspRunningWorkerNode, jspAvailableService;
	private DefaultListModel<String> dlmWorkerNode, dlmAvailableService;

	private void initJList() {
		dlmWorkerNode = new DefaultListModel<>();
		dlmAvailableService = new DefaultListModel<>();
		runningWorkerNodeList = new JList<>(dlmWorkerNode);
		runningWorkerNodeList.addListSelectionListener(new ListSelectionListener() {
			@Override
			public void valueChanged(ListSelectionEvent e) {
				if (!e.getValueIsAdjusting()) {
					String selectString = runningWorkerNodeList.getSelectedValue();
					if (selectString != null) {
						RunningWorkerNode runningWorkerNode = runningWorkerNodes.get(indexMap.get(selectString));
						jlbIdValue.setText(String.valueOf(runningWorkerNode.getPsmpControlPanelId()));
						jlbUuidValue.setText(runningWorkerNode.getUuid().toString());
						jlbIpValue.setText(runningWorkerNode.getNodeAddress());
						jlbPortValue.setText(String.valueOf(runningWorkerNode.getUdpServerPort()));
						jlbTypeValue.setText(runningWorkerNode.getWorkerNodeType());
					} else {
						jlbIdValue.setText("");
						jlbUuidValue.setText("");
						jlbIpValue.setText("");
						jlbPortValue.setText("");
						jlbTypeValue.setText("");
					}
				}
			}
		});
		jspRunningWorkerNode = new JScrollPane(runningWorkerNodeList);
		jspRunningWorkerNode.setBounds(10, 35, 170, 200);
		add(jspRunningWorkerNode);
	}

	////////////////////////////////////////////////
	// TextArea
	////////////////////////////////////////////////

	private JTextArea jtaRuninngWorkerNodeList;
	private JTextArea jtaAvailableServiceList;

	private JScrollPane jspWorkerNodeList;
	private JScrollPane jspAvailableServiceList;

	private void initTextArea() {
		// jtaRuninngWorkerNodeList = new JTextArea();
		// jtaRuninngWorkerNodeList.setEditable(false);
		// jtaRuninngWorkerNodeList.setFocusable(false);
		// jtaRuninngWorkerNodeList.setLineWrap(true);
		// jtaRuninngWorkerNodeList.setWrapStyleWord(true);
		//
		// jspWorkerNodeList = new JScrollPane(jtaRuninngWorkerNodeList);
		// jspWorkerNodeList.setBounds(10, 35, 170, 200);
		// add(jspWorkerNodeList);

		jtaAvailableServiceList = new JTextArea();
		jtaAvailableServiceList.setEditable(false);
		jtaAvailableServiceList.setFocusable(false);
		jtaAvailableServiceList.setLineWrap(true);
		jtaAvailableServiceList.setWrapStyleWord(true);

		jspAvailableServiceList = new JScrollPane(jtaAvailableServiceList);
		jspAvailableServiceList.setBounds(10, 280, 530, 200);
		add(jspAvailableServiceList);
	}

	////////////////////////////////////////////////
	// Label
	////////////////////////////////////////////////

	private JLabel jlbRunningWorkerNodeList;
	private JLabel jlbAvailableServiceList;
	private JLabel jlbIdField, jlbUuidField, jlbIpField, jlbPortField, jlbTypeField;
	private JLabel jlbIdValue, jlbUuidValue, jlbIpValue, jlbPortValue, jlbTypeValue;
	private JLabel jlbKillSingleNodeField, jlbKillMultipleNodeField1, jlbKillMultipleNodeField2;

	private void initLabel() {
		jlbRunningWorkerNodeList = new JLabel("Running Worker Node List");
		jlbRunningWorkerNodeList.setFont(new java.awt.Font("Dialog", 0, 13));
		jlbRunningWorkerNodeList.setBounds(10, 5, 400, 25);
		add(jlbRunningWorkerNodeList);

		jlbIdField = new JLabel("ID:   ");
		jlbIdField.setFont(new java.awt.Font("Dialog", 0, 13));
		jlbIdField.setBounds(190, 35, 200, 25);
		add(jlbIdField);

		jlbUuidField = new JLabel("UUID: ");
		jlbUuidField.setFont(new java.awt.Font("Dialog", 0, 13));
		jlbUuidField.setBounds(190, 60, 200, 25);
		add(jlbUuidField);

		jlbIpField = new JLabel("IP:    ");
		jlbIpField.setFont(new java.awt.Font("Dialog", 0, 13));
		jlbIpField.setBounds(190, 85, 200, 25);
		add(jlbIpField);

		jlbPortField = new JLabel("Port:  ");
		jlbPortField.setFont(new java.awt.Font("Dialog", 0, 13));
		jlbPortField.setBounds(190, 110, 200, 25);
		add(jlbPortField);

		jlbTypeField = new JLabel("Type:  ");
		jlbTypeField.setFont(new java.awt.Font("Dialog", 0, 13));
		jlbTypeField.setBounds(190, 135, 200, 25);
		add(jlbTypeField);

		jlbIdValue = new JLabel("");
		jlbIdValue.setFont(new java.awt.Font("Dialog", 0, 13));
		jlbIdValue.setBounds(235, 35, 400, 25);
		add(jlbIdValue);

		jlbUuidValue = new JLabel("");
		jlbUuidValue.setFont(new java.awt.Font("Dialog", 0, 13));
		jlbUuidValue.setBounds(235, 60, 400, 25);
		add(jlbUuidValue);

		jlbIpValue = new JLabel("");
		jlbIpValue.setFont(new java.awt.Font("Dialog", 0, 13));
		jlbIpValue.setBounds(235, 85, 400, 25);
		add(jlbIpValue);

		jlbPortValue = new JLabel("");
		jlbPortValue.setFont(new java.awt.Font("Dialog", 0, 13));
		jlbPortValue.setBounds(235, 110, 400, 25);
		add(jlbPortValue);

		jlbTypeValue = new JLabel("");
		jlbTypeValue.setFont(new java.awt.Font("Dialog", 0, 13));
		jlbTypeValue.setBounds(235, 135, 400, 25);
		add(jlbTypeValue);

		jlbKillSingleNodeField = new JLabel("Kill");
		jlbKillSingleNodeField.setFont(new java.awt.Font("Dialog", 0, 13));
		jlbKillSingleNodeField.setBounds(190, 170, 400, 25);
		add(jlbKillSingleNodeField);

		jlbKillMultipleNodeField1 = new JLabel("Kill random");
		jlbKillMultipleNodeField1.setFont(new java.awt.Font("Dialog", 0, 13));
		jlbKillMultipleNodeField1.setBounds(190, 195, 400, 25);
		add(jlbKillMultipleNodeField1);

		jlbKillMultipleNodeField2 = new JLabel(" node(s)");
		jlbKillMultipleNodeField2.setFont(new java.awt.Font("Dialog", 0, 13));
		jlbKillMultipleNodeField2.setBounds(345, 195, 400, 25);
		add(jlbKillMultipleNodeField2);

		jlbAvailableServiceList = new JLabel("AvailableServiceList");
		jlbAvailableServiceList.setFont(new java.awt.Font("Dialog", 0, 13));
		jlbAvailableServiceList.setBounds(10, 250, 400, 25);
		add(jlbAvailableServiceList);
	}

	////////////////////////////////////////////////
	// Combo Box
	////////////////////////////////////////////////

	private JComboBox<String> jcbWorkerNodeList;
	private DefaultComboBoxModel<String> dcmWorkerNode;

	// private String workerNodeListString[] = {};
	private Vector<String> comboBoxItems = new Vector<>();

	private void initComboBox() {
		dcmWorkerNode = new DefaultComboBoxModel<>(comboBoxItems);
		jcbWorkerNodeList = new JComboBox<>(dcmWorkerNode);
		// jcbWorkerNodeList.setModel(new
		// DefaultComboBoxModel<String>(workerNodeListString));
		jcbWorkerNodeList.setBounds(220, 170, 180, 25);
		// jcbWorkerNodeList.setEditable(true);
		jcbWorkerNodeList.setFont(new java.awt.Font("Dialog", 0, 13));
		jcbWorkerNodeList.setBackground(Color.white);
		add(jcbWorkerNodeList);
	}

	////////////////////////////////////////////////
	// Button
	////////////////////////////////////////////////

	private JButton btnKillSingleNode, btnKillMultipleNode;

	private void initButton() {
		btnKillSingleNode = new JButton("OK");
		btnKillSingleNode.setFont(new java.awt.Font("Dialog", 0, 13));
		btnKillSingleNode.setBounds(400, 170, 60, 25);
		btnKillSingleNode.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				String selectItem = jcbWorkerNodeList.getSelectedItem().toString();
				UUID nodeUuid = indexMap.get(selectItem);
				String message = nodeUuid + " suiside";
				sendToUnicastSocket(runningWorkerNodes.get(nodeUuid).getNodeAddress(),
						runningWorkerNodes.get(nodeUuid).getUdpServerPort(), message);
			}
		});
		add(btnKillSingleNode);

		btnKillMultipleNode = new JButton("OK");
		btnKillMultipleNode.setFont(new java.awt.Font("Dialog", 0, 13));
		btnKillMultipleNode.setBounds(400, 195, 60, 25);
		btnKillMultipleNode.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				int nodeNumber = Integer.parseInt(jtfNodeNumber.getText());
				List<UUID> keys = new ArrayList<>(runningWorkerNodes.keySet());
				Collections.shuffle(keys);
				for (Object o : keys.subList(0, nodeNumber)) {
					String message = o + " suiside";
					sendToUnicastSocket(runningWorkerNodes.get(o).getNodeAddress(),
							runningWorkerNodes.get(o).getUdpServerPort(), message);
				}
			}
		});
		add(btnKillMultipleNode);
	}

	////////////////////////////////////////////////
	// TextField
	////////////////////////////////////////////////

	private JTextField jtfNodeNumber;

	private void initTextField() {
		jtfNodeNumber = new JTextField();
		jtfNodeNumber.setBounds(265, 195, 80, 25);
		add(jtfNodeNumber);
	}

	////////////////////////////////////////////////
	// Worker Node List
	////////////////////////////////////////////////

	private Map<UUID, RunningWorkerNode> runningWorkerNodes;
	private Map<String, UUID> indexMap;
	private int nodeId;

	private void initRunningWorkerNodes() {
		runningWorkerNodes = new HashMap<>();
		indexMap = new HashMap<>();
		nodeId = 0;
	}

	////////////////////////////////////////////////
	// Subscribe multicast address
	////////////////////////////////////////////////

	private Thread thread;
	private volatile Boolean isRunning;
	private MulticastSocket ms;

	public void startMulticastServerThread() {
		isRunning = true;
		InetAddress ia = null;
		try {
			ia = InetAddress.getByName("224.0.1.20");
			ms = new MulticastSocket(2020);
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
					String string = new String(datagramPacket.getData(), 0, datagramPacket.getLength());
					if (string.contains("ssdp:alive")) {
						if (string.contains("ST: psmp:workernode:")) {
							int uuidIndex = string.indexOf("USN: ") + 5;
							UUID workerNodeUuid = UUID.fromString(string.substring(uuidIndex, uuidIndex + 36));
							if (runningWorkerNodes.containsKey(workerNodeUuid)) {
								int index = runningWorkerNodes.get(workerNodeUuid).getPsmpControlPanelId();
								dlmWorkerNode.addElement("WorkerNode" + index);
								comboBoxItems.add("WorkerNode" + index);
							} else {
								//System.out.println("test");
								int workerNodeTypeIndex = string.indexOf("ST: psmp:workernode:") + 20;
								int addressIndex = string.indexOf("From: ") + 6;
								String workerNodeType = string.substring(workerNodeTypeIndex, workerNodeTypeIndex + 3);
								String workerNodeAddress = string.substring(addressIndex, addressIndex + 13);
								int workerNodePort = Integer
										.parseInt(string.substring(addressIndex + 14, addressIndex + 18));
								RunningWorkerNode runningWorkerNode = new RunningWorkerNode();
								runningWorkerNode.setPsmpControlPanelId(nodeId);
								runningWorkerNode.setWorkerNodeType(workerNodeType);
								runningWorkerNode.setUuid(workerNodeUuid);
								runningWorkerNode.setNodeAddress(workerNodeAddress);
								runningWorkerNode.setUdpServerPort(workerNodePort);
								runningWorkerNodes.put(workerNodeUuid, runningWorkerNode);
								indexMap.put("WorkerNode" + nodeId, workerNodeUuid);
								dlmWorkerNode.addElement("WorkerNode" + nodeId++);
								comboBoxItems.add("WorkerNode" + (nodeId - 1));

							}
						}
					} else if (string.contains("ssdp:bye-bye")) {
						if (string.contains("ST: psmp:workernode:")) {
							int uuidIndex = string.indexOf("USN: ") + 5;
							UUID workerNodeUuid = UUID.fromString(string.substring(uuidIndex, uuidIndex + 36));
							if (runningWorkerNodes.containsKey(workerNodeUuid)) {
								int index = runningWorkerNodes.get(workerNodeUuid).getPsmpControlPanelId();
								// System.out.println(index);
								dlmWorkerNode.removeElement("WorkerNode" + index);
								comboBoxItems.remove("WorkerNode" + index);
								// indexMap.remove("WorkerNode" + index);
								// runningWorkerNodes.remove(workerNodeUuid);
							}
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
		thread.start();
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

	////////////////////////////////////////////////
	// MQTT
	////////////////////////////////////////////////

	private Map<String, String> availableServices;

	private void subscribeMqttBroker() {
		availableServices = new HashMap<>();
		String clientId = "PSMPControlPanel";
		MemoryPersistence persistence = new MemoryPersistence();
		try {
			// connect
			MqttAsyncClient sampleClient = new MqttAsyncClient("tcp://localhost:1883", clientId, persistence);
			MqttConnectOptions connOpts = new MqttConnectOptions();
			connOpts.setCleanSession(true);
			sampleClient.setCallback(new MqttCallback() {
				@Override
				public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception {
					updateTextArea(mqttMessage.toString());

				}

				@Override
				public void connectionLost(Throwable arg0) {
					// TODO Auto-generated method stub

				}

				@Override
				public void deliveryComplete(IMqttDeliveryToken arg0) {
					// TODO Auto-generated method stub

				}
			});
			IMqttToken conToken = sampleClient.connect(connOpts);
			conToken.waitForCompletion();

			// subscribe
			String topicName = "hsm/ps/#";
			IMqttToken subToken = sampleClient.subscribe(topicName, 0, null, null);
			subToken.waitForCompletion();
			// disconnect
			// sampleClient.disconnect();
			// System.out.println("Disconnected");
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	private void updateTextArea(String string) {
		if (string.contains("service start")) {
			availableServices.put(string.substring(26, 62),
					", " + string.substring(63, 76) + ", " + string.substring(77, 81) + "\n");

		} else if (string.contains("service stop")) {
			availableServices.remove(string.substring(26, 62));
		}
		jtaAvailableServiceList.setText(null);
		Iterator iter = availableServices.entrySet().iterator();
		while (iter.hasNext()) {
			Map.Entry entry = (Map.Entry) iter.next();
			Object key = entry.getKey();
			Object val = entry.getValue();
			jtaAvailableServiceList.setText(key.toString() + val.toString());
		}
	}

	////////////////////////////////////////////////
	// main
	////////////////////////////////////////////////

	public static void main(String[] args) {
		javax.swing.SwingUtilities.invokeLater(() -> {
			new PSMPControlPanel().setVisible(true);
		});
	}

}
