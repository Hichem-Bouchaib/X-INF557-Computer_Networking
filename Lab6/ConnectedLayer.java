import javafx.util.Pair;

import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


/**
 * Modified for question 6
 * The most important modification concerns the fetching of the missed message:
 * 		During the construction, the messages destined to the current ConnectedLayer
 * 		are waiting in a Queue, and have to be fetched after this ConnectedLayer has been registered
 * Also, an optional parameter in the constructor allows to remove alea between when the ConnectedLayer is
 * constructed and when its upperLayer is being set. Other options were to register the ConnectedLayer in the
 * upperLayer after construction, after which the upperLayer prompts the ConnectedLayer to fetch the missing messages.
 * Another option would be to store the messages in a queue while the upperLayer attribute is null. Then, when the
 * method deliverTo is called, empty the queue into the new upperLayer.
 *
 * The session closes when the upper layer prompts it, and when the remote has not sent any more message to this ConnectedLayer
 * during a given period of time.
 */

public class ConnectedLayer implements Layer {

	private static final String INIT = "--HELLO--";
	private static final String ACK = "--ACK--";

	private int atPort;
	private int sessionID;
	private String toHost;

	private volatile boolean receivedACK; // Volatile to avoid the CPU optimization and always get an up to date value in the while loop
	private int num;                      // Number of packets handled
	private int remoteSessionID;          // Session ID on the other side of the connection
	private int remoteNum;                // Number of packets handled by the other side of the connection
	private Layer upperLayer;             // Layer above this one
	private Lock lock;                    // Lock to manage concurrency
	private Condition receivingACK;       // Condition to wait for receiving ACK
	private Timer timer;                  // Timer to execute the task at regular intervals

    public static final int SESSION_CLEAR_DELAY = 5000; // ms
    Thread sessionClearer;

	ConnectedLayer(String host, int port, int id) { // Backward-compatible constructor
		init(host, port, id, -1, null);
	}

	ConnectedLayer(ConnectionParameters params, Layer upperLayer){ // More flexible constructor
		init(params.remoteHost, params.remotePort, params.localConnectionId, params.remoteConnectionId, upperLayer);
	}

	private void init(String host, int port, int localSessionId, int remoteSessionID, Layer upperLayer) {
		this.toHost          = host;
		this.atPort          = port;
		this.sessionID       = localSessionId;
		this.num             = 0;
		this.remoteSessionID = remoteSessionID;
		this.remoteNum       = -1;
		this.upperLayer      = upperLayer;
		this.receivedACK     = false;

		DispatchLayer_6.register(this, localSessionId);
        DispatchLayer_6.register(this, remoteSessionID);

		this.lock         = new ReentrantLock();
		this.receivingACK = lock.newCondition();
		this.timer        = new Timer(true);

        sessionClearer = new Thread(() -> {
            while(true) {
                try{
                    Thread.sleep(SESSION_CLEAR_DELAY);
                    DispatchLayer_6.clearSession(sessionID, remoteSessionID);
                    break;
                } catch (InterruptedException e) {
                    // Continue
                }
            }
        });

        fetchPendingMessages();

		this.send(INIT); // Send the first packet to initiate connection
	}

	/**
	 * Function written for question 6
	 * Fetches messages that were received by DispatchLayer since the start of the construction.
	 *
	 * The function assumes that this ConnectedLayer is already registered in DispatchLayer.table.
	 */
	private void fetchPendingMessages() {
		Queue<Pair<String, String>> pendingMessages = DispatchLayer_6.getPendingMessages(remoteSessionID);

		if(pendingMessages == null)
			return;

		for(Pair<String, String> message: pendingMessages)
			receive(message.getKey(), message.getValue());
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

		timer.schedule(task, 0, 100); // Every 100 ms, wait for 0 ms and execute task

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

		if (packetNum == 0) { // Initiate the connection
			remoteSessionID = packetSessionID;
			remoteNum = 0;
		}
		if (remoteSessionID == packetSessionID) { // If the connection is already set, forward message to the GroundLayer
			GroundLayer.send(packetSessionID+ ";" +packetNum+ ";" +ACK, toHost, atPort);
		}
	}

	public void receiveACK(int packetSessionID, int packetNum) {
		/* Handle the reception of an ACK packet */

		if (packetNum == num && packetSessionID == sessionID) {
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

		GroundLayer.send(packetSessionID+ ";" +packetNum+ ";" + ACK, toHost, atPort);

		if (upperLayer != null && packetNum == remoteNum+1) {
			upperLayer.receive(message, sender+ " @" + packetSessionID +" ["+ packetNum+ "]");
			remoteNum = packetNum;

			// Resets the session clear timer if applicable
            if(sessionClearer != null && sessionClearer.isAlive())
			    sessionClearer.interrupt();
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

	public void requestSessionClear() {
	     sessionClearer.start();
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
