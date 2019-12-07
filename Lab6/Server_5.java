public class Server_5 {

  public static void main(String[] args) {
    if (args.length != 1) {
      System.err.println("syntax : java Server_5 myPort");
      return;
    }
    if (GroundLayer.start(Integer.parseInt(args[0]))) {
      GroundLayer.RELIABILITY = 0.6;
      DispatchLayer.start();
    }
  }
}