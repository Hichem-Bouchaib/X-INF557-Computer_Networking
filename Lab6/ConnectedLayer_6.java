import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ConnectedLayer_6 implements Layer {
	/* Implementation of ConnectedLayer.java from Exo 3, adapted to Exo 6 */

	private static final String INIT = "--HELLO--";
	private static final String ACK = "--ACK--";

	private final int atPort;
	private final int sessionID;
	private final String toHost;

	private volatile boolean receivedACK; // Volatile to avoid the CPU optimization and always get an up to date value in the while loop
	private int num;                      // Number of packets handled
	private int remoteSessionID;          // Session ID on the other side of the connection
	private int remoteNum;                // Number of packets handled by the other side of the connection
	private Layer upperLayer;             // Layer above this one
	private Lock lock;                    // Lock to manage concurrency
	private Condition receivingACK;       // Condition to wait for receiving ACK
	private Timer timer;                  // Timer to execute the task at regular intervals

	ConnectedLayer_6(String host, int port, int id) {
		this.toHost          = host;
		this.atPort          = port;
		this.sessionID       = id;
		this.num             = 0;
		this.remoteSessionID = -1;
		this.remoteNum       = -1;
		this.upperLayer      = null;
		this.receivedACK     = false;

		this.lock         = new ReentrantLock();
		this.receivingACK = lock.newCondition();
		this.timer        = new Timer(true); 

		DispatchLayer_6.register(this, sessionID); // Sets DispatchLayer
		this.send(INIT); // Send the first packet to initiate connection
	}

	@Override
	public void send(String payload) {
		/* Send the payload to GroundLayer */

		receivedACK = false;
		TimerTask task = new TimerTask() {
			@Override
			public void run() {
				GroundLayer.send(sessionID+";"+num+";"+payload, toHost, atPort);
			}
		};

		timer.schedule(task, 0, 10); // Every 10 ms, wait for 0 ms and execute task

		lock.lock();
		try {
			while (!receivedACK) receivingACK.await();
			num++;
			task.cancel(); // Interupt the task when the ACK is received
		} catch (InterruptedException e) {
			e.printStackTrace();
		} finally {
			lock.unlock();
		}
	}

	public void receiveINIT(int packetSessionID, int packetNum) {
		/* Handle the reception of an INIT packet */

		if (packetNum == 0 && remoteSessionID == -1) { // Initiate the connection
			remoteSessionID = packetSessionID;
			remoteNum = 0;
		}
		if (remoteSessionID == packetSessionID) { // If the connection is already set, forward message to the GroundLayer
			GroundLayer.send(packetSessionID+ ";" +packetNum+ ";" +ACK, toHost, atPort);
		}
	}

	public void receiveACK(int packetSessionID, int packetNum) {
		/* Handle the reception of an ACK packet */

		if (packetNum <= num && packetSessionID == sessionID) {
			lock.lock();
			try {
				receivedACK = true;
				receivingACK.signalAll();
			} finally {
				lock.unlock();
			}
		}
	}

	public void receiveMsg(String message, int packetSessionID, int packetNum, String sender) {
		/* Handle the reception of a message packet */

		if (packetNum == remoteNum + 1) { // From GroundLayer to upper layer
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
				receiveACK(packetSessionID, packetNum);
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
		/* Sets the upper layer */

		upperLayer = above;
	}

	@Override
	public void close() {
		/* Closes the connection with the upper layer */

		upperLayer = null;
	}

}
