import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class GroundLayer {

  /**
   * This {@code Charset} is used to convert between our Java native String
   * encoding and a chosen encoding for the effective payloads that fly over the
   * network.
   */
  private static final Charset CONVERTER = StandardCharsets.UTF_8;

  /**
   * This value is used as the probability that {@code send} really sends a
   * datagram. This allows to simulate the loss of packets in the network.
   */
  public static double RELIABILITY = 1.0;

  private static DatagramSocket localSocket = null;
  private static Layer aboveLayer = null;
  private static Thread receiver = null;

  public static boolean start(int localPort) {
    try {
      localSocket = new DatagramSocket(localPort);
    } catch (SocketException e) {
      System.err.println(e);
      return false;
    }
    Runnable r = new Runnable() {
      @SuppressWarnings("synthetic-access")
      @Override
      public void run() {
        // a single instance of buffer is enough to receive
        byte[] buffer = new byte[16384]; // such a size should be enough !
        // this buffer is shared as the storage for an incoming DatagramPacket
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        while (!Thread.interrupted()) {
          try {
            localSocket.receive(packet);
            String payload = new String(buffer, 0, packet.getLength(),
                CONVERTER);
            SocketAddress sender = packet.getSocketAddress();
           if (aboveLayer != null)
              aboveLayer.receive(payload, sender.toString());
          } catch (SocketException e) {
            System.err.println("-- socket closed --");
            Thread.currentThread().interrupt();
          } catch (IOException e) {
            System.err.println(e);
            Thread.currentThread().interrupt();
          }
        }
      }
    };
    receiver = new Thread(r, "GroundLayer.receiver");
    receiver.start();
    return true;
  }

  public static void deliverTo(Layer layer) {
    aboveLayer = layer;
  }

  public static void send(String payload, String destinationHost,
      int destinationPort) {
    if (localSocket != null && Math.random() <= RELIABILITY) {
      SocketAddress destination = new InetSocketAddress(destinationHost,
          destinationPort);
      byte[] buffer = payload.getBytes(CONVERTER);
      DatagramPacket packet = new DatagramPacket(buffer, buffer.length,
          destination);
      try {
        localSocket.send(packet);
      } catch (IOException e) {
        System.err.println(e);
      }
    }
  }

  public static void close() {
    receiver.interrupt();
    localSocket.close();
    System.err.println("GroundLayer closed");
  }

}
