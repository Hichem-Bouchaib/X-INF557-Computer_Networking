import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class DispatchLayer implements Layer {

	private static final String INIT = "--HELLO--";

	private static HashSet<Integer> sessionIDs = new HashSet<Integer>();
	private static Map<Integer, Layer> table = new HashMap<Integer, Layer>();
	private static Layer dispatcher = null;

	public static synchronized void start() {
		if (dispatcher == null)
			dispatcher = new DispatchLayer();
		GroundLayer.deliverTo(dispatcher);
	}

	@SuppressWarnings("boxing")
	public static synchronized void register(Layer layer, int sessionId) {
		if (dispatcher != null) {
			table.put(sessionId, layer);
			GroundLayer.deliverTo(dispatcher);
		} else
			GroundLayer.deliverTo(layer);
	}

	private DispatchLayer() { // singleton pattern
	}

	@Override
	public void send(String payload) {
		throw new UnsupportedOperationException("don't use this for sending");
	}
	
	private static int convertSafe(String s) {
		/* Convert safely a String to an int
		 * From ConnecterLayer.java correction 
		 */

		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) { // nothing
			return -1;
		}
	}
	
    public void receiveINIT(int packetSessionID, String source) {
    	/* Manage the reception of an HELLO message by adding 
    	 * the source to the table, within a new thread
    	*/

        String[] details = source.split(":");

        String host = details[0].split("/")[1];
        int port = convertSafe(details[1]);
        
        // Find a session ID that is not already in the set sessionIDs
        int ID = 0;
        do {
        	ID = (int) (Math.random()*Integer.MAX_VALUE);
        } while (sessionIDs.contains(ID));
        final int finalID = ID; // Need to be declared as final to avoid error in run()

        Runnable blade = new Runnable() {
            @Override
            public void run() {
                FileReceiver receiver = new FileReceiver(host, port, finalID);
                Layer layer = receiver.getSubLayer();
                register(layer, packetSessionID);
                layer.receive(packetSessionID + ";0;"+INIT, source);
            }
        };

		new Thread(blade).start();
    }

	@Override
	public void receive(String payload, String source) {
		/* Receive a packet and perform an action depending on its type 
		 * Similar to ConnecterLayer.receive(); 
		 */

		String[] packet = payload.split(";");
		if (packet.length == 3) {
			int packetSessionID = convertSafe(packet[0]); // Using convertSafe function from the correction of ConnectedLayer.java
			String message = packet[2];

			Layer toLayer = table.get(packetSessionID);
			if (toLayer != null) {
				toLayer.receive(payload, source);
			} else if (message.equals(INIT)) {
				receiveINIT(packetSessionID, source);
			}

		} else {
			System.err.println("Wrong packet length: "+ packet.length);
		}
	}

	@Override
	public void deliverTo(Layer above) {
		throw new UnsupportedOperationException("don't support a single Layer above");
	}

	@Override
	public void close() { // nothing
	}

}