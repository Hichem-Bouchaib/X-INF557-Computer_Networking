public class ConnectedLayer implements Layer {

	private static final String INIT = "--HELLO--";
	private static final String ACK = "--ACK--";

	private final String toHost;  // Destination host
	private final int atPort;     // Destination port
	private final int sessionID;  // Current session ID

	private Layer upperLayer;     // Layer above (this)
	private int num;              // Number of packets handled
	private int remoteSessionID;  // Session ID on the other side of the connection
	private int remoteNum;        // Number of packets handled by the other side of the connection

	ConnectedLayer(String host, int port, int id){
		this.toHost          = host;
		this.atPort          = port;
		this.sessionID       = id;
		this.num             = 0;
		this.remoteSessionID = -1;
		this.remoteNum       = -1;
		this.upperLayer      = null;

		GroundLayer.deliverTo(this); // Set GroundLayer
		this.send(INIT); // Send the first packet to initiate the connection
	}

	@Override
	public void send(String payload) {
		/* Send the payload to GroundLayer */

		GroundLayer.send(sessionID+";"+num+";"+payload, toHost, atPort);
		num++;
	}

	public void receiveINIT(int packetSessionID, int packetNum) {
		/* Handle the reception of an INIT */

		if (packetNum == 0 && remoteSessionID == -1) { // Initiate the connection
			remoteSessionID = packetSessionID;
			remoteNum = 0;
		}
		if (remoteSessionID == packetSessionID) { // If the connection is already set, forward message to the GroundLayer
			GroundLayer.send(packetSessionID+ ";" +packetNum+ ";" +ACK, toHost, atPort);
		}
	}

	public void receiveMsg(String message, int packetSessionID, int packetNum, String sender) {
		/* Handle the reception of a message */

		if (remoteSessionID == packetSessionID) { // From GroundLayer to upper layer
			if (upperLayer != null) {
				upperLayer.receive(message, sender+ " @" + packetSessionID +" ["+ packetNum+ "]");
				remoteNum = packetNum;
			}
		}
		if (upperLayer != null) { // From upper to GroundLayer
			GroundLayer.send(packetSessionID+ ";" +remoteNum+ ";" + ACK, toHost, atPort);
		}
	}

	@Override
	public void receive(String payload, String source) {
		/* Receive a packet and perform an action depending on its type */

		String[] packet = payload.split(";");
		if (packet.length == 3) {
			int packetSessionID = Integer.parseInt(packet[0]);
			int packetNum = Integer.parseInt(packet[1]);
			String message = packet[2];

			switch(message) {
			case ACK:
				break;
			case INIT:
				receiveINIT(packetSessionID, packetNum);
				break;
			default:
				receiveMsg(message, packetSessionID, packetNum, source);
			}
		}
	}

	@Override
	public void deliverTo(Layer above) {
		/* Set the upper layer */

		upperLayer = above;
	}

	@Override
	public void close() {
		/* Close the connection */

		upperLayer = null;
	}

}
