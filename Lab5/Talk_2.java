import java.util.Scanner;

class SimpleLayer_2 implements Layer {

	private final Layer subLayer;

	public SimpleLayer_2(String host, int port) {
		subLayer = new ConnectedLayer(host, port, (int) (Math.random() * Integer.MAX_VALUE));
		subLayer.deliverTo(this);
	}

	@Override
	public void send(String payload) {
		subLayer.send(payload);
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
		subLayer.close();
	}

}

public class Talk_2 {

	public static void main(String[] args) {
		if (args.length != 3) {
			System.err.println(
					"syntax : java Talk_2 myPort destinationHost destinationPort ");
			return;
		}
		if (GroundLayer.start(Integer.parseInt(args[0]))) {
			// GroundLayer.RELIABILITY = 0.5;
			Layer myTalk = new SimpleLayer_2(args[1], Integer.parseInt(args[2]));
			Scanner sc = new Scanner(System.in);
			while (sc.hasNextLine()) {
				myTalk.send(sc.nextLine());
			}
			System.out.println("closing");
			sc.close();
			myTalk.close();
			GroundLayer.close();
		}
	}
}
