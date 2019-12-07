import java.util.LinkedList;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Synchronized implementation with a LinkedList.
 */
public class BlockingListQueue implements URLQueue {
	
	private final int maxSize = 16;
	private final LinkedList<String> queue;
	private ReentrantLock lock = new ReentrantLock();
	private Condition notEmpty = lock.newCondition();
	private Condition notFull = lock.newCondition();

	public BlockingListQueue() {
		queue = new LinkedList<String>();
	}

	public boolean isEmpty() {
		return queue.size() == 0;
	}

	public boolean isFull() {
		return queue.size() == maxSize;
	}

	public void enqueue(String url) {
		lock.lock();
		try {
			while(this.isFull()) notFull.await();
			boolean wasEmpty = this.isEmpty();
			queue.add(url);
			if(wasEmpty) notEmpty.signalAll();
		} catch (InterruptedException e) {
			return;
		} finally {
			lock.unlock();
		}
	}

	public String dequeue() {
		lock.lock();
		try {
			while(this.isEmpty()) notEmpty.await();
			boolean wasFull = this.isFull();
			String result = queue.remove();
			if(wasFull) notFull.signalAll();
			return result;
		} catch (InterruptedException e) {
			System.out.println("END");
			return "";
		} finally {
			lock.unlock();
		}
	}

}

