package io.midasprotocol.core.net;

import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.testng.collections.Lists;
import io.midasprotocol.common.application.ApplicationContext;
import io.midasprotocol.common.net.udp.message.Message;
import io.midasprotocol.common.net.udp.message.discover.FindNodeMessage;
import io.midasprotocol.common.net.udp.message.discover.NeighborsMessage;
import io.midasprotocol.common.net.udp.message.discover.PingMessage;
import io.midasprotocol.common.net.udp.message.discover.PongMessage;
import io.midasprotocol.common.overlay.discover.RefreshTask;
import io.midasprotocol.common.overlay.discover.node.Node;
import io.midasprotocol.common.overlay.discover.node.NodeManager;
import io.midasprotocol.core.config.args.Args;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class UdpTest {

	private NodeManager nodeManager;
	private int port = Args.getInstance().getNodeListenPort();
	private volatile boolean finishFlag = false;
	private long timeOut = 30_000;

	public UdpTest(ApplicationContext context) {
		nodeManager = context.getBean(NodeManager.class);
	}

	public void test() throws Exception {
//		Thread thread = new Thread(() -> {
//			try {
//				discover();
//			} catch (Exception e) {
//				logger.info("Discover test failed.", e);
//			}
//		});
//		thread.start();
//
//		long time = System.currentTimeMillis();
//		while (!finishFlag && System.currentTimeMillis() - time < timeOut) {
//			Thread.sleep(1000);
//		}
//		if (!finishFlag) {
//			thread.interrupt();
//			Assert.assertTrue(false);
//		}
	}

	public void discover() throws Exception {

		InetAddress server = InetAddress.getByName("127.0.0.1");

		Node from = Node.instanceOf("127.0.0.1:10002");
		Node peer1 = Node.instanceOf("127.0.0.1:10003");
		Node peer2 = Node.instanceOf("127.0.0.1:10004");

		Assert.assertFalse(nodeManager.hasNodeHandler(peer1));
		Assert.assertFalse(nodeManager.hasNodeHandler(peer2));
		Assert.assertTrue(nodeManager.getTable().getAllNodes().isEmpty());

		PingMessage pingMessage = new PingMessage(from, nodeManager.getPublicHomeNode());

		PongMessage pongMessage = new PongMessage(from);

		FindNodeMessage findNodeMessage = new FindNodeMessage(from, RefreshTask.getNodeId());

		List<Node> peers = Lists.newArrayList(peer1, peer2);
		NeighborsMessage neighborsMessage = new NeighborsMessage(from, peers);

		DatagramSocket socket = new DatagramSocket();

		DatagramPacket pingPacket = new DatagramPacket(pingMessage.getSendData(),
				pingMessage.getSendData().length, server, port);

		DatagramPacket pongPacket = new DatagramPacket(pongMessage.getSendData(),
				pongMessage.getSendData().length, server, port);

		DatagramPacket findNodePacket = new DatagramPacket(findNodeMessage.getSendData(),
				findNodeMessage.getSendData().length, server, port);

		DatagramPacket neighborsPacket = new DatagramPacket(neighborsMessage.getSendData(),
				neighborsMessage.getSendData().length, server, port);

		// send ping msg
		socket.send(pingPacket);
		byte[] data = new byte[1024];
		DatagramPacket packet = new DatagramPacket(data, data.length);

		boolean pingFlag = false;
		boolean pongFlag = false;
		boolean findNodeFlag = false;
		boolean neighborsFlag = false;
		while (true) {
			socket.receive(packet);
			byte[] bytes = Arrays.copyOfRange(data, 0, packet.getLength());
			Message msg = Message.parse(bytes);
			Assert.assertArrayEquals(msg.getFrom().getId(), nodeManager.getPublicHomeNode().getId());
			if (!pingFlag) {
				pingFlag = true;
				Assert.assertTrue(msg instanceof PingMessage);
				Assert.assertArrayEquals(((PingMessage) msg).getTo().getId(), from.getId());
				socket.send(pongPacket);
			} else if (!pongFlag) {
				pongFlag = true;
				Assert.assertTrue(msg instanceof PongMessage);
			} else if (!findNodeFlag) {
				findNodeFlag = true;
				Assert.assertTrue(msg instanceof FindNodeMessage);
				socket.send(neighborsPacket);
				socket.send(findNodePacket);
			} else if (!neighborsFlag) {
				Assert.assertTrue(msg instanceof NeighborsMessage);
				break;
			}
		}

		Assert.assertTrue(nodeManager.hasNodeHandler(peer1));
		Assert.assertTrue(nodeManager.hasNodeHandler(peer2));
		Assert.assertEquals(1, nodeManager.getTable().getAllNodes().size());

		socket.close();

		finishFlag = true;
	}
}

