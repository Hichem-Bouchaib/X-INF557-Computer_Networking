import javafx.util.Pair;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class FileReceiver implements Layer {

    private final Layer subLayer;

    private Lock completedLock;
    private Condition completedCondition;
    private boolean isCompleted;

    private String currentFileName;
    private BufferedWriter writer;

    public FileReceiver(String destinationHost, int destinationPort,
                        int connectionId) {

        subLayer = new ConnectedLayer(destinationHost, destinationPort,
                connectionId, this);

        completedLock = new ReentrantLock();
        completedCondition = completedLock.newCondition();
        isCompleted = false;

        currentFileName = "";
    }



    public Layer getSubLayer() {
        return subLayer;
    }

    @Override
    public void send(String payload) {
        throw new UnsupportedOperationException(
                "don't support any send from above");
    }

    @Override
    public void receive(String payload, String sender) {
        System.out.println("FileReceiver received "+payload);

        boolean createFile = payload.length() > 5 && payload.substring(0, 4).equals("SEND");

        if(createFile) {
            try {
                currentFileName = "_received_" + payload.substring(5);
                writer = new BufferedWriter(new FileWriter(currentFileName));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        else if(payload.equals("**CLOSE**")) {
            try {
                completedLock.lock();
                isCompleted = true;
                completedCondition.signalAll();
                ((ConnectedLayer)subLayer).clearSession();
                writer.close();
            } catch (IOException | NullPointerException e) {
                e.printStackTrace();
            } finally {
                completedLock.unlock();
            }

        }

        else {
            try {
                writer.write(payload+"\n");
            } catch (IOException | NullPointerException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void deliverTo(Layer above) {
        throw new UnsupportedOperationException("don't support any Layer above");
    }

    @Override
    public void close() {
        // here, first wait for completion

        try {
            completedLock.lock();

            while (!isCompleted)
                completedCondition.await();

        } catch(InterruptedException e) {
            e.printStackTrace();
        } finally {
            completedLock.unlock();
        }

        System.out.println("closing");
        subLayer.close();
    }

}

public class Server_4 {

    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println(
                    "syntax : java Server_4 myPort destinationHost destinationPort");
            return;
        }
        if (GroundLayer.start(Integer.parseInt(args[0]))) {
            // GroundLayer.RELIABILITY = 0.5;
            FileReceiver receiver = new FileReceiver(args[1],
                    Integer.parseInt(args[2]), (int) (Math.random() * Integer.MAX_VALUE));
            receiver.close();
            GroundLayer.close();
        }
    }
}
