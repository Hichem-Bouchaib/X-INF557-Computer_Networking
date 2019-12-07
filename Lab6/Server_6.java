public class Server_6 {

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("syntax : java Server_6 myPort");
            return;
        }

        if (GroundLayer.start(Integer.parseInt(args[0]))) {
            GroundLayer.RELIABILITY = 0.3;
            DispatchLayer_6.start();

            while (true) {
                try {
                    ConnectionParameters newParams = DispatchLayer_6.accept();

                    new FileReceiver(newParams);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

    }
}
