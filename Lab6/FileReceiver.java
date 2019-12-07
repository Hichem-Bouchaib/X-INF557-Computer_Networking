import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 * The main subtlety is the timing of the session clearing: the session has to be cleared after the --ACK-- of the **CLOSE** has been received by the other part.
 * to make sure that it has been received, clear the session some seconds after the ClientFileSender has sent the last **CLOSE** (if it has not received --ACK-- from **CLOSE**,
 * it will keep sending **CLOSE** messages).
 */

class FileReceiver implements Layer {

    private final Layer subLayer;

    private Lock completedLock;
    private Condition completedCondition;
    private boolean isCompleted;

    private String currentFileName;
    private BufferedWriter writer;

    public FileReceiver(ConnectionParameters params) {

        subLayer = new ConnectedLayer(params, this);

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
                ((ConnectedLayer)subLayer).requestSessionClear();
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
