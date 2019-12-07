import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Xurl {
	
	// Exo 2b
	private String urlString;         // URL to fetch
	private MyURL url;                // Parsed URL with MyURL module
	private String host;              
	private String path;
	private int port;
	private String fileName;          // File to write into
	private Socket socket;            // Socket (connection to the server)
	private BufferedReader buffer;    // Input Buffer
	private PrintWriter printStream;  // Output Stream
	
	// Exo 2c
	private Boolean proxyOn = false;  // true <=> Proxy is on
	private String proxy;             // Proxy URL
	private int proxyPort;            // Proxy PORT

	// Exo 2d
	private boolean isChunked = false;// Manage chunked responses

	Xurl(String url){
		System.setProperty("line.separator","\r\n"); // Use correct delimiters
		this.urlString = url;
		this.url      = new MyURL(url);
		this.host     = this.url.getHost();
		this.path     = this.url.getPath();
		this.port     = (this.url.getPort() >= 0) ? this.url.getPort() : 80; // Set default port to 80
		this.fileName = !path.substring(path.lastIndexOf("/")+1).isEmpty() ? path.substring(path.lastIndexOf("/")+1) : "index"; // Set default fileName to index
	}

	Xurl(String url, String proxy, String proxyPort){
		this(url);
		this.proxy = proxy;
		this.proxyPort = Integer.parseInt(proxyPort);
		this.proxyOn = true;
	}

	public void connectToServer() throws UnknownHostException, IOException {
		/* Establish the connection with the server */

		this.socket = proxyOn ? new Socket(proxy, proxyPort) : new Socket(host, port);
	}

	public void sendRequest() throws IOException {
		/* Send the GET request to the server */

		// Wrap the socket output stream into a print stream
		this.printStream = new PrintWriter(socket.getOutputStream());
		
		String firstArg = proxyOn ? urlString:path;
		printStream.printf("GET %s HTTP/1.1\r\nHost: %s \r\n\r\n", firstArg, host);
		printStream.flush();
	}
	
	public void getReply() throws IOException {
		/* Get the reply from the server */

		// Convert inputStream to a Buffer
		this.buffer = new BufferedReader(new InputStreamReader(socket.getInputStream()));
	}

	public void parseHeader() throws IOException {
		/* Parse header information and manager error codes */

		String currentLine;
		
		// Be sure to set the buffer pointer to the very beginning the HTTP reply
		do { currentLine = buffer.readLine(); } while (!currentLine.contains("HTTP/1"));
		this.checkError(currentLine);
		
		// Read the header until the HTML content is reached, and check for content length and chunked transfer encoding
		Pattern patternChunked = Pattern.compile("Transfer-Encoding:\\s(.+)+$");
		Matcher matcherChunked;
		do {
			currentLine = buffer.readLine();
        	matcherChunked = patternChunked.matcher(currentLine);
    		if (matcherChunked.find()) {
	   			this.isChunked = matcherChunked.group(1).equals("chunked");
	   		}
		} while(!currentLine.equals(""));
	}
	
	public void checkError(String line) {
		/* Check HTTP Error code and exit if necessary */

		// Get Error Code with regex
		int code = 0;
		Pattern pattern = Pattern.compile("HTTP/1\\S* (\\d+)+.*");
		Matcher matcher = pattern.matcher(line);
		if (matcher.find()) {
			 code = Integer.parseInt(matcher.group(1));
		}

		switch(code) {
			case 200:
				break;
			case 301:
				System.err.println("URL Moved Permanently");
				System.exit(0);
				break;
			case 203:
				System.err.println("URL Moved Temporarily");
				System.exit(0);
				break;
			case 400:
				System.err.println("Bad Request");
				System.exit(0);
				break;
			case 404:
				System.err.println("Not Found");
				System.exit(0);
				break;
			default:
				System.err.println("Unexpected HTTP Error: "+code);
				System.exit(0);
				break;
		}
	}

	public void writeHTMLToFile() throws IOException {
		/* Extract HTML content from the buffer and write it locally
		 * Inspired by: https://howtodoinjava.com/java/io/how-to-read-data-from-inputstream-into-string-in-java/
		 */

		PrintStream file = new PrintStream(new File(this.fileName));
        int chunkLength;
        int currentLength;
        String line;

        if (isChunked){
    		line = buffer.readLine();
    		chunkLength = Integer.parseInt(line, 16); // Len is encoded in Hexa
    		while(chunkLength>0) {
    			currentLength = chunkLength;

    			// Read the current chunk char by char and print it to file
    			while(currentLength>0) {
    				file.print((char) buffer.read());
    				currentLength--;
    			}

    			// Get rid of empty lines
    			line = buffer.readLine();
    			while (line.isEmpty()) {
    				line = buffer.readLine();
    			}
  
    			chunkLength = Integer.parseInt(line, 16);
    		}

        } else { // Read and write the buffer line by line until the EOF
            do {
            	file.println(buffer.readLine());
            } while(buffer.ready());
        }

        file.close();
	}


	public void disconnect() throws IOException {
		/* Close connection with the server */

		this.socket.close();
		this.printStream.close();
		this.buffer.close();
		System.exit(0);
	}
	
	public void run() throws UnknownHostException, IOException {
		/* Run the code in the right order */

		this.connectToServer();
		this.sendRequest();
		this.getReply();
		this.parseHeader();
		this.writeHTMLToFile();
	}

	public static void main(String[] args) {
		try {
			if (args.length == 1) { // No proxy case
				Xurl downloader = new Xurl(args[0]);
				downloader.run();
				downloader.disconnect();
			} else if (args.length == 3) { // Proxy case
				Xurl downloader = new Xurl(args[0], args[1], args[2]);
				downloader.run();
				downloader.disconnect();
			} else {
				throw new IllegalArgumentException("You should enter exactly 1 or 3 arguments, but "+ args.length +" entered.");
			}
        } catch (Exception e) {
            e.printStackTrace();
        }
	}

}
