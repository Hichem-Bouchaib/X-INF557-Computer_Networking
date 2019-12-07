public class ConnectionParameters {
    public String remoteHost;
    public int remotePort;
    public int remoteConnectionId;
    public int localConnectionId;

    public ConnectionParameters(String remoteHost, int remotePort, int remoteConnectionId, int localConnectionId) {
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
        this.remoteConnectionId = remoteConnectionId;
        this.localConnectionId = localConnectionId;
    }
}
