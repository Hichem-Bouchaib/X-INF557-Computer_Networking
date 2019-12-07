import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

// The Worker implementing Runnable
class Blade implements Runnable{

	String url;
	String proxyHost;
	int proxyPort;

	Blade(String url, String proxyHost, int proxyPort){
		this.url = url;
		this.proxyHost = proxyHost;
		this.proxyPort = proxyPort;
	}

	@Override
	public void run() {
		Xurl.query(url, proxyHost, proxyPort);
		System.out.println(Thread.currentThread() + " was here.");
	}

}

public class Wget {

	public static void doIterative(String requestedURL, String proxyHost, int proxyPort) {
		final URLQueue queue = new ListQueue();
		final HashSet<String> seen = new HashSet<String>();

		URLprocessing.handler = new URLprocessing.URLhandler() {
			// this method is called for each matched url
			public void takeUrl(String url) {
				if (!seen.contains(url)) {
					seen.add(url);
					queue.enqueue(url);
				}
			}
		};

		// to start, we push the initial url into the queue
		URLprocessing.handler.takeUrl(requestedURL);
		while (!queue.isEmpty()) {
			String url = queue.dequeue();
			Xurl.query(url, proxyHost, proxyPort); // or equivalent yours
		}
	}

	public static void doMultiThreaded(String requestedURL, String proxyHost, int proxyPort) {
		final URLQueue queue = new SynchronizedListQueue();
		final HashSet<String> seen = new HashSet<String>();

		URLprocessing.handler = new URLprocessing.URLhandler() {
			// this method is called for each matched url
			public synchronized void takeUrl(String url) {
				if (!seen.contains(url)) {
					seen.add(url);
					queue.enqueue(url);
				}
			}
		};

		int activeThreadsBase = Thread.activeCount(); // Save the original value of numbers of threads
		// to start, we push the initial url into the queue
		URLprocessing.handler.takeUrl(requestedURL);
		while (!queue.isEmpty() || Thread.activeCount() > activeThreadsBase) {
			if (!queue.isEmpty()) {
				String url = queue.dequeue();
				Runnable runner = new Blade(url, proxyHost, proxyPort);
				Thread t = new Thread(runner);
				t.start();
			} else { // If the queue is empty, wait: maybe another thread is going to fill it!
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public static void doThreadedPool(String requestedURL, String proxyHost, int proxyPort) {
		/* We thought about this exercise with my fellow Flavien SOLT*/

		final URLQueue queue = new BlockingListQueue();
		final HashSet<String> seen = new HashSet<String>();
		ExecutorService exec = Executors.newFixedThreadPool(16);
		AtomicInteger activeThreads = new AtomicInteger(0); // Used to implement manually the number of active threads

		URLprocessing.handler = new URLprocessing.URLhandler() {
			// this method is called for each matched url
			public synchronized void takeUrl(String url) {
				if (!seen.contains(url)) {
					seen.add(url);
					queue.enqueue(url);
				}
			}
		};

		// to start, we push the initial url into the queue
		URLprocessing.handler.takeUrl(requestedURL);
		while (!queue.isEmpty() || activeThreads.get() > 0) {
			if (!queue.isEmpty()) {
				System.out.println(activeThreads.get());
				Runnable blade = new Runnable() {
                    @Override
                    public void run() {
                    	activeThreads.getAndIncrement(); // Update the number of active threads (++)
                    	String url = queue.dequeue();
                    	if (url.isEmpty()) return;
                        Xurl.query(url, proxyHost, proxyPort);
                        activeThreads.getAndDecrement(); // Idem (--)
                    }
                };
				exec.execute(blade);
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
			} else { // If the queue is empty, wait: maybe another thread is going to fill it!
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
        exec.shutdownNow();
	}

	public static void main(String[] args) {
		if (args.length < 1) {
			System.err.println("Usage: java Wget url [proxyHost proxyPort]");
			System.exit(-1);
		}
		String proxyHost = null;
		if (args.length > 1)
			proxyHost = args[1];
		int proxyPort = -1;
		if (args.length > 2)
			proxyPort = Integer.parseInt(args[2]);
		doThreadedPool(args[0], proxyHost, proxyPort);
	}

}
