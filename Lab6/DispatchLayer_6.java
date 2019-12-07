import javafx.util.Pair;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

/**
 * DispatchLayer for question 6
 *
 * The sessions are cleared at the time of getting the corresponding **CLOSE** in FileReceiver. It might disrupt the Moulinette. I take no responsibility if the Moulinette speaks German because of such a disruption :) .
 *
 * The strategy is the following:
 *      - During the construction of the FileReceivers, the messages are being stored in Thread-unsafe queues.
 *      - After a ConnectedLayer is registered in the "table" HashMap, it will fetch all the messages it has missed, including the --HELLO-- message.
 *      - To ensure correctness in the multithreaded environment, locks are being used in the following way:
 *          To add a message from a certain sessionId when the corresponding ConnectedLayer is not registered yet, the message is being put into a queue, and this is surrounded by a lock protection (one lock per sessionId).
 *          Prior to taking the lock, it is being ensured that the corresponding --HELLO-- has already been received first. If this is the case, it tries to take the lock.
 *          If the lock is already taken, this implies that the corresponding ConnectedLayer is fetching its missed messages and thus has been registered.
 *          So, after taking the lock, check if the ConnectedLayer has been registered in-between. In this case, instead of enqueuing the message, send it to the ConnectedLayer directly.
 *      - The accept() method basically interfaces with the pending connection blocking queue.
 *
 */

public class DispatchLayer_6 implements Layer {

    public static final int QUEUE_CAPACITY = 512;

    private static Map<Integer, Layer> table = new HashMap<>();
    private static Map<Integer, Queue<Pair<String, String>>> pendingQueues = new HashMap<>();
    private static Map<Integer, ReentrantLock> pendingQueueLocks = new HashMap<>();
    private static LinkedBlockingQueue<ConnectionParameters> pendingConnections = new LinkedBlockingQueue<>(QUEUE_CAPACITY);

    private static HashSet<Integer> pendingRemoteConnectionIds = new HashSet<>();
    private static HashSet<Integer> localSessionIds = new HashSet<>();

    private static Layer dispatcher = null;

    public static synchronized void start() {
        if (dispatcher == null)
            dispatcher = new DispatchLayer_6();
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

    private DispatchLayer_6() { // singleton pattern
    }

    @Override
    public void send(String payload) {
        throw new UnsupportedOperationException("don't use this for sending");
    }

    @Override
    public void receive(String payload, String source) {
        int remoteSessionId;

        String[] splitPayload = payload.split(";");

        try {
            remoteSessionId = Integer.parseInt(payload.split(";")[0]);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            e.printStackTrace();
            return;
        }

        Layer above = table.get(remoteSessionId);
        if(above != null) { // If already in the table
            System.out.println("DispatchLayer transmitted to the above layer: "+payload);
            above.receive(payload, source);
        } else if(splitPayload[2].equals("--HELLO--")) {

            if(pendingRemoteConnectionIds.contains(remoteSessionId)) // Means, that the HELLO is still pending.
                return; // Ignore the new INIT and be patient

            String[] splitSource = source.split(":");

            if(pendingConnections.remainingCapacity() == 0) // If exactly now, the queue is full, ignore the packet
                return;

            int tempLocalSessionId;
            do {
                tempLocalSessionId = (int) (Math.random()*Integer.MAX_VALUE);
            } while (localSessionIds.contains(tempLocalSessionId));


            // Else, since this Thread is the only Threads that pushes into the queue, there should be no waiting when putting something to the queue

            LinkedList<Pair<String, String>> newMessageQueue = new LinkedList<>();
            newMessageQueue.offer(new Pair<>(payload, source));
            pendingQueues.put(remoteSessionId, newMessageQueue);
            pendingQueueLocks.put(remoteSessionId, new ReentrantLock());

            pendingRemoteConnectionIds.add(remoteSessionId); // Do this before offering to the queue. Else, the sessionId could be removed before being inserted in the set

            pendingConnections.offer(new ConnectionParameters(splitSource[0].substring(1), Integer.parseInt(splitSource[1]), remoteSessionId, tempLocalSessionId));

        } else {
            Queue<Pair<String, String>> pendingQueue = pendingQueues.get(remoteSessionId);

            if(pendingQueue == null)
                System.out.println("DispatchLayer, not knowing the remote sessionID, dropped payload "+payload);
            else {
                pendingQueueLocks.get(remoteSessionId).lock();

                if(pendingQueues.get(remoteSessionId) == null) { // This means, that the layer has just been constructed and registered
                    table.get(remoteSessionId).receive(payload, source);
                } else {
                    pendingQueue.add(new Pair<>(payload, source));
                }

                pendingQueueLocks.get(remoteSessionId).unlock();
            }
        }
    }

    /**
     * Simply interfaces with the LinkedBlockingQueue
     * @throws InterruptedException
     */
    public static ConnectionParameters accept() throws InterruptedException {
        return pendingConnections.take();
    }

    /**
     * The layer is supposed to be already registered
     * @return null if the remoteConnectionId is unknown, else return a queue of (payload, source)
     */
    public static Queue<Pair<String, String>> getPendingMessages(int remoteConnectionId) {
        ReentrantLock tempLock = pendingQueueLocks.get(remoteConnectionId);

        if(tempLock == null)
            return null;

        tempLock.lock();

        try {
            return pendingQueues.get(remoteConnectionId);
        } finally {
            tempLock.unlock();
        }
    }

    /**
     * Clears the session, once the file has been successfully received
     */
    public static void clearSession(int localSessionId, int remoteSessionId) {
        pendingQueueLocks.remove(remoteSessionId);
        pendingQueues.remove(remoteSessionId);
        pendingRemoteConnectionIds.remove(remoteSessionId);
        localSessionIds.remove(remoteSessionId);
        table.remove(remoteSessionId);
        table.remove(localSessionId);
    }

    @Override
    public void deliverTo (Layer above){
        throw new UnsupportedOperationException(
                "don't support a single Layer above");
    }

    @Override
    public void close () { // nothing
        pendingRemoteConnectionIds.clear();
        pendingConnections.clear();
        pendingQueues.clear();
        pendingQueueLocks.clear();
        localSessionIds.clear();
    }

}
