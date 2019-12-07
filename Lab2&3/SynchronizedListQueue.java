import java.util.LinkedList;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Synchronized implementation with a LinkedList.
 */
public class SynchronizedListQueue implements URLQueue {

  private final LinkedList<String> queue;
  private ReentrantLock lock = new ReentrantLock();
  private Condition isEmpty = lock.newCondition();
  private Condition isFull = lock.newCondition();

  public SynchronizedListQueue() {
    queue = new LinkedList<String>();
  }

  public boolean isEmpty() {
    return queue.size() == 0;
  }

  public boolean isFull() {
    return false;
  }

  public synchronized void enqueue(String url) {
    queue.add(url);
  }

  public synchronized String dequeue() {
    return queue.remove();
  }

}
