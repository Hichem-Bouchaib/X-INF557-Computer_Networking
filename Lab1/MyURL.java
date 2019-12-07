public class MyURL {
	
	private String protocol;
	private String host;
	private String path;
	private int port;
	
	MyURL(String url){	
		this.port = -1;
		IllegalArgumentException error = new IllegalArgumentException("Invalid URL");

		// Extracting protocol
        String[] substrings = url.split("://");
        if (substrings.length != 2) {
        	throw error;
        }
        this.protocol = substrings[0];

        // Extracting path
        substrings = substrings[1].split("/", 2);
        if (substrings.length != 2) {
        	throw error;
        }
        this.path = "/" + substrings[1];

        // Extracting host and potential port
        if (substrings[0].contains(":")) {
        	substrings = substrings[0].split(":");
        	this.host = substrings[0];

        	// Handle the case the port is not a number
        	try {
        		this.port = Integer.parseInt(substrings[1]);
        	}catch (Exception e) {
        		throw error;
        	}
        } else {
        	this.host = substrings[0];
        }

	}
	
	public String getProtocol(){
		return this.protocol;
	}

	public String getHost(){
		return this.host;
	}

	public int getPort() {
		return this.port;
	}
	
	public String getPath() {
		return this.path;
	}


	public static void main(String[] args) {
		MyURL test = new MyURL("http://host/path/");
		System.out.println(test.getProtocol());
		System.out.println(test.getHost());
		System.out.println(test.getPort());
		System.out.println(test.getPath());
	}
}
