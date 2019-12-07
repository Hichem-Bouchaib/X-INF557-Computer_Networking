import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;

class SimpleLayer_1 implements Layer {

	private final String destinationHost;
	private final int destinationPort;

	public SimpleLayer_1(String host, int port) {
		destinationHost = host;
		destinationPort = port;
	}

	@Override
	public void send(String payload) {
		GroundLayer.send(payload, destinationHost, destinationPort);
	}

	@Override
	public void receive(String payload, String sender) {
		System.out.println('"' + payload + "\" from " + sender);
	}

	@Override
	public void deliverTo(Layer above) {
		throw new UnsupportedOperationException("don't support any Layer above");
	}

	@Override
	public void close() { // nothing
	}

}

class TickLayer_1 implements Layer {

	private static final Timer TIMER = new Timer("TickTimer", true);
	private final TimerTask task;

	public TickLayer_1(String host, int port, String message, int delay) {
		task = new TimerTask() {
			@Override
			public void run() {
				GroundLayer.send(message, host, port);
			}
		};
		TIMER.schedule(task, 0, delay);
	}

	@Override
	public void send(String payload) {
		throw new UnsupportedOperationException("don't relay");
	}

	@Override
	public void receive(String payload, String sender) {
		System.out.println('"' + payload + "\" from " + sender);
	}

	@Override
	public void deliverTo(Layer above) {
		throw new UnsupportedOperationException("don't support any Layer above");
	}

	@Override
	public void close() {
		task.cancel();
		System.err.println("ticker stopped");
	}

}

public class Talk_1 {

	public static void main(String[] args) {
		if (args.length != 3) {
			System.err.println(
					"syntax : java Talk_1 myPort destinationHost destinationPort ");
			return;
		}
		if (GroundLayer.start(Integer.parseInt(args[0]))) {
			// GroundLayer.RELIABILITY = 0.5;
			Layer tick_1 = new TickLayer_1(args[1], Integer.parseInt(args[2]),
					"Hello", 2000);
			Layer tick_2 = new TickLayer_1(args[1], Integer.parseInt(args[2]) + 1,
					"Hi", 3000);
			Layer myTalk = new SimpleLayer_1(args[1], Integer.parseInt(args[2]));
			GroundLayer.deliverTo(myTalk);
			Scanner sc = new Scanner(System.in);
			while (sc.hasNextLine()) {
				myTalk.send(sc.nextLine());
			}
			System.out.println("closing");
			sc.close();
			myTalk.close();
			tick_1.close();
			tick_2.close();
			GroundLayer.close();
		}
	}
}
