import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class GroundLayer {

	private static int bufferLen = 2048;
	private static DatagramSocket socket;
	private static byte[] rcvBuffer = new byte[bufferLen];
	private static byte[] sendBuffer = new byte[bufferLen];
	private static DatagramPacket rcvPacket = new DatagramPacket(rcvBuffer, bufferLen);
	private static DatagramPacket sendPacket = new DatagramPacket(rcvBuffer, bufferLen);
	private static Thread thread;
	private static Layer upperLayer = null;

	/**
	 * This {@code Charset} is used to convert between our Java native String
	 * encoding and a chosen encoding for the effective payloads that fly over the
	 * network.
	 */
	private static final Charset CONVERTER = StandardCharsets.UTF_8;

	/**
	 * This value is used as the probability that {@code send} really sends a
	 * datagram. This allows to simulate the loss of packets in the network.
	 */
	public static double RELIABILITY = 1.0;

	public static boolean start(int localPort) {
		/* Starts the server launching a thread which waits for incoming packets */

		try {

			socket = new DatagramSocket(localPort);

			thread = new Thread(new Runnable() {
				@Override
				public void run() {
					while (!Thread.currentThread().isInterrupted()) {
						try {
							socket.receive(rcvPacket);
							String stringRcv = new String(rcvBuffer, 0, rcvPacket.getLength(), CONVERTER); // Constructs a new String by decoding the specified subarray of bytes using the specified charset.
							if (upperLayer != null) {
								upperLayer.receive(stringRcv, "localhost:"+localPort);
							}
						} catch (IOException e) {
							System.err.println("Datagram reception failed!");
							e.printStackTrace();
						}
					}
				}
			});

			thread.start();

			return true;
		} catch (IOException e) {
			System.err.println("DatagramSocket declaration failed");
			e.printStackTrace();
			return false;
		}
	}

	public static void deliverTo(Layer layer) {
		/* Sets the upper layer */

		upperLayer = layer;
	}

	public static void send(String payload, String destinationHost, int destinationPort) {
		/* Send the payload to destinationHost:destinationPort */

		if (Math.random() <= RELIABILITY) { // Simulates the data loss
			try {
				InetAddress IP = InetAddress.getByName(destinationHost); // Returns the IP from address of a specified hostname
				sendBuffer = payload.getBytes(CONVERTER);
				sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length, IP, destinationPort);
				socket.send(sendPacket);
			} catch (IOException e) {
				System.err.println("Unknown host: "+destinationHost);
				e.printStackTrace();
			}
		} else {
			System.err.println("Package lost with reliability: "+RELIABILITY);
		}
	}

	public static void close() {
		/* Close the diagram socket */

		socket.close();
		thread.interrupt();
		System.err.println("GroundLayer closed");
	}

}
